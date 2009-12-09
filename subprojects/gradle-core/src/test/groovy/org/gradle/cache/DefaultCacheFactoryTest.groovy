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
package org.gradle.cache

import org.junit.Test
import org.gradle.CacheUsage
import org.junit.Rule
import org.gradle.util.TemporaryFolder
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

class DefaultCacheFactoryTest {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder()
    private final DefaultCacheFactory factory = new DefaultCacheFactory()

    @Test
    public void createsCache() {
        PersistentCache cache = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])
        assertThat(cache, instanceOf(DefaultPersistentDirectoryCache))
        assertThat(cache.baseDir, equalTo(tmpDir.dir))
    }

    @Test
    public void cachesCacheInstances() {
        PersistentCache cache = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])
        assertThat(factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value']), sameInstance(cache))
        assertThat(factory.open(tmpDir.dir('some-other-dir'), CacheUsage.ON, [prop: 'value']), not(sameInstance(cache)))
    }

    @Test
    public void discardsCacheInstanceWhenClosed() {
        PersistentCache cache = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])
        factory.close(cache)
        assertThat(factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value']), not(sameInstance(cache)))
    }
}



