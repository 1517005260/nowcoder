package com.nowcoder.community.actuator;

import com.nowcoder.community.util.CommunityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@Endpoint(id = "database")
public class DataBaseEndPoint {

    private final Logger logger = LoggerFactory.getLogger(DataBaseEndPoint.class);

    // 连接池顶层接口
    @Autowired
    private DataSource dataSource;

    // 尝试访问链接，失败则数据库链接有问题
    // @ReadOperation代表GET请求访问该端点时，会调用该方法
    @ReadOperation
    public String checkConnection() {
        try (
                var ignored = dataSource.getConnection();
        ) {
            return CommunityUtil.getJSONString(0, "获取连接成功!");
        } catch (Exception e) {
            logger.error("获取连接失败: " + e.getMessage());
            return CommunityUtil.getJSONString(1, "获取连接失败!");
        }
    }
}
