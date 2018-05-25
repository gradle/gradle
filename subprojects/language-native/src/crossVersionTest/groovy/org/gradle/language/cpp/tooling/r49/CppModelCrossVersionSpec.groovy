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

package org.gradle.plugins.ide.tooling.r49

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.cpp.CppComponent

@ToolingApiVersion(">=4.9 <5.0")
@TargetGradleVersion(">=4.9 <5.0")
class BobCrossVersionSpec extends ToolingApiSpecification {

    def "bob"() {
        buildFile = """"""
        projectDir.create {
            src {
                main {
                    java {}
                    resources {}
                }
                test {
                    java {}
                    resources {}
                }
            }
        }

        when:
        CppComponent project = withConnection { connection -> connection.getModel(CppComponent.class) }
//        IdeaModule module = project.children[0]
//        IdeaContentRoot root = module.contentRoots[0]

        then:
        false
//        root.sourceDirectories.empty
//        root.resourceDirectories.empty
//        root.testDirectories.empty
//        root.testResourceDirectories.empty
    }
}
