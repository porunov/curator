package org.apache.curator.x.async.modeled.details;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.apache.curator.utils.Compatibility;
import java.util.concurrent.ExecutorService;

interface CacheInterface
{
    void addListener(TreeCacheListener listener);

    void start();

    void close();

    static CacheInterface build(CuratorFramework client, String path, ExecutorService executor, boolean compress, boolean createParentsIfNeeded, boolean createParentsAsContainers)
    {
        if ( Compatibility.hasPersistentWatches()  )
        {
            return new CuratorCacheInterface(client, path, executor, compress, createParentsIfNeeded, createParentsAsContainers);
        }
        return new TreeCacheInterface(client, path, executor, compress, createParentsIfNeeded, createParentsAsContainers);
    }
}
