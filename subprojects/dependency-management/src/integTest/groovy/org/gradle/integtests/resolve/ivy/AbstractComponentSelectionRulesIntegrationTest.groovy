/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

abstract class AbstractComponentSelectionRulesIntegrationTest extends AbstractModuleDependencyResolveTest {
    def setup() {
        buildFile << "List<String> candidates = []\n"

        repository {
            group('org.utils') {
                module('api') {
                    '1.0'()
                    '1.1' {
                        withModule(org.gradle.test.fixtures.ivy.IvyModule) {
                            withBranch('test')
                            withStatus('milestone')
                        }
                    }
                    '1.2'()
                    '2.0' {
                        withModule(org.gradle.test.fixtures.ivy.IvyModule) {
                            withBranch('test')
                            withStatus('milestone')
                        }
                    }
                    '2.1'()
                }

                module('lib') {
                    '1.0'()
                    '1.1' {
                        withModule(org.gradle.test.fixtures.ivy.IvyModule) {
                            withBranch('test')
                            withStatus('milestone')
                        }
                    }
                }
            }
        }
    }

    void checkDependencies(boolean expectFailure, Closure<?> onSuccess = {}) {
        checkDependencies(':checkDeps', expectFailure, onSuccess)
    }

    void checkDependencies(String task = ':checkDeps', boolean expectFailure = false, Closure<?> onSuccess = {}) {
        try {
            succeeds task
            onSuccess()
        } catch (Throwable e) {
            // Happily ignore failures if they are allowed, which is not the same as expecting the build to fail
            if (!expectFailure) {
                throw e
            }
        }
    }

    static Map<String, String> rules = [
        "reject all": """{ ComponentSelection selection ->
                selection.reject("rejecting everything")
                candidates << selection.candidate.version
            }
            """,
        "reject all with metadata": """{ ComponentSelection selection, ComponentMetadata metadata ->
                selection.reject("rejecting everything")
                candidates << selection.candidate.version
            }
            """,
        "select 1.1": """{ ComponentSelection selection ->
                if (selection.candidate.version != '1.1') {
                    selection.reject("not 1.1")
                }
                candidates << selection.candidate.version
            }
            """,
        "select 2.0": """{ ComponentSelection selection ->
                if (selection.candidate.version != '2.0') {
                    selection.reject("not 2.0")
                }
                candidates << selection.candidate.version
            }
            """,
        "select 2.1": """{ ComponentSelection selection ->
                if (selection.candidate.version != '2.1') {
                    selection.reject("not 2.1")
                }
                candidates << selection.candidate.version
            }
            """,
        "select branch": """{ ComponentSelection selection, IvyModuleDescriptor ivy ->
                if (ivy.branch != 'test') {
                    selection.reject("not branch")
                }
                candidates << selection.candidate.version
            }
            """,
        "select status": """{ ComponentSelection selection, ComponentMetadata metadata ->
                if (metadata.status != 'milestone') {
                    selection.reject("not milestone")
                }
                candidates << selection.candidate.version
            }
            """
    ]
}
