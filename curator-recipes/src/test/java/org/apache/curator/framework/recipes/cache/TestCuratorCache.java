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
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.BaseClassForTests;
import org.apache.curator.test.InstanceSpec;
import org.apache.curator.test.TestingCluster;
import org.apache.curator.test.compatibility.Timing2;
import org.apache.curator.test.compatibility.Zk35MethodInterceptor;
import org.testng.Assert;
import org.testng.annotations.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.curator.framework.recipes.cache.CuratorCache.Options.RECURSIVE;
import static org.apache.curator.framework.recipes.cache.CuratorCacheListener.builder;

@Test(groups = Zk35MethodInterceptor.zk35Group)
public class TestCuratorCache extends BaseClassForTests
{
    private final Timing2 timing = new Timing2();

    @Test
    public void testServerLoss() throws Exception   // mostly copied from TestPathChildrenCacheInCluster
    {
        try (TestingCluster cluster = new TestingCluster(3))
        {
            cluster.start();

            try (CuratorFramework client = CuratorFrameworkFactory.newClient(cluster.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1)))
            {
                client.start();
                client.create().creatingParentsIfNeeded().forPath("/test");

                try (CuratorCache cache = CuratorCache.build(client, "/test", RECURSIVE))
                {
                    cache.start();

                    CountDownLatch reconnectLatch = new CountDownLatch(1);
                    client.getConnectionStateListenable().addListener((__, newState) -> {
                        if ( newState == ConnectionState.RECONNECTED )
                        {
                            reconnectLatch.countDown();
                        }
                    });
                    CountDownLatch latch = new CountDownLatch(3);
                    cache.listenable().addListener((__, ___, ____) -> latch.countDown());

                    client.create().forPath("/test/one");
                    client.create().forPath("/test/two");
                    client.create().forPath("/test/three");

                    Assert.assertTrue(timing.awaitLatch(latch));

                    InstanceSpec connectionInstance = cluster.findConnectionInstance(client.getZookeeperClient().getZooKeeper());
                    cluster.killServer(connectionInstance);

                    Assert.assertTrue(timing.awaitLatch(reconnectLatch));

                    timing.sleepABit();

                    Assert.assertEquals(cache.storage().stream().count(), 4);
                }
            }
        }
    }

    @Test
    public void testUpdateWhenNotCachingData() throws Exception // mostly copied from TestPathChildrenCache
    {
        CuratorCacheStorage storage = new StandardCuratorCacheStorage(false);
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1)))
        {
            client.start();
            final CountDownLatch updatedLatch = new CountDownLatch(1);
            final CountDownLatch addedLatch = new CountDownLatch(1);
            client.create().creatingParentsIfNeeded().forPath("/test");
            try (CuratorCache cache = CuratorCache.build(client, storage, "/test", RECURSIVE))
            {
                cache.listenable().addListener(builder().forChanges((__, ___) -> updatedLatch.countDown()).build());
                cache.listenable().addListener(builder().forCreates(__ -> addedLatch.countDown()).build());
                cache.start();

                client.create().forPath("/test/foo", "first".getBytes());
                Assert.assertTrue(timing.awaitLatch(addedLatch));

                client.setData().forPath("/test/foo", "something new".getBytes());
                Assert.assertTrue(timing.awaitLatch(updatedLatch));
            }
        }
    }

    @Test
    public void testAfterInitialized() throws Exception
    {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1)))
        {
            client.start();
            client.create().creatingParentsIfNeeded().forPath("/test");
            client.create().creatingParentsIfNeeded().forPath("/test/one");
            client.create().creatingParentsIfNeeded().forPath("/test/one/two");
            client.create().creatingParentsIfNeeded().forPath("/test/one/two/three");
            try (CuratorCache cache = CuratorCache.build(client, "/test", RECURSIVE))
            {
                CountDownLatch initializedLatch = new CountDownLatch(1);
                AtomicInteger eventCount = new AtomicInteger(0);
                CuratorCacheListener listener = new CuratorCacheListener()
                {
                    @Override
                    public void event(Type type, ChildData oldData, ChildData data)
                    {
                        eventCount.incrementAndGet();
                    }

                    @Override
                    public void initialized()
                    {
                        initializedLatch.countDown();
                    }
                };
                cache.listenable().addListener(CuratorCacheListener.builder().forAll(listener).afterInitialized().build());
                cache.start();
                Assert.assertTrue(timing.awaitLatch(initializedLatch));

                Assert.assertEquals(initializedLatch.getCount(), 0);
                Assert.assertEquals(cache.storage().size(), 4);
                Assert.assertTrue(cache.storage().get("/test").isPresent());
                Assert.assertTrue(cache.storage().get("/test/one").isPresent());
                Assert.assertTrue(cache.storage().get("/test/one/two").isPresent());
                Assert.assertTrue(cache.storage().get("/test/one/two/three").isPresent());
            }
        }
    }

    @Test
    public void testListenerBuilder() throws Exception
    {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1)))
        {
            client.start();
            try (CuratorCache cache = CuratorCache.build(client, "/test", RECURSIVE))
            {
                Semaphore all = new Semaphore(0);
                Semaphore deletes = new Semaphore(0);
                Semaphore changes = new Semaphore(0);
                Semaphore creates = new Semaphore(0);
                Semaphore createsAndChanges = new Semaphore(0);

                CuratorCacheListener listener = CuratorCacheListener.builder().forAll((__, ___, ____) -> all.release()).forDeletes(__ -> deletes.release()).forChanges((__, ___) -> changes.release()).forCreates(__ -> creates.release()).forCreatesAndChanges((__, ___) -> createsAndChanges.release()).build();
                cache.listenable().addListener(listener);
                cache.start();

                client.create().forPath("/test");
                Assert.assertTrue(timing.acquireSemaphore(all, 1));
                Assert.assertTrue(timing.acquireSemaphore(creates, 1));
                Assert.assertTrue(timing.acquireSemaphore(createsAndChanges, 1));
                Assert.assertEquals(changes.availablePermits(), 0);
                Assert.assertEquals(deletes.availablePermits(), 0);

                client.setData().forPath("/test", "new".getBytes());
                Assert.assertTrue(timing.acquireSemaphore(all, 1));
                Assert.assertTrue(timing.acquireSemaphore(changes, 1));
                Assert.assertTrue(timing.acquireSemaphore(createsAndChanges, 1));
                Assert.assertEquals(creates.availablePermits(), 0);
                Assert.assertEquals(deletes.availablePermits(), 0);

                client.delete().forPath("/test");
                Assert.assertTrue(timing.acquireSemaphore(all, 1));
                Assert.assertTrue(timing.acquireSemaphore(deletes, 1));
                Assert.assertEquals(creates.availablePermits(), 0);
                Assert.assertEquals(changes.availablePermits(), 0);
                Assert.assertEquals(createsAndChanges.availablePermits(), 0);
            }
        }
    }

    @Test
    public void testOverrideExecutor() throws Exception
    {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1)))
        {
            client.start();
            try ( CuratorCache cache = CuratorCache.build(client, "/test", RECURSIVE) )
            {
                CountDownLatch latch = new CountDownLatch(2);
                cache.setExecutor(proc -> {
                    latch.countDown();
                    proc.run();
                });
                cache.listenable().addListener((type, oldData, data) -> latch.countDown());
                cache.start();

                client.create().forPath("/test");

                Assert.assertTrue(timing.awaitLatch(latch));
            }
        }
    }

    @Test
    public void testPathFilter() throws Exception
    {
        try (CuratorFramework client = CuratorFrameworkFactory.newClient(server.getConnectString(), timing.session(), timing.connection(), new RetryOneTime(1)))
        {
            client.start();
            try ( CuratorCache cache = CuratorCache.build(client, "/a", RECURSIVE) )
            {
                CountDownLatch latch = new CountDownLatch(1);
                cache.listenable().addListener((type, oldData, data) -> latch.countDown());
                cache.start();
                cache.setPathFilter(path -> path.equals("/a/b/c/d"));

                client.create().forPath("/a");
                client.create().forPath("/a/b");
                client.create().forPath("/a/b/c");
                client.create().forPath("/a/b/c/d");

                Assert.assertTrue(timing.awaitLatch(latch));
                Assert.assertTrue(cache.storage().containsPath("/a/b/c/d"));
                Assert.assertEquals(cache.storage().size(), 1);
            }
        }
    }
}
