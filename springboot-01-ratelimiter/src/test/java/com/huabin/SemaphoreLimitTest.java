package com.huabin;

import java.util.concurrent.*;


public class SemaphoreLimitTest {

    // 定义一个执行线程池
    private final Executor executor = new ThreadPoolExecutor(10, 20, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(10));
    // 每次只能执行5个任务
    private final Semaphore semaphore = new Semaphore(5);

    // 模拟测试
    public static void main(String[] args) {
        final SemaphoreLimitTest semaphoreLimitTest = new SemaphoreLimitTest();
        // 同时进来8个任务
        for (int i = 0; i < 8; i++) {
            // 定义8个线程
//            new Thread("线程" + i) {
//                @Override
//                public void run() {
            semaphoreLimitTest.process();
//                }
//            }.start();
        }
        System.out.println("=======");
    }

    private void process() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean acquire = semaphore.tryAcquire(1, TimeUnit.SECONDS);
                    if (!acquire) {
                        throw new Exception("异常");
                    }
                    System.out.println(acquire);
                    System.out.println(Thread.currentThread().getId());
                    Thread.sleep(3000);
                    semaphore.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

}
