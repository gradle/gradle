/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.id

import org.gradle.util.internal.MultithreadedTestRule
import org.junit.Rule
import org.junit.Test

import java.util.concurrent.CopyOnWriteArraySet

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertTrue

class LongIdGeneratorTest {
    private final LongIdGenerator generator = new LongIdGenerator()

    @Rule
    public MultithreadedTestRule parallel = new MultithreadedTestRule();

    @Test
    void generatesMonotonicallyIncreasingLongIdsStartingAtOne() {
        assertThat(generator.generateId(), equalTo(1L))
        assertThat(generator.generateId(), equalTo(2L))
        assertThat(generator.generateId(), equalTo(3L))
    }

    @Test
    void generatesUniqueIdsWhenInvokedConcurrently() {
        Set<Long> ids = new CopyOnWriteArraySet<Long>()

        5.times {
            parallel.start {
                100.times {
                    assertTrue(ids.add(generator.generateId()))
                }
            }
        }
        parallel.waitForAll()
    }
}
