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
package org.gradle.cache.internal;

import org.gradle.CacheUsage;
import org.gradle.api.Action;
import org.gradle.cache.CacheOpenException;
import org.gradle.cache.PersistentCache;
import org.gradle.util.GUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.TestFile;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.gradle.cache.internal.FileLockManager.LockMode;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(JMock.class)
public class DefaultPersistentDirectoryCacheTest {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final FileLockManager lockManager = new DefaultFileLockManager();
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final Action<PersistentCache> action = context.mock(Action.class);
    private final Map<String, String> properties = GUtil.map("prop", "value", "prop2", "other-value");

    @Test
    public void initialisesCacheWhenCacheDirDoesNotExist() {
        TestFile emptyDir = tmpDir.getDir().file("dir");
        emptyDir.assertDoesNotExist();

        context.checking(new Expectations() {{
            one(action).execute(with(notNullValue(PersistentCache.class)));
        }});

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(emptyDir, CacheUsage.ON, properties, LockMode.Shared, action, lockManager);
        assertThat(loadProperties(emptyDir.file("cache.properties")), equalTo(properties));
    }

    @Test
    public void initializesCacheWhenPropertiesFileDoesNotExist() {
        TestFile dir = tmpDir.getDir().file("dir").createDir();

        context.checking(new Expectations() {{
            one(action).execute(with(notNullValue(PersistentCache.class)));
        }});

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties, LockMode.Shared, action, lockManager);
        assertThat(loadProperties(dir.file("cache.properties")), equalTo(properties));
    }

    @Test
    public void rebuildsCacheWhenPropertiesHaveChanged() {
        TestFile dir = createCacheDir("prop", "other-value");

        context.checking(new Expectations() {{
            one(action).execute(with(notNullValue(PersistentCache.class)));
        }});

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties, LockMode.Shared, action, lockManager);
        assertThat(loadProperties(dir.file("cache.properties")), equalTo(properties));
    }

    @Test
    public void rebuildsCacheWhenCacheRebuildRequested() {
        TestFile dir = createCacheDir();

        context.checking(new Expectations() {{
            one(action).execute(with(notNullValue(PersistentCache.class)));
        }});

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.REBUILD, properties, LockMode.Shared, action, lockManager);
        assertThat(loadProperties(dir.file("cache.properties")), equalTo(properties));
    }

    @Test
    public void rebuildsCacheWhenInitialiserFailedOnPreviousOpen() {
        TestFile dir = tmpDir.getDir().file("dir").createDir();
        final RuntimeException failure = new RuntimeException();

        context.checking(new Expectations() {{
            one(action).execute(with(notNullValue(PersistentCache.class)));
            will(throwException(failure));
        }});

        try {
            new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties, LockMode.Shared, action, lockManager);
            fail();
        } catch (CacheOpenException e) {
            assertThat(e.getCause(), sameInstance((Throwable) failure));
        }

        context.checking(new Expectations() {{
            one(action).execute(with(notNullValue(PersistentCache.class)));
        }});

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties, LockMode.Shared, action, lockManager);
        assertThat(loadProperties(dir.file("cache.properties")), equalTo(properties));
    }
    
    @Test
    public void doesNotInitializeCacheWhenCacheDirExistsAndIsNotInvalid() {
        TestFile dir = createCacheDir();

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties, LockMode.Shared, action, lockManager);
        dir.file("cache.properties").assertIsFile();
        dir.file("some-file").assertIsFile();
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

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.putAll(this.properties);
        properties.putAll(GUtil.map((Object[]) extraProps));

        DefaultPersistentDirectoryCache cache = new DefaultPersistentDirectoryCache(dir, CacheUsage.ON, properties, LockMode.Shared, null, lockManager);
        dir.file("some-file").touch();
        cache.close();

        return dir;
    }
}
