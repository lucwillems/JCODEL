/*
 * Copyright 2014 Luc Willems (T.M.M.)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * Codel - The Controlled-Delay Active Queue Management algorithm
 *  Copyright (C) 2011-2012 Kathleen Nichols <nichols@pollere.com>
 *  Copyright (C) 2011-2012 Van Jacobson <van@pollere.net>
 *
 *  Implemented on linux by :
 *  Copyright (C) 2012 Michael D. Taht <dave.taht@bufferbloat.net>
 *  Copyright (C) 2012 Eric Dumazet <edumazet@google.com>
 */

package org.it4y.codel;

import org.junit.Assert;
import org.junit.Test;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Created by luc on 8/22/14.
 */
public class PacketQueueTest {


    @Test
    public void testMultiThreadedQueue() throws InterruptedException {
        PacketQueue queue = new PacketQueue();

        class Producer implements Runnable {
            private final PacketQueue queue;
            private final Random rn = new Random();

            Producer(PacketQueue q) {
                queue = q;
            }

            public void run() {
                System.out.println("start producer " + Thread.currentThread().getId());
                for (int i = 0; i < 10; i++) {
                    try {
                        Thread.sleep(Math.abs(rn.nextInt() % 5));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    queue.add(new Packet(i));
                }
                System.out.println(System.currentTimeMillis() + " done producer");
            }
        }

        class Consumer implements Runnable {
            private final PacketQueue queue;

            Consumer(PacketQueue q) {
                queue = q;
            }

            public void run() {
                System.out.println("start consumer");
                try {
                    Packet x;
                    while (true) {
                        x = queue.Take(10, TimeUnit.MILLISECONDS);
                        if (x != null) {
                            //System.out.println(System.currentTimeMillis() + " " + x.size + " " + x.getDeltaTime() + " " + x.queueTime);
                        } else {
                            break;
                        }
                    }
                    System.out.println(System.currentTimeMillis() + " done consumer");
                } catch (InterruptedException ex) {
                }
            }
        }

        Producer producer = new Producer(queue);
        Consumer consumer = new Consumer(queue);
        Thread p1 = new Thread(new Producer(queue));
        Thread p2 = new Thread(new Producer(queue));
        Thread p3 = new Thread(new Producer(queue));
        Thread c1 = new Thread(consumer);
        p1.start();
        p2.start();
        p3.start();
        c1.start();
        c1.join();
        System.out.println("done");
    }

    @Test
    public void testPerformanceMultiThreadedQueue() throws InterruptedException {
        PacketQueue queue = new PacketQueue();
        final long count = 100000000L;
        System.out.println("warm up...");
        //warmup JIT and other optimized code
        for (int i = 0; i < 10000; i++) {
            queue.add(new Packet(0));
            queue.remove();
        }
        queue.clear();

        class Monitor implements Runnable {
            private final PacketQueue queue;
            private long prevAdded;
            private long prevRemoved;
            private long prevWaited;
            Monitor(PacketQueue queue) {
                this.queue = queue;
            }

            public void run() {
                System.out.println("Start monitor " + Thread.currentThread().getId());
                int cnt=0;
                try {

                    do {
                        Thread.sleep(1000);
                        cnt++;
                        long a=queue.getAdded();
                        long r=queue.getRemoved();
                        long w=queue.getWaited();
                        System.out.println(cnt+" Queued: " + (a-prevAdded) + " DeQueued: " + (r-prevRemoved) + " TOTAL: "+((a-prevAdded)+(r-prevRemoved))+" size: " + queue.size() + " waiting:" + (w-prevWaited) + " = " + ((w-prevWaited) * 100.0 / (r-prevRemoved)) + " %");
                        prevAdded=a;
                        prevRemoved=r;
                        prevWaited=w;

                    } while (queue.getRemoved() < count);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("done monitor" + Thread.currentThread().getId());
            }
        }
        class Producer implements Runnable {
            private final PacketQueue queue;
            private final Random rn = new Random();

            Producer(PacketQueue q) {
                queue = q;
            }

            public void run() {
                System.out.println("start producer " + Thread.currentThread().getId());
                for (long i = 0; i < count; i++) {
                    queue.add(new Packet(10));
                }
                System.out.println("Queued: " + queue.getAdded() + " DeQueued: " + queue.getRemoved() + " size: " + queue.size() + " waiting:" + queue.getWaited());
                System.out.println("done producer " + Thread.currentThread().getId());
            }
        }

        class Consumer implements Runnable {
            private final PacketQueue queue;

            Consumer(PacketQueue q) {
                queue = q;
            }

            public void run() {
                System.out.println("start consumer " + Thread.currentThread().getId());
                while (true) {
                    try {
                        queue.Take();//10,TimeUnit.MICROSECONDS);//this blocks if nothing is in the queue
                        //queue.remove();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (queue.getRemoved() >= count && queue.isEmpty()) {
                        break;
                    }
                }
                System.out.println(System.currentTimeMillis() + " done consumer");
                System.out.println("done consumer " + Thread.currentThread().getId());
            }
        }

        Producer producer = new Producer(queue);
        Consumer consumer = new Consumer(queue);
        Monitor monitor = new Monitor(queue);
        Thread p1 = new Thread(new Producer(queue));
        Thread c1 = new Thread(consumer);
        Thread m1 = new Thread(monitor);
        Long start = System.nanoTime();
        p1.start();
        m1.start();
        Thread.sleep(100);
        c1.start();
        c1.join();
        long time = (System.nanoTime() - start) / 1000;
        System.out.println("done : time=" + time + "usec for " + count + " " + ((double) time / count) + " usec per action");
        System.out.println("Waited: " + queue.getWaited());
        System.out.println("Added: " + queue.getAdded());
        System.out.println("RemoveD: " + queue.getRemoved());
        System.out.println("size: "+queue.size());
        System.out.println("backlog: "+queue.getBacklog());
    }


    @Test
    public void testPacketQueue() {
        PacketQueue queue = new PacketQueue();
        Assert.assertNotNull(queue);
        Assert.assertEquals(0, queue.size());
        Assert.assertTrue(queue.isEmpty());
        queue.add(new Packet(10));
        Assert.assertEquals(1, queue.size());
        Assert.assertEquals(10,queue.getBacklog());
        Assert.assertFalse(queue.isEmpty());
        queue.add(new Packet(20));
        Assert.assertEquals(2, queue.size());
        Assert.assertEquals(30,queue.getBacklog());
        Assert.assertFalse(queue.isEmpty());
        queue.add(new Packet(30));
        Assert.assertEquals(3, queue.size());
        Assert.assertFalse(queue.isEmpty());
        Assert.assertEquals(60,queue.getBacklog());
        queue.add(new Packet(40));
        Assert.assertEquals(4, queue.size());
        Assert.assertFalse(queue.isEmpty());
        Assert.assertEquals(100,queue.getBacklog());


        //pop queue
        Packet x1 = queue.remove();
        Assert.assertNotNull(x1);
        Assert.assertEquals(10, x1.size);
        Assert.assertNull(x1.next());
        Assert.assertEquals(90,queue.getBacklog());

        Packet x2 = queue.remove();
        Assert.assertNotNull(x2);
        Assert.assertEquals(20, x2.size);
        Assert.assertNull(x2.next());
        Assert.assertEquals(70,queue.getBacklog());

        Packet x3 = queue.remove();
        Assert.assertNotNull(x3);
        Assert.assertEquals(30, x3.size);
        Assert.assertNull(x3.next());
        Assert.assertEquals(40,queue.getBacklog());

        Packet x4 = queue.remove();
        Assert.assertNotNull(x4);
        Assert.assertEquals(40, x4.size);
        Assert.assertNull(x4.next());
        Assert.assertEquals(0,queue.getBacklog());

        Packet x5 = queue.remove();
        Assert.assertNull(x5);
        Assert.assertEquals(0, queue.size());
        Assert.assertEquals(0,queue.getBacklog());
        Assert.assertTrue(queue.isEmpty());

        Packet x6 = queue.remove();
        Assert.assertNull(x6);
        Assert.assertEquals(0, queue.size());
        Assert.assertEquals(0,queue.getBacklog());
        Assert.assertTrue(queue.isEmpty());
    }

}