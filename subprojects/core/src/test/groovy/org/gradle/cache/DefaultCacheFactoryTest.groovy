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


package org.gradle.cache

import org.gradle.CacheUsage
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

class DefaultCacheFactoryTest extends Specification {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder()
    private final DefaultCacheFactory factory = new DefaultCacheFactory()

    public void "creates cache instance"() {
        when:
        def ref = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])

        then:
        ref.cache instanceof DefaultPersistentDirectoryCache
        ref.cache.baseDir == tmpDir.dir
    }

    public void "caches instances"() {
        when:
        def ref1 = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])
        def ref2 = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])

        then:
        ref1.is(ref2)
    }

    public void "releases instance when last reference released"() {
        given:
        factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])
        def oldRef = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])
        oldRef.release()
        oldRef.release()

        when:
        def ref = factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])

        then:
        !ref.is(oldRef)
    }

    public void "fails when cache is already open with different properties"() {
        given:
        factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])

        when:
        factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'other'])

        then:
        IllegalStateException e = thrown()
        e.message == "Cache '${tmpDir.dir}' is already open with different state."
    }

    public void "fails when cache is already open when rebuild is requested"() {
        given:
        factory.open(tmpDir.dir, CacheUsage.ON, [prop: 'value'])

        when:
        factory.open(tmpDir.dir, CacheUsage.REBUILD, [prop: 'value'])

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot rebuild cache '${tmpDir.dir}' as it is already open."
    }
}



