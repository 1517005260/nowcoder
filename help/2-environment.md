# 环境配置

- <b>确保所有路径无中文、无空格</b>
- `jdk`版本：17
  - [环境变量配置](https://zhuanlan.zhihu.com/p/582240447)
  - 检查：`C:\Users\15170>java -version`<br>
    `openjdk version "17.0.10" 2024-01-16 LTS`<br>
    `OpenJDK Runtime Environment Corretto-17.0.10.7.1 (build 17.0.10+7-LTS)`<br>
    `OpenJDK 64-Bit Server VM Corretto-17.0.10.7.1 (build 17.0.10+7-LTS, mixed mode, sharing)`<br>


## [`Apache Maven`](https://maven.apache.org/) 安装配置

### 简介
- 可以帮助我们构建项目（编译测试打包），并管理项目中的`jar`包
- Maven仓库：存放构件（依赖的插件、`jar`包等）的位置
  - 本地仓库：默认为`~/.m2/repository`
  - 远程仓库：中央、镜像（推荐）、私服仓库
    - 下载顺序：先看本地有没有，没有再去远程仓库下

### 下载
- 在`官网/Download`下可以选择版本，`binary`是不带源码的版本，`source`是带源码的版本。由于我们只是使用，所以`binary`即可
- 我的版本：`apache-maven-3.9.6-bin.zip`
- 解压完成后配置远程镜像仓库（阿里云）
  - 在`apache-maven-3.9.6-bin.zip/conf/settings.xml`修改`<mirrors>`
    - 配置如下：`<mirror>`<br>
              `<id>alimaven</id>`<br>
              `<mirrorOf>central</mirrorOf>`<br>
              `<name>aliyun maven</name>`<br>
              `<url>https://maven.aliyun.com/repository/central</url>`<br>
              `</mirror>`
- 配置命令行环境变量
  - 检查：`C:\Users\15170>mvn -version`<br>
    `Apache Maven 3.9.6 (bc0240f3c744dd6b6ec2920b3cd08dcc295161ae)`<br>
    `Maven home: D:\Program Files\apache-maven-3.9.6`<br>
    `Java version: 1.8.0_402, vendor: Amazon.com Inc., runtime: C:\Users\15170\.jdks\corretto-1.8.0_402\jre`<br>
    `Default locale: zh_CN, platform encoding: GBK`<br>
    `OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"`<br>

### [常用命令](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html)
1. 创建项目：`mvn archetype:generate -DgroupId=com.mycompany.app -DartifactId=my-app -DarchetypeArtifactId=maven-archetype-quickstart -DarchetypeVersion=1.4 -DinteractiveMode=false`
- 域名`-DgroupId`中`com.mycompany.app`可以修改为`com.nowcoder.mavendemo1`等
- 项目id`-DartifactId`中`my-app`可以修改为`mavendemo1`
- 项目生成模板`-DarchetypeArtifactId`
- 版本`-DarchetypeVersion`
- 交互模式`-DinteractiveMode`（每执行一步问你是或否）
- 创建完毕后`src`存有`main`,`test`,前者存放正式代码，后者存放测试代码
2. 编译项目：cd到带有`pom.xml`的文件目录下`mvn compile`
- 编译结果会存放在`target`目录下，我们重点关注`classes`文件夹，里面就是编译好的类（main）
- 重新编译：`mvn clean compile`
- 自动测试：`mvn test`（测试包含了编译）

## IDEA安装配置
（淘宝激活
1. [官网](https://www.jetbrains.com/zh-cn/idea/download/#section=windows)
- Install Options 全选，之后重启电脑
- 设置选项全部默认即可，在settings里可以自行更改设置
- 在<b>项目结构</b>中可以更改jdk版本
2. 配置`Maven`
- 设置中直接搜索即可
- 选择`Maven`安装路径，并重写`settings file`
- 之后可以界面化用maven创建项目，效果和刚刚命令行一样，快捷键`ctrl+f9`
- 可以在[这里](https://mvnrepository.com/)搜索需要的包
3. 使用[`Spring Initializr`](https://start.spring.io/)创建项目，比maven更集成化，不用一个一个下包
- 分门别类整合了常用开发包，底层还是基于maven
- 先导入这些包：`Spring Web, Thymeleaf, Dev Tools, Spring Web Services`剩下的包后续会逐渐导入
- 网站上直接下载配置好的压缩包，本地解压即可
- 运行项目：右键运行即可
4. 开发一个最简单的`Spring Boot`程序
- `Spring Boot`核心作用：起步依赖（starter），自动配置，端点监控
- 访问服务器某个路径时，能返回：Hello World
  - 见`src/main/java/com.nowcoder.community`
