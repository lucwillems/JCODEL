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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by luc on 8/22/14.
 */
public abstract class LinkedQueue<T extends Queueable<T>> {
    private volatile T head;
    private T tail;
    private int size;
    private long waited;
    private long added;
    private long removed;

    final Lock lock = new ReentrantLock();
    final Condition notEmpty = this.lock.newCondition();

    public boolean isEmpty() {
       return this.head == null;
    }

    public T Take(final long time, final TimeUnit unit) throws InterruptedException {
        if (this.head==null) {
            this.lock.lock();
            try {
                if (this.head == null) {
                    this.notEmpty.await(time, unit);
                    this.waited++;
                }
                return this.remove();
            } finally {
                this.lock.unlock();
            }
        }
        return remove();
    }

    public T Take() throws InterruptedException {
        if (this.head==null) {
            this.lock.lock();
            try {
                while (this.head == null) {
                    this.notEmpty.await();
                    this.waited++;
                }
                return this.remove();
            } finally {
                this.lock.unlock();
            }
        }
        return remove();
    }

    public int size() {
        return this.size;
    }

    //performance counters, are always unsigned !!!
    public long getWaited() {
        return (this.waited & (Long.MAX_VALUE>>1));
    }
    public long getAdded() {
        return (this.added & (Long.MAX_VALUE>>1));
    }
    public long getRemoved() {
        return (this.removed & (Long.MAX_VALUE>>1));
    }

    public T first() {
        return this.head;
    }


    public abstract void doneAdd(T x);

    public abstract void doneRemove(T x);

    public void add(final T x) {
        this.lock.lock();
        try {
            if (this.tail == null) {
                this.head = x;
                this.tail = x;
            } else {
                this.tail.next(x);
                this.tail = x;
            }
            this.size++;
            this.added++;
            this.doneAdd(x);
            this.notEmpty.signal();
      } finally {
            this.lock.unlock();
      }
    }

    public T remove() {
        if (head == null) {
            return null;
        }
        this.lock.lock();
        try {
            if (this.head != null) {
                final T result = this.head;
                if ((this.head = this.head.next()) ==null){
                    this.tail = null;
                }
                result.next(null);
                this.size--;
                this.removed++;
                this.doneRemove(result);
                return result;
            }
            return null;
        } finally {
            this.lock.unlock();
        }
    }

    public void clear() {
        this.lock.lock();
        try {
            while (this.head != null) {
                this.remove();
            }
            this.added =0;
            this.removed =0;
            this.waited =0;
        } finally {
            this.lock.unlock();
        }
    }
}
