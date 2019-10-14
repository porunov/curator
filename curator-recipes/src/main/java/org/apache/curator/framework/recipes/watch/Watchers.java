/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.curator.framework.recipes.watch;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Functional builders to wrap a ZooKeeper Watcher. Call the filter/map/etc. methods
 * as needed and complete by calling {@link Next#process(java.util.function.Consumer)} which
 * returns a Watcher that can be passed to any Curator/ZooKeeper method that accepts Watchers.
 */
public interface Watchers
{
    /**
     * Filter the chained watched event. If the predicate returns false, the chain ends and no further processing
     * occurs.
     *
     * @param filter predicate
     * @return chain
     */
    static Next<WatchedEvent> filter(Predicate<WatchedEvent> filter)
    {
        return new WatchersImpl<>(filter);
    }

    /**
     * Map the chained watched event to a new object
     *
     * @param mapper mapper
     * @return chain
     */
    static <R> Next<R> map(Function<WatchedEvent, ? super R> mapper)
    {
        return new WatchersImpl<>(mapper);
    }

    interface Next<T>
    {
        /**
         * Filter the chained value. If the predicate returns false, the chain ends and no further processing
         * occurs.
         *
         * @param filter predicate
         * @return chain
         */
        Next<T> filter(Predicate<? super T> filter);

        /**
         * Peek at the chained value
         *
         * @param consumer consumer
         * @return chain
         */
        Next<T> peek(Consumer<? super T> consumer);

        /**
         * Map the chained value to a new object
         *
         * @param mapper mapper
         * @return chain
         */
        <R> Next<R> map(Function<? super T, ? extends R> mapper);

        /**
         * Complete the chain and return a new Watcher that consists of all the previous steps in the
         * chain. When the Watcher is called by ZooKeeper, all steps of the chain are called in order.
         *
         * @param handler consumer for the final value of the chain
         * @return new Watcher
         */
        Watcher process(Consumer<T> handler);
    }
}
