/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.hawt.synapse.jolokia;

import java.util.List;

class MBeanListCache {
    /**
     * Cache Time-To-Live in milliseconds.
     */
    static final long CACHE_TTL_MILLIS = 5 * 60 * 1000; // 5 minutes

    final List<String> mbeans;
    final long timestamp;

    MBeanListCache(List<String> mbeans) {
        this.mbeans = mbeans;
        this.timestamp = System.currentTimeMillis();
    }

    boolean isValid() {
        return (System.currentTimeMillis() - timestamp) < CACHE_TTL_MILLIS;
    }

    List<String> getMBeans() {
        return mbeans;
    }
}
