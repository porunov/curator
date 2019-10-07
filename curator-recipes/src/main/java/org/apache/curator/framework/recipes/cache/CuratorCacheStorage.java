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

import java.io.Closeable;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Interface for maintaining data in a {@link org.apache.curator.framework.recipes.cache.CuratorCache}
 */
public interface CuratorCacheStorage extends Closeable
{
    /**
     * Return a new standard storage instance
     *
     * @return storage instance
     */
    static CuratorCacheStorage standard() {
        return new StandardCuratorCacheStorage(true);
    }

    /**
     * Return a new storage instance that does not retain the data bytes. i.e. ChildData objects
     * returned by this storage will always return {@code null} for {@link ChildData#getData()}.
     *
     * @return storage instance that does not retain data bytes
     */
    static CuratorCacheStorage bytesNotCached() {
        return new StandardCuratorCacheStorage(false);
    }

    /**
     * Returns a new copy of this storage that does not clear its internal data when it is closed.
     * Useful for retaining the cache after it is closed.
     *
     * @return new copy that does not clear on close
     */
    CuratorCacheStorage doNotClearOnClose();

    /**
     * Add an entry to storage and return any previous entry at that path
     *
     * @param data entry to add
     * @return previous entry or {@code empty()}
     */
    Optional<ChildData> put(ChildData data);

    /**
     * Remove the entry from storage and return any previous entry at that path
     *
     * @param path path to remove
     * @return previous entry or {@code empty()}
     */
    Optional<ChildData> remove(String path);

    /**
     * Return an entry from storage
     *
     * @param path path to get
     * @return entry or {@code empty()}
     */
    Optional<ChildData> get(String path);

    /**
     * Compute the value at the given path. Same semantics as {@link Map#compute(Object, java.util.function.BiFunction)}
     *
     * @param path path to compute at
     * @param remappingFunction remapping function
     * @return the new value associated with the specified path, or empty if none
     */
    Optional<ChildData> compute(String path, BiFunction<String, ? super ChildData, ? extends ChildData> remappingFunction);

    /**
     * Return true if the storage currently has an entry for the given path
     *
     * @param path path to check
     * @return true/false
     */
    boolean containsPath(String path);

    /**
     * Return the current number of entries in storage
     *
     * @return number of entries
     */
    int size();

    /**
     * Return a stream over the storage entries. Note: for a standard storage instance, the stream
     * behaves like a stream returned by {@link java.util.concurrent.ConcurrentHashMap#entrySet()}
     *
     * @return stream over entries
     */
    Stream<ChildData> stream();

    /**
     * Return a stream over the storage entries that are the immediate children of the given node.
     *
     * @return stream over entries
     */
    Stream<ChildData> streamImmediateChildren(String fromParent);

    /**
     * Utility - given a stream of child nodes, build a map. Note: it is assumed that each child
     * data in the stream has a unique path
     *
     * @param stream stream of child nodes with unique paths
     * @return map
     */
    static Map<String, ChildData> toMap(Stream<ChildData> stream)
    {
        return stream.map(data -> new AbstractMap.SimpleEntry<>(data.getPath(), data))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Reset the storage to zero entries
     */
    void clear();

    /**
     * Close the storage. For a standard storage instance, the storage is cleared.
     */
    @Override
    default void close()
    {
        clear();
    }
}
