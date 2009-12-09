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

import org.gradle.CacheUsage
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

@RunWith(JMock.class)
public class AutoCloseCacheFactoryTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final CacheFactory backingFactory = context.mock(CacheFactory.class)
    private final AutoCloseCacheFactory factory = new AutoCloseCacheFactory(backingFactory)

    @Test
    public void closesEachCacheOnClose() {
        PersistentCache cache1 = context.mock(PersistentCache, '1')
        PersistentCache cache2 = context.mock(PersistentCache, '2')
        context.checking {
            one(backingFactory).open(new File('dir1'), CacheUsage.ON, [:])
            will(returnValue(cache1))
            one(backingFactory).open(new File('dir2'), CacheUsage.ON, [:])
            will(returnValue(cache2))
        }
        assertThat(factory.open(new File('dir1'), CacheUsage.ON, [:]), sameInstance(cache1))
        assertThat(factory.open(new File('dir2'), CacheUsage.ON, [:]), sameInstance(cache2))

        context.checking {
            one(backingFactory).close(cache1)
            one(backingFactory).close(cache2)
        }

        factory.close()
    }
}

