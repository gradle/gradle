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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.junit.runner.RunWith

@RunWith(FluidDependenciesResolveRunner)
class FileDependencyResolveIntegrationTest extends AbstractDependencyResolutionTest {
    def resolve = new ResolveTestFixture(buildFile)

    def setup() {
        resolve.prepare()
    }

    def "can specify producer task for file dependency"() {
        settingsFile << "include 'sub'; rootProject.name='main'"
        buildFile << '''
            allprojects {
                configurations { compile }
                task jar {
                    doLast { 
                        file("${project.name}.jar").text = 'content' 
                    } 
                }
            }
            dependencies { 
                compile project(path: ':sub', configuration: 'compile') 
                compile files('main.jar') { builtBy jar } 
            }
            checkDeps.inputs.files configurations.compile
'''
        file("sub/build.gradle") << '''
            dependencies { 
                compile files('sub.jar') { builtBy jar } 
            }
'''

        when:
        run ":checkDeps"

        then:
        executed ":jar", ":sub:jar", ":checkDeps"
        resolve.expectGraph {
            root(":", ":main:") {
                files << "main.jar"
                files << "sub.jar"
                project(":sub", "main:sub:") {
                    configuration = "compile"
                    noArtifacts()
                }
            }
        }
    }

    def "result includes files that match pattern at the time queried"() {
        settingsFile << "include 'sub'; rootProject.name='main'"
        buildFile << '''
            allprojects {
                configurations { compile }
                task jar {
                    doLast { 
                        file("${project.name}-1.jar").text = 'content' 
                        file("${project.name}-2.jar").text = 'content' 
                    } 
                }
            }
            dependencies { 
                compile project(path: ':sub', configuration: 'compile') 
                compile fileTree(dir: projectDir, include: '*.jar', builtBy: [jar]) 
            }
            
            // Nothing built yet, result should be empty
            assert configurations.compile.files.empty
            
            checkDeps.inputs.files configurations.compile
'''
        file("sub/build.gradle") << '''
            dependencies { 
                compile fileTree(dir: projectDir, include: '*.jar', builtBy: [jar]) 
            }
'''

        when:
        run ":checkDeps"

        then:
        executed ":jar", ":sub:jar", ":checkDeps"
        resolve.expectGraph {
            root(":", ":main:") {
                files << "main-1.jar"
                files << "main-2.jar"
                files << "sub-1.jar"
                files << "sub-2.jar"
                project(":sub", "main:sub:") {
                    configuration = "compile"
                    noArtifacts()
                }
            }
        }
    }

    def "files are requested once only when dependency is resolved"() {
        buildFile << '''
            def jarFile = file("jar-1.jar")
            jarFile << 'content'
            def libFiles = new org.gradle.api.internal.file.collections.ListBackedFileSet(jarFile) {
                Set<File> getFiles() {
                    println "FILES REQUESTED"
                    return super.getFiles()
                }
            }
            
            configurations { compile }
            dependencies { 
                compile new org.gradle.api.internal.file.collections.FileCollectionAdapter(libFiles)
            }
            
            task checkFiles {
                doLast {
                    assert configurations.compile.files == [jarFile] as Set
                }
            }
'''

        when:
        run ":checkFiles"

        then:
        output.count("FILES REQUESTED") == 1
    }

    def "files referenced by file dependency are included when there is a cycle in the dependency graph"() {
        settingsFile << "include 'sub'; rootProject.name='main'"
        buildFile << '''
            allprojects {
                configurations { compile }
                task jar {
                    def outputFile = file("${project.name}.jar")
                    outputs.file outputFile
                    doLast { 
                        outputFile.text = 'content' 
                    } 
                }
            }
            dependencies { 
                compile project(path: ':sub', configuration: 'compile') 
                compile jar.outputs.files
            }
            checkDeps.inputs.files configurations.compile
'''
        file("sub/build.gradle") << '''
            dependencies { 
                compile jar.outputs.files
                compile project(path: ':', configuration: 'compile') 
            }
'''

        when:
        run ":checkDeps"

        then:
        executed ":jar", ":sub:jar", ":checkDeps"
        resolve.expectGraph {
            root(":", ":main:") {
                files << "main.jar"
                files << "sub.jar"
                project(":sub", "main:sub:") {
                    configuration = "compile"
                    noArtifacts()
                    project(":", ":main:") {
                        configuration = "compile"
                        noArtifacts()
                    }
                }
            }
        }
    }

    def "files referenced by file dependency are not included or built when referenced by a non-transitive dependency"() {
        settingsFile << "include 'sub'; rootProject.name='main'"
        buildFile << '''
            allprojects {
                configurations { compile }
                task jar {
                    def outputFile = file("${project.name}.jar")
                    outputs.file outputFile
                    doLast { 
                        outputFile.text = 'content' 
                    } 
                }
            }
            dependencies { 
                compile project(path: ':sub', configuration: 'compile', transitive: false)
                compile jar.outputs.files
            }
            checkDeps.inputs.files configurations.compile
'''
        file("sub/build.gradle") << '''
            dependencies { 
                compile jar.outputs.files
            }
'''

        when:
        run ":checkDeps"

        then:
        executed ":jar", ":checkDeps"
        resolve.expectGraph {
            root(":", ":main:") {
                files << "main.jar"
                project(":sub", "main:sub:") {
                    configuration = "compile"
                    noArtifacts()
                }
            }
        }
    }

}
