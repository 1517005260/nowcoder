# [Kafka](https://kafka.apache.org/)入门

![kafka](/imgs/kafka.png)

- Kafka简介
  - Kafka是一个分布式的流媒体平台 （由消息队列扩展而来，现在的功能比较综合）
  - 应用：消息系统（核心）、日志收集、用户行为追踪、流式处理
- Kafka特点
  - 高吞吐量：可以处理TB级数据
  - 消息持久化：消息存到硬盘上而非内存里  ==> 对硬盘的读取是顺序而非随机的，所以性能有时甚至高于内存
  - 高可靠性  ==> 分布式，即有多个服务器
  - 高扩展性  ==> 配置容易
- Kafka术语
  - Broker：Kafka的服务器
  - Zookeeper：管理集群用
  - Topic：消息队列有两种实现方式，上次演示用的是点对点，只有一个消息队列。还有一种发布-订阅式，Kafka采用后者，生产者把消息放到某个位置，可以有很多的消费者订阅这个位置，读取消息，会被同时或先后读到。生产者发布消息的地方叫topic，可以理解为存放消息队列的地方
  - Partition：对topic的子分区
  - Offset：消息在partition中存储的索引
  - Leader Replica：副本，对数据的备份，每个分区有多个副本。leader副本是主副本，能力比较强，尝试从分区中获取数据的消费者，主副本可以给数据，处理请求
  - Follower Replica：从副本只能存数据，不能处理请求。分布式下，若主副本挂了，会随机挑一个从副本顶上


## 安装配置
1. 官网下载安装包（不区分操作系统）后配置config

配置 zookeeper.properties

```
dataDir=D:\kafka_2.13-3.3.1\data\zookeeper
```

配置 server.properties

```
log.dirs=D:\kafka_2.13-3.3.1\logs\kafka-logs
```

配置consumer.properties

```
group.id=community-consumer-group
```

windows命令位于：`D:\kafka_2.13-3.3.1\bin\windows`

2. 基本演示

a. 启动zookeeper

```bash
D:\kafka_2.13-3.3.1>bin\windows\zookeeper-server-start.bat config\zookeeper.properties
```

b. 新开窗口开启kafka

```bash
D:\kafka_2.13-3.3.1>bin\windows\kafka-server-start.bat config\server.properties
```

c. 再开个窗口

```bash
// 创建topic,名字为test,指定自主服务器与9092端口,设置1个副本一个分区
D:\kafka_2.13-3.3.1\bin\windows>kafka-topics.bat --create --bootstrap-server localhost:9092 --replication-factor 1 --partitions 1 --topic test
Created topic test.

// 查看目标服务器和端口的所有topic
D:\kafka_2.13-3.3.1\bin\windows>kafka-topics.bat --list --bootstrap-server localhost:9092
test

// 生产者向指定服务器的端口和topic发送消息,此时阻塞,需另开窗口用消费者接收
D:\kafka_2.13-3.3.1\bin\windows>kafka-console-producer.bat --broker-list localhost:9092 --topic test
>hello
>world
>

// 消费者接收消息
D:\kafka_2.13-3.3.1\bin\windows>kafka-console-consumer.bat --bootstrap-server localhost:9092 --topic test --from-beginning
hello
world


// 此时消费者和生产者已经可以通信
```

# Spring整合Kafka

- 访问Kafka
  - 生产者 `kafkaTemplate.send(topic, data);`
- 消费者 
  ```
  @KafkaListener(topics = {"test"})
  public void handleMessage(ConsumerRecord record) {}
  ````

1. 导包

```xml
<dependency>
  <groupId>org.springframework.kafka</groupId>
  <artifactId>spring-kafka</artifactId>
</dependency>
```

2. 在application.properties配置

```
# kafka
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=test-consumer-group
spring.kafka.consumer.enable-auto-commit=true
spring.kafka.consumer.auto-commit-interval=3000
```

3. 测试代码KafkaTests（须在后台启动Kafka，即启动上面所有的cmd命令）

```java
package com.nowcoder.community;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
public class KafkaTests {

    @Autowired
    private KafkaProducer kafkaProducer;

    @Test
    public void testKafka(){
        // 测试生产者生产一个消息，消费者能不能自动收到并消费（这里是打印）
        kafkaProducer.sendMessage("test", "你好");
        kafkaProducer.sendMessage("test", "在吗");

        try{
            Thread.sleep(1000 * 10);  // 阻塞10s，防止消费者来不及输出
        }catch (InterruptedException e){
            e.printStackTrace();
        }
    }

}

@Component
class KafkaProducer{
    @Autowired
    private KafkaTemplate kafkaTemplate;   // 生产者主动调

    public void sendMessage(String topic, String content){
        kafkaTemplate.send(topic, content);
    }
}

@Component
class KafkaConsumer{   // 消费者被动接受

    @KafkaListener(topics = {"test"})  // 没有消息就阻塞，有消息就立刻读（交给方法）
    public void handleMessage(ConsumerRecord record){
        System.out.println(record.value());
    }
}
```

输出

```
2024-05-11 21:21:43,132 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] o.a.k.c.Metadata [Metadata.java:287] [Consumer clientId=consumer-community-consumer-group-1, groupId=community-consumer-group] Cluster ID: mfrvvorjS7yi7__QJnRKBA
2024-05-11 21:21:43,135 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] o.a.k.c.c.i.ConsumerCoordinator [AbstractCoordinator.java:906] [Consumer clientId=consumer-community-consumer-group-1, groupId=community-consumer-group] Discovered group coordinator ggg:9092 (id: 2147483647 rack: null)
2024-05-11 21:21:43,145 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] o.a.k.c.c.i.ConsumerCoordinator [AbstractCoordinator.java:576] [Consumer clientId=consumer-community-consumer-group-1, groupId=community-consumer-group] (Re-)joining group
2024-05-11 21:21:43,175 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] o.a.k.c.c.i.ConsumerCoordinator [AbstractCoordinator.java:1072] [Consumer clientId=consumer-community-consumer-group-1, groupId=community-consumer-group] Request joining group due to: need to re-join with the given member-id: consumer-community-consumer-group-1-ff61a1a6-5228-4ee1-a9ad-dfa5619509fe
2024-05-11 21:21:43,175 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] o.a.k.c.c.i.ConsumerCoordinator [AbstractCoordinator.java:1072] [Consumer clientId=consumer-community-consumer-group-1, groupId=community-consumer-group] Request joining group due to: rebalance failed due to 'The group member needs to have a valid member id before actually entering a consumer group.' (MemberIdRequiredException)
2024-05-11 21:21:43,176 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] o.a.k.c.c.i.ConsumerCoordinator [AbstractCoordinator.java:576] [Consumer clientId=consumer-community-consumer-group-1, groupId=community-consumer-group] (Re-)joining group
2024-05-11 21:21:43,179 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] o.a.k.c.c.i.ConsumerCoordinator [AbstractCoordinator.java:637] [Consumer clientId=consumer-community-consumer-group-1, groupId=community-consumer-group] Successfully joined group with generation Generation{generationId=1, memberId='consumer-community-consumer-group-1-ff61a1a6-5228-4ee1-a9ad-dfa5619509fe', protocol='range'}
2024-05-11 21:21:43,195 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] o.a.k.c.c.i.ConsumerCoordinator [ConsumerCoordinator.java:717] [Consumer clientId=consumer-community-consumer-group-1, groupId=community-consumer-group] Finished assignment for group at generation 1: {consumer-community-consumer-group-1-ff61a1a6-5228-4ee1-a9ad-dfa5619509fe=Assignment(partitions=[test-0])}
2024-05-11 21:21:43,206 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] o.a.k.c.c.i.ConsumerCoordinator [AbstractCoordinator.java:812] [Consumer clientId=consumer-community-consumer-group-1, groupId=community-consumer-group] Successfully synced group in generation Generation{generationId=1, memberId='consumer-community-consumer-group-1-ff61a1a6-5228-4ee1-a9ad-dfa5619509fe', protocol='range'}
2024-05-11 21:21:43,207 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] o.a.k.c.c.i.ConsumerCoordinator [ConsumerCoordinator.java:312] [Consumer clientId=consumer-community-consumer-group-1, groupId=community-consumer-group] Notifying assignor about the new Assignment(partitions=[test-0])
2024-05-11 21:21:43,214 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] o.a.k.c.c.i.ConsumerCoordinator [ConsumerCoordinator.java:324] [Consumer clientId=consumer-community-consumer-group-1, groupId=community-consumer-group] Adding newly assigned partitions: test-0
2024-05-11 21:21:43,227 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] o.a.k.c.c.i.ConsumerCoordinator [ConsumerCoordinator.java:1578] [Consumer clientId=consumer-community-consumer-group-1, groupId=community-consumer-group] Found no committed offset for partition test-0
2024-05-11 21:21:43,242 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] o.a.k.c.c.i.SubscriptionState [SubscriptionState.java:398] [Consumer clientId=consumer-community-consumer-group-1, groupId=community-consumer-group] Resetting offset for partition test-0 to position FetchPosition{offset=0, offsetEpoch=Optional.empty, currentLeader=LeaderAndEpoch{leader=Optional[ggg:9092 (id: 0 rack: null)], epoch=0}}.
2024-05-11 21:21:43,243 INFO [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] o.s.k.l.KafkaMessageListenerContainer [LogAccessor.java:292] community-consumer-group: partitions assigned: [test-0]
2024-05-11 21:21:43,644 INFO [main] o.a.k.c.p.ProducerConfig [AbstractConfig.java:370] ProducerConfig values: 
	acks = -1
	auto.include.jmx.reporter = true
	batch.size = 16384
	bootstrap.servers = [localhost:9092]
	buffer.memory = 33554432
	client.dns.lookup = use_all_dns_ips
	client.id = producer-1
	compression.type = none
	connections.max.idle.ms = 540000
	delivery.timeout.ms = 120000
	enable.idempotence = true
	interceptor.classes = []
	key.serializer = class org.apache.kafka.common.serialization.StringSerializer
	linger.ms = 0
	max.block.ms = 60000
	max.in.flight.requests.per.connection = 5
	max.request.size = 1048576
	metadata.max.age.ms = 300000
	metadata.max.idle.ms = 300000
	metric.reporters = []
	metrics.num.samples = 2
	metrics.recording.level = INFO
	metrics.sample.window.ms = 30000
	partitioner.adaptive.partitioning.enable = true
	partitioner.availability.timeout.ms = 0
	partitioner.class = null
	partitioner.ignore.keys = false
	receive.buffer.bytes = 32768
	reconnect.backoff.max.ms = 1000
	reconnect.backoff.ms = 50
	request.timeout.ms = 30000
	retries = 2147483647
	retry.backoff.ms = 100
	sasl.client.callback.handler.class = null
	sasl.jaas.config = null
	sasl.kerberos.kinit.cmd = /usr/bin/kinit
	sasl.kerberos.min.time.before.relogin = 60000
	sasl.kerberos.service.name = null
	sasl.kerberos.ticket.renew.jitter = 0.05
	sasl.kerberos.ticket.renew.window.factor = 0.8
	sasl.login.callback.handler.class = null
	sasl.login.class = null
	sasl.login.connect.timeout.ms = null
	sasl.login.read.timeout.ms = null
	sasl.login.refresh.buffer.seconds = 300
	sasl.login.refresh.min.period.seconds = 60
	sasl.login.refresh.window.factor = 0.8
	sasl.login.refresh.window.jitter = 0.05
	sasl.login.retry.backoff.max.ms = 10000
	sasl.login.retry.backoff.ms = 100
	sasl.mechanism = GSSAPI
	sasl.oauthbearer.clock.skew.seconds = 30
	sasl.oauthbearer.expected.audience = null
	sasl.oauthbearer.expected.issuer = null
	sasl.oauthbearer.jwks.endpoint.refresh.ms = 3600000
	sasl.oauthbearer.jwks.endpoint.retry.backoff.max.ms = 10000
	sasl.oauthbearer.jwks.endpoint.retry.backoff.ms = 100
	sasl.oauthbearer.jwks.endpoint.url = null
	sasl.oauthbearer.scope.claim.name = scope
	sasl.oauthbearer.sub.claim.name = sub
	sasl.oauthbearer.token.endpoint.url = null
	security.protocol = PLAINTEXT
	security.providers = null
	send.buffer.bytes = 131072
	socket.connection.setup.timeout.max.ms = 30000
	socket.connection.setup.timeout.ms = 10000
	ssl.cipher.suites = null
	ssl.enabled.protocols = [TLSv1.2, TLSv1.3]
	ssl.endpoint.identification.algorithm = https
	ssl.engine.factory.class = null
	ssl.key.password = null
	ssl.keymanager.algorithm = SunX509
	ssl.keystore.certificate.chain = null
	ssl.keystore.key = null
	ssl.keystore.location = null
	ssl.keystore.password = null
	ssl.keystore.type = JKS
	ssl.protocol = TLSv1.3
	ssl.provider = null
	ssl.secure.random.implementation = null
	ssl.trustmanager.algorithm = PKIX
	ssl.truststore.certificates = null
	ssl.truststore.location = null
	ssl.truststore.password = null
	ssl.truststore.type = JKS
	transaction.timeout.ms = 60000
	transactional.id = null
	value.serializer = class org.apache.kafka.common.serialization.StringSerializer

2024-05-11 21:21:43,655 INFO [main] o.a.k.c.p.KafkaProducer [KafkaProducer.java:580] [Producer clientId=producer-1] Instantiated an idempotent producer.
2024-05-11 21:21:43,672 INFO [main] o.a.k.c.u.AppInfoParser [AppInfoParser.java:119] Kafka version: 3.6.1
2024-05-11 21:21:43,672 INFO [main] o.a.k.c.u.AppInfoParser [AppInfoParser.java:120] Kafka commitId: 5e3c2b738d253ff5
2024-05-11 21:21:43,672 INFO [main] o.a.k.c.u.AppInfoParser [AppInfoParser.java:121] Kafka startTimeMs: 1715433703672
2024-05-11 21:21:43,680 INFO [kafka-producer-network-thread | producer-1] o.a.k.c.Metadata [Metadata.java:287] [Producer clientId=producer-1] Cluster ID: mfrvvorjS7yi7__QJnRKBA
2024-05-11 21:21:43,681 INFO [kafka-producer-network-thread | producer-1] o.a.k.c.p.i.TransactionManager [TransactionManager.java:505] [Producer clientId=producer-1] ProducerId set to 1 with epoch 0
你好
在吗
```