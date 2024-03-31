# MyBatis入门

## 安装数据库
1. 安装[MySQL Server](https://dev.mysql.com/downloads/mysql/) 服务器端
- 在解压完的根目录下配置`my.ini`:
```bash
[mysql]
default-character-set=utf8mb4
[mysqld]
port=3306
basedir=D:\Program Files\mysql-8.3.0-winx64
max_connections=20
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
default-storage-engine=INNODB
 ```
- 将bin目录`D:\Program Files\mysql-8.3.0-winx64\bin`加到环境变量里
- 初始化时命令必须在`D:\Program Files\mysql-8.3.0-winx64\bin`目录以管理员身份cmd
```bash
D:\Program Files\mysql-8.3.0-winx64\bin>mysqld --initialize --console
2024-03-31T03:48:09.142874Z 0 [System] [MY-015017] [Server] MySQL Server Initialization - start.
2024-03-31T03:48:09.150982Z 0 [System] [MY-013169] [Server] D:\Program Files\mysql-8.3.0-winx64\bin\mysqld.exe (mysqld 8.3.0) initializing of server in progress as process 4760
2024-03-31T03:48:09.152994Z 0 [Warning] [MY-013242] [Server] --character-set-server: 'utf8' is currently an alias for the character set UTF8MB3, but will be an alias for UTF8MB4 in a future release. Please consider using UTF8MB4 in order to be unambiguous.
2024-03-31T03:48:09.176487Z 1 [System] [MY-013576] [InnoDB] InnoDB initialization has started.
2024-03-31T03:48:09.724570Z 1 [System] [MY-013577] [InnoDB] InnoDB initialization has ended.
2024-03-31T03:48:11.475565Z 6 [Note] [MY-010454] [Server] A temporary password is generated for root@localhost: HkdzzE3>?:1Q
2024-03-31T03:48:14.734669Z 0 [System] [MY-015018] [Server] MySQL Server Initialization - end.
```
- 可见密码是：`HkdzzE3>?:1Q`
- 初始化完成后正式安装，启动：
```bash
D:\Program Files\mysql-8.3.0-winx64\bin>mysqld install
Service successfully installed.
D:\Program Files\mysql-8.3.0-winx64\bin>net start mysql
MySQL 服务正在启动 .
MySQL 服务已经启动成功。
 ```
- 现在在cmd中可以登录mysql了，记得把刚刚复杂的密码改简单（123456）：
```bash
C:\Users\15170>mysql -uroot -p
Enter password: ************
Welcome to the MySQL monitor.  Commands end with ; or \g.
Your MySQL connection id is 9
Server version: 8.3.0

Copyright (c) 2000, 2024, Oracle and/or its affiliates.

Oracle is a registered trademark of Oracle Corporation and/or its
affiliates. Other names may be trademarks of their respective
owners.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

mysql>alter user root@localhost identified by '123456';
Query OK, 0 rows affected (0.01 sec)
```
- 接下来，我们为社区项目新建数据库（cmd），并导入一些必要的表和测试数据（脚本）
```bash
mysql> create database community;
Query OK, 1 row affected (0.01 sec)

mysql> show databases;
+--------------------+
| Database           |
+--------------------+
| community          |
| information_schema |
| mysql              |
| performance_schema |
| sys                |
+--------------------+
5 rows in set (0.01 sec)

mysql> use community;
Database changed
mysql> source C:/Users/15170/Desktop/community/scripts/init_schema.sql;
...
mysql> source C:/Users/15170/Desktop/community/scripts/init_data.sql;
...
mysql> show tables;
+---------------------+
| Tables_in_community |
+---------------------+
| comment             |
| discuss_post        |
| login_ticket        |
| message             |
| user                |
+---------------------+
5 rows in set (0.00 sec)
```
- 可以发现，命令行虽然快，但是看表不方便，所以我们需要客户端
2. 安装[MySQL Workbench](https://dev.mysql.com/downloads/workbench/) 客户端
- 修改完安装路径后一路默认即可
- 启动后右键`Local instance MySQL`,选择`edit connetions`修改密码，默认数据库选择刚刚新建的`community`。之后测试连接，成功即可
- 现在我们左键单击`Local instance MySQL`即可启动。点击坐下`schemas`查看数据库详情
- 可以在左上Edit-Preference修改配置

## [MyBatis](https://mybatis.org/mybatis-3/zh_CN/index.html)
- [Spring整合MyBatis](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/zh/index.html)

### 核心组件
- SqlSessionFactory：用于创建SqlSession的工厂类
- SqlSession：MyBatis核心组件，用于<b>向数据库执行SQL</b>
- 主配置文件：XML配置文件，可以对MyBatis的底层行为做出详细的配置
- Mapper接口：即DAO接口
- Mapper映射器：用于编写SQL，并将SQL和实体类映射的组件，采用XML、注解均可实现

### 代码实例：使用MyBatis对user表进行增删改查
1. usr表有什么属性？<br>


![user](/imgs/user.png)
<br>


- 其中，password是加密过的密码。salt是5位随机字符串，为了防止用户密码过于简单，于是在后面拼上了5位字符串再加密，防止被轻易破解。type是用户类型，0普通用户，1管理员，2版主。status表示是否激活。active_code是激活码。<br>


``` sql
//详细建表语句
CREATE TABLE `user` (
                        `id` int NOT NULL AUTO_INCREMENT,
                        `username` varchar(50) DEFAULT NULL,
                        `password` varchar(50) DEFAULT NULL,
                        `salt` varchar(50) DEFAULT NULL,
                        `email` varchar(100) DEFAULT NULL,
                        `type` int DEFAULT NULL COMMENT '0-普通用户; 1-超级管理员; 2-版主;',
                        `status` int DEFAULT NULL COMMENT '0-未激活; 1-已激活;',
                        `activation_code` varchar(100) DEFAULT NULL,
                        `header_url` varchar(200) DEFAULT NULL,
                        `create_time` timestamp NULL DEFAULT NULL,
                        PRIMARY KEY (`id`),
                        KEY `index_username` (`username`(20)),
                        KEY `index_email` (`email`(20))
) ENGINE=InnoDB AUTO_INCREMENT=150 DEFAULT CHARSET=utf8mb3
```

<br>
2. 导入数据库相关包
- 打开之前说的[查包网站](https://mvnrepository.com)
- 搜索mysql，点击排名第一的包，找到如下maven配置，复制粘贴即可：

<br>

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.3.0</version>
</dependency>
```
- 同理搜索MyBatis，选择SpringBoot整合版<br>
```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.3</version>
</dependency>
```
3. 配置MyBatis，在Spring Boot整合下直接进入`application.properties`配置即可<br>
```bash
# DataSourceProperties // MySQL
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver  # 连接数据库的工具
spring.datasource.url=jdbc:mysql://localhost:3306/community?characterEncoding=utf-8&useSSL=false&serverTimezone=Hongkong # 连接本地数据库
spring.datasource.username=root # 账号密码
spring.datasource.password=123456
spring.datasource.type=com.zaxxer.hikari.HikariDataSource  #指定Spring Boot使用的连接池实现。这里使用的是HikariCP，它是当前最快的数据源连接池
spring.datasource.hikari.maximum-pool-size=15 # 最高访问量
spring.datasource.hikari.minimum-idle=5 # 保持空闲的最少访问量
spring.datasource.hikari.idle-timeout=30000  # 闲置最长时间，超过时间断开连接

# MybatisProperties
mybatis.mapper-locations=classpath:mapper/*.xml #MyBatis的映射文件位置
mybatis.type-aliases-package=com.nowcoder.community.entity # 实体类地址
mybatis.configuration.useGeneratedKeys=true #可以获取数据库自动生成的主键值
# ex. head_url -> headUrl
mybatis.configuration.mapUnderscoreToCamelCase=true # 将数据库中的下划线命名映射到java中的驼峰命名
```

<br>

4. 访问表之前，先要有实体类`entity`。我们在entity下新建类User与user表一一对应
- 以驼峰命名类的属性对应表的字段
- `alt+insert`自动生成对应的get和set、以及toString方法
```java
package com.nowcoder.community.entity;

import java.util.Date;

public class User {

    private int id;
    private String username;
    private String password;
    private String salt;
    private String email;
    private int type;
    private int status;
    private String activationCode;
    private String headerUrl;
    private Date createTime;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getActivationCode() {
        return activationCode;
    }

    public void setActivationCode(String activationCode) {
        this.activationCode = activationCode;
    }

    public String getHeaderUrl() {
        return headerUrl;
    }

    public void setHeaderUrl(String headerUrl) {
        this.headerUrl = headerUrl;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", salt='" + salt + '\'' +
                ", email='" + email + '\'' +
                ", type=" + type +
                ", status=" + status +
                ", activationCode='" + activationCode + '\'' +
                ", headerUrl='" + headerUrl + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
```
5. 为Dao书写主键（MyBatis集成接口）便于访问数据库
- 不同于之前的数据库注解`@Repository`,MyBatis提供`@Mapper`注解，效果实质上是一样的，声明数据库的装配Bean
```java
package com.nowcoder.community.dao;

import com.nowcoder.community.entity.User;
import jakarta.jws.soap.SOAPBinding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
    //业务需要什么写什么

    User selectById(int id);

    User selectByName(String username);

    User selectByEmail(String email);

    int insertUser(User user);  //返回修改了几行记录

    int updateStatus(int id, int status);

    int updateHeader(int id, String headerUrl);
    
    int updatePassword(int id, String password);
}
```
6. 在`resources/mapper`配置xml文件，使得MyBatis能够根据我们的接口自动生成实现类（就是实现sql语句）。注意插入不用提供id，因为默认在最后一行后面插入
```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<!--填写接口位置-->
<mapper namespace="com.nowcoder.community.dao.UserMapper">
    <!--select 字段标记-->
    <sql id="selectFields">
        id, username, password, salt, email, type, status, activation_code, header_url, create_time
    </sql>
    <sql id="insertFields">
        username, password, salt, email, type, status, activation_code, header_url, create_time
    </sql>

    <!--每个标签对应一个接口中的函数-->
    <!--id对应函数名字，返回类型也要对应，里面写sql语句-->
    <select id="selectById" resultType="User">
        select <include refid="selectFields"></include>
        from user
        where id = #{id}
    </select>
    <select id="selectByName" resultType="User">
        select <include refid="selectFields"></include>
        from user
        where username = #{username}
    </select>
    <select id="selectByEmail" resultType="User">
        select <include refid="selectFields"></include>
        from user
        where email = #{email}
    </select>

    <!--声明插入属性，以及与主键对应的属性-->
    <insert id="insertUser" parameterType="User" keyProperty="id">
        insert into user (<include refid="insertFields"></include>)
        values(#{username}, #{password}, #{salt}, #{email}, #{type}, #{status}, #{activationCode}, #{headerUrl}, #{createTime})
    </insert>

    <update id="updateStatus">
        update user 
        set status = #{status} 
        where id = #{id}
    </update>

    <update id="updateHeader">
        update user 
        set header_url = #{headerUrl} 
        where id = #{id}
    </update>

    <update id="updatePassword">
        update user 
        set password = #{password} 
        where id = #{id}
    </update>

</mapper>
```
7. 写一个测试类对我们的代码进行测试
```java
package com.nowcoder.community;

import com.nowcoder.community.dao.UserMapper;
import com.nowcoder.community.entity.User;
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
public class MapperTests {

    //注入Mapper
    @Autowired
    private UserMapper userMapper;

    @Test
    public void testSelectUser(){
        User user = userMapper.selectById(101);
        System.out.println(user);

        user = userMapper.selectByName("liubei");
        System.out.println(user);

        user = userMapper.selectByEmail("nowcoder101@sina.com");
        System.out.println(user);
    }

    @Test
    public void testInsertUser(){
        User user = new User();
        user.setUsername("test");
        user.setPassword("123456");
        user.setEmail("test@qq.com");
        user.setSalt("abc");
        user.setHeaderUrl("www.test.com");
        user.setCreateTime(new Date());

        int rows = userMapper.insertUser(user);
        System.out.println(rows);
        System.out.println(user.getId());
    }

    @Test
    public void updateUser(){
        int rows = userMapper.updateStatus(150,1);
        System.out.println(rows);

        rows = userMapper.updateHeader(150, "www.test2.com");
        System.out.println(rows);

        rows = userMapper.updatePassword(150, "1234567");
        System.out.println(rows);
    }
}

```
8. 补充：我们会发现`user-mapper.xml`比较难以调试。因为java文件ide会自动为你报错，但是这个sql嵌入xml的没有提示
- 解决方法：调整dao日志级别为debug：在`appliction.properties`里增加`logging.level.com.nowcoder.community = debug`即可
- 再次运行test方法，就会发现多输出了预编译的sql，如下所以，方便调试：
```bash
2024-03-31T14:26:54.776+08:00  INFO 9036 --- [community] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Starting...
2024-03-31T14:26:54.943+08:00  INFO 9036 --- [community] [           main] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Added connection com.mysql.cj.jdbc.ConnectionImpl@4d2f9e3c
2024-03-31T14:26:54.945+08:00  INFO 9036 --- [community] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Start completed.
2024-03-31T14:26:54.951+08:00 DEBUG 9036 --- [community] [           main] c.n.community.dao.UserMapper.selectById  : ==>  Preparing: select id, username, password, salt, email, type, status, activation_code, header_url, create_time from user where id = ?
2024-03-31T14:26:54.973+08:00 DEBUG 9036 --- [community] [           main] c.n.community.dao.UserMapper.selectById  : ==> Parameters: 101(Integer)
2024-03-31T14:26:54.998+08:00 DEBUG 9036 --- [community] [           main] c.n.community.dao.UserMapper.selectById  : <==      Total: 1
User{id=101, username='liubei', password='390ba5f6b5f18dd4c63d7cda170a0c74', salt='12345', email='nowcoder101@sina.com', type=0, status=1, activationCode='null', headerUrl='http://images.nowcoder.com/head/100t.png', createTime=Wed Apr 03 12:04:55 CST 2019}
2024-03-31T14:26:55.016+08:00 DEBUG 9036 --- [community] [           main] c.n.c.dao.UserMapper.selectByName        : ==>  Preparing: select id, username, password, salt, email, type, status, activation_code, header_url, create_time from user where username = ?
2024-03-31T14:26:55.017+08:00 DEBUG 9036 --- [community] [           main] c.n.c.dao.UserMapper.selectByName        : ==> Parameters: liubei(String)
2024-03-31T14:26:55.019+08:00 DEBUG 9036 --- [community] [           main] c.n.c.dao.UserMapper.selectByName        : <==      Total: 1
User{id=101, username='liubei', password='390ba5f6b5f18dd4c63d7cda170a0c74', salt='12345', email='nowcoder101@sina.com', type=0, status=1, activationCode='null', headerUrl='http://images.nowcoder.com/head/100t.png', createTime=Wed Apr 03 12:04:55 CST 2019}
2024-03-31T14:26:55.020+08:00 DEBUG 9036 --- [community] [           main] c.n.c.dao.UserMapper.selectByEmail       : ==>  Preparing: select id, username, password, salt, email, type, status, activation_code, header_url, create_time from user where email = ?
2024-03-31T14:26:55.021+08:00 DEBUG 9036 --- [community] [           main] c.n.c.dao.UserMapper.selectByEmail       : ==> Parameters: nowcoder101@sina.com(String)
2024-03-31T14:26:55.022+08:00 DEBUG 9036 --- [community] [           main] c.n.c.dao.UserMapper.selectByEmail       : <==      Total: 1
User{id=101, username='liubei', password='390ba5f6b5f18dd4c63d7cda170a0c74', salt='12345', email='nowcoder101@sina.com', type=0, status=1, activationCode='null', headerUrl='http://images.nowcoder.com/head/100t.png', createTime=Wed Apr 03 12:04:55 CST 2019}
```