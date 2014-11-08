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

/**
 * Created by luc on 8/22/14.
 */
public class CodelPacketQueue extends PacketQueue {
    public int deficit;
    public int dropped;

    /* codel_var struct is embedded */
    public int codel_var_count;
    public int codel_var_lastcount;
    public boolean codel_var_dropping;
    public int codel_var_rec_inv_sqrt;
    public long codel_var_first_above_time;
    public long codel_var_drop_next;
    public long codel_var_ldelay;


}
