# 单元测试

项目上线前的单元测试——让项目质量有保障

当然，之前我们每开发一个功能，也都进行了单元测试

- Spring Boot Testing
  - 依赖：spring-boot-starter-test
  - 包括：Junit，Spring Test，AssertJ……
- Test Case（测试用例）
  - 要求：保证测试方法的独立性（A方法和B方法不能有数据依赖）
  - 步骤：初始化数据、执行测试代码、验证测试结果、清理测试数据 `数据单独为测试服务`
  - 常用注解：`@BeforeClass 类初始化前`, `@AfterClass 类销毁前`, `@Before`, `@After`

## 执行流程的例子

新建测试类

```java
package com.nowcoder.community;

import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
public class SpringBootTests {

  // 这个注解只能修饰静态方法，因为在类初始化之前执行
    @BeforeClass
    public static void beforeClass(){
        System.out.println("before class");
    }

    @AfterClass
    public static void afterClass(){
        System.out.println("after class");
    }

  // 这个注解在每个测试方法执行之前执行
    @Before
    public void before(){
        System.out.println("before");
    }

    @After
    public void after(){
        System.out.println("after");
    }

    @Test
    public void test1(){
        System.out.println("test1");
    }

    @Test
    public void test2(){
        System.out.println("test2");
    }
}
```

可以若仅测试test1，看到控制台仅输出：

```
before
test1
after
```

而before class和after class是在测试类加载前、销毁前输出的 

即：`before class ==> ( before ==> test ==> after) * n ==> after class`

## 一个实际的例子

在刚刚的测试方法中：

```java
@Autowired
private DiscussPostService discussPostService;

private DiscussPost data;

@Before
public void before(){
  System.out.println("before");

  // 初始化测试数据
  data = new DiscussPost();
  data.setUserId(111);
  data.setTitle("Test");
  data.setContent("Test content");
  data.setCreateTime(new Date());
  discussPostService.addDiscussPost(data);
}

@After
public void after(){
  System.out.println("after");

  // 删除测试数据
  discussPostService.updateStatus(data.getId(), 2);
}

@Test
public void testFindById() {
  DiscussPost post = discussPostService.findDiscussPostById(data.getId());  // data是期望，post是实际
  // 通过断言判断是否符合预期  断言：成立什么都不发生，不成立抛异常
  Assertions.assertNotNull(post);  // 判断查询结果是否非空，抛异常表示帖子为空
  Assertions.assertEquals(data.getTitle(), post.getTitle());
  Assertions.assertEquals(data.getContent(), post.getContent());
}

@Test
public void testUpdateScore() {
  int rows = discussPostService.updateScore(data.getId(), 2000.00);
  Assertions.assertEquals(1, rows);

  DiscussPost post = discussPostService.findDiscussPostById(data.getId());
  // 小数比较时，需要指定一个误差范围：2000 +- 2
  Assertions.assertEquals(2000.00, post.getScore(), 2);
}
```

这样，调test1方法，就会为test1生成一份数据，用完即删。调test2时又会新生成一份数据。坏处是效率低，好处是相互独立，变量名相同但是数据不同