/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.changedetection.state;

import org.gradle.cache.PersistentIndexedCache;
import org.gradle.messaging.serialize.Serializer;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.gradle.util.Matchers.reflectionEquals;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class CachingHasherTest {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final Hasher delegate = context.mock(Hasher.class);
    private final PersistentIndexedCache<File, CachingHasher.FileInfo> cache = context.mock(
            PersistentIndexedCache.class);
    private final TaskArtifactStateCacheAccess cacheAccess = context.mock(TaskArtifactStateCacheAccess.class);
    private final byte[] hash = "hash".getBytes();
    private final File file = tmpDir.createFile("testfile").write("content");
    private CachingHasher hasher;

    @Before
    public void setup() {
        context.checking(new Expectations(){{
            one(cacheAccess).createCache(with(equalTo("fileHashes")), with(equalTo(File.class)), with(notNullValue(Class.class)), with(notNullValue(Serializer.class)));
            will(returnValue(cache));
        }});
        hasher = new CachingHasher(delegate, cacheAccess);
    }

    @Test
    public void hashesFileWhenHashNotCached() {
        context.checking(new Expectations() {{
            one(cache).get(file);
            will(returnValue(null));
            one(delegate).hash(file);
            will(returnValue(hash));
            one(cache).put(with(equalTo(file)), with(reflectionEquals(new CachingHasher.FileInfo(hash, file.length(),
                    file.lastModified()))));
        }});

        assertThat(hasher.hash(file), sameInstance(hash));
    }

    @Test
    public void hashesFileWhenLengthHasChanged() {
        context.checking(new Expectations() {{
            one(cache).get(file);
            will(returnValue(new CachingHasher.FileInfo(hash, 1078, file.lastModified())));
            one(delegate).hash(file);
            will(returnValue(hash));
            one(cache).put(with(equalTo(file)), with(reflectionEquals(new CachingHasher.FileInfo(hash, file.length(),
                    file.lastModified()))));
        }});

        assertThat(hasher.hash(file), sameInstance(hash));
    }

    @Test
    public void hashesFileWhenTimestampHasChanged() {
        context.checking(new Expectations() {{
            one(cache).get(file);
            will(returnValue(new CachingHasher.FileInfo(hash, file.length(), 12)));
            one(delegate).hash(file);
            will(returnValue(hash));
            one(cache).put(with(equalTo(file)), with(reflectionEquals(new CachingHasher.FileInfo(hash, file.length(),
                    file.lastModified()))));
        }});

        assertThat(hasher.hash(file), sameInstance(hash));
    }

    @Test
    public void doesNotHashFileWhenTimestampAndLengthHaveNotChanged() {
        context.checking(new Expectations() {{
            one(cache).get(file);
            will(returnValue(new CachingHasher.FileInfo(hash, file.length(), file.lastModified())));
        }});

        assertThat(hasher.hash(file), sameInstance(hash));
    }
}
