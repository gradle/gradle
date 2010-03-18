/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.cache;

import org.gradle.CacheUsage;
import org.gradle.cache.btree.BTreePersistentIndexedCache;
import org.gradle.util.TestFile;
import org.gradle.util.GUtil;
import org.gradle.util.TemporaryFolder;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class DefaultPersistentDirectoryCacheTest {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final Map<String, String> properties = GUtil.map("prop", "value", "prop2", "other-value");

    @Test
    public void cacheIsInvalidWhenCacheDirDoesNotExist() {
        TestFile emptyDir = tmpDir.getDir().file("dir");
        emptyDir.assertDoesNotExist();

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(emptyDir, CacheUsage.ON, properties);
        assertFalse(cache.isValid());

        emptyDir.assertIsDir();
    }

    @Test
    public void cacheIsInvalidWhenPropertiesFileDoesNotExist() {
        TestFile dir = tmpDir.getDir().file("dir").createDir();

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties);
        assertFalse(cache.isValid());

        dir.assertIsDir();
    }

    @Test
    public void rebuildsCacheWhenPropertiesHaveChanged() {
        TestFile dir = createCacheDir("prop", "other-value");

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties);
        assertFalse(cache.isValid());

        dir.assertHasDescendants();
    }

    @Test
    public void rebuildsCacheWhenCacheRebuildRequested() {
        TestFile dir = createCacheDir();

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.REBUILD, properties);
        assertFalse(cache.isValid());

        dir.assertHasDescendants();
    }

    @Test
    public void usesExistingCacheDirWhenItIsNotInvalid() {
        TestFile dir = createCacheDir();

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties);
        assertTrue(cache.isValid());

        dir.file("cache.properties").assertIsFile();
        dir.file("some-file").assertIsFile();
    }

    @Test
    public void updateCreatesPropertiesFileWhenItDoesNotExist() {
        TestFile dir = tmpDir.getDir().file("dir");

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties);
        cache.markValid();

        assertTrue(cache.isValid());
        assertThat(loadProperties(dir.file("cache.properties")), equalTo(properties));
    }

    @Test
    public void updatesPropertiesWhenMarkedValid() {
        TestFile dir = createCacheDir("prop", "some-other-value");

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties);
        cache.markValid();

        assertTrue(cache.isValid());
        assertThat(loadProperties(dir.file("cache.properties")), equalTo(properties));
    }

    @Test
    public void createsAnIndexedCache() {
        TestFile dir = tmpDir.getDir().file("dir");
        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties);
        assertThat(cache.openIndexedCache(), instanceOf(BTreePersistentIndexedCache.class));
    }

    @Test
    public void reusesTheIndexedCache() {
        TestFile dir = tmpDir.getDir().file("dir");
        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties);
        assertThat(cache.openIndexedCache(), sameInstance(cache.openIndexedCache()));
    }

    @Test
    public void closesIndexedCacheOnClose() {
        TestFile dir = tmpDir.getDir().file("dir");
        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties);
        
        BTreePersistentIndexedCache indexedCache = cache.openIndexedCache();
        assertTrue(indexedCache.isOpen());

        cache.close();
        assertFalse(indexedCache.isOpen());
    }
    
    @Test
    public void createsAnStateCache() {
        TestFile dir = tmpDir.getDir().file("dir");
        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties);
        assertThat(cache.openStateCache(), instanceOf(SimpleStateCache.class));
    }

    @Test
    public void reusesTheStateCache() {
        TestFile dir = tmpDir.getDir().file("dir");
        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties);
        assertThat(cache.openStateCache(), sameInstance(cache.openStateCache()));
    }

    private Map<String, String> loadProperties(TestFile file) {
        Properties properties = GUtil.loadProperties(file);
        Map<String, String> result = new HashMap<String, String>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue().toString());
        }
        return result;
    }

    private TestFile createCacheDir(String... extraProps) {
        TestFile dir = tmpDir.getDir();
        Properties properties = new Properties();
        properties.putAll(this.properties);
        properties.putAll(GUtil.map((Object[])extraProps));
        GUtil.saveProperties(properties, dir.file("cache.properties"));
        dir.file("some-file").touch();

        return dir;
    }
}
