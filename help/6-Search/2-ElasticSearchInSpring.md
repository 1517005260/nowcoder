# Spring整合ElasticSearch

- 引入依赖
  - spring-boot-starter-data-elasticsearch
- 配置Elasticsearch
  - cluster-name、cluster-nodes`经典Master-Slave结构`
- Spring Data Elasticsearch
  - ElasticsearchTemplate
  - ElasticsearchRepository

## 导包

```xml
<dependency>
<groupId>org.springframework.boot</groupId>
<artifactId>spring-boot-starter-data-elasticsearch</artifactId>
<version>3.1.1</version>
</dependency>
```

## 配置

1. 在application.properties中

```
# elasticsearch
spring.elasticsearch.uris=127.0.0.1:9200
```

2. 解决冲突：redis和es都是基于netty的，启动时会冲突（其实我的es7已经解决了这个问题，接下来的代码可以不用写的）

相关源码：

NettyRuntime:

```java
synchronized void setAvailableProcessors(int availableProcessors) {
    ObjectUtil.checkPositive(availableProcessors, "availableProcessors");
    if (this.availableProcessors != 0) {
        String message = String.format(Locale.ROOT, "availableProcessors is already set to [%d], rejecting [%d]", this.availableProcessors, availableProcessors);
        throw new IllegalStateException(message);
    } else {
        this.availableProcessors = availableProcessors;
    }
}
```

我们关闭这个函数的冲突检测功能，在CommunityApplication主类中：

```java
@PostConstruct
public void init(){
    // 解决netty启动冲突问题
    System.setProperty("es.set.netty.runtime.available.processors", "false");
}
```


## 搜索——把es看成一个单独数据库

把数据库里存的帖子复制到es服务器里，让es能够搜索

1. 优先用简单的`ElasticsearchRepository`解决，解决不了的再上template

映射设置——es的索引和sql的表discussPost（之后操作的时候，spring发现es没有该索引，会自动创建一一映射）==> 相当于在es中建表

在实体类DiscussPost上加注解：

```java
@Document(indexName = "discusspost") // 映射到es的索引discusspost，注意es的索引全小写
public class DiscussPost {
    @Id // es主键id
    private int id;
    @Field(type = FieldType.Integer) // 普通类型field
    private int userId;

    // analyzer是存储时的分词器，应该尽可能详细，拆分尽可能多的词汇  ex.互联网校招 = 互联、联网、互联网、网校、校招
    // searchAnalyzer是搜索时的分词器，应该尽可能模糊，拆分合适的词汇 ex.互联网校招= 互联网、校招
    // 这样，一个搜索分词可以尽可能匹配到更多的存储索引（召回率↑），但是过于精细的拆分会导致噪声（互联网校招和网校无关），因此需要取舍
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")  // 指定存储解析器和搜索解析器
    private String title;  // es搜索关键字
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content; // es搜索关键字
    @Field(type = FieldType.Integer)
    private int type;
    @Field(type = FieldType.Integer)
    private int status;
    @Field(type = FieldType.Date)
    private Date createTime;
    @Field(type = FieldType.Integer)
    private int commentCount;
    @Field(type = FieldType.Double)
    private double score;
}
```

2. 在dao新建子包elasticsearch

新建接口DiscussPostRepository:

```java
package com.nowcoder.community.dao.elasticsearch;

import com.nowcoder.community.entity.DiscussPost;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

// @Mapper 是MyBatis专用的注解，@Repository 是Spring 自带的数据库层注解
@Repository
public interface DiscussPostRepository  extends ElasticsearchRepository<DiscussPost,Integer> {  // 泛型声明查询类型和主键类型
    // 具体逻辑spring已经实现
}
```

3. 新建测试类ElasticSearchTests

```java
package com.nowcoder.community;

import com.nowcoder.community.dao.DiscussPostMapper;
import com.nowcoder.community.dao.elasticsearch.DiscussPostRepository;
import com.nowcoder.community.entity.DiscussPost;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.*;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.test.context.ContextConfiguration;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
public class ElasticSearchTests {
    @Autowired
    private DiscussPostMapper discussMapper;  // 从mysql中调数据往es存

    @Autowired
    private DiscussPostRepository discussRepository;

    @Autowired
    private ElasticsearchTemplate elasticTemplate;  // 以防有些时候Repository处理不了

    @Test
    public void insert() {  // 单条数据插入
        discussRepository.save(discussMapper.selectDiscussPostById(241));
        discussRepository.save(discussMapper.selectDiscussPostById(242));
        discussRepository.save(discussMapper.selectDiscussPostById(243));
        // 结果查看：postman GET  localhost:9200/discusspost/_search
    }

    @Test
    public void insertList() {  // 多条数据插入
        discussRepository.saveAll(discussMapper.selectDiscussPosts(101, 0, 100));  // 肯定条数不足limit，这里是配合mapper方法进行分页
        discussRepository.saveAll(discussMapper.selectDiscussPosts(102, 0, 100));
        // 结果查看：postman GET  localhost:9200/discusspost/_search
    }

    @Test
    public void update() {  // 修改
        DiscussPost post = discussMapper.selectDiscussPostById(241);
        post.setContent("我爱中国");
        discussRepository.save(post);
        // 结果查看：postman GET  localhost:9200/discusspost/_doc/241
    }

    @Test
    public void delete() {  // 删除一条
        discussRepository.deleteById(241);
        // 结果查看：postman GET  localhost:9200/discusspost/_doc/241
    }

    @Test
    public void deleteall(){  // rm -rf /*
        discussRepository.deleteAll();
        // 结果查看：postman GET  localhost:9200/discusspost/_search
    }

    @Test
    public void searchByRepository(){  // 搜索(Repository)
        // es 7 已经不支持了
    }

  @Test
  public void searchByTemplate() {  // 搜索(Template)  --更完善
    // 查询标准构建，匹配字段"content"和"title"中包含"互联网寒冬"关键字的数据
    // 在这里使用matches，而不是contains，contains必须包含完整的关键字，而matches不需要，如果要匹配更多字段使用or或者and
    Criteria criteria = new Criteria("title").matches("互联网寒冬").or(new Criteria("content").matches("互联网寒冬"));

    // 高亮前端格式：<em></em>
    List<HighlightField> highlightFieldList = new ArrayList<>();
    HighlightField highlightField = new HighlightField("title", HighlightFieldParameters.builder().withPreTags("<em>").withPostTags("</em>").build());
    highlightFieldList.add(highlightField);
    highlightField = new HighlightField("content", HighlightFieldParameters.builder().withPreTags("<em>").withPostTags("</em>").build());
    highlightFieldList.add(highlightField);
    Highlight highlight = new Highlight(highlightFieldList);
    HighlightQuery highlightQuery = new HighlightQuery(highlight, DiscussPost.class);

    // 构建细化查询
    CriteriaQueryBuilder builder = new CriteriaQueryBuilder(criteria)   // 指定关键词
            .withSort(Sort.by(Sort.Direction.DESC, "type"))  // 置顶
            .withSort(Sort.by(Sort.Direction.DESC, "score"))  // 热度
            .withSort(Sort.by(Sort.Direction.DESC, "createTime"))  // 时间
            .withHighlightQuery(highlightQuery)  // 指定关键词高亮格式
            .withPageable(PageRequest.of(0, 10));  //  按分页查询（第几页，显示几条数据）
    CriteriaQuery query = new CriteriaQuery(builder);

    // 通过elasticsearchTemplate查询
    SearchHits<DiscussPost> result = elasticTemplate.search(query, DiscussPost.class);  // 传入查询条件和实体类型

    // 处理结果
    List<SearchHit<DiscussPost>> searchHitList = result.getSearchHits();
    List<DiscussPost> discussPostList = new ArrayList<>();
    if(searchHitList != null){
      for (SearchHit<DiscussPost> hit : searchHitList) {
        // 遍历每个搜索命中对象
        DiscussPost post = hit.getContent();
        // 将高亮结果添加到返回的结果类中显示
        var titleHighlight = hit.getHighlightField("title");
        if (titleHighlight.size() != 0) {  // 如果标题中包含<em></em>
          post.setTitle(titleHighlight.get(0)); // 那么就在内存中暂时修改格式，无Mapper不涉及数据库
        }
        var contentHighlight = hit.getHighlightField("content");
        if (contentHighlight.size() != 0) {
          post.setContent(contentHighlight.get(0));
        }
        discussPostList.add(post);
      }
    }

    // 构建Page对象
    Page<DiscussPost> page = new PageImpl<>(discussPostList, PageRequest.of(1, 10), result.getTotalHits());

    // 打印结果
    System.out.println(result.getTotalHits());
    for (DiscussPost post : page) {
      System.out.println(post);
    }
  }
}
```

## 有关搜索的解读

### 示例：

假设我们有以下帖子：

- **标题**：科技行业现状
- **内容**：随着技术的快速发展，许多公司面临“互联网寒冬”的挑战。

### 高亮显示后的处理：

在执行搜索并高亮显示“互联网寒冬”后，假设`titleHighlight`和`contentHighlight`的结果如下：

- **titleHighlight**：[]
- **contentHighlight**：["随着技术的快速发展，许多公司面临<em>互联网寒冬</em>的挑战。"]

### 代码处理：

```java
var titleHighlight = hit.getHighlightField("title");
if (titleHighlight.size() != 0) {
    post.setTitle(titleHighlight.get(0));
}
var contentHighlight = hit.getHighlightField("content");
if (contentHighlight.size() != 0) {
    post.setContent(contentHighlight.get(0));
}
```

### 结果：

- **标题**：科技行业现状
- **内容**：随着技术的快速发展，许多公司面临<em>互联网寒冬</em>的挑战。

### 解读：

- `titleHighlight`为空，所以标题保持不变。
- `contentHighlight`不为空，所以内容被更新为包含高亮显示的结果，用户可以看到“互联网寒冬”被高亮显示。


## 测试遇到问题：co.elastic.clients.transport.TransportException: [es/search] Missing [X-Elastic-Product] header</b>

解决：

重新导包：

```xml
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
</dependency>
```

并添加es客户端配置类：

```java
package com.nowcoder.community.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.util.ContentType;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ElasticsearchConfig {

    private static final String IP = "127.0.0.1";
    private static final Integer PORT = 9200;

    @Bean(name = "esClient")
    public ElasticsearchClient getClient() {
        RestClient restClient = RestClient.builder(new HttpHost(IP, PORT))
                .setHttpClientConfigCallback(httpClientBuilder
                        ->httpClientBuilder.setDefaultHeaders(
                                List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString())))
                        .addInterceptorLast((HttpResponseInterceptor) (response, context)
                                -> response.addHeader("X-Elastic-Product", "Elasticsearch"))).build();
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper());
        ElasticsearchClient client = new ElasticsearchClient(transport);
        return client;
    }
}
```

问题解决，测试通过