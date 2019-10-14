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

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.compatibility.Timing2;
import org.apache.zookeeper.Watcher;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import static org.apache.curator.framework.recipes.watch.WatcherHelpers.*;
import static org.apache.curator.framework.recipes.watch.Watchers.filter;
import static org.apache.curator.framework.recipes.watch.Watchers.map;

public class TestWatchers extends BaseClassForTests
{
    private final Timing2 timing = new Timing2();

    @Test
    public void testFilterFirst() throws Exception
    {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1)) )
        {
            client.start();

            Semaphore latch = new Semaphore(0);
            Watcher watcher = filter(filterNonSystemEvents())
                .filter(event -> event.getType() == Watcher.Event.EventType.NodeCreated)
                .map(WatcherHelpers::mapToPathAndNode)
                .filter(z -> z.getPath().equals("/one/two"))
                .process(__ -> latch.release());
            client.checkExists().usingWatcher(watcher).forPath("/one/two/three");

            client.create().forPath("/one");
            timing.sleepABit();
            Assert.assertEquals(latch.availablePermits(), 0);

            client.create().forPath("/one/two");
            timing.sleepABit();
            Assert.assertEquals(latch.availablePermits(), 0);

            client.create().forPath("/one/two/three");
            Assert.assertTrue(timing.acquireSemaphore(latch, 1));
        }
    }

    @Test
    public void testMapFirst() throws Exception
    {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1)) )
        {
            client.start();

            BlockingQueue<String> result = new LinkedBlockingQueue<>();
            BlockingQueue<String> peekedResult = new LinkedBlockingQueue<>();
            Watcher watcher = map(event -> "The path is: " + event.getPath())
                .peek(peekedResult::add)
                .process(result::add);
            client.checkExists().usingWatcher(watcher).forPath("/one");

            client.create().forPath("/one");
            Assert.assertEquals(timing.takeFromQueue(peekedResult), "The path is: /one");
            Assert.assertEquals(timing.takeFromQueue(result), "The path is: /one");
        }
    }

    @Test
    public void testSystemEvents() throws Exception
    {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1)) )
        {
            client.start();

            CountDownLatch latch = new CountDownLatch(1);
            Watcher watcher = filter(filterSystemEvents())
                .process(__ -> latch.countDown());
            client.checkExists().usingWatcher(watcher).forPath("/one");
            server.stop();
            Assert.assertTrue(timing.awaitLatch(latch));
        }
    }

    @Test
    public void testHelpers() throws Exception
    {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1)) )
        {
            client.start();

            BlockingQueue<String> result = new LinkedBlockingQueue<>();
            Watcher watcher = filter(filterPath(node -> node.startsWith("/a")))
                .filter(filterNode(node -> node.equals("c")))
                .filter(filterDepth(depth -> depth > 1))
                .map(WatcherHelpers::mapToParts)
                .map(parts -> String.join("|", parts))
                .process(result::add);
            client.checkExists().usingWatcher(watcher).forPath("/a/b/c");
            client.create().creatingParentsIfNeeded().forPath("/a/b/c");
            Assert.assertEquals(timing.takeFromQueue(result), "a|b|c");
        }
    }
}
