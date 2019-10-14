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

import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

public class WatcherHelpers
{
    private static final ZKPaths.PathAndNode NULL_PATH_AND_NODE = new ZKPaths.PathAndNode("/", "");

    public static ZKPaths.PathAndNode mapToPathAndNode(WatchedEvent event)
    {
        if ( event.getType() == Watcher.Event.EventType.None )
        {
            return NULL_PATH_AND_NODE;
        }
        return ZKPaths.getPathAndNode(event.getPath());
    }

    public static List<String> mapToParts(WatchedEvent event)
    {
        if ( event.getType() == Watcher.Event.EventType.None )
        {
            return Collections.emptyList();
        }
        return ZKPaths.split(event.getPath());
    }

    public static Predicate<WatchedEvent> filterNode(Predicate<String> nodeFilter)
    {
        return event -> nodeFilter.test(mapToPathAndNode(event).getNode());
    }

    public static Predicate<WatchedEvent> filterPath(Predicate<String> pathFilter)
    {
        return event -> pathFilter.test(mapToPathAndNode(event).getPath());
    }

    public static Predicate<WatchedEvent> filterDepth(IntPredicate depthFilter)
    {
        return event -> depthFilter.test(mapToParts(event).size());
    }

    public static Predicate<WatchedEvent> filterSystemEvents()
    {
        return event -> event.getType() == Watcher.Event.EventType.None;
    }

    public static Predicate<WatchedEvent> filterNonSystemEvents()
    {
        return event -> event.getType() != Watcher.Event.EventType.None;
    }

    private WatcherHelpers()
    {
    }
}
