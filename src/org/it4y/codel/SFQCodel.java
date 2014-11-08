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
 */

package org.it4y.codel;


/**
 * Created by luc on 8/16/14.
 */
public class SFQCodel implements Classify {
    private static final int SQRT_top=1023;
    private static final int[] SQRT = new int[SQRT_top+1];
    {
        /* we calculate srt(x) for values of 1..100 for codel inverse sqrt calculation.
         * we cap on 100 so values above 1023 will have same values but that should not give a problem.
         */
        SQRT[0]=1;//to prevent devision by zero later ;-)
        for(int i=1;i<=SQRT_top;i++) {
            SQRT[i]=(int)Math.sqrt((double)i);

        }
    }

    private final int maxQueueSize;
    private final int flow_cnt;
    private final int quantum;
    private final long interval;
    private final long target;

    private int qlen;
    private int backlog;
    private int maxsize;
    private CodelPacketQueue[] flows;
    private int backlogs[];
    private FlowQueue new_flows;
    private FlowQueue old_flows;
    private Classify classifier;

    public SFQCodel() {
        //init linked lists
        this.new_flows =new FlowQueue();
        this.old_flows =new FlowQueue();
        this.maxQueueSize=1024*10;
        this.flow_cnt=1024;
        this.quantum=1500;//thould be MTU or lower
        this.interval=100;
        this.target=10;
        this.qlen = 0;
        this.classifier=this;
        this.init();
    }

    public void init() {
        this.flows =new CodelPacketQueue[this.flow_cnt];
        this.backlogs =new int[this.flow_cnt];
        //init flows structures
        for (int i=0;i< this.flow_cnt;i++) {
            this.flows[i]=new CodelPacketQueue();
        }
    }

    public int hash(final Object d) {
        return d.hashCode();
    }

    @Override
    public int classifyPacket(Packet p) {
        if (p==null) {
            return 0;
        }
        return p.hashCode() % this.flow_cnt;
    }

    /**
     * Drop packet and keep count of flow and queue statistics
     * @param flow
     * @param p
     */
    public void do_drop(final CodelPacketQueue flow, final Packet p) {
        this.qlen--;
        flow.dropped++;
        p.drop();
    }

    /**
     * Drop packet from flow with biggest backlog , as queue is full
     * @return index of flow which has been dropped
     */
    public int drop() {
     int idx=0;
     int maxbacklog=-1;
     //find flow with biggest backlog
     for (int i=0;i< this.flow_cnt;i++) {
            if (this.backlogs[i]> maxbacklog) {
                maxbacklog= this.backlogs[i];
                idx=i;
            }
     }

     final CodelPacketQueue flow = this.flows[idx];
     Packet p=flow.remove();
        this.backlogs[idx]= this.backlogs[idx]-p.size;
        this.do_drop(flow,p);
     p=null;
     //TODO some statistics
     return idx;
    }

    /**
     * Queue a packet, potentially drop a hog sessoin packet if queue is getting full
     * @param p
     */
    public void enqueue(final Packet p) {
        final int idx= this.classifier.classifyPacket(p) % flow_cnt;
        //System.out.println("idx: "+idx+" size: "+p.size+" qlen: "+qlen);
        final CodelPacketQueue flow = this.flows[idx];
        final boolean wasEmpty=flow.isEmpty();
        p.queueTime=System.currentTimeMillis();
        flow.add(p);
        this.qlen++;
        this.backlog=this.backlog()+p.size;
        this.backlogs[idx] = this.backlogs[idx] + p.size;
        if (wasEmpty) {
            this.new_flows.add(flow);
            flow.deficit= this.quantum;
            flow.dropped = 0;
        }
        //in case the number of packet queued max size
        if (this.qlen < this.maxQueueSize) {
            return;
        }
        //drop packet from biggest queue
        System.out.println("dropping overflow");
        this.drop();
    }

    /**
     * get flow which can be used for dequeuing. give higher priority to new_flows when exisiting.
     * always select the same flow until empty our deficite < 0
     * @return
     */
    private CodelPacketQueue getFlow() {
        CodelPacketQueue flow = null;
        FlowQueue head;
        //find a flow for dequeuing
        //First check new_flows. if available , dequeue until empty or deficit < 0 (has dequeued more than quantum butes)
        //in case flow is coming from new_flows & isEmpty move it to old_flows
        //else use this flow for packet dequeuing . we keep the flow in front until we have dequeued quantum bytes
        while(true) {
            head = this.new_flows;
            if (head.isEmpty()) {
                head = this.old_flows;
                if (head.isEmpty()) {
                    //Nothing to report;
                    return null;
                }
            }
            //get flow with removing from queue;
            flow = (CodelPacketQueue) head.first();
            if (flow.deficit <= 0) {
                flow.deficit += this.quantum;
                //remove from queue and add to end of old
                this.old_flows.add(head.remove());
            } else if (flow.isEmpty() && head == this.new_flows) {
                //Add empty new flows to old_flows to prevent starvation
                this.old_flows.add(head.remove());
            } else {
                //found valid flow to investigate
                return flow;
            }
        }
    }

    /**
     * check if packet of flow x must be dropped according to codel algorithm
     * @param flow
     * @param p
     * @param now
     * @return
     */
    private boolean shouldDrop(final CodelPacketQueue flow, final Packet p, final long now) {
        if (p==null) {
            flow.codel_var_first_above_time=0;
            return false;
        }

        //keep track of max size seen
        if (p.size> this.maxsize) {
            this.maxsize =p.size;
        }
        flow.codel_var_ldelay=now-p.queueTime;

        //if time below target or queue.size() < max packet
        if (flow.codel_var_ldelay< this.target || flow.getBacklog() <= this.maxsize) {
            flow.codel_var_first_above_time=0;
            return false;
        }

        if (flow.codel_var_first_above_time==0) {
            flow.codel_var_first_above_time=now+ this.interval;
        } else if(now >= flow.codel_var_first_above_time) {
            return true;
        }
        return false;
    }

    /**
     * calculate next "potential" drop time according to codel control law
     * which is
     *     time = interval / sqrt(count)
     *
     * @param q
     * @param now
     * @return
     */
    private long control_law(final CodelPacketQueue q, final long now) {
        int cnt=Math.min(SQRT_top,q.codel_var_count);
        return now+(interval/SQRT[cnt]);
    }

    /**
     * Dequeue packet , could result in NULL in case all packets are dropped because of codel reason.
     *
     * @return
     */
    public Packet dequeue() {
        final CodelPacketQueue flow;
        Boolean drop;
        //no flows, return no packet
        if ((flow= this.getFlow())==null)
            return null;

        //Dequeue a packet from queue
        Packet p=flow.remove();
        if (p==null) {
            flow.codel_var_dropping = false;
            return p;
        }
        qlen--;
        backlog=backlog-p.size;
        final long now=System.currentTimeMillis();
        drop= this.shouldDrop(flow,p,now);
        /* each flow (queue) can be in 2 states
         *   dropping = false : packet queue was below sojourn time
         *   dropping = true  : packet queue time is >= sojourn time
         */
        if (flow.codel_var_dropping) {
            if (!drop) {
                //packet queue time < sojourn time , leave dropping state
                flow.codel_var_dropping=false;
            } else if (now >= flow.codel_var_drop_next) {
                /*
                 * it time to drop packet as where in dropping state and queue time has been high for interval time
                 * we keep dropping until queue is empty or 1 packet has low sojourn time
                 * this will drop big
                 */
                while(flow.codel_var_dropping && now>flow.codel_var_drop_next) {
                    flow.codel_var_count++;
                    this.do_drop(flow,p); //do_drop will handle drop statics
                    p=flow.remove();
                    if (p==null || !this.shouldDrop(flow,p,now)) {
                        //break the drop loop, we have a good packet
                        flow.codel_var_dropping=false;
                    } else {
                        //calculate next drop interval
                        flow.codel_var_drop_next= this.control_law(flow,now);
                    }
                }
            }
        } else if (drop) {
            this.do_drop(flow,p);
            p=flow.remove();
            drop= this.shouldDrop(flow,p,now);
            flow.codel_var_dropping=true;
            final int delta=flow.codel_var_count-flow.codel_var_lastcount;
            if (delta>1 && (now -flow.codel_var_drop_next)<16* this.interval) {
                flow.codel_var_count=delta;
            } else {
                flow.codel_var_count=1;
            }
            flow.codel_var_lastcount=flow.codel_var_count;
            flow.codel_var_drop_next= this.control_law(flow,now);
        }
        return p;
 	}

    /**
     * reset this queue , silently dropping all packets
     */
    public void reset() {
         long start=System.nanoTime();
         int cnt=qlen;
         Packet p;
         while((p= this.dequeue()) != null) {
             //TODO: SHOULD we count this ?
             p.drop();
         }
        //all empty flows are now in old_flows, remove them
        while(!old_flows.isEmpty()) {
            old_flows.remove();
        }
        System.out.println("reset time: "+((System.nanoTime()-start)/1000L)+"uSec for "+cnt+" packets");
    }

    public int size() {
        return qlen;
    }

    public int backlog(){
        return backlog;
    }

    public boolean isEmpty() {
        return qlen <=0;
    }

    public void setClassifier(Classify classifier) {
        this.classifier=classifier;
    }

    public int getFlowSize() {
        return new_flows.size()+old_flows.size();
    }
    public int getNewFlowSize() {
        return new_flows.size();
    }
}
