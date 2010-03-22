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


import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.integration.junit4.JMock
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.util.TemporaryFolder
import org.junit.Rule

@RunWith(JMock.class)
class SimpleStateCacheTest {
    @Rule public TemporaryFolder tmpDir = new TemporaryFolder()
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery()
    private final PersistentCache backingCache = context.mock(PersistentCache.class)

    @Before
    public void setup() {
        context.checking {
            allowing(backingCache).getBaseDir()
            will(returnValue(tmpDir.dir))
        }
    }

    @Test
    public void getReturnsNullWhenFileDoesNotExist() {
        SimpleStateCache<String> cache = new SimpleStateCache<String>(backingCache, new DefaultSerializer<String>())
        assertThat(cache.get(), nullValue())
    }
    
    @Test
    public void getReturnsLastWrittenValue() {
        SimpleStateCache<String> cache = new SimpleStateCache<String>(backingCache, new DefaultSerializer<String>())

        context.checking {
            one(backingCache).markValid()
        }

        cache.set('some value')
        tmpDir.file('state.bin').assertIsFile()
        assertThat(cache.get(), equalTo('some value'))
    }
}
