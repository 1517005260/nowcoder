package com.nowcoder.community;

import com.nowcoder.community.service.AlphaService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
public class ThreadPoolTests {
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolTests.class);

    // jdk普通线程池
    private ExecutorService executorService = Executors.newFixedThreadPool(5); // 包含5个线程

    // jdk定时线程池
    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(5);

    // spring普通线程池
    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    // spring定时线程池
    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    private AlphaService alphaService;

    // 阻塞程序，防止程序过快结束
    private void sleep(long m){
        try{
            Thread.sleep(m);  // 毫秒
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }

    @Test
    public void textJDKExecutorService(){
        Runnable task = new Runnable() {   // 给这个线程分配一个任务
            @Override
            public void run() {
                logger.debug("This is jdk ExecutorService");
            }
        };

        for(int i = 0; i < 10; i++){
            executorService.submit(task);
        }

        sleep(10000);  // 10s
    }

    @Test
    public void textJDKScheduledExecutorService(){
        Runnable task = new Runnable() {
            @Override
            public void run() {
                logger.debug("This is jdk ScheduledExecutorService");
            }
        };

        // 以固定频率定时执行,参数：任务、任务延迟多久才执行、时间间隔、时间单位
        scheduledExecutorService.scheduleAtFixedRate(task, 10000, 1000, TimeUnit.MILLISECONDS) ;
        sleep(30000); // 30s结束任务
    }

    @Test
    public void springTaskExecutor(){
        Runnable task = new Runnable() {
            @Override
            public void run() {
                logger.debug("This is jdk Spring TaskExecutor");
            }
        };

        for(int i = 0; i < 10; i++){
            taskExecutor.submit(task);
        }
        sleep(10000);
    }

    @Test
    public void springTaskScheduler(){
        Runnable task = new Runnable() {
            @Override
            public void run() {
                logger.debug("This is Spring TaskScheduler");
            }
        };

        Date startTime = new Date(System.currentTimeMillis() + 10000);  // 系统时 + 10s
        taskScheduler.scheduleAtFixedRate(task, startTime, 1000);  // 间隔1s
        sleep(30000); // 30s结束任务
    }

    @Test
    public void testEasyMethod(){
        // 相当于 Runnable 被封装在了 alphaService
        for(int i = 0; i < 10; i++){
            alphaService.execute1();
        }
        sleep(10000);
    }

    public @Test void testEasyMethod2(){
        // 方法会被自动调
        sleep(30000);
    }
}
