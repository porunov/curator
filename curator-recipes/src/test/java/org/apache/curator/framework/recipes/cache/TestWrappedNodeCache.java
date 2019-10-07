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
package org.apache.curator.framework.recipes.cache;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.TestCleanState;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.compatibility.Timing2;
import org.apache.curator.test.compatibility.Zk35MethodInterceptor;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.utils.Compatibility;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

import static org.apache.curator.framework.recipes.cache.CuratorCache.Options.RECURSIVE;
import static org.apache.curator.framework.recipes.cache.CuratorCacheListener.builder;

@Test(groups = Zk35MethodInterceptor.zk35Group)
public class TestWrappedNodeCache extends BaseClassForTests
{
    private final Timing2 timing = new Timing2();

    @Test
    public void testDeleteThenCreate() throws Exception
    {
        CuratorCache cache = null;
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), new RetryOneTime(1));
        try
        {
            client.start();
            client.create().creatingParentsIfNeeded().forPath("/test/foo", "one".getBytes());

            final Semaphore semaphore = new Semaphore(0);
            cache = CuratorCache.build(client, "/test/foo", RECURSIVE);
            NodeCacheListener listener = semaphore::release;
            cache.listenable().addListener(builder().forNodeCache(listener).build());

            cache.start();
            Assert.assertTrue(timing.acquireSemaphore(semaphore));

            Assert.assertTrue(cache.getRootData().isPresent());
            Assert.assertEquals(cache.getRootData().get().getData(), "one".getBytes());

            client.delete().forPath("/test/foo");
            Assert.assertTrue(timing.acquireSemaphore(semaphore));
            client.create().forPath("/test/foo", "two".getBytes());
            Assert.assertTrue(timing.acquireSemaphore(semaphore));

            Assert.assertTrue(cache.getRootData().isPresent());
            Assert.assertEquals(cache.getRootData().get().getData(), "two".getBytes());
        }
        finally
        {
            CloseableUtils.closeQuietly(cache);
            TestCleanState.closeAndTestClean(client);
        }
    }

    @Test
    public void testKilledSession() throws Exception
    {
        CuratorCache cache = null;
        CuratorFramework client = null;
        try
        {
            client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1));
            client.start();
            client.create().creatingParentsIfNeeded().forPath("/test/node", "start".getBytes());

            CountDownLatch lostLatch = new CountDownLatch(1);
            client.getConnectionStateListenable().addListener((__, newState) -> {
                if ( newState == ConnectionState.LOST )
                {
                    lostLatch.countDown();
                }
            });

            cache = CuratorCache.build(client,"/test/node", RECURSIVE);

            Semaphore latch = new Semaphore(0);
            NodeCacheListener listener = latch::release;
            cache.listenable().addListener(builder().forNodeCache(listener).build());

            cache.start();
            Assert.assertTrue(timing.acquireSemaphore(latch));

            Compatibility.injectSessionExpiration(client.getZookeeperClient().getZooKeeper());
            Assert.assertTrue(timing.awaitLatch(lostLatch));

            Assert.assertTrue(cache.getRootData().isPresent());
            Assert.assertEquals(cache.getRootData().get().getData(), "start".getBytes());

            client.setData().forPath("/test/node", "new data".getBytes());
            Assert.assertTrue(timing.acquireSemaphore(latch));
            Assert.assertTrue(cache.getRootData().isPresent());
            Assert.assertEquals(cache.getRootData().get().getData(), "new data".getBytes());
        }
        finally
        {
            CloseableUtils.closeQuietly(cache);
            TestCleanState.closeAndTestClean(client);
        }
    }

    @Test
    public void testBasics() throws Exception
    {
        CuratorCache cache = null;
        CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1));
        try
        {
            client.start();
            client.create().forPath("/test");

            cache = CuratorCache.build(client, "/test/node", RECURSIVE);
            cache.start();

            final Semaphore semaphore = new Semaphore(0);
            NodeCacheListener listener = semaphore::release;
            cache.listenable().addListener(builder().forNodeCache(listener).build());

            Assert.assertNull(cache.getRootData().orElse(null));

            client.create().forPath("/test/node", "a".getBytes());
            Assert.assertTrue(timing.acquireSemaphore(semaphore));
            Assert.assertEquals(cache.getRootData().orElse(null).getData(), "a".getBytes());

            client.setData().forPath("/test/node", "b".getBytes());
            Assert.assertTrue(timing.acquireSemaphore(semaphore));
            Assert.assertEquals(cache.getRootData().orElse(null).getData(), "b".getBytes());

            client.delete().forPath("/test/node");
            Assert.assertTrue(timing.acquireSemaphore(semaphore));
            Assert.assertNull(cache.getRootData().orElse(null));
        }
        finally
        {
            CloseableUtils.closeQuietly(cache);
            TestCleanState.closeAndTestClean(client);
        }
    }
}
