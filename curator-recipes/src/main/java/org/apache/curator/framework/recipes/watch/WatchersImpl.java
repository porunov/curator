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

public class WatchersImpl<T> implements Watchers.Next<T>
{
    private final Step step;

    private static class Step
    {
        private final Predicate filter;
        private final Function mapper;

        private final Step head;
        private Step next;

        Step(Predicate filter, Function mapper)
        {
            this(filter, mapper, null);
        }

        Step(Predicate filter, Function mapper, Step head)
        {
            this.filter = (filter != null) ? filter : (__ -> true);
            this.mapper = (mapper != null) ? mapper : Function.identity();
            this.head = (head != null) ? head : this;
        }
    }

    WatchersImpl(Predicate<WatchedEvent> filter)
    {
        step = new Step(filter, null);
    }

    <R> WatchersImpl(Function<WatchedEvent, ? super R> mapper)
    {
        step = new Step(null, mapper);
    }

    private WatchersImpl(Step step)
    {
        this.step = step;
    }

    @Override
    public Watchers.Next<T> filter(Predicate<? super T> filter)
    {
        this.step.next = new Step(filter, null, step.head);
        return new WatchersImpl<>(this.step.next);
    }

    @Override
    public Watchers.Next<T> peek(Consumer<? super T> consumer)
    {
        return filter(value -> {
            consumer.accept(value);
            return true;
        });
    }

    @Override
    public <R> Watchers.Next<R> map(Function<? super T, ? extends R> mapper)
    {
        this.step.next = new Step(null, mapper, step.head);
        return new WatchersImpl<>(this.step.next);
    }

    @Override
    @SuppressWarnings("unchecked")  // all public APIs are correctly typed so this is safe
    public Watcher process(Consumer<T> handler)
    {
        return event -> {
            Object value = event;
            Step currentStep = step.head;
            while ( currentStep != null )
            {
                if ( !currentStep.filter.test(value) )
                {
                    return; // exit lambda
                }
                value = currentStep.mapper.apply(value);
                currentStep = currentStep.next;
            }

            ((Consumer)handler).accept(value);
        };
    }
}
