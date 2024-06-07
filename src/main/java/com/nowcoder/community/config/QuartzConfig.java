package com.nowcoder.community.config;

import com.nowcoder.community.quartz.AlphaJob;
import com.nowcoder.community.quartz.PostScoreRefreshJob;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean;

// 配置 ——> 初始化到数据库，以后Quartz仅访问数据库不访问本配置文件
@Configuration
public class QuartzConfig {

    // FactoryBean 和 BeanFactory（IoC）：后者是IoC的容器顶层接口，而前者可简化Bean的实例化过程
    // ex：JobDetailFactoryBean 封装了JobDetail的详细实例化过程
    // 1.Spring通过FactoryBean封装了Bean的实例化过程
    // 2.我们可以将FactoryBean装配到Spring容器里，注入给其他bean，其他bean得到的是FactoryBean所管理的对象实例

    // 配置JobDetail
    // @Bean
    public JobDetailFactoryBean alphaJobDetail(){
        JobDetailFactoryBean factoryBean = new JobDetailFactoryBean();
        factoryBean.setJobClass(AlphaJob.class);
        factoryBean.setName("alphaJob");  // 名称是唯一的
        factoryBean.setGroup("alphaJobGroup");
        factoryBean.setDurability(true);  // 是持久保存的
        factoryBean.setRequestsRecovery(true); // 任务是可恢复的（redo）
        return factoryBean;
    }

    // 配置Trigger（SimpleTriggerFactoryBean or CronTriggerFactoryBean，前者简单后者复杂）
    // @Bean
    public SimpleTriggerFactoryBean alphaTrigger(JobDetail alphaJobDetail){
        SimpleTriggerFactoryBean factoryBean = new SimpleTriggerFactoryBean();
        factoryBean.setJobDetail(alphaJobDetail);  // 是谁的触发器
        factoryBean.setName("alphaTrigger");
        factoryBean.setGroup("alphaTriggerGroup");
        factoryBean.setRepeatInterval(3000); // 频率：3s
        factoryBean.setJobDataMap(new JobDataMap()); // 存Job的状态，用默认的JobDataMap类型
        return factoryBean;
    }

    // 刷新帖子分数Job
    @Bean
    public JobDetailFactoryBean postScoreRefreshJobDetail(){
        JobDetailFactoryBean factoryBean = new JobDetailFactoryBean();
        factoryBean.setJobClass(PostScoreRefreshJob.class);
        factoryBean.setName("postScoreRefreshJob");
        factoryBean.setGroup("communityJobGroup");
        factoryBean.setDurability(true);
        factoryBean.setRequestsRecovery(true);
        return factoryBean;
    }

    @Bean
    public SimpleTriggerFactoryBean postScoreRefreshTrigger(JobDetail postScoreRefreshJobDetail){
        SimpleTriggerFactoryBean factoryBean = new SimpleTriggerFactoryBean();
        factoryBean.setJobDetail(postScoreRefreshJobDetail);
        factoryBean.setName("postScoreRefreshTrigger");
        factoryBean.setGroup("CommunityTriggerGroup");
        factoryBean.setRepeatInterval(1000 * 60 * 60); // 定时任务：1h
        factoryBean.setJobDataMap(new JobDataMap());
        return factoryBean;
    }
}
