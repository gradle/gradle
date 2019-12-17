/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r56

import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.model.eclipse.EclipseClasspathEntry
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.util.GradleVersion

@ToolingApiVersion('>=5.6')
@TargetGradleVersion(">=5.6")
class ToolingApiEclipseModelTestSourcesCrossVersionSpec extends ToolingApiSpecification {

    def "Test source folders and dependencies has test attribute"() {
        setup:
        buildFile << """
            plugins {
                id 'java-library'
            }

            ${RepoScriptBlockUtil.jcenterRepository()}

            dependencies {
                implementation 'com.google.guava:guava:21.0'
                testImplementation 'junit:junit:4.12'
            }
        """
        file('src/main/java').mkdirs()
        file('src/test/java').mkdirs()

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        def mainSrcDir = project.sourceDirectories.find { it.path == 'src/main/java' }
        def testSrcDir = project.sourceDirectories.find { it.path == 'src/test/java' }
        def guava = project.classpath.find { it.file.name.contains 'guava' }
        def junit = project.classpath.find { it.file.name.contains 'junit' }

        then:
        !hasTestAttributes(mainSrcDir)

        and:
        hasTestAttributes(testSrcDir)

        and:
        !hasTestAttributes(guava)

        and:
        hasTestAttributes(junit)
    }

    private boolean hasTestAttributes(EclipseClasspathEntry entry) {
        entry.classpathAttributes.find { it.name == 'test' && it.value == 'true' }
    }

    def "can use compile classpath for API and implementation separation"() {
        given:
        settingsFile << "include('a', 'b', 'c', 'd')"
        buildFile << """
            subprojects {
                apply plugin: 'java-library'
                apply plugin: 'eclipse'

                configurations {
                    compileClasspath {
                        attributes {
                            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
                        }
                    }
                }
                eclipse {
                    classpath {
                        plusConfigurations = [configurations.compileClasspath]
                    }
                }

                ${RepoScriptBlockUtil.jcenterRepository()}
            }
            project(':a') {
                dependencies {
                    implementation project(':b')
                }
            }
            project(':b') {
                dependencies {
                    implementation project(':c')
                    api 'org.apache.commons:commons-lang3:3.9'
                    implementation 'commons-io:commons-io:1.4'
                }
            }
            project(':c') {
                dependencies {
                    api project(':d')
                }
            }
        """

        if (targetVersion.baseVersion < GradleVersion.version('6.1')) {
            // workaround for https://github.com/gradle/gradle/pull/11677
            buildFile << """
                subprojects {
                    configurations {
                        apiElements {
                            attributes {
                                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
                            }
                        }
                        runtimeElements {
                            attributes {
                                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "UNUSED"))
                            }
                        }
                    }
                }
            """
        }

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        EclipseProject projectA = project.children[0]
        EclipseProject projectB = project.children[1]
        EclipseProject projectC = project.children[2]
        EclipseProject projectD = project.children[3]

        then:
        projectA.classpath.collect { it.file.name } as Set == [ 'commons-lang3-3.9.jar' ] as Set
        projectA.projectDependencies.collect { it.path } as Set == [ 'b' ] as Set
        projectB.classpath.collect { it.file.name } as Set == [ 'commons-lang3-3.9.jar', 'commons-io-1.4.jar' ] as Set
        projectB.projectDependencies.collect { it.path } as Set == [ 'c', 'd' ] as Set
        projectC.classpath.empty
        projectC.projectDependencies.collect { it.path } as Set == [ 'd' ] as Set
        projectD.classpath.empty
        projectD.projectDependencies.empty
    }
}
