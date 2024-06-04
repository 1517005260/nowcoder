package com.nowcoder.community;

import com.nowcoder.community.entity.DiscussPost;
import com.nowcoder.community.service.DiscussPostService;
import org.junit.*;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
public class SpringBootTests {

    @Autowired
    private DiscussPostService discussPostService;

    private DiscussPost data;

    @BeforeClass
    public static void beforeClass(){
        System.out.println("before class");
    }

    @AfterClass
    public static void afterClass(){
        System.out.println("after class");
    }

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
    public void test1(){
        System.out.println("test1");
    }

    @Test
    public void test2(){
        System.out.println("test2");
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
}
