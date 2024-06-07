# 网站上线

项目部署图：

![project](/imgs/project.png)

- 浏览器直接访问nginx，负责分发请求（反向代理、负载均衡）
- 服务器本身为tomcat，nginx是其代理
- 伪分布式安装，因为服务器资源有限。真实应该多服务器分布式部署
- putty：访问云服务器工具

## 安装

1. [putty](https://putty.org/)
- 登录之前记得把connection改成10

2. 租好云服务器（京东云 ecs 2c4g Ubuntu）新人160左右一年

不要用root用户，自己新建用户 `newuser`

```bash
root@lavm-bubg7yqvfv:~# adduser newuser
Adding user `newuser' ...
Adding new group `newuser' (1001) ...
Adding new user `newuser' (1001) with group `newuser' ...
Creating home directory `/home/newuser' ...
Copying files from `/etc/skel' ...
Enter new UNIX password:
Retype new UNIX password:
passwd: password updated successfully
Changing the user information for newuser
Enter the new value, or press ENTER for the default
        Full Name []: 
        Room Number []: 
        Work Phone []: 
        Home Phone []: 
        Other []: 
Is the information correct? [Y/n] y
root@lavm-bubg7yqvfv:~# usermod -aG sudo newuser
```

3. 在云服务上装各种软件的压缩包，建议本地传上去
- jdk 17
- maven 3.9.6
- mysql 8.3.0
- redis
- kafka 2.13-3.3.1
- es 7.11.1 以及分词文件
- tomcat 9.0.22
- 本地开发的sql脚本

4. sql脚本生成

```bash
C:\Users\15170>mysqldump -u root -p123456 --no-data community > "C:\Users\15170\Desktop\community\scripts\community_structure.sql"
mysqldump: [Warning] Using a password on the command line interface can be insecure.
```

注意要保留qrtz所有表的数据，其他表可以不要

sql脚本的导入：

```bash
mysql> CREATE DATABASE community;
Query OK, 1 row affected (0.01 sec)

mysql> use community
Database changed
mysql> SOURCE ./community_structure.sql;
```

创建系统通知用户：

```bash
mysql> INSERT INTO `user` VALUES (1,'SYSTEM','SYSTEM','SYSTEM','system@sina.com',0,1,NULL,'http://static.nowcoder.com/images/head/notify.png','2024-06-7 14:50:03', NULL);
Query OK, 1 row affected (0.01 sec)
```

5. JDK - 17 安装：

```bash
sudo apt update
sudo apt install openjdk-17-jdk


ggg@lavm-bubg7yqvfv:~$ java --version
openjdk 17.0.10 2024-01-16
OpenJDK Runtime Environment (build 17.0.10+7-Ubuntu-122.04.1)
OpenJDK 64-Bit Server VM (build 17.0.10+7-Ubuntu-122.04.1, mixed mode, sharing)
```

6. redis安装：

```bash
sudo apt install redis-server

ggg@lavm-bubg7yqvfv:~$ redis-cli
127.0.0.1:6379>
```

7. kafka:

```bash
tar -xzf kafka_2.13-3.3.1.tgz
```

启动：

进入kafka目录下：

```bash
bin/zookeeper-server-start.sh config/zookeeper.properties
bin/kafka-server-start.sh config/server.properties
```

8. maven:

```bash
wget https://mirrors.aliyun.com/apache/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz

tar -xzf apache-maven-3.9.6-bin.tar.gz
vim ~/.bashrc
export M2_HOME=/home/ggg/apache-maven-3.9.6
export PATH=$M2_HOME/bin:$PATH

ggg@lavm-bubg7yqvfv:~$ mvn --version
Apache Maven 3.9.6 (bc0240f3c744dd6b6ec2920b3cd08dcc295161ae)
Maven home: /home/ggg/apache-maven-3.9.6
Java version: 17.0.10, vendor: Private Build, runtime: /usr/lib/jvm/java-17-openjdk-amd64
Default locale: en_US, platform encoding: UTF-8
OS name: "linux", version: "5.15.0-60-generic", arch: "amd64", family: "unix"

ggg@lavm-bubg7yqvfv:~/apache-maven-3.9.6/conf$ vim settings.xml

<mirrors>
    <mirror> 
    <id>alimaven</id> 
        <name>aliyun maven</name> 
            <url>http://maven.aliyun.com/nexus/content/groups/public/</url> 
        <mirrorOf>central</mirrorOf> 
    </mirror> 
</mirrors>
```

9. es

```bash
ggg@lavm-bubg7yqvfv:~$ tar -xzf elasticsearch-7.11.1-linux-x86_64.tar.gz
ggg@lavm-bubg7yqvfv:~$ tar -xzf analysis-ik-7.11.1.tar.gz
ggg@lavm-bubg7yqvfv:~$ cd elasticsearch-7.11.1/
ggg@lavm-bubg7yqvfv:~/elasticsearch-7.11.1$ mv ../analysis-ik-7.11.1/ ./plugins/
ggg@lavm-bubg7yqvfv:~/elasticsearch-7.11.1$ cd plugins/
ggg@lavm-bubg7yqvfv:~/elasticsearch-7.11.1/plugins$ mv analysis-ik-7.11.1/ ik

ggg@lavm-bubg7yqvfv:~/elasticsearch-7.11.1/config$ vim elasticsearch.yml

cluster.name: nowcoder
path.data: /tmp/elasticsearch/data
path.logs: /tmp/elasticsearch/logs

ggg@lavm-bubg7yqvfv:~/elasticsearch-7.11.1/config$ vim jvm.options

-Xms256m  # 启动占用内存，我们一共久4G，要省点
-Xmx512m  # 最大占用内存
```

10. wkhtmltopdf:

```bash
ggg@lavm-bubg7yqvfv:~$ sudo apt update
ggg@lavm-bubg7yqvfv:~$ sudo apt install -y software-properties-common
ggg@lavm-bubg7yqvfv:~$ sudo apt-add-repository -y ppa:savoirfairelinux
ggg@lavm-bubg7yqvfv:~$ sudo apt update
ggg@lavm-bubg7yqvfv:~$ sudo apt install -y wkhtmltopdf
ggg@lavm-bubg7yqvfv:~$ wkhtmltopdf --version
wkhtmltopdf 0.12.6

ggg@lavm-bubg7yqvfv:~$ sudo apt install -y xvfb  # linux无gui，故需要这个包配合
ggg@lavm-bubg7yqvfv:~$ xvfb-run --server-args="-screen 0 1024x768x24" wkhtmltoimage https://www.baidu.com 1.png
Loading page (1/2)
Rendering (2/2)
Done
Warning: Received createRequest signal on a disposed ResourceObject's NetworkAccessManager. This might be an indication of an iframe taking too long to load.
[1]+  Done                    Xvfb :99 -screen 0 1024x768x16

ggg@lavm-bubg7yqvfv:~$ vim wkhtmltoimage.sh

xvfb-run --server-args="-screen 0 1024x768x24" wkhtmltoimage "$@"

ggg@lavm-bubg7yqvfv:~$ chmod +x wkhtmltoimage.sh
```

11. tomcat:

```bash
ggg@lavm-bubg7yqvfv:~$ tar -xzf apache-tomcat-9.0.22.tar.gz

ggg@lavm-bubg7yqvfv:~/apache-tomcat-9.0.22$ vim ~/.bashrc

export CATALINA_HOME=/home/ggg/apache-tomcat-9.0.22
export PATH=$CATALINA_HOME/bin:$PATH

ggg@lavm-bubg7yqvfv:~/apache-tomcat-9.0.22$ source ~/.bashrc
```

启动：

```bash
startup.sh

现在可访问 http://116.198.216.39:8080/ 进入tomcat默认页面（记得开放服务器安全组）
```

12. nginx

```bash
ggg@lavm-bubg7yqvfv:~$ sudo apt install nginx

ggg@lavm-bubg7yqvfv:~$ sudo vim /etc/nginx/nginx.conf

upstream myserver {
    server 127.0.0.1:8080 max_fails=3 fail_timeout=30s;  # 真实服务器，端口为8080的tomcat
} 

server {
    listen 80;
    server_name 116.198.216.39;   # 若有请求访问116.198.216.39:80

    location / {
        proxy_pass http://myserver;  # 则分发到myserver处
    }
}


ggg@lavm-bubg7yqvfv:~$ sudo nginx -t
nginx: the configuration file /etc/nginx/nginx.conf syntax is ok
nginx: configuration file /etc/nginx/nginx.conf test is successful
ggg@lavm-bubg7yqvfv:~$ sudo systemctl reload nginx

# 现在直接访问 http://116.198.216.39/ 默认80端口，结果和之前一样
```

13. 部署代码进入 tomcat/webapp下

a. 删除webapp下的所有示例代码

```bash
ggg@lavm-bubg7yqvfv:~/apache-tomcat-9.0.22$ cd webapps/
ggg@lavm-bubg7yqvfv:~/apache-tomcat-9.0.22/webapps$ ls
docs  examples  host-manager  manager  ROOT
ggg@lavm-bubg7yqvfv:~/apache-tomcat-9.0.22/webapps$ rm ./* -rf
```

b. 总体处理思路

![tomcat](/imgs/tomcat.png)

## 修改上线的代码

1. 修改项目路径，让用户敲击网址直接来论坛处

application.properties:

```
server.servlet.context-path=
```

global.js:

```javascript
let CONTEXT_PATH = "";
```

HomeController新增：

```java
@RequestMapping(path = "/", method = RequestMethod.GET)
public String root(){
    return "forward:/index";
}
```

2. 修改pom.xml，使其不要打包成jar包，而是war包（web用）

```xml
<description>nowcoder community</description>
<packaging>war</packaging>
<properties>
    <java.version>17</java.version>
</properties>

<build>
<finalName>ROOT</finalName>  <!--war包名字-->
</build>
```

3. 修改项目中所有的本地路径

a. 重命名原来的application.properties为application-develop.properties；原来的logback-spring.xml为logback-spring-develop.xml

b. 新建application-produce.properties和logback-spring-produce.xml

c. 配置spring使得能识别两套配置文件
-   原application.properties：
```
# profile
spring.profiles.active=produce  # 激活produce配置
# logback
logging.config=classpath:logback-spring-${spring.profiles.active}.xml
```

d. 重新配置CommunityApplication，因为tomcat占用了main方法，一个java工程就一个main方法

CommunityApplication同级新建：

```java
package com.nowcoder.community;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

public class CommunityServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(CommunityApplication.class);
    }
}
```

e. produce更改路径：

```
spring.thymeleaf.cache=true

spring.datasource.password=MimaGlk030923

community.path.domain=http://116.198.216.39

community.path.upload=/tmp/uploads

wk.image.command = /home/ggg/wkhtmltoimage.sh
wk.image.storage = /tmp/wk-image
```

f. logback-spring-procude.xml:

```xml
<property name="LOG_PATH" value="/tmp"/>
```

g. 为了省钱，还是选择本地存头像等资源，修改UserController和前端即可；并且为了方便，直接删除了生成长图功能

4. 清空target目录，使得打包的文件小一点（maven clean）

5. 压缩zip上传至服务器

现在服务器的情况：

```bash
ggg@lavm-bubg7yqvfv:~$ ls
apache-maven-3.9.6  apache-tomcat-9.0.22  community  elasticsearch-7.11.1  kafka_2.13-3.3.1  wkhtmltoimage.sh
```

6. 在community下：

```bash
ggg@lavm-bubg7yqvfv:~/community$ mvn clean package -Dmaven.test.skip=true

ggg@lavm-bubg7yqvfv:~/community/target$ ll
total 173660
drwxrwxr-x 7 ggg ggg     4096 Jun  6 20:55 ./
drwxrwxr-x 7 ggg ggg     4096 Jun  6 20:54 ../
drwxrwxr-x 6 ggg ggg     4096 Jun  6 20:54 classes/
drwxrwxr-x 3 ggg ggg     4096 Jun  6 20:54 generated-sources/
drwxrwxr-x 2 ggg ggg     4096 Jun  6 20:55 maven-archiver/
drwxrwxr-x 3 ggg ggg     4096 Jun  6 20:54 maven-status/
drwxrwxr-x 4 ggg ggg     4096 Jun  6 20:55 ROOT/
-rw-rw-r-- 1 ggg ggg 92950796 Jun  6 20:55 ROOT.war
-rw-rw-r-- 1 ggg ggg 84837643 Jun  6 20:55 ROOT.war.original

ggg@lavm-bubg7yqvfv:~/community/target$ mv ROOT.war ~/apache-tomcat-9.0.22/webapps/

ggg@lavm-bubg7yqvfv:~/apache-tomcat-9.0.22/webapps$ startup.sh
Using CATALINA_BASE:   /home/ggg/apache-tomcat-9.0.22
Using CATALINA_HOME:   /home/ggg/apache-tomcat-9.0.22
Using CATALINA_TMPDIR: /home/ggg/apache-tomcat-9.0.22/temp
Using JRE_HOME:        /usr/lib/jvm/java-17-openjdk-amd64
Using CLASSPATH:       /home/ggg/apache-tomcat-9.0.22/bin/bootstrap.jar:/home/ggg/apache-tomcat-9.0.22/bin/tomcat-juli.jar
Tomcat started.
```

### bug——发现tomcat启动后404

不知道怎么解决，遂换方法部署，不用tomcat，直接把项目打包成jar包，在服务器上用tmux运行 `java -jar ROOT.jar` 即可

只要删除 pom.xml中的`<packaging>war</packaging>`即可

重新打包（本地也可，不一定服务器）：

```bash
mvn clean
mvn clean package -DskipTests
```

### 特别注意

自己建表的qrtz系列是全小写的，用jar包启动时会提示找不到QRTZ表，因为它要的是大写的。

解决：用tables_mysql_innodb.sql导入解决

### bug——jar包启动后报错

具体错误：template might not exist or might not be accessible by any of the configured Template Resolvers

解决：controller所有的return里，除了`forward:/index`这种不用改，其他所有的return全部改为：`site/xxx` 而不是`/site/xxx`

### bug——share按钮的api仅支持本地或https，而我们仅有ip地址

解决：

discuss.js

```javascript
var titleText = "";
var authorText = "";
document.addEventListener("DOMContentLoaded", function() {
    // 获取包含ID为"title"的元素
    var title = document.getElementById("title");
    // 获取元素的文本值
    titleText = title.innerText; // 或者使用 textContent

    var author = document.getElementById("author");
    authorText = author.innerText;
});

function share() {
    let currentUrl = window.location.href;
    // 组织要复制的内容
    const formattedText = `${titleText} - ${authorText}的帖子 - 校园论坛\n${currentUrl}`;

    // 创建一个临时的textarea元素
    const tempTextarea = document.createElement('textarea');
    tempTextarea.value = formattedText;
    document.body.appendChild(tempTextarea);

    // 选择并复制内容
    tempTextarea.select();
    document.execCommand('copy');

    // 删除临时元素
    document.body.removeChild(tempTextarea);

    // 显示自定义提示框
    const customAlert = document.getElementById('customAlert');
    customAlert.style.display = 'block';

    // 1秒后自动隐藏提示框
    setTimeout(() => {
        customAlert.style.display = 'none';
    }, 1000);
}
```

### bug——传头像的检查

更新判断逻辑：

```java
@LoginRequired
@RequestMapping(path = "/upload", method = RequestMethod.POST)
public String uploadHeader(MultipartFile headerImage, Model model) {
    if (headerImage == null) {
        model.addAttribute("error", "上传的头像图片为空！");
        return "site/setting";
    }

    // 检查文件大小
    if (headerImage.getSize() > 500 * 1024) { // 500KB = 500 * 1024 bytes
        model.addAttribute("error", "上传的文件大小不能超过500KB！建议使用QQ截图缩小！");
        return "site/setting";
    }

    // 给上传的文件重命名
    String fileName = headerImage.getOriginalFilename();
    if (fileName == null || StringUtils.isBlank(fileName)) {
        model.addAttribute("error", "您还没有选择图片!");
        return "site/setting";
    }

    int index = fileName.lastIndexOf(".");
    if (index == -1) {
        model.addAttribute("error", "文件格式不正确!（只支持*.png/*.jpg/*.jpeg）");
        return "site/setting";
    }

    String suffix = fileName.substring(index);
    if (StringUtils.isBlank(suffix) || (!".png".equals(suffix) && !".jpg".equals(suffix) && !".jpeg".equals(suffix))) {
        model.addAttribute("error", "文件格式不正确!（只支持*.png/*.jpg/*.jpeg）");
        return "site/setting";
    }

    // 随机文件名
    fileName = CommunityUtil.genUUID() + suffix;

    // 存储文件
    File dist = new File(uploadPath + "/" + fileName); // 存放路径
    try {
        headerImage.transferTo(dist);
    } catch (IOException e) {
        logger.error("上传文件失败：" + e.getMessage());
        throw new RuntimeException("上传文件失败，服务器发生异常！", e);
    }

    // 更新用户头像（非服务器，而是web路径）
    // http://...../community/user/header/xxx.png
    User user = hostHolder.getUser();
    String headerUrl = domain + contextPath + "/user/header/" + fileName;
    userService.updateHeader(user.getId(), headerUrl);

    return "redirect:/index";
}
```

### bug——点击“记住我”后，无法登录

原因：设置的30天太长，可能溢出了，改成15天就不会了：

```java
//记住我
int REMEMBER_EXPIRED_SECONDS = 3600*24*15;
```

上线之前，还要记得改写之前测试用的任务时间，比如写入数据库，quartz，和热帖缓存。分别改为1天，1h，30s

至此，上线完成