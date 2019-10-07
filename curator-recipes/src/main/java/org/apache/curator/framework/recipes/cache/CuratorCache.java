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
import org.apache.curator.framework.listen.Listenable;
import java.io.Closeable;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * <p>
 *     A utility that attempts to keep the data from a node locally cached. Optionally the entire
 *     tree of children below the node can also be cached. Will respond to update/create/delete events, pull
 *     down the data, etc. You can register listeners that will get notified when changes occur.
 * </p>
 *
 * <p>
 *     <b>IMPORTANT</b> - it's not possible to stay transactionally in sync. Users of this class must
 *     be prepared for false-positives and false-negatives. Additionally, always use the version number
 *     when updating data to avoid overwriting another process' change.
 * </p>
 */
public interface CuratorCache extends Closeable
{
    /**
     * cache build options
     */
    enum Options
    {
        /**
         * Cache the entire tree of nodes starting at the given node
         */
        RECURSIVE,

        /**
         * Decompress data via {@link org.apache.curator.framework.api.GetDataBuilder#decompressed()}
         */
        COMPRESSED_DATA
    }

    /**
     * Return a Curator cache for the given path with the given options using a standard storage instance
     *
     * @param client Curator client
     * @param path path to cache
     * @param options any options
     * @return cache (note it must be started via {@link #start()}
     */
    static CuratorCache build(CuratorFramework client, String path, Options... options)
    {
        return build(client, CuratorCacheStorage.standard(), path, options);
    }

    /**
     * Return a Curator cache for the given path with the given options and the given storage instance
     *
     * @param client Curator client
     * @param storage storage to use
     * @param path path to cache
     * @param options any options
     * @return cache (note it must be started via {@link #start()}
     */
    static CuratorCache build(CuratorFramework client, CuratorCacheStorage storage, String path, Options... options)
    {
        return new CuratorCacheImpl(client, storage, path, options);
    }

    /**
     * Start the cache. This will cause a complete refresh from the cache's root node and generate
     * events for all nodes found, etc.
     */
    void start();

    /**
     * Close the cache, stop responding to events, etc. Note: also calls {@link CuratorCacheStorage#close()}
     */
    @Override
    void close();

    /**
     * Utility to force a rebuild of the cache. Normally, this should not ever be needed
     */
    void forceRebuild();

    /**
     * Return the storage instance being used
     *
     * @return storage
     */
    CuratorCacheStorage storage();

    /**
     * Return the root node being cached (i.e. the node passed to the builder)
     *
     * @return root node path
     */
    String getRootPath();

    /**
     * Convenience to return the root node data
     *
     * @return data (if it's in the cache)
     */
    default Optional<ChildData> getRootData()
    {
        return storage().get(getRootPath());
    }

    /**
     * Return the listener container so that listeners can be registered to be notified of changes to the cache
     *
     * @return listener container
     */
    Listenable<CuratorCacheListener> listenable();

    /**
     * By default any unexpected exception is handled by logging the exception. You can change
     * so that a handler is called instead. Under normal circumstances, this shouldn't be necessary.
     *
     * @param newHandler new exception handler or {@code null} to reset to the default logging
     */
    void setExceptionHandler(Consumer<Exception> newHandler);

    /**
     * Normally, listeners are wrapped in {@link org.apache.curator.framework.CuratorFramework#runSafe(Runnable)}. Use this
     * method to set a different executor.
     *
     * @param executor to use
     */
    void setExecutor(Executor executor);

    /**
     * Normally, for RECURSIVE caches, all nodes that are part of the tree are cached. You can
     * change this by setting a filter that determines which nodes are cached. If the filter returns
     * true for a path, it is cached. Otherwise it is not cached.
     *
     * @param pathFilter new path filter or {@code null} to reset to default (all nodes cached)
     */
    void setPathFilter(Predicate<String> pathFilter);
}
