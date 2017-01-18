/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r34

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.idea.IdeaProject

class ToolingApiIdeaModelCrossVersionSpec extends ToolingApiSpecification {
    def setup(){
        settingsFile << "rootProject.name = 'root'"
    }

    @ToolingApiVersion(">=3.4")
    @TargetGradleVersion(">=3.4")
    def "jdkName property from idea module model is available in the tooling API"() {
        given:
        settingsFile << "\ninclude 'root', 'child1', 'child2', 'child3'"
        buildFile << """
            allprojects {
                apply plugin: 'idea'
                apply plugin: 'java'
            }

            idea {
                module {
                    jdkName = 'MyJDK1'
                }
            }

            project(':child1') {
                idea {
                    module {
                        jdkName = 'MyJDK2'
                    }
                }
            }

            project(':child2') {
                idea {
                    module {
                        jdkName = 'MyJDK3'
                    }
                }
            }

        """

        when:
        def ideaProject = withConnection { connection -> connection.getModel(IdeaProject) }

        then:
        ideaProject.modules.find { it.name == 'root' }.jdkName == 'MyJDK1'
        ideaProject.modules.find { it.name == 'child1' }.jdkName == 'MyJDK2'
        ideaProject.modules.find { it.name == 'child2' }.jdkName == 'MyJDK3'
        ideaProject.modules.find { it.name == 'child3' }.jdkName == null
    }

    @ToolingApiVersion("current")
    @TargetGradleVersion(">=1.2 <3.4")
    def "jdkName property from idea module model is not available in the tooling before 3.4"() {
        when:
        def ideaProject = withConnection { connection -> connection.getModel(IdeaProject) }
        ideaProject.modules.find { it.name == 'root' }.jdkName

        then:
        UnsupportedMethodException e = thrown()
        e.message.startsWith("Unsupported method: IdeaModule.getJdkName()")
    }
}
