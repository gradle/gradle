/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform

import com.google.common.collect.ImmutableList
import org.gradle.internal.Try
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

import java.util.concurrent.atomic.AtomicInteger

class AbstractCachingTransformationWorkspaceProviderTest extends ConcurrentSpec {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def executionHistoryStore = Mock(ExecutionHistoryStore)
    private workspaceProvider = new AbstractCachingTransformationWorkspaceProvider(new TestTransformationWorkspaceProvider(tmpDir.file("transforms"), executionHistoryStore)) {}

    def "locks on transformer identity"() {
        def numberOfCalls = new AtomicInteger()

        when:
        async {
            100.times {
                start {
                    this.workspaceProvider.withWorkspace(new TestWorkspaceIdentity("id")) { id, workspace ->
                        if (numberOfCalls.getAndIncrement() != 0) {
                            throw new IllegalStateException("Use workspace called concurrently")
                        }
                        return Try.successful(ImmutableList.of())
                    }
                }
            }
        }

        then:
        noExceptionThrown()
    }

    def "runs actions for different identities in parallel"() {
        when:
        async {
            start {
                workspaceProvider.withWorkspace(new TestWorkspaceIdentity("first")) { id, workspace ->
                    instant.first
                    thread.blockUntil.go
                    return Try.successful(ImmutableList.of())
                }
            }
            start {
                workspaceProvider.withWorkspace(new TestWorkspaceIdentity("second")) { id, workspace ->
                    instant.second
                    thread.blockUntil.go
                    return Try.successful(ImmutableList.of())
                }
            }
            start {
                thread.blockUntil.first
                thread.blockUntil.second
                instant.go
            }
        }

        then:
        noExceptionThrown()
    }

    def "has cached result works as expected"() {
        expect:
        !workspaceProvider.getCachedResult(new TestWorkspaceIdentity("first"))

        when:
        workspaceProvider.withWorkspace(new TestWorkspaceIdentity("first")) { id, workspace ->
            return Try.successful(ImmutableList.of())
        }
        then:
        workspaceProvider.getCachedResult(new TestWorkspaceIdentity("first"))
        !workspaceProvider.getCachedResult(new TestWorkspaceIdentity("second"))
    }

    private static class TestWorkspaceIdentity implements TransformationWorkspaceIdentity {
        private final String name

        TestWorkspaceIdentity(String name) {
            this.name = name
        }

        @Override
        String getIdentity() {
            return name
        }

        boolean equals(o) {
            if (this.is(o)) {
                return true
            }
            if (getClass() != o.class) {
                return false
            }

            TestWorkspaceIdentity that = (TestWorkspaceIdentity) o

            if (name != that.name) {
                return false
            }

            return true
        }

        int hashCode() {
            return name.hashCode()
        }
    }
}
