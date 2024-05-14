# [ElasticSearch](https://www.elastic.co/cn/)入门

- ElasticSearch简介——特殊的数据库（需要MySQL、Redis的数据副本）
  - 一个分布式的、Restful风格（规定了前后端不同的HTTP请求的标准）的搜索引擎
  - 支持对各种类型的数据的检索（结构化、非结构化、地理位置、指标等均可）
  - 搜索速度快，可以提供实时的搜索服务
  - 便于水平扩展（新增一个集群的服务器），每秒可以处理PB级海量数据
- ElasticSearch术语
  - 索引——对应MySQL的库（在本项目中是community库，包含了5张表）
  - 类型——对应MySQL的表  `6.0版本以后在弱化这个概念，现在基本消除了类型的概念，由索引指表`
  - 文档——对应MySQL的一行元组 ==> `用json存`
  - 字段——对应MySQL的一列属性
  - 集群——多台服务器组成集群
  - 节点——集群中的每个服务器都是节点
  - 分片——一个索引相当于一个表，表里数据可能很多，于是对一个索引的数据进行分片划分，可以并发存储
  - 副本——对分片的备份，一个分片可以有多个副本

## [安装](https://www.elastic.co/cn/downloads/past-releases#elasticsearch)

1. 下载后直接解压即可

2. 修改配置elasticsearch.yaml

```yaml
cluster.name: nowcoder

path.data: D:\elasticsearch-7.11.1\data

path.logs: D:\elasticsearch-7.11.1\logs
```

3. 环境变量：`D:\elasticsearch-7.11.1\bin`

4. [中文分词插件](https://github.com/infinilabs/analysis-ik/releases/tag/v7.11.1) // 分词：搜索“互联网校招”，会分成：互联网、校招

解压路径：`D:\elasticsearch-6.4.3\plugins\ik`

分词字典：`plugins\ik\config`下所有.dic文件

如果有新的网络热词，可用配置 IKAnalyzer.cfg

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
  <comment>IK Analyzer 扩展配置</comment>
  <!--用户可以在这里配置自己的扩展字典 -->
  <entry key="ext_dict"></entry>
  <!--用户可以在这里配置自己的扩展停止词字典-->
  <entry key="ext_stopwords"></entry>
  <!--用户可以在这里配置远程扩展字典 -->
  <!-- <entry key="remote_ext_dict">words_location</entry> -->
  <!--用户可以在这里配置远程扩展停止词字典-->
  <!-- <entry key="remote_ext_stopwords">words_location</entry> -->
</properties>
```

5. 安装[postman](https://www.postman.com/)用于模拟网页发送http请求，虽然命令行也行，但是存东西时，命令太复杂

从官网下载后安装即可，注册账号登录


## 演示

1. 启动ES：直接在bin下双击`elasticsearch.bat`即可，注意以上的所有路径不能有空格

见到如下字段即为启动成功
```
[2024-05-14T20:36:50,280][INFO ][o.e.t.TransportService   ] [GGG] publish_address {127.0.0.1:9300}, bound_addresses {127.0.0.1:9300}, {[::1]:9300}
```

2. 启动后原cmd作为server，现在新开一个cmd作为client

```bash
// 查看server健康状况
C:\Users\15170>curl -X GET "localhost:9200/_cat/health?v"
epoch      timestamp cluster  status node.total node.data shards pri relo init unassign pending_tasks max_task_wait_time active_shards_percent
1715692231 13:10:31  nowcoder green           1         1      0   0    0    0        0             0                  -                100.0%

// 集群节点（本机为单节点）
C:\Users\15170>curl -X GET "localhost:9200/_cat/nodes?v"
ip        heap.percent ram.percent cpu load_1m load_5m load_15m node.role  master name
127.0.0.1            5          96   4                          cdhilmrstw *      GGG

// 查看索引（表）
C:\Users\15170>curl -X GET "localhost:9200/_cat/indices?v"
health status index uuid pri rep docs.count docs.deleted store.size pri.store.size

// 新建表
C:\Users\15170>curl -X PUT "localhost:9200/test"
{"acknowledged":true,"shards_acknowledged":true,"index":"test"}

// 再次查看（健康状况为yellow，因为未备份）
C:\Users\15170>curl -X GET "localhost:9200/_cat/indices?v"
health status index uuid                   pri rep docs.count docs.deleted store.size pri.store.size
yellow open   test  nkTYUD93RAOCd0ubsLhoEA   1   1          0            0       208b           208b

// 删除
C:\Users\15170>curl -X DELETE "localhost:9200/test"
{"acknowledged":true}
```

可见命令行操作十分麻烦，因此需要Postman

3. Postman操作

如图所示，简洁易懂

![Postman](/imgs/Postman.png)

![json](/imgs/postmanjson.png)

![getjson](/imgs/postmanjsonget.png)

4. Postman搜索

造数据如下：

```
_doc/1: 
{
        "title": "互联网求职",
        "content": "寻求一份运营的岗位"
    }
    
_doc/2:
 {
        "title": "互联网招聘",
        "content": "招聘一名资深程序员"
    }
    
_doc/3:
{
        "title": "实习生推荐",
        "content": "本人在一家互联网公司任职，可推荐岗位"
    }        
```

搜索：

![search](/imgs/postmansearch.png)

复杂搜索（使用请求体body）：

![query](/imgs/postmanquery.png)