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
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

import java.util.concurrent.atomic.AtomicInteger

class DefaultTransformerExecutionHistoryRepositoryTest extends ConcurrentSpec {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def executionHistoryStore = Mock(ExecutionHistoryStore)
    private historyRepository = new DefaultTransformerExecutionHistoryRepository(new WorkspaceProvider(), executionHistoryStore) {}

    def "locks on transformer identity"() {
        def numberOfCalls = new AtomicInteger()

        when:
        async {
            100.times {
                start {
                    this.historyRepository.withWorkspace(new TestIdentity("id")) { id, workspace ->
                        if (numberOfCalls.getAndIncrement() != 0) {
                            throw new IllegalStateException("Use workspace called concurrently")
                        }
                        return ImmutableList.of()
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
                historyRepository.withWorkspace(new TestIdentity("first")) { id, workspace ->
                    instant.first
                    thread.blockUntil.go
                    return ImmutableList.of()
                }
            }
            start {
                historyRepository.withWorkspace(new TestIdentity("second")) { id, workspace ->
                    instant.second
                    thread.blockUntil.go
                    return ImmutableList.of()
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
        !historyRepository.hasCachedResult(new TestIdentity("first"))

        when:
        historyRepository.withWorkspace(new TestIdentity("first")) { id, workspace ->
            return ImmutableList.of()
        }
        then:
        historyRepository.hasCachedResult(new TestIdentity("first"))
        !historyRepository.hasCachedResult(new TestIdentity("second"))
    }

    private class WorkspaceProvider implements TransformerWorkspaceProvider {

        @Override
        ImmutableList<File> withWorkspace(TransformationIdentity identity, TransformationWorkspaceAction workspaceAction) {
            return workspaceAction.useWorkspace(identity.identity, new DefaultTransformationWorkspace(tmpDir.file(identity)))
        }
    }

    private static class TestIdentity implements TransformationIdentity {
        private final String name

        TestIdentity(String name) {
            this.name = name
        }

        @Override
        String getInitialSubjectFileName() {
            return name
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

            TestIdentity that = (TestIdentity) o

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
