/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous

import org.gradle.process.internal.streams.SafeStreams

import java.util.concurrent.CountDownLatch

class KeyboardCancelContinuousIntegrationTest extends AbstractContinuousIntegrationTest {
    static final byte KEY_CODE_CTRL_D = 4

    def setup() {
        buildFile << """
    apply plugin: 'java'
"""
    }

    def "should not cancel build when System.in immediately returns EOF"() {
        given:
        executer.withStdIn(SafeStreams.emptyInput())
        when:
        succeeds("build")
        then:
        expectOutput {
            !it.contains("Build cancelled")
        }
    }

    def "should cancel build when System.in contains CTRL+D"() {
        given:
        executer.withStdIn(new ByteArrayInputStream([KEY_CODE_CTRL_D] as byte[]))
        when:
        succeeds("build")
        then:
        expectOutput {
            it.contains("Build cancelled")
        }
    }

    def "should cancel build when System.in contains some other characters, then CTRL+D and then blocks after that"() {
        given:
        def latch = new CountDownLatch(1)
        def inputReadFlag = false
        byte[] inputArray = "abc\4".getBytes()
        InputStream inputStream = Mock() {
            read(_, _, _) >> { byte[] buf, int off, int len ->
                if (!inputReadFlag) {
                    inputReadFlag = true
                    System.arraycopy(inputArray, 0, buf, off, 4)
                    assert 4 <= len
                    return 4
                } else {
                    // just block
                    latch.await()
                }
            }
        }
        executer.withStdIn(inputStream);
        when:
        succeeds("build")
        then:
        expectOutput {
            it.contains("Build cancelled")
        }
        cleanup:
        latch.countDown()
    }

    def "should cancel build when System.in contains contains characters and then returns EOF"() {
        given:
        executer.withStdIn(new ByteArrayInputStream('ABC'.getBytes()))
        when:
        succeeds("build")
        then:
        expectOutput {
            it.contains("Build cancelled")
        }
    }
}
