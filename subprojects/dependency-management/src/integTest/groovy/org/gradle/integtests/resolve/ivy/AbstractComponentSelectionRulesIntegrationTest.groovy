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

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
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

    void checkDependencies(Closure<?> onSuccess) {
        checkDependencies(':checkDeps', onSuccess)
    }

    void checkDependencies(String task = ':checkDeps', Closure<?> onSuccess = {}) {
        succeeds task
        onSuccess()
    }

    String triedMetadata(String group, String module, String version, boolean fallbackToArtifact = false, boolean stopFirst = false) {
        Set uris = []
        def repo = GradleMetadataResolveRunner.useIvy() ? ivyHttpRepo : mavenHttpRepo
        def desc = GradleMetadataResolveRunner.useIvy() ? 'ivy' : 'pom'
        def resolve = repo.module(group, module, version)
        if (GradleMetadataResolveRunner.experimentalResolveBehaviorEnabled) {
            uris << resolve.moduleMetadata.uri
        }
        uris << resolve."$desc".uri
        if (fallbackToArtifact) {
            uris << resolve.artifact.uri
        }
        if (stopFirst) {
            uris = [uris[0]]
        }
        uris.collect { "  - $it" }.join('\n')
    }

    static Map<String, String> rules = [
        "reject all": """{ ComponentSelection selection ->
                selection.reject("rejecting everything")
                candidates << selection.candidate.version
            }
            """,
        "reject all with metadata": """{ ComponentSelection selection ->
                if (selection.metadata != null) {
                    selection.reject("rejecting everything")
                    candidates << selection.candidate.version
                }
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
        "select branch": """{ ComponentSelection selection ->
                if (selection.getDescriptor(IvyModuleDescriptor)?.branch != 'test') {
                    selection.reject("not branch")
                }
                candidates << selection.candidate.version
            }
            """,
        "select status": """{ ComponentSelection selection ->
                if (selection.metadata?.status != 'milestone') {
                    selection.reject("not milestone")
                }
                candidates << selection.candidate.version
            }
            """
    ]
}
