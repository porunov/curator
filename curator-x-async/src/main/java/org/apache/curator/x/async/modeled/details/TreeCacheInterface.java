package org.apache.curator.x.async.modeled.details;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.apache.curator.utils.ThreadUtils;
import java.util.concurrent.ExecutorService;

@SuppressWarnings("deprecation")
class TreeCacheInterface implements CacheInterface
{
    private final TreeCache cache;
    private final CuratorFramework client;
    private final String path;
    private final boolean createParentsIfNeeded;
    private final boolean createParentsAsContainers;

    static void checkCreateParents(CuratorFramework client, String path, boolean createParentsIfNeeded, boolean createParentsAsContainers)
    {
        if ( createParentsIfNeeded )
        {
            try
            {
                if ( createParentsAsContainers )
                {
                    client.create().creatingParentContainersIfNeeded().inBackground().forPath(path);
                }
                else
                {
                    client.create().creatingParentsIfNeeded().inBackground().forPath(path);
                }
            }
            catch ( Exception e )
            {
                throw new RuntimeException("Could not create parents", e);
            }
        }
    }

    TreeCacheInterface(CuratorFramework client, String path, ExecutorService executor, boolean compress, boolean createParentsIfNeeded, boolean createParentsAsContainers)
    {
        this.client = client;
        this.path = path;
        this.createParentsIfNeeded = createParentsIfNeeded;
        this.createParentsAsContainers = createParentsAsContainers;
        if ( executor == null )
        {
            executor = ThreadUtils.newSingleThreadExecutor("CachedModeledFramework");
        }
        cache = TreeCache.newBuilder(client, path)
            .setCacheData(false)
            .setDataIsCompressed(compress)
            .setExecutor(executor)
            .setCreateParentNodes(false)
            .build();
    }

    @Override
    public void addListener(TreeCacheListener listener)
    {
        cache.getListenable().addListener(listener);
    }

    @Override
    public void start()
    {
        checkCreateParents(client, path, createParentsIfNeeded, createParentsAsContainers);
        try
        {
            cache.start();
        }
        catch ( Exception e )
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close()
    {
        cache.close();
    }
}
