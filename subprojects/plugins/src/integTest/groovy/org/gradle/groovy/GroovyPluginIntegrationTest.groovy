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


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
import spock.lang.Unroll

class GroovyPluginIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/7398")
    @Unroll
    def "groovy #classesOrJar output is transitive when #consumerPlugin plugin adds a project dependency to #consumerConf"(
        String consumerPlugin, String consumerConf, boolean groovyWithJavaLib, String classesOrJar) {
        given:
        multiProjectBuild('issue7398', ['groovyLib', 'javaLib']) {
            file('groovyLib').with {
                file('src/main/groovy/GroovyClass.groovy') << "public class GroovyClass {}"
                file('build.gradle') << """
                        ${groovyWithJavaLib ? "apply plugin: 'java-library'" : ''}
                        apply plugin: 'groovy'
                        dependencies {
                            compileOnly localGroovy()
                        }
                """
            }
            file('javaLib').with {
                // Hm...  if we extend GroovyClass, compilation will fail because we did not make localGroovy() transitive,
                // and our GroovyClass actually extends groovy.lang.GroovyObject, which fails when referenced as a supertype.
                // We ignore this implementation detail by referencing GroovyClass as a field; (composition +1, inheritance -1).
                file('src/main/java/JavaClass.java') << "public class JavaClass { GroovyClass reference; }"
                file('build.gradle') << """
                        apply plugin: '$consumerPlugin'
                        dependencies {
                          $consumerConf project(':groovyLib')
                        }
                        
                        task assertDependsOnGroovyLib {
                            dependsOn compileJava
                            doLast {
                                ${ classesOrJar == 'classes' ? '''
                                    def classesDirs = ['compileJava', 'compileGroovy']
                                            .collect { project(':groovyLib').tasks[it].destinationDir }
                                    
                                    assert compileJava.classpath.files.size() == 2
                                    assert compileJava.classpath.files.containsAll(classesDirs)
                                ''' : '''
                                    def jarFile = project(':groovyLib').tasks.jar.archiveFile.get().asFile
                                    
                                    assert compileJava.classpath.files.size() == 1
                                    assert compileJava.classpath.files.contains(jarFile)
                                    assert new java.util.jar.JarFile(jarFile).getJarEntry("GroovyClass.class")
                                '''
                }
                            }
                        }
                """
            }
        }
        expect:
        succeeds 'assertDependsOnGroovyLib'

        where:
        // whenever groovy+java-library plugins are used, classesOrJar will be "classes",
        // and when it is not, it will be "jar".  We use two variables for nice Unrolled feature names.
        consumerPlugin | consumerConf     | groovyWithJavaLib | classesOrJar
        'java-library' | 'api'            | true              | "classes"
        'java-library' | 'api'            | false             | "jar"
        'java-library' | 'compile'        | true              | "classes"
        'java-library' | 'compile'        | false             | "jar"
        'java-library' | 'implementation' | true              | "classes"
        'java-library' | 'implementation' | false             | "jar"

        'java'         | 'compile'        | true              | "classes"
        'java'         | 'compile'        | false             | "jar"
        'java'         | 'implementation' | true              | "classes"
        'java'         | 'implementation' | false             | "jar"
    }
}
