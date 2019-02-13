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

package org.gradle.integtests.resolve.transform


import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

class ArtifactTransformArtifactInputIntegrationTest extends AbstractDependencyResolutionTest implements ArtifactTransformTestFixture {
    def "re-runs transform when project artifact file content or name or path changes"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithConfigurableProducers()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            abstract class MakeGreen implements ArtifactTransformAction {
                @PrimaryInput
                abstract File getInput()
                
                void transform(ArtifactTransformOutputs outputs) {
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b.jar")
        outputContains("processing c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksSkipped(":b:producer", ":c:producer")
        outputDoesNotContain("processing")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PproducerContent=new")
        succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b.jar")
        outputContains("processing c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PproducerContent=new")
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksSkipped(":b:producer", ":c:producer")
        outputDoesNotContain("processing")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PproducerContent=new", "-PproducerOutputDir=out")
        succeeds(":a:resolve")

        then: // path has changed, should run
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b.jar")
        outputContains("processing c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PproducerContent=new", "-PproducerOutputDir=out")
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksSkipped(":b:producer", ":c:producer")
        outputDoesNotContain("processing")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PproducerContent=new", "-PproducerOutputDir=out", "-PproducerFileName=blue")
        succeeds(":a:resolve")

        then: // new file name, should run
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b-blue.jar")
        outputContains("processing c-blue.jar")
        outputContains("result = [b-blue.jar.green, c-blue.jar.green]")

        when:
        executer.withArguments("-PproducerContent=new", "-PproducerOutputDir=out", "-PproducerFileName=blue")
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksSkipped(":b:producer", ":c:producer")
        outputDoesNotContain("processing")
        outputContains("result = [b-blue.jar.green, c-blue.jar.green]")

        when:
        succeeds(":a:resolve")

        then: // have already seen these artifacts before
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputDoesNotContain("processing")
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    def "re-runs transform when project artifact directory content or name or path changes"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithConfigurableProducers {
            produceDirs()
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            abstract class MakeGreen implements ArtifactTransformAction {
                @PrimaryInput
                abstract File getInput()
                
                void transform(ArtifactTransformOutputs outputs) {
                    if (input.exists()) {
                        println "processing \${input.name}"
                    } else {
                        println "processing missing \${input.name}"
                    }
                    def output = outputs.file(input.name + ".green")
                    output.text = "green"
                }
            }
        """

        when:
        succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b-dir")
        outputContains("processing c-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        succeeds(":a:resolve")

        then: // no change, should be up-to-date
        result.assertTasksSkipped(":b:producer", ":c:producer")
        outputDoesNotContain("processing")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        executer.withArguments("-PproducerName=new")
        succeeds(":a:resolve")

        then: // directory content has changed (file renamed)
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b-dir")
        outputContains("processing c-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        executer.withArguments("-PproducerName=new")
        succeeds(":a:resolve")

        then: // no change, should be up-to-date
        result.assertTasksSkipped(":b:producer", ":c:producer")
        outputDoesNotContain("processing")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        executer.withArguments("-PproducerName=new", "-PproducerContent=new")
        succeeds(":a:resolve")

        then: // directory content has changed (file contents changed)
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b-dir")
        outputContains("processing c-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        executer.withArguments("-PproducerName=new", "-PproducerContent=new")
        succeeds(":a:resolve")

        then: // no change, should be up-to-date
        result.assertTasksSkipped(":b:producer", ":c:producer")
        outputDoesNotContain("processing")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        executer.withArguments("-PproducerName=new", "-PproducerContent=new", "-PproducerFileName=blue")
        succeeds(":a:resolve")

        then: // directory name has changed
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b-blue")
        outputContains("processing c-blue")
        outputContains("result = [b-blue.green, c-blue.green]")

        when:
        executer.withArguments("-PproducerName=new", "-PproducerContent=new", "-PproducerFileName=blue")
        succeeds(":a:resolve")

        then: // no change, should be up-to-date
        result.assertTasksSkipped(":b:producer", ":c:producer")
        outputDoesNotContain("processing")
        outputContains("result = [b-blue.green, c-blue.green]")

        when:
        executer.withArguments("-PproducerName=new", "-PproducerContent=new", "-PproducerFileName=blue", "-PproducerOutputDir=out")
        succeeds(":a:resolve")

        then: // directory path has changed
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b-blue")
        outputContains("processing c-blue")
        outputContains("result = [b-blue.green, c-blue.green]")

        when:
        executer.withArguments("-PproducerName=new", "-PproducerContent=new", "-PproducerFileName=blue", "-PproducerOutputDir=out")
        succeeds(":a:resolve")

        then: // no change, should be up-to-date
        result.assertTasksSkipped(":b:producer", ":c:producer")
        outputDoesNotContain("processing")
        outputContains("result = [b-blue.green, c-blue.green]")
    }

    def "re-runs transform when input artifact file changes from file to missing"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithConfigurableProducers()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            abstract class MakeGreen implements ArtifactTransformAction {
                @PrimaryInput
                abstract File getInput()
                
                void transform(ArtifactTransformOutputs outputs) {
                    if (input.exists()) {
                        println "processing \${input.name}"
                    } else {
                        println "processing missing \${input.name}"
                    }
                    def output = outputs.file(input.name + ".green")
                    output.text = "green"
                }
            }
        """

        when:
        succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b.jar")
        outputContains("processing c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve")

        then:
        result.assertTasksSkipped(":b:producer", ":c:producer")
        outputDoesNotContain("processing")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve", "-PproduceNothing")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing missing b.jar")
        outputContains("processing missing c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve")

        then: // seen these before
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputDoesNotContain("processing")
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    def "can attach @PathSensitive(NONE) to input artifact property for project artifact"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithConfigurableProducers()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            abstract class MakeGreen implements ArtifactTransformAction {
                @PathSensitive(PathSensitivity.NONE)
                @PrimaryInput
                abstract File getInput()
                
                void transform(ArtifactTransformOutputs outputs) {
                    println "processing \${input.name}"
                    def output = outputs.file(input.text + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b.jar")
        outputContains("processing c.jar")
        outputContains("result = [b.green, c.green]")

        when:
        executer.withArguments("-PproducerOutputDir=out")
        succeeds(":a:resolve")

        then: // path has changed, but should be up to date
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputDoesNotContain("processing")
        outputContains("result = [b.green, c.green]")

        when:
        executer.withArguments("-PproducerOutputDir=out", "-PproducerFileName=blue")
        succeeds(":a:resolve")

        then: // name has changed, but should be up to date
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputDoesNotContain("processing")
        outputContains("result = [b.green, c.green]")

        when:
        executer.withArguments("-PproducerOutputDir=out", "-PproducerFileName=blue", "-PproducerContent=new")
        succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b-blue.jar")
        outputContains("processing c-blue.jar")
        outputContains("result = [b-new.green, c-new.green]")

        when:
        executer.withArguments("-PproducerOutputDir=out", "-PproducerFileName=blue", "-PproducerContent=new")
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksSkipped(":b:producer", ":c:producer")
        outputDoesNotContain("processing")
        outputContains("result = [b-new.green, c-new.green]")

        when:
        succeeds(":a:resolve")

        then: // have already seen these artifacts before
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputDoesNotContain("processing")
        outputContains("result = [b.green, c.green]")
    }

    def "can attach @PathSensitive(NAME_ONLY) to input artifact property for project artifact"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithConfigurableProducers()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            abstract class MakeGreen implements ArtifactTransformAction {
                @PathSensitive(PathSensitivity.NAME_ONLY)
                @PrimaryInput
                abstract File getInput()
                
                void transform(ArtifactTransformOutputs outputs) {
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b.jar")
        outputContains("processing c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PproducerOutputDir=out")
        succeeds(":a:resolve")

        then: // path has changed, but should be up to date
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputDoesNotContain("processing")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PproducerOutputDir=out", "-PproducerFileName=blue")
        succeeds(":a:resolve")

        then: // name has changed, should run
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b-blue.jar")
        outputContains("processing c-blue.jar")
        outputContains("result = [b-blue.jar.green, c-blue.jar.green]")

        when:
        executer.withArguments("-PproducerOutputDir=out", "-PproducerFileName=blue", "-PproducerContent=new")
        succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputContains("processing b-blue.jar")
        outputContains("processing c-blue.jar")
        outputContains("result = [b-blue.jar.green, c-blue.jar.green]")

        when:
        executer.withArguments("-PproducerOutputDir=out", "-PproducerFileName=blue", "-PproducerContent=new")
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksSkipped(":b:producer", ":c:producer")
        outputDoesNotContain("processing")
        outputContains("result = [b-blue.jar.green, c-blue.jar.green]")

        when:
        succeeds(":a:resolve")

        then: // have already seen these artifacts before
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        outputDoesNotContain("processing")
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    def "can attach @PathSensitive(NONE) to input artifact property for external artifact"() {
        setupBuildWithConfigurableProducers()
        def lib1 = mavenRepo.module("group1", "lib", "1.0").adhocVariants().variant('runtime', [color: 'blue']).withModuleMetadata().publish()
        lib1.artifactFile.text = "lib"
        def lib2 = mavenRepo.module("group2", "lib", "1.0").adhocVariants().variant('runtime', [color: 'blue']).withModuleMetadata().publish()
        lib2.artifactFile.text = "lib"
        def lib3 = mavenRepo.module("group2", "lib", "1.1").adhocVariants().variant('runtime', [color: 'blue']).withModuleMetadata().publish()
        lib3.artifactFile.text = "lib"
        def lib4 = mavenRepo.module("group2", "lib2", "1.0").adhocVariants().variant('runtime', [color: 'blue']).withModuleMetadata().publish()
        lib4.artifactFile.text = "lib2"

        buildFile << """
            repositories {
                maven { 
                    url = '${mavenRepo.uri}' 
                    metadataSources { gradleMetadata() }
                }
            }
            dependencies {
                if (project.hasProperty('externalCoords')) {
                    implementation project.externalCoords
                } else {
                    implementation 'group1:lib:1.0'
                }
                implementation 'group2:lib2:1.0'
            }
            
            abstract class MakeGreen implements ArtifactTransformAction {
                @PathSensitive(PathSensitivity.NONE)
                @PrimaryInput
                abstract File getInput()
                
                void transform(ArtifactTransformOutputs outputs) {
                    println "processing \${input.name}"
                    def output = outputs.file(input.text + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        succeeds(":resolve")

        then:
        outputContains("processing lib-1.0.jar")
        outputContains("processing lib2-1.0.jar")
        outputContains("result = [lib.green, lib2.green]")

        when:
        succeeds(":resolve")

        then: // no change, should be up-to-date
        outputDoesNotContain("processing")
        outputContains("result = [lib.green, lib2.green]")

        when:
        succeeds(":resolve", "-PexternalCoords=group2:lib:1.0")

        then: // path change, should be up-to-date
        outputDoesNotContain("processing")
        outputContains("result = [lib.green, lib2.green]")

        when:
        succeeds(":resolve", "-PexternalCoords=group2:lib:1.1")

        then: // name change, should be up-to-date
        outputDoesNotContain("processing")
        outputContains("result = [lib.green, lib2.green]")

        when:
        lib1.artifactFile.text = "new-lib"
        succeeds(":resolve")

        then: // content change, should run
        outputContains("processing lib-1.0.jar")
        outputDoesNotContain("processing lib2")
        outputContains("result = [new-lib.green, lib2.green]")

        when:
        lib4.artifactFile.text = "new-lib"
        succeeds(":resolve")

        then: // duplicate content
        outputDoesNotContain("processing")
        outputContains("result = [new-lib.green]")
    }

    def "can attach @PathSensitive(NAME_ONLY) to input artifact property for external artifact"() {
        setupBuildWithConfigurableProducers()
        def lib1 = withColorVariants(mavenRepo.module("group1", "lib", "1.0")).publish()
        lib1.artifactFile.text = "lib"
        def lib2 = withColorVariants(mavenRepo.module("group2", "lib", "1.0")).publish()
        lib2.artifactFile.text = "lib"
        def lib3 = withColorVariants(mavenRepo.module("group2", "lib", "1.1")).publish()
        lib3.artifactFile.text = "lib"
        def lib4 = withColorVariants(mavenRepo.module("group2", "lib2", "1.0")).publish()
        lib4.artifactFile.text = "lib2"

        buildFile << """
            repositories {
                maven { 
                    url = '${mavenRepo.uri}' 
                    metadataSources { gradleMetadata() }
                }
            }
            dependencies {
                if (project.hasProperty('externalCoords')) {
                    implementation project.externalCoords
                } else {
                    implementation 'group1:lib:1.0'
                }
                implementation 'group2:lib2:1.0'
            }
            
            abstract class MakeGreen implements ArtifactTransformAction {
                @PathSensitive(PathSensitivity.NAME_ONLY)
                @PrimaryInput
                abstract File getInput()
                
                void transform(ArtifactTransformOutputs outputs) {
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        succeeds(":resolve")

        then:
        outputContains("processing lib-1.0.jar")
        outputContains("processing lib2-1.0.jar")
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")

        when:
        succeeds(":resolve")

        then: // no change, should be up-to-date
        outputDoesNotContain("processing")
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")

        when:
        succeeds(":resolve", "-PexternalCoords=group2:lib:1.0")

        then: // path change, should be up-to-date
        outputDoesNotContain("processing")
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")

        when:
        succeeds(":resolve", "-PexternalCoords=group2:lib:1.1")

        then: // name change, should run
        outputContains("processing lib-1.1.jar")
        outputContains("result = [lib-1.1.jar.green, lib2-1.0.jar.green]")

        when:
        lib1.artifactFile.text = "new-lib"
        succeeds(":resolve")

        then: // content change, should run
        outputContains("processing lib-1.0.jar")
        outputDoesNotContain("processing lib2")
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")
    }

    /**
     * Caller should provide an implementation of `MakeGreen` transform action
     */
    void setupBuildWithConfigurableProducers() {
        setupBuildWithConfigurableProducers {}
    }

    /**
     * Caller should provide an implementation of `MakeGreen` transform action
     */
    void setupBuildWithConfigurableProducers(@DelegatesTo(Builder) Closure cl) {
        setupBuildWithColorTransformAction(cl)
        buildFile << """
            allprojects {
                afterEvaluate {
                    if (project.hasProperty('producerOutputDir')) {
                        buildDir = project.file(producerOutputDir)
                    }
                    tasks.withType(FileProducer) {
                        if (project.hasProperty('produceNothing')) {
                            content = ""
                        } else if (project.hasProperty('producerContent')) {
                            content = project.name + '-' + project.producerContent
                        } else {
                            content = project.name
                        }
                        if (project.hasProperty('producerFileName')) {
                            output = layout.buildDir.file("\${project.name}-\${producerFileName}.jar")
                        } else {
                            output = layout.buildDir.file("\${project.name}.jar")
                        }
                    }
                    tasks.withType(DirProducer) {
                        if (project.hasProperty('produceNothing')) {
                            content = ""
                        } else if (project.hasProperty('producerContent')) {
                            content = project.name + '-' + project.producerContent
                        } else {
                            content = project.name
                        }
                        if (project.hasProperty('producerName')) {
                            names = [project.producerName]
                        } else {
                            names = [project.name]
                        }
                        if (project.hasProperty('producerFileName')) {
                            output = layout.buildDir.dir("\${project.name}-\${producerFileName}")
                        } else {
                            output = layout.buildDir.dir("\${project.name}-dir")
                        }
                    }
                }
            }
        """
    }

}
