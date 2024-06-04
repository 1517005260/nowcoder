# 项目监控

怎么判断项目运行时是否健康而稳定？

- Spring Boot Actuator —— 弥补了Spring Boot的过度封装，使底层暴露出来
  - EndPoints：监控应用的入口，Spring Boot内置了很多端点，也支持自定义端点
  - 监控方式：HTTP或JMX
  - 访问路径：例如`/actuator/health`
  - 注意事项：按需配置暴露的端点（如果一个端点从来不用就不需要配了），并对所有的端点进行权限控制

## 例子

1. 导包

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

2. 导包完成后，立即使得路径生效，但是不是所有的端点都是默认暴露的（仅停止服务器的端点未配置，一般不建议配置），默认仅暴露2个端点，共20+个端点

默认暴露：`http://localhost:8080/community/actuator/health` 返回up则健康

和 `http://localhost:8080/community/actuator/info`  默认为`[]`

3. 配置

在application.properties中：

```
# actuator
management.endpoints.web.exposure.include=* # 启动端点
management.endpoints.web.exposure.exclude=info,caches # 禁用端点
```

4. 自制端点——监控数据库连接是否正常

a. 新建软件包actuator

b. 新建DataBaseEndPoint

```java
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
```

c. 权限配置

```java
.requestMatchers(
                        "/discuss/delete",
                        "/data/**",
                        "/user/updatetype",
                        "/actuator/**"
                ).hasAnyAuthority(
                        AUTHORITY_ADMIN
                )
```