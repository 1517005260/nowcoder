# 阻塞队列

框架只是封装，我们这次先看底层实现

![queue](/imgs/queue.png)

- BlockingQueue（java核心api接口）
  - 解决线程通信问题
  - 阻塞方法：put（向队列放数据）、take（从队列拿数据）
- 生产者消费者模式 --> 缓冲两个线程的速度不一致问题 
  - 生产者：产生数据的线程（Thread-1）
  - 消费者：使用数据的进程（Thread-2）
- 实现类
  - `ArrayBlockingQueue` 数组
  - `LinkedBlockingQueue` 链表
  - `PriorityBlockingQueue、SynchronousQueue、DelayQueue`

## 例子——ArrayBlockingQueue

BlockingQueueTests

我们令生产者快于消费者，关注头尾输出

```java
package com.nowcoder.community;

import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class BlockingQueueTests {
    public static void main(String[] args) {
        BlockingQueue queue = new ArrayBlockingQueue(10);  // 容量10

        // 1个生产者3个消费者，工厂模式
        new Thread(new Producer(queue)).start();
        new Thread(new Consumer(queue)).start();
        new Thread(new Consumer(queue)).start();
        new Thread(new Consumer(queue)).start();
    }
}

class Producer implements Runnable{  // 生产数据

    private BlockingQueue<Integer> queue;

    public Producer(BlockingQueue<Integer> queue){
        this.queue = queue;  // 初始化类时就构造队列
    }

    @Override
    public void run() {
        try {
            for(int i = 0; i < 100; i ++){
                Thread.sleep(20); // 每20ms执行一次
                queue.put(i);
                System.out.println(Thread.currentThread().getName() + "生产：" + queue.size());
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}


class Consumer implements Runnable{
    private BlockingQueue<Integer> queue;

    public Consumer(BlockingQueue<Integer> queue){
        this.queue = queue;
    }

    @Override
    public void run() {
        // 一直不停消费
        try {
            while (true){
                Thread.sleep(new Random().nextInt(1000));  // 0-1000ms中随机，大概率消费能力跟不上生产能力
                queue.take();
                System.out.println(Thread.currentThread().getName() + "消费：" + queue.size());
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
```

输出:

```
Thread-0生产：1
Thread-0生产：2
Thread-0生产：3
Thread-0生产：4
Thread-2消费：3
Thread-0生产：4
Thread-0生产：5
Thread-0生产：6
Thread-0生产：7
Thread-0生产：8
Thread-0生产：9
Thread-0生产：10
Thread-3消费：9
Thread-0生产：10
Thread-3消费：9
Thread-0生产：10
.......
.......
Thread-3消费：9
Thread-0生产：10
Thread-1消费：9
Thread-0生产：10
Thread-1消费：9
Thread-0生产：10
Thread-1消费：9
Thread-0生产：10
Thread-3消费：9
Thread-2消费：8
Thread-3消费：7
Thread-1消费：6
Thread-1消费：5
Thread-2消费：4
Thread-3消费：3
Thread-1消费：2
Thread-3消费：1
Thread-2消费：0
```

可以发现，0速度显著快于1，2，3，所以当阻塞队列满了时，便不再生产，缓和了速度矛盾

线程1，2，3交替消费

而且当生产者不生产后，整个程序阻塞了，需要手动停止，因为没有元素消费了