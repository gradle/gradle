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

import org.gradle.api.tasks.PathSensitivity
import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import spock.lang.Unroll

import java.util.regex.Pattern

class ArtifactTransformInputArtifactIntegrationTest extends AbstractDependencyResolutionTest implements ArtifactTransformTestFixture {
    def "transform does not execute when project artifact cannot be built"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransformAction()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            project(':b') {
                tasks.producer.doLast { throw new RuntimeException('broken') }
            }
            
            abstract class MakeGreen implements TransformAction {
                @InputArtifact
                abstract File getInput()
                
                void transform(TransformOutputs outputs) {
                    println "processing \${input.name}"
                }
            }
        """

        when:
        executer.withArgument("--continue")
        fails(":a:resolve")

        then:
        transformed("c.jar")
        failure.assertHasFailures(1)
        failure.assertHasDescription("Execution failed for task ':b:producer'.")
        failure.assertHasCause("broken")
    }

    @Unroll
    def "can attach #description to input artifact property with project artifact file"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransformAction()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            abstract class MakeGreen implements TransformAction {
                @InputArtifact ${annotation}
                abstract File getInput()
                
                void transform(TransformOutputs outputs) {
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
        transformed("b.jar", "c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PbContent=new")
        succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PbContent=new")
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PbContent=new", "-PbOutputDir=out")
        succeeds(":a:resolve")

        then: // path has changed, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PbContent=new", "-PbOutputDir=out")
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PbContent=new", "-PbOutputDir=out", "-PbFileName=b-blue.jar")
        succeeds(":a:resolve")

        then: // new file name, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue.jar")
        outputContains("result = [b-blue.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PbContent=new", "-PbOutputDir=out", "-PbFileName=b-blue.jar")
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-blue.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve")

        then: // have already seen these artifacts before
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        where:
        description                                | annotation
        "no sensitivity"                           | ""
        "@PathSensitive(PathSensitivity.ABSOLUTE)" | "@PathSensitive(PathSensitivity.ABSOLUTE)"
    }

    @Unroll
    def "can attach #description to input artifact property with project artifact directory"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransformAction {
            produceDirs()
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            abstract class MakeGreen implements TransformAction {
                @InputArtifact ${annotation}
                abstract File getInput()
                
                void transform(TransformOutputs outputs) {
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
        transformed("b-dir", "c-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        succeeds(":a:resolve")

        then: // no change, should be up-to-date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        executer.withArguments("-PbName=new")
        succeeds(":a:resolve")

        then: // directory content has changed (file renamed)
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        executer.withArguments("-PbName=new")
        succeeds(":a:resolve")

        then: // no change, should be up-to-date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        executer.withArguments("-PbName=new", "-PbContent=new")
        succeeds(":a:resolve")

        then: // directory content has changed (file contents changed)
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        executer.withArguments("-PbName=new", "-PbContent=new")
        succeeds(":a:resolve")

        then: // no change, should be up-to-date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        executer.withArguments("-PbName=new", "-PbContent=new", "-PbDirName=b-blue")
        succeeds(":a:resolve")

        then: // directory name has changed
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        executer.withArguments("-PbName=new", "-PbContent=new", "-PbDirName=b-blue")
        succeeds(":a:resolve")

        then: // no change, should be up-to-date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        executer.withArguments("-PbName=new", "-PbContent=new", "-PbDirName=b-blue", "-PbOutputDir=out")
        succeeds(":a:resolve")

        then: // directory path has changed
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")

        when:
        executer.withArguments("-PbName=new", "-PbContent=new", "-PbDirName=b-blue", "-PbOutputDir=out")
        succeeds(":a:resolve")

        then: // no change, should be up-to-date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-blue.green, c-dir.green]")

        where:
        description                                | annotation
        "no sensitivity"                           | ""
        "@PathSensitive(PathSensitivity.ABSOLUTE)" | "@PathSensitive(PathSensitivity.ABSOLUTE)"
    }

    def "re-runs transform when input artifact file changes from file to missing"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransformAction()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            abstract class MakeGreen implements TransformAction {
                @InputArtifact
                abstract File getInput()
                
                void transform(TransformOutputs outputs) {
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
        transformed("b.jar", "c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve")

        then: // no changes
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve", "-PbProduceNothing")

        then: // file is missing, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("missing b.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve")

        then: // seen these before
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")
    }

    def "can attach @PathSensitive(NONE) to input artifact property for project artifact file"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransformAction()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            abstract class MakeGreen implements TransformAction {
                @PathSensitive(PathSensitivity.NONE)
                @InputArtifact
                abstract File getInput()
                
                void transform(TransformOutputs outputs) {
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
        transformed("b.jar", "c.jar")
        outputContains("result = [b.green, c.green]")

        when:
        executer.withArguments("-PbOutputDir=out")
        succeeds(":a:resolve")

        then: // path has changed, but should be up to date
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.green, c.green]")

        when:
        executer.withArguments("-PbOutputDir=out", "-PbFileName=b-blue.jar")
        succeeds(":a:resolve")

        then: // name has changed, but should be up to date
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.green, c.green]")

        when:
        executer.withArguments("-PbOutputDir=out", "-PbFileName=b-blue.jar", "-PbContent=b-new")
        succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue.jar")
        outputContains("result = [b-new.green, c.green]")

        when:
        executer.withArguments("-PbOutputDir=out", "-PbFileName=b-blue.jar", "-PbContent=b-new")
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-new.green, c.green]")

        when:
        succeeds(":a:resolve")

        then: // have already seen these artifacts before
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.green, c.green]")
    }

    @Unroll
    def "can attach @PathSensitive(#sensitivity) to input artifact property for project artifact directory"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransformAction {
            produceDirs()
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            abstract class MakeGreen implements TransformAction {
                @PathSensitive(PathSensitivity.${sensitivity})
                @InputArtifact
                abstract File getInput()
                
                void transform(TransformOutputs outputs) {
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = "green"
                }
            }
        """

        when:
        succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        transformed("b-dir", "c-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        executer.withArguments("-PbOutputDir=out")
        succeeds(":a:resolve")

        then: // path has changed, but should be up to date
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        executer.withArguments("-PbOutputDir=out", "-PbDirName=b-blue")
        succeeds(":a:resolve")

        then: // name has changed, but should be up to date
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        executer.withArguments("-PbOutputDir=out", "-PbDirName=b-blue", "-PbContent=new")
        succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        executer.withArguments("-PbOutputDir=out", "-PbDirName=b-blue", "-PbContent=new")
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        executer.withArguments("-PbOutputDir=out", "-PbDirName=b-blue", "-PbContent=new", "-PbName=new")
        succeeds(":a:resolve")

        then: // new content (renamed file), should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        succeeds(":a:resolve")

        then: // have already seen these artifacts before
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")

        where:
        sensitivity << [PathSensitivity.RELATIVE, PathSensitivity.NONE]
    }

    @Unroll
    def "can attach @PathSensitive(#sensitivity) to input artifact property for project artifact file"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransformAction()
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }
            
            abstract class MakeGreen implements TransformAction {
                @PathSensitive(PathSensitivity.${sensitivity})
                @InputArtifact
                abstract File getInput()
                
                void transform(TransformOutputs outputs) {
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
        transformed("b.jar", "c.jar")
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PbOutputDir=out")
        succeeds(":a:resolve")

        then: // path has changed, but should be up to date
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PbOutputDir=out", "-PbFileName=b-blue.jar")
        succeeds(":a:resolve")

        then: // name has changed, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue.jar")
        outputContains("result = [b-blue.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PbOutputDir=out", "-PbFileName=b-blue.jar", "-PbContent=new")
        succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue.jar")
        outputContains("result = [b-blue.jar.green, c.jar.green]")

        when:
        executer.withArguments("-PbOutputDir=out", "-PbFileName=b-blue.jar", "-PbContent=new")
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-blue.jar.green, c.jar.green]")

        when:
        succeeds(":a:resolve")

        then: // have already seen these artifacts before
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b.jar.green, c.jar.green]")

        where:
        sensitivity << [PathSensitivity.RELATIVE, PathSensitivity.NAME_ONLY]
    }

    def "can attach @PathSensitive(NAME_ONLY) to input artifact property for project artifact directory"() {
        settingsFile << "include 'a', 'b', 'c'"
        setupBuildWithColorTransformAction {
            produceDirs()
        }
        buildFile << """
            project(':a') {
                dependencies {
                    implementation project(':b')
                    implementation project(':c')
                }
            }

            abstract class MakeGreen implements TransformAction {
                @PathSensitive(PathSensitivity.NAME_ONLY)
                @InputArtifact
                abstract File getInput()
                
                void transform(TransformOutputs outputs) {
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = "green"
                }
            }
        """

        when:
        succeeds(":a:resolve")

        then:
        result.assertTasksNotSkipped(":b:producer", ":c:producer", ":a:resolve")
        transformed("b-dir", "c-dir")
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        executer.withArguments("-PbOutputDir=out")
        succeeds(":a:resolve")

        then: // path has changed, but should be up to date
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")

        when:
        executer.withArguments("-PbOutputDir=out", "-PbDirName=b-blue")
        succeeds(":a:resolve")

        then: // name has changed, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        executer.withArguments("-PbOutputDir=out", "-PbDirName=b-blue", "-PbContent=new")
        succeeds(":a:resolve")

        then: // new content, should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        executer.withArguments("-PbOutputDir=out", "-PbDirName=b-blue", "-PbContent=new")
        succeeds(":a:resolve")

        then: // no change, should be up to date
        result.assertTasksNotSkipped(":a:resolve")
        transformed()
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        executer.withArguments("-PbOutputDir=out", "-PbDirName=b-blue", "-PbContent=new", "-PbName=new")
        succeeds(":a:resolve")

        then: // new content (renamed file), should run
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed("b-blue")
        outputContains("result = [b-blue.green, c-dir.green]")

        when:
        succeeds(":a:resolve")

        then: // have already seen these artifacts before
        result.assertTasksNotSkipped(":b:producer", ":a:resolve")
        transformed()
        outputContains("result = [b-dir.green, c-dir.green]")
    }

    def "can attach @PathSensitive(NONE) to input artifact property for external artifact"() {
        setupBuildWithColorTransformAction()
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
            
            abstract class MakeGreen implements TransformAction {
                @PathSensitive(PathSensitivity.NONE)
                @InputArtifact
                abstract File getInput()
                
                void transform(TransformOutputs outputs) {
                    println "processing \${input.name}"
                    def output = outputs.file(input.text + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        succeeds(":resolve")

        then:
        transformed("lib-1.0.jar", "lib2-1.0.jar")
        outputContains("result = [lib.green, lib2.green]")

        when:
        succeeds(":resolve")

        then: // no change, should be up-to-date
        transformed()
        outputContains("result = [lib.green, lib2.green]")

        when:
        succeeds(":resolve", "-PexternalCoords=group2:lib:1.0")

        then: // path change, should be up-to-date
        transformed()
        outputContains("result = [lib.green, lib2.green]")

        when:
        succeeds(":resolve", "-PexternalCoords=group2:lib:1.1")

        then: // name change, should be up-to-date
        transformed()
        outputContains("result = [lib.green, lib2.green]")

        when:
        lib1.artifactFile.text = "new-lib"
        succeeds(":resolve")

        then: // content change, should run
        transformed("lib-1.0.jar")
        outputContains("result = [new-lib.green, lib2.green]")

        when:
        lib4.artifactFile.text = "new-lib"
        succeeds(":resolve")

        then: // duplicate content
        transformed()
        outputContains("result = [new-lib.green]")
    }

    @Unroll
    def "can attach @PathSensitive(#sensitivity) to input artifact property for external artifact"() {
        setupBuildWithColorTransformAction()
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
            
            abstract class MakeGreen implements TransformAction {
                @PathSensitive(PathSensitivity.${sensitivity})
                @InputArtifact
                abstract File getInput()
                
                void transform(TransformOutputs outputs) {
                    println "processing \${input.name}"
                    def output = outputs.file(input.name + ".green")
                    output.text = input.text + ".green"
                }
            }
        """

        when:
        succeeds(":resolve")

        then:
        transformed("lib-1.0.jar", "lib2-1.0.jar")
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")

        when:
        succeeds(":resolve")

        then: // no change, should be up-to-date
        transformed()
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")

        when:
        succeeds(":resolve", "-PexternalCoords=group2:lib:1.0")

        then: // path change, should be up-to-date
        transformed()
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")

        when:
        succeeds(":resolve", "-PexternalCoords=group2:lib:1.1")

        then: // name change, should run
        transformed("lib-1.1.jar")
        outputContains("result = [lib-1.1.jar.green, lib2-1.0.jar.green]")

        when:
        lib1.artifactFile.text = "new-lib"
        succeeds(":resolve")

        then: // content change, should run
        transformed("lib-1.0.jar")
        outputContains("result = [lib-1.0.jar.green, lib2-1.0.jar.green]")

        where:
        sensitivity << [PathSensitivity.RELATIVE, PathSensitivity.NAME_ONLY]
    }

    void transformed(String... expected) {
        def actual = output.readLines().inject([]) { items, line ->
            def matcher = Pattern.compile("processing\\s+(.+)").matcher(line)
            if (matcher.find()) {
                items.add(matcher.group(1))
            }
            return items
        }
        assert actual.sort() == (expected as List).sort()
    }
}
