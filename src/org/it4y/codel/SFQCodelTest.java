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

/**
 * Created by luc on 8/24/14.
 */
public class SFQCodelTest {

  @Test
  public void testEmptySFQCodel() {
      SFQCodel codel=new SFQCodel();
      Assert.assertNotNull(codel);
      //Queue should be empty
      Assert.assertEquals(0,codel.size());
      Assert.assertEquals(0,codel.backlog());
      Assert.assertEquals(true,codel.isEmpty());
      Assert.assertEquals(0,codel.getFlowSize());
      Packet packet = codel.dequeue();
      Assert.assertNull(packet);
      Assert.assertEquals(0,codel.size());
      Assert.assertEquals(0,codel.backlog());
      Assert.assertEquals(0,codel.getFlowSize());
      Assert.assertEquals(true,codel.isEmpty());

      testReset(codel);
    }


    @Test
    public void testSingleFlowSFQCodel() {
        SFQCodel codel=new SFQCodel();
        codel.setClassifier(new Classify() {
            @Override
            public int classifyPacket(Packet p) {
                return p.size;
            }
        });

        Assert.assertNotNull(codel);
        //Queue should be empty
        Assert.assertEquals(0,codel.size());
        Assert.assertEquals(0,codel.backlog());
        Assert.assertEquals(true,codel.isEmpty());
        //Queue a packet
        codel.enqueue(new Packet(10));
        Assert.assertEquals(1,codel.size());
        Assert.assertEquals(10,codel.backlog());
        Assert.assertEquals(1,codel.getFlowSize());
        Assert.assertEquals(1,codel.getNewFlowSize());
        Assert.assertEquals(false,codel.isEmpty());
        //Queue a packet
        codel.enqueue(new Packet(10));
        Assert.assertEquals(2,codel.size());
        Assert.assertEquals(20,codel.backlog());
        Assert.assertEquals(1,codel.getFlowSize());
        Assert.assertEquals(1,codel.getNewFlowSize());
        Assert.assertEquals(false,codel.isEmpty());
        //Queue a packet
        codel.enqueue(new Packet(10));
        Assert.assertEquals(3,codel.size());
        Assert.assertEquals(30,codel.backlog());
        Assert.assertEquals(1,codel.getFlowSize());
        Assert.assertEquals(1,codel.getNewFlowSize());
        Assert.assertEquals(false,codel.isEmpty());

        testReset(codel);
    }

    @Test
    public void testMultiFlowsSFQCodel() {
        SFQCodel codel=new SFQCodel();
        codel.setClassifier(new Classify() {
            @Override
            public int classifyPacket(Packet p) {
                return p.size;
            }
        });

        Assert.assertNotNull(codel);
        //Queue should be empty
        Assert.assertEquals(0,codel.size());
        Assert.assertEquals(0,codel.backlog());
        Assert.assertEquals(true,codel.isEmpty());
        //Queue a packet
        codel.enqueue(new Packet(10));
        Assert.assertEquals(1,codel.size());
        Assert.assertEquals(10,codel.backlog());
        Assert.assertEquals(1,codel.getFlowSize());
        Assert.assertEquals(1,codel.getNewFlowSize());
        Assert.assertEquals(false,codel.isEmpty());
        //Queue a packet
        codel.enqueue(new Packet(10));
        Assert.assertEquals(2,codel.size());
        Assert.assertEquals(20,codel.backlog());
        Assert.assertEquals(1,codel.getFlowSize());
        Assert.assertEquals(1,codel.getNewFlowSize());
        Assert.assertEquals(false,codel.isEmpty());
        //Queue a packet
        codel.enqueue(new Packet(20));
        Assert.assertEquals(3,codel.size());
        Assert.assertEquals(40,codel.backlog());
        Assert.assertEquals(2,codel.getFlowSize());
        Assert.assertEquals(2,codel.getNewFlowSize());
        Assert.assertEquals(false,codel.isEmpty());

        testReset(codel);
    }

    @Test
    public void testOverRunFlowsSFQCodel() {
        SFQCodel codel=new SFQCodel();
        codel.setClassifier(new Classify() {
            @Override
            public int classifyPacket(Packet p) {
                return p.size;
            }
        });

        Assert.assertNotNull(codel);
        //Queue should be empty
        Assert.assertEquals(0,codel.size());
        Assert.assertEquals(0,codel.backlog());
        Assert.assertEquals(true,codel.isEmpty());
        int count=10000;
        int size=0;
        for (int i=0;i<count;i++) {
            codel.enqueue(new Packet(i));
            size=size+i;

        }
        Assert.assertEquals(count,codel.size());
        Assert.assertEquals(size,codel.backlog());
        Assert.assertEquals(1024,codel.getFlowSize());
        Assert.assertEquals(1024,codel.getNewFlowSize());
        Assert.assertEquals(false,codel.isEmpty());
        testReset(codel);
    }

    @Test
    public void testOverRunPacketSFQCodel() {
        SFQCodel codel=new SFQCodel();
        codel.setClassifier(new Classify() {
            @Override
            public int classifyPacket(Packet p) {
                return p.size;
            }
        });

        Assert.assertNotNull(codel);
        //Queue should be empty
        Assert.assertEquals(0,codel.size());
        Assert.assertEquals(0,codel.backlog());
        Assert.assertEquals(true,codel.isEmpty());
        int count=10000;
        int size=0;
        for (int i=0;i<count;i++) {
            codel.enqueue(new Packet(10));
            size=size+10;

        }
        Assert.assertEquals(count,codel.size());
        Assert.assertEquals(size,codel.backlog());
        Assert.assertEquals(1,codel.getFlowSize());
        Assert.assertEquals(1,codel.getNewFlowSize());
        Assert.assertEquals(false,codel.isEmpty());
        testReset(codel);
    }

    private void testReset(SFQCodel codel) {
        codel.reset();
        Assert.assertEquals(0,codel.size());
        Assert.assertEquals(0,codel.backlog());
        Assert.assertEquals(0,codel.getFlowSize());
        Assert.assertEquals(0,codel.getNewFlowSize());
        Assert.assertEquals(true,codel.isEmpty());

    }
}
