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

package org.gradle.plugins.ide.tooling.r34

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject

class ToolingApiIdeaModelCrossVersionSpec extends ToolingApiSpecification {
    def setup(){
        settingsFile << "rootProject.name = 'root'"
    }

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

    @TargetGradleVersion(">=3.0 <3.4")
    def "jdkName property from idea module model is not available in the tooling before 3.4"() {
        when:
        def ideaProject = withConnection { connection -> connection.getModel(IdeaProject) }
        ideaProject.modules.find { it.name == 'root' }.jdkName

        then:
        UnsupportedMethodException e = thrown()
        e.message.startsWith("Unsupported method: IdeaModule.getJdkName()")
    }

    @TargetGradleVersion(">=3.4 <4.5")
    def "provides correct dependencies when using java-library plugin"() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            include 'a', 'b', 'c', 'd', 'e', 'f'
        """

        buildFile << """
            allprojects {
                apply plugin: 'java'
                apply plugin: 'idea'
            }

            dependencies {
                implementation project(':a')
                testImplementation project(':f')
            }

            project(':a') {
                apply plugin: 'java-library'
                dependencies {
                    api project(':b')
                    implementation project(':c')
                    compileOnly project(':d')
                    runtimeOnly project(':e')
                }
            }
        """

        when:
        def ideaProject = withConnection { connection -> connection.getModel(IdeaProject) }
        def module = ideaProject.modules.find {it. name == 'root'}

        then:
        module.dependencies.size() == 11
        hasDependency(module, "a", "PROVIDED")
        hasDependency(module, "a", "RUNTIME")
        hasDependency(module, "a", "TEST")
        hasDependency(module, "f", "TEST")
        hasDependency(module, "b", "PROVIDED")
        hasDependency(module, "b", "RUNTIME")
        hasDependency(module, "b", "TEST")
        hasDependency(module, "c", "RUNTIME")
        hasDependency(module, "c", "TEST")
        hasDependency(module, "e", "RUNTIME")
        hasDependency(module, "e", "TEST")
    }

    @TargetGradleVersion(">=4.5")
    def "provides minimal set of dependencies when using java-library plugin"() {
        given:
        settingsFile << """
            rootProject.name = 'root'
            include 'a', 'b', 'c', 'd', 'e', 'f'
        """

        buildFile << """
            allprojects {
                apply plugin: 'java'
                apply plugin: 'idea'
            }

            dependencies {
                implementation project(':a')
                testImplementation project(':f')
            }

            project(':a') {
                apply plugin: 'java-library'
                dependencies {
                    api project(':b')
                    implementation project(':c')
                    compileOnly project(':d')
                    runtimeOnly project(':e')
                }
            }
        """

        when:
        def ideaProject = withConnection { connection -> connection.getModel(IdeaProject) }
        def module = ideaProject.modules.find {it. name == 'root'}

        then:
        module.dependencies.size() == 7
        hasDependency(module, "a", "COMPILE")
        hasDependency(module, "b", "COMPILE")
        hasDependency(module, "c", "RUNTIME")
        hasDependency(module, "c", "TEST")
        hasDependency(module, "e", "RUNTIME")
        hasDependency(module, "e", "TEST")
        hasDependency(module, "f", "TEST")
    }

    def hasDependency(IdeaModule module, String name, String scope) {
        module.dependencies.find { IdeaModuleDependency dep ->
            dep.targetModuleName == name && dep.scope.scope == scope
        }
    }
}
