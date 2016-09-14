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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.server.http.IvyHttpModule

abstract class AbstractComponentSelectionRulesIntegrationTest extends AbstractHttpDependencyResolutionTest {
    Map<String, IvyHttpModule> modules = [:]

    def setup() {
        modules['1.0'] = ivyHttpRepo.module("org.utils", "api", "1.0").publish()
        modules['1.1'] = ivyHttpRepo.module("org.utils", "api", "1.1").withBranch("test").withStatus("milestone").publish()
        modules['1.2'] = ivyHttpRepo.module("org.utils", "api", "1.2").publish()
        modules['2.0'] = ivyHttpRepo.module("org.utils", "api", "2.0").withBranch("test").withStatus("milestone").publish()
        modules['2.1'] = ivyHttpRepo.module("org.utils", "api", "2.1").publish()
        modules['1.0-lib'] = ivyHttpRepo.module("org.utils", "lib", "1.0").publish()
        modules['1.1-lib'] = ivyHttpRepo.module("org.utils", "lib", "1.1").withBranch("test").withStatus("milestone").publish()
    }

    String getBaseBuildFile() {
        """
        def candidates = []
        configurations { conf }
        repositories {
            ivy { url "${ivyRepo.uri}" }
        }
        task resolveConf { doLast { configurations.conf.files } }
        """
    }

    String getHttpBaseBuildFile() {
        """
        def candidates = []
        configurations { conf }
        repositories {
            ivy { url "${ivyHttpRepo.uri}" }
        }
        task resolveConf { doLast { configurations.conf.files } }
        """
    }

    static def rules = [
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
