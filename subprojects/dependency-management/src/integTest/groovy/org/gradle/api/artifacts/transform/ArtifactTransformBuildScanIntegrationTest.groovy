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

package org.gradle.api.artifacts.transform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ExperimentalIncrementalArtifactTransformationsRunner
import org.gradle.internal.scan.config.fixtures.BuildScanPluginFixture
import org.junit.runner.RunWith

import static org.gradle.integtests.fixtures.ExperimentalIncrementalArtifactTransformationsRunner.configureIncrementalArtifactTransformations

@RunWith(ExperimentalIncrementalArtifactTransformationsRunner)
class ArtifactTransformBuildScanIntegrationTest extends AbstractIntegrationSpec {
    def fixture = new BuildScanPluginFixture(testDirectory, mavenRepo, createExecuter())

    def setup() {
        settingsFile << fixture.pluginManagement() << """
            include 'lib'
            include 'util'
        """
        configureIncrementalArtifactTransformations(settingsFile)
        fixture.logConfig = true
        fixture.publishDummyBuildScanPluginNow()
    }

    def "transform works with build scan"() {
        given:
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
                dependencies {
                    registerTransform {
                        from.attribute(artifactType, "jar")
                        to.attribute(artifactType, "size")
                        artifactTransform(FileSizer)
                    }
                }
            }

            class FileSizer extends ArtifactTransform {
                List<File> transform(File input) {
                    File output = new File(outputDirectory, input.name + ".txt")
                    output.text = String.valueOf(input.length())
                    println "Transformed \$input.name to \$output.name into \$outputDirectory"
                    return [output]
                }
            }
            
            project(':lib') {
                apply plugin: 'base'
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
                task resolve {
                    def size = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts

                    inputs.files size.artifactFiles

                    doLast {
                        println "files: " + size.artifactFiles.collect { it.name }
                    }
                }
            }
        """

        when:
        run ":util:resolve", "--scan"

        then:
        output.contains "PUBLISHING BUILD SCAN"
    }

}
