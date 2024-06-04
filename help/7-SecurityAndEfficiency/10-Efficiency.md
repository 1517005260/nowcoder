# 优化网站性能

- 本地缓存
  - 将数据缓存在应用服务器上，性能最好
  - 常用缓存工具：Ehcache, Guava, Caffeine等
- 分布式缓存（仅比本地缓存多了网络开销）
  - 将数据缓存在NoSQL数据库上，跨服务器（比如登录凭证等）
  - 常用缓存工具：MemCache, Redis等
- 多级缓存
  - 一级缓存（本地缓存） ==> 二级缓存（分布式缓存） ==> 三级缓存（DB）
  - 避免缓存雪崩（缓存失效，大量请求直达DB）， 提高系统可用性

缓存示例：

![缓存](/imgs/efficience1.png)

多级缓存：若访问不到则级级向下找，直到找到DB，然后将DB数据级级向上缓存

![多级缓存](/imgs/efficiency.png)

缓存淘汰：详见计组的cache替换策略

## 代码实现

目标：优化热门帖子列表（随时间变化不大，隔一段时间才变一次）

使用本地缓存[Caffeine](https://github.com/ben-manes/caffeine)

1. 导包

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

2. 自定义参数

在application.properties中：

```
# caffeine
caffeine.posts.max-size=15 # 仅缓存15个数据就够了，用户一般不会再往后看了
caffeine.posts.expire-seconds=180 # 3分钟定时淘汰
```

3. 优化Service方法，因为Controller最后调的都是Service

DiscussPostService:

```java
@Value("${caffeine.posts.max-size}")
private int maxSize;

@Value("${caffeine.posts.expire-seconds}")
private int expireSeconds;

// 帖子列表（热帖）缓存
private LoadingCache<String, List<DiscussPost>> postListCache;

// 缓存帖子总数
private LoadingCache<Integer, Integer> postRowsCache;

// 初始化缓存
@PostConstruct
public void init(){
  postListCache = Caffeine.newBuilder()
          .maximumSize(maxSize)
          .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
          .build(new CacheLoader<String, List<DiscussPost>>() {
            @Override
            public @Nullable List<DiscussPost> load(String key) throws Exception { // load实质上查询了数据库
              if(key == null || key.length() ==0){
                throw new IllegalArgumentException("参数错误！");
              }
              String[] params = key.split(":");
              if(params == null && params.length != 2){
                throw new IllegalArgumentException("参数错误！");
              }
              int offset = Integer.parseInt(params[0]);
              int limit = Integer.parseInt(params[1]);

              // 可以在这里访问redis建立多级缓存，如果没有数据再进入db查找

              logger.debug("load post list from DB!");
              return discussPostMapper.selectDiscussPosts(0, offset, limit, 1);
            }
          });

  postRowsCache = Caffeine.newBuilder()
          .maximumSize(maxSize)
          .expireAfterWrite(expireSeconds, TimeUnit.SECONDS)
          .build(new CacheLoader<Integer, Integer>() {
            @Override
            public @Nullable Integer load(Integer key) throws Exception {
              logger.debug("load post rows from DB !");
              return discussPostMapper.selectDiscussPostRows(key);
            }
          });
}

public List<DiscussPost> findDiscussPosts(int userId, int offset, int limit, int orderMode){
  // offset 和 limit 作为key标识一页
  if(userId == 0 && orderMode ==1){
    // 仅缓存热帖
    return postListCache.get(offset + ":" + limit); // 直接从缓存返回结果
  }
  logger.debug("load post list from DB!");
  return discussPostMapper.selectDiscussPosts(userId, offset,limit, orderMode);
}

public int findDiscussPostRows(int userId){
  if(userId == 0){
    // 首页查询时缓存数量
    return postRowsCache.get(userId);
  }
  logger.debug("load post rows from DB !");
  return discussPostMapper.selectDiscussPostRows(userId);
}
```

Caffeine核心接口：Cache，LoadingCache（同步缓存），AsyncLoadingCache（异步缓存）

4. 测试类测试是否真的缓存了：

```java
package com.nowcoder.community;

import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.service.DiscussPostService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
public class CaffeineTests {
  @Autowired
  private DiscussPostService discussPostService;

  @Test
  public void initDataForTest() {  // 压力测试前置，先插入30w条数据，否则缓存的优势看不出来
    for (int i = 0; i < 300000; i++) {
      DiscussPost post = new DiscussPost();
      post.setUserId(111);
      post.setTitle("互联网求职暖春计划");
      post.setContent("压力测试！！！！");
      post.setCreateTime(new Date());
      post.setScore(Math.random() * 2000);
      discussPostService.addDiscussPost(post);
    }
  }

  @Test
  public void testCache(){
    System.out.println(discussPostService.findDiscussPosts(0, 0, 10, 1));
    System.out.println(discussPostService.findDiscussPosts(0, 0, 10, 1));
    System.out.println(discussPostService.findDiscussPosts(0, 0, 10, 1));
    // 以上三次应该只访问一次数据库，即只输出一次logger-debug
    System.out.println(discussPostService.findDiscussPosts(0, 0, 10, 0));
  }
}
```

5. 压力测试——[JMeter](https://jmeter.apache.org/download_jmeter.cgi)

下载完成后进入bin目录双击jmeter.bat启动

jmeter配置如下测试：

![jmeter](/imgs/jmeter.png)


![jmeter1](/imgs/jmeter1.png)

![jmeter2](/imgs/jmeter2.png)

![jmeter3](/imgs/jmeter3.png)

开始压力测试，主要关注吞吐量：

未用缓存：

![jmeter4](/imgs/jmeter4.png)

用了缓存：

![jmeter5](/imgs/jmeter5.png)

可以发现吞吐量显著提升，而且后台的sql日志仅输出了一次