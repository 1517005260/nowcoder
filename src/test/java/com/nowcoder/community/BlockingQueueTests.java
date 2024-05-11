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