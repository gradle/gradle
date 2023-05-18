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
package org.gradle.groovy

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.archive.JarTestFixture
import spock.lang.Issue

class GroovyJavaLibraryInteractionIntegrationTest extends AbstractDependencyResolutionTest {

    ResolveTestFixture resolve

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
    }

    @ToBeFixedForConfigurationCache(because = "serializes the incorrect artifact in ArtifactCollection used by resolve fixture")
    @Issue("https://github.com/gradle/gradle/issues/7398")
    def "selects #expected output when #consumerPlugin plugin adds a project dependency to #consumerConf and producer has java-library=#groovyWithJavaLib (compileClasspath)"() {
        given:
        resolve = new ResolveTestFixture(buildFile, "compileClasspath")
        resolve.prepare()
        if (compileClasspathPackaging) {
            propertiesFile << """
                systemProp.org.gradle.java.compile-classpath-packaging=true
            """.trim()
        }
        multiProjectBuild('issue7398', ['groovyLib', 'javaLib']) {
            file('groovyLib').with {
                file('src/main/groovy/GroovyClass.groovy') << "public class GroovyClass {}"
                file('build.gradle') << """
                        ${groovyWithJavaLib ? "apply plugin: 'java-library'" : ''}
                        apply plugin: 'groovy'
                        dependencies {
                            implementation localGroovy()
                        }
                """
            }
            file('javaLib').with {
                file('src/main/java/JavaClass.java') << "public class JavaClass { GroovyClass reference; }"
                file('build.gradle') << """
                        apply plugin: '$consumerPlugin'
                        dependencies {
                          $consumerConf project(':groovyLib')
                        }
                """
            }
        }
        when:
        succeeds 'javaLib:checkDeps'

        if (expected == 'jar') {
            def jar = new JarTestFixture(testDirectory.file("groovyLib/build/libs/groovyLib-1.0.jar"))
            executedAndNotSkipped(":groovyLib:compileJava", ":groovyLib:compileGroovy", ":groovyLib:classes", ":groovyLib:jar")
            jar.hasDescendants("Dummy.class", "GroovyClass.class")
        } else {
            executedAndNotSkipped(":groovyLib:compileJava", ":groovyLib:compileGroovy")
            notExecuted(":groovyLib:classes", ":groovyLib:jar")
        }

        then:
        resolve.expectGraph {
            root(":javaLib", "org.test:javaLib:1.0") {
                project(":groovyLib", "org.test:groovyLib:1.0") {
                    variant("apiElements", [
                        'org.gradle.category': 'library',
                        'org.gradle.dependency.bundling': 'external',
                        'org.gradle.jvm.version': JavaVersion.current().majorVersion,
                        'org.gradle.usage': 'java-api',
                        'org.gradle.libraryelements': 'jar',
                        ])
                    switch (expected) {
                        case "jar":
                            artifact(name: "groovyLib")
                            break
                        case "classes":
                            // first one is "main" from Java sources
                            artifact name: 'main', version: '', extension: '', type: 'java-classes-directory'
                            // second one is "main" from Groovy sources
                            artifact name: 'main', version: '', extension: '', type: 'java-classes-directory'
                            break
                    }
                }
            }
        }

        where:
        consumerPlugin | consumerConf     | groovyWithJavaLib | compileClasspathPackaging | expected
        'java-library' | 'api'            | true              | false                     | "classes"
        'java-library' | 'api'            | false             | false                     | "jar"
        'java-library' | 'implementation' | true              | false                     | "classes"
        'java-library' | 'implementation' | false             | false                     | "jar"

        'java'         | 'implementation' | true              | false                     | "classes"
        'java'         | 'implementation' | false             | false                     | "jar"

        'java-library' | 'api'            | true              | true                      | "jar"
        'java-library' | 'api'            | false             | true                      | "jar"
        'java-library' | 'implementation' | true              | true                      | "jar"
        'java-library' | 'implementation' | false             | true                      | "jar"

        'java'         | 'implementation' | true              | true                      | "jar"
        'java'         | 'implementation' | false             | true                      | "jar"
    }

    def "selects classes when #consumerPlugin plugin adds a project dependency to #consumerConf and producer has java-library=#groovyWithJavaLib (runtime classes variant)"() {
        given:
        resolve = new ResolveTestFixture(buildFile, "runtimeClasspath")
        resolve.prepare()

        multiProjectBuild('issue7398', ['groovyLib', 'javaLib']) {
            file('groovyLib').with {
                file('src/main/groovy/GroovyClass.groovy') << "public class GroovyClass {}"
                file('build.gradle') << """
                        ${groovyWithJavaLib ? "apply plugin: 'java-library'" : ''}
                        apply plugin: 'groovy'
                        dependencies {
                            implementation 'org.codehaus.groovy:groovy:2.5.10'
                        }
                """
            }
            file('javaLib').with {
                file('src/main/java/JavaClass.java') << "public class JavaClass { GroovyClass reference; }"
                file('build.gradle') << """
                        apply plugin: '$consumerPlugin'
                        dependencies {
                          $consumerConf project(':groovyLib')
                        }
                """
            }
        }

        buildFile << """
            subprojects {

                ${mavenCentralRepository()}

                afterEvaluate {
                    configurations.runtimeClasspath.attributes {
                        // make sure we explicitly require the "classes" variant
                        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.CLASSES))
                    }
                }
            }
        """

        when:
        succeeds 'javaLib:checkDeps'

        executedAndNotSkipped(":groovyLib:compileJava", ":groovyLib:compileGroovy")
        notExecuted(":groovyLib:classes", ":groovyLib:jar")

        then:
        resolve.expectGraph {
            root(":javaLib", "org.test:javaLib:1.0") {
                project(":groovyLib", "org.test:groovyLib:1.0") {
                    variant("runtimeElements", [
                        'org.gradle.category': 'library',
                        'org.gradle.dependency.bundling': 'external',
                        'org.gradle.jvm.version': JavaVersion.current().majorVersion,
                        'org.gradle.usage': 'java-runtime',
                        'org.gradle.libraryelements': 'jar'])

                    module('org.codehaus.groovy:groovy:2.5.10')
                    // first one is "main" from Java sources
                    artifact name: 'main', version: '', extension: '', type: 'java-classes-directory'
                    // second one is "main" from Groovy sources
                    artifact name: 'main', version: '', extension: '', type: 'java-classes-directory'
                }
            }
        }

        where:
        consumerPlugin | consumerConf     | groovyWithJavaLib
        'java-library' | 'api'            | true
        'java-library' | 'api'            | false
        'java-library' | 'implementation' | true
        'java-library' | 'implementation' | false

        'java'         | 'implementation' | true
        'java'         | 'implementation' | false
    }
}
