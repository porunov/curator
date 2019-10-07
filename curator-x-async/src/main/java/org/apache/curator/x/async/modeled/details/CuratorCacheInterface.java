package org.apache.curator.x.async.modeled.details;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.framework.recipes.cache.CuratorCacheStorage;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import java.util.concurrent.ExecutorService;

import static org.apache.curator.framework.recipes.cache.CuratorCache.Options.COMPRESSED_DATA;
import static org.apache.curator.framework.recipes.cache.CuratorCache.Options.RECURSIVE;

class CuratorCacheInterface implements CacheInterface
{
    private final CuratorCache cache;
    private final CuratorFramework client;
    private final String path;
    private final boolean createParentsIfNeeded;
    private final boolean createParentsAsContainers;

    CuratorCacheInterface(CuratorFramework client, String path, ExecutorService executor, boolean compress, boolean createParentsIfNeeded, boolean createParentsAsContainers)
    {
        this.client = client;
        this.path = path;
        this.createParentsIfNeeded = createParentsIfNeeded;
        this.createParentsAsContainers = createParentsAsContainers;
        CuratorCacheStorage storage = CuratorCacheStorage.bytesNotCached();
        cache = compress ? CuratorCache.build(client, storage, path, RECURSIVE, COMPRESSED_DATA) : CuratorCache.build(client, storage, path, RECURSIVE);
        if ( executor != null )
        {
            cache.setExecutor(executor);
        }
    }

    @Override
    public void addListener(TreeCacheListener listener)
    {
        cache.listenable().addListener(CuratorCacheListener.builder().forTreeCache(client, listener).build());
    }

    @Override
    public void start()
    {
        TreeCacheInterface.checkCreateParents(client, path, createParentsIfNeeded, createParentsAsContainers);
        cache.start();
    }

    @Override
    public void close()
    {
        cache.close();
    }
}
