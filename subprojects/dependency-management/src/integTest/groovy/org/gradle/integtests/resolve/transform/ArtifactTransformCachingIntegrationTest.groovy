/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ArtifactTransformCachingIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'util'
            include 'app'
        """

        buildFile << """
def usage = Attribute.of('usage', String)
def artifactType = Attribute.of('artifactType', String)
    
allprojects {
    dependencies {
        attributesSchema {
            attribute(usage)
        }
    }
    configurations {
        compile {
            attributes.attribute usage, 'api'
        }
    }
}

class FileSizer extends ArtifactTransform {
    List<File> transform(File input) {
        def output = new File(outputDirectory, input.name + ".txt")
        println "Transforming \${input.name} to \${output.name}"
        output.text = String.valueOf(input.length())
        return [output]
    }
}

"""
    }

    def "transformation is applied once only to each file once per build"() {
        given:
        buildFile << """
            allprojects {
                dependencies {
                    registerTransform {
                        from.attribute(artifactType, "jar")
                        to.attribute(artifactType, "size")
                        artifactTransform(FileSizer)
                    }
                }
                task resolve {
                    def artifacts = configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'size') }.artifacts
                    inputs.files artifacts.artifactFiles
                    doLast {
                        println "files 1: " + artifacts.artifactFiles.collect { it.name }
                        println "files 2: " + artifacts.collect { it.file.name }
                        println "ids 1: " + artifacts.collect { it.id.displayName }
                        println "components 1: " + artifacts.collect { it.id.componentIdentifier }
                    }
                }
            }

            project(':lib') {
                task jar1(type: Jar) {            
                    archiveName = 'lib1.jar'
                }
                task jar2(type: Jar) {            
                    archiveName = 'lib2.jar'
                }
                artifacts {
                    compile jar1
                    compile jar2
                }
            }
            
            project(':util') {
                dependencies {
                    compile project(':lib')
                }
            }

            project(':app') {
                dependencies {
                    compile project(':util')
                }
            }
        """

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files 1: [lib1.jar.txt, lib2.jar.txt]") == 2
        output.count("files 2: [lib1.jar.txt, lib2.jar.txt]") == 2
        output.count("ids 1: [lib1.jar.txt (project :lib), lib2.jar.txt (project :lib)]") == 2
        output.count("components 1: [project :lib, project :lib]") == 2

        output.count("Transforming lib1.jar to lib1.jar.txt") == 1
        output.count("Transforming lib2.jar to lib2.jar.txt") == 1
    }

    def "each file is transformed once per set of parameters"() {
        given:
        buildFile << """
            class TransformWithMultipleTargets extends ArtifactTransform {
                private String target
                
                TransformWithMultipleTargets(String target) {
                    this.target = target
                }
                
                List<File> transform(File input) {
                    if (target.equals("size")) {
                        def outSize = new File(outputDirectory, input.name + ".size")
                        println "Transforming \$input.name to \$outSize.name"
                        outSize.text = String.valueOf(input.length())
                        return [outSize]
                    }
                    if (target.equals("hash")) {
                        def outHash = new File(outputDirectory, input.name + ".hash")
                        println "Transforming \$input.name to \$outHash.name"
                            outHash.text = 'hash'
                        return [outHash]
                    }             
                }
            }
            
            allprojects {
                dependencies {
                    registerTransform {
                        from.attribute(artifactType, 'jar')
                        to.attribute(artifactType, 'size')
                        artifactTransform(TransformWithMultipleTargets) {
                            params('size')
                        }
                    }
                    registerTransform {
                        from.attribute(artifactType, 'jar')
                        to.attribute(artifactType, 'hash')
                        artifactTransform(TransformWithMultipleTargets) {
                            params('hash')
                        }
                    }
                }
                task resolve {
                    doLast {
                        def size = configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'size') }.artifacts
                        def hash = configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'hash') }.artifacts
                        println "files 1: " + size.collect { it.file.name }
                        println "ids 1: " + size.collect { it.id }
                        println "components 1: " + size.collect { it.id.componentIdentifier }
                        println "files 2: " + hash.collect { it.file.name }
                        println "ids 2: " + hash.collect { it.id }
                        println "components 2: " + hash.collect { it.id.componentIdentifier }
                    }
                }
            }

            project(':lib') {
                task jar1(type: Jar) {            
                    archiveName = 'lib1.jar'
                }
                task jar2(type: Jar) {            
                    archiveName = 'lib2.jar'
                }
                artifacts {
                    compile jar1
                    compile jar2
                }
            }
            
            project(':util') {
                dependencies {
                    compile project(':lib')
                }
            }

            project(':app') {
                dependencies {
                    compile project(':util')
                }
            }
        """

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files 1: [lib1.jar.size, lib2.jar.size]") == 2
        output.count("ids 1: [lib1.jar.size (project :lib), lib2.jar.size (project :lib)]") == 2
        output.count("components 1: [project :lib, project :lib]") == 2
        output.count("files 2: [lib1.jar.hash, lib2.jar.hash]") == 2
        output.count("ids 2: [lib1.jar.hash (project :lib), lib2.jar.hash (project :lib)]") == 2
        output.count("components 2: [project :lib, project :lib]") == 2

        output.count("Transforming lib1.jar to lib1.jar.size") == 1
        output.count("Transforming lib2.jar to lib2.jar.size") == 1
        output.count("Transforming lib1.jar to lib1.jar.hash") == 1
        output.count("Transforming lib2.jar to lib2.jar.hash") == 1
    }

    def "each file is transformed once per transform class"() {
        given:
        buildFile << """
            class Sizer extends ArtifactTransform {
                Sizer(String target) {
                    // ignore config
                }
                
                List<File> transform(File input) {
                    def outSize = new File(outputDirectory, input.name + ".size")
                    println "Transforming \$input.name to \$outSize.name"
                    outSize.text = String.valueOf(input.length())
                    return [outSize]
                }
            }
            class Hasher extends ArtifactTransform {
                private String target
                
                Hasher(String target) {
                    // ignore config
                }
                
                List<File> transform(File input) {
                    def outHash = new File(outputDirectory, input.name + ".hash")
                    println "Transforming \$input.name to \$outHash.name"
                        outHash.text = 'hash'
                    return [outHash]
                }
            }
            
            allprojects {
                dependencies {
                    registerTransform {
                        from.attribute(artifactType, 'jar')
                        to.attribute(artifactType, 'size')
                        artifactTransform(Sizer) { params('size') }
                    }
                    registerTransform {
                        from.attribute(artifactType, 'jar')
                        to.attribute(artifactType, 'hash')
                        artifactTransform(Hasher) { params('hash') }
                    }
                }
                task resolve {
                    doLast {
                        def size = configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'size') }.artifacts
                        def hash = configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'hash') }.artifacts
                        println "files 1: " + size.collect { it.file.name }
                        println "ids 1: " + size.collect { it.id }
                        println "components 1: " + size.collect { it.id.componentIdentifier }
                        println "files 2: " + hash.collect { it.file.name }
                        println "ids 2: " + hash.collect { it.id }
                        println "components 2: " + hash.collect { it.id.componentIdentifier }
                    }
                }
            }

            project(':lib') {
                task jar1(type: Jar) {            
                    archiveName = 'lib1.jar'
                }
                task jar2(type: Jar) {            
                    archiveName = 'lib2.jar'
                }
                artifacts {
                    compile jar1
                    compile jar2
                }
            }
            
            project(':util') {
                dependencies {
                    compile project(':lib')
                }
            }

            project(':app') {
                dependencies {
                    compile project(':util')
                }
            }
        """

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files 1: [lib1.jar.size, lib2.jar.size]") == 2
        output.count("ids 1: [lib1.jar.size (project :lib), lib2.jar.size (project :lib)]") == 2
        output.count("components 1: [project :lib, project :lib]") == 2
        output.count("files 2: [lib1.jar.hash, lib2.jar.hash]") == 2
        output.count("ids 2: [lib1.jar.hash (project :lib), lib2.jar.hash (project :lib)]") == 2
        output.count("components 2: [project :lib, project :lib]") == 2

        output.count("Transforming lib1.jar to lib1.jar.size") == 1
        output.count("Transforming lib2.jar to lib2.jar.size") == 1
        output.count("Transforming lib1.jar to lib1.jar.hash") == 1
        output.count("Transforming lib2.jar to lib2.jar.hash") == 1
    }
}
