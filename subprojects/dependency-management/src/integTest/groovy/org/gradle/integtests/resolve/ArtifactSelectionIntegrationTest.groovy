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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.junit.runner.RunWith

@RunWith(FluidDependenciesResolveRunner)
class ArtifactSelectionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'ui'
            include 'app'
        """

        buildFile << """
def artifactType = Attribute.of('artifactType', String)
def usage = Attribute.of('usage', String)
def buildType = Attribute.of('buildType', String)
def flavor = Attribute.of('flavor', String)

allprojects {
    repositories {
        ivy { url '${ivyHttpRepo.uri}' }
    }
    dependencies {
        attributesSchema {
           attribute(usage)
           attribute(buildType) { 
               compatibilityRules.assumeCompatibleWhenMissing()
           }
           attribute(flavor)
        }
    }
    configurations {
        compile {
            attributes { attribute(usage, 'api') }
        }
    }
    task utilJar {
        outputs.file("\${project.name}-util.jar")
    }
    task jar {
        outputs.file("\${project.name}.jar")
    }
    task utilClasses {
        outputs.file("\${project.name}-util.classes")
    }
    task classes {
        outputs.file("\${project.name}.classes")
    }
    task dir {
        outputs.file("\${project.name}")
    }
    task utilDir {
        outputs.file("\${project.name}-util")
    }
}
"""
    }

    def "selects artifacts and files whose format matches the requested"() {
        given:
        def m1 = ivyHttpRepo.module('org', 'test', '1.0')
                    .artifact(name: 'some-jar', type: 'jar')
                    .publish()
        def m2 = ivyHttpRepo.module('org', 'test2', '1.0')
                    .artifact(name: 'some-classes', type: 'classes')
                    .publish()

        buildFile << """
            project(':lib') {
                dependencies {
                    compile utilJar.outputs.files
                    compile utilClasses.outputs.files
                    compile utilDir.outputs.files
                    compile 'org:test:1.0'
                    compile 'org:test2:1.0'
                }
                configurations {
                    compile {
                        outgoing {
                            variants {
                                jarFormat {
                                    artifact file: file('lib.jar'), builtBy: tasks.jar
                                }
                                classesFormat {
                                    artifact file: file('lib.classes'), builtBy: tasks.classes
                                }
                                dirFormat {
                                    artifact file: file('lib'), builtBy: tasks.dir
                                }
                            }
                        }
                    }
                }
            }
            project(':ui') {
                artifacts {
                    compile file: file('ui.jar'), builtBy: jar
                }
            }

            project(':app') {
                def otherAttributeRequired = Attribute.of('otherAttributeRequired', String)
                def otherAttributeOptional = Attribute.of('otherAttributeOptional', String)

                dependencies {
                    compile project(':lib'), project(':ui')
                    
                    attributesSchema {
                        attribute(otherAttributeRequired)
                        attribute(otherAttributeOptional) {
                            compatibilityRules.assumeCompatibleWhenMissing()
                        }
                    }
                }

                task resolve {
                    inputs.files configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'jar') }.files
                    doLast {
                        // Get a view specifying the default type
                        assert configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'jar') }.files.collect { it.name } == ['lib-util.jar', 'lib.jar', 'ui.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'jar') }.artifacts.collect { it.id.displayName }  == ['lib-util.jar', 'lib.jar (project :lib)', 'ui.jar (project :ui)', 'some-jar.jar (org:test:1.0)']

                        // Get a view with additional optional attribute
                        assert configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'jar'); it.attribute(otherAttributeOptional, 'anything') }.files.collect { it.name } == ['lib-util.jar', 'lib.jar', 'ui.jar', 'some-jar-1.0.jar']
                        assert configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'jar'); it.attribute(otherAttributeOptional, 'anything') }.artifacts.collect { it.id.displayName }  == ['lib-util.jar', 'lib.jar (project :lib)', 'ui.jar (project :ui)', 'some-jar.jar (org:test:1.0)']
                    
                        // Get a view with additional required attribute
                        assert configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'jar'); it.attribute(otherAttributeRequired, 'anything') }.files.collect { it.name } == []
                        assert configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'jar'); it.attribute(otherAttributeRequired, 'anything') }.artifacts.collect { it.id.displayName }  == []
                    }
                }
            }
        """

        m1.ivy.expectGet()
        m2.ivy.expectGet()
        m1.getArtifact(name: 'some-jar', type: 'jar').expectGet()

        expect:
        succeeds "resolve"
        // Currently builds all file dependencies
        result.assertTasksExecuted(":lib:jar", ":lib:utilClasses", ":lib:utilDir", ":lib:utilJar", ":ui:jar", ":app:resolve")
    }

    def "can create a view that selects different artifacts from the same dependency graph"() {
        given:
        def m1 = ivyHttpRepo.module('org', 'test', '1.0')
                    .artifact(name: 'some-jar', type: 'jar')
                    .publish()
        def m2 = ivyHttpRepo.module('org', 'test2', '1.0')
                    .artifact(name: 'some-classes', type: 'classes')
                    .publish()

        buildFile << """
            project(':lib') {
                dependencies {
                    compile utilJar.outputs.files
                    compile utilClasses.outputs.files
                    compile utilDir.outputs.files
                    compile 'org:test:1.0'
                    compile 'org:test2:1.0'
                }
                configurations {
                    compile {
                        outgoing {
                            variants {
                                jarFormat {
                                    artifact file: file('lib.jar'), builtBy: tasks.jar
                                }
                                classesFormat {
                                    artifact file: file('lib.classes'), builtBy: tasks.classes
                                }
                                dirFormat {
                                    artifact file: file('lib'), builtBy: tasks.dir
                                }
                            }
                        }
                    }
                }
            }
            project(':ui') {
                artifacts {
                    compile file: file('ui.classes'), builtBy: classes
                }
            }

            project(':app') {
                configurations {
                    compile {
                        attributes { attribute(artifactType, 'jar') }
                    }
                }

                dependencies {
                    compile project(':lib'), project(':ui')
                }

                task resolve {
                    def view = configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'classes') }
                    inputs.files view.files
                    doLast {
                        assert view.files.collect { it.name } == ['lib-util.classes', 'lib.classes', 'ui.classes', 'some-classes-1.0.classes']
                        assert view.artifacts.collect { it.id.displayName } == ['lib-util.classes', 'lib.classes (project :lib)', 'ui.classes (project :ui)', 'some-classes.classes (org:test2:1.0)']
                    }
                }
            }
        """

        m1.ivy.expectGet()
        m2.ivy.expectGet()
        m2.getArtifact(name: 'some-classes', type: 'classes').expectGet()

        expect:
        succeeds "resolve"
        // Currently builds all file dependencies
        result.assertTasksExecuted(":lib:classes", ":lib:utilClasses", ":lib:utilDir", ":lib:utilJar", ":ui:classes", ":app:resolve")
    }

    def "applies compatibility and disambiguation rules when selecting variant"() {
        buildFile << """

allprojects {
    configurations.compile.attributes.attribute(usage, 'compile')
    dependencies.attributesSchema {
        attribute(buildType) {
            compatibilityRules.add { details -> if (details.consumerValue == "debug" && details.producerValue == "profile") { details.compatible() } }
        }
        attribute(flavor) {
            disambiguationRules.add { details -> if (details.candidateValues.contains('tasty')) { details.closestMatch('tasty') } }
        }
    }
}

dependencies {
    compile project(':lib')
}

project(':lib') {
    configurations {
        compile {
            outgoing {
                variants {
                    var1 {
                        artifact file('a1.jar')
                        attributes.attribute(buildType, 'profile')
                        attributes.attribute(flavor, 'bland')
                    }
                    var2 {
                        artifact file('a2.jar')
                        attributes.attribute(buildType, 'profile')
                        attributes.attribute(flavor, 'tasty')
                    }
                    var3 {
                        artifact file('a3.jar')
                        attributes.attribute(buildType, 'debug')
                        attributes.attribute(flavor, 'bland')
                    }
                }
            }
        }
    }
}

task show {
    inputs.files configurations.compile
    doLast {
        def artifacts = configurations.compile.incoming.artifactView().attributes {
            it.attribute(buildType, 'debug')
        }.artifacts
        println "files: " + artifacts.collect { it.file.name }
        println "variants: " + artifacts.collect { it.variant.attributes }
    }
}
"""
        when:
        run 'show'

        then:
        outputContains("files: [a2.jar]")
        outputContains("variants: [{artifactType=jar, buildType=profile, flavor=tasty, usage=compile}]")
    }

    def "can select the implicit variant of a configuration"() {
        buildFile << """

allprojects {
    configurations.compile.attributes.attribute(usage, 'compile')
    dependencies.attributesSchema {
        attribute(buildType) {
            compatibilityRules.assumeCompatibleWhenMissing()
        }
    }
}

dependencies {
    compile project(':lib')
    compile project(':ui')
}

project(':lib') {
    configurations {
        legacy
        compile.extendsFrom legacy
    }
    artifacts {
        legacy file('a1.jar')
    }
    configurations.compile.outgoing {
        artifact file('a2.jar')
        attributes.attribute(buildType, 'debug')
        variants {
            var1 {
                artifact file('ignore-me.jar')
                attributes.attribute(buildType, 'release')
            }
            var2 {
                artifact file('ignore-me.jar')
                attributes.attribute(buildType, 'profile')
            }
        }
    }
}
project(':ui') {
    configurations {
        legacy
        compile.extendsFrom legacy
    }
    artifacts {
        legacy file('b1.jar')
        compile file('b2.jar')
    }
}

task show {
    inputs.files configurations.compile
    doLast {
        def artifacts = configurations.compile.incoming.artifactView().attributes {
            it.attribute(buildType, 'debug')
        }.artifacts
        println "files: " + artifacts.collect { it.file.name }
        println "variants: " + artifacts.collect { it.variant.attributes }
    }
}
"""
        when:
        run 'show'

        then:
        outputContains("files: [a2.jar, a1.jar, b2.jar, b1.jar]")
        outputContains("variants: [{artifactType=jar, buildType=debug, usage=compile}, {artifactType=jar, buildType=debug, usage=compile}, {artifactType=jar, usage=api}, {artifactType=jar, usage=api}]")
    }

    def "result includes consumer-provided variants"() {
        def m1 = ivyHttpRepo.module("org", "test", "1.0").publish()

        buildFile << """

class VariantArtifactTransform extends ArtifactTransform {
    List<File> transform(File input) {
        def output = new File(outputDirectory, "transformed-" + input.name)
        output << "transformed"
        return [output]         
    }
}

allprojects {
    configurations.compile.attributes.attribute(usage, 'compile')
}

dependencies {
    compile files('test-lib.jar')
    compile project(':lib')
    compile project(':ui')
    compile 'org:test:1.0'
    registerTransform {
        to.attribute(Attribute.of('usage', String), "transformed")
        artifactTransform(VariantArtifactTransform)
    }
}

project(':lib') {
    configurations {
        compile {
            attributes.attribute(buildType, 'debug')
            outgoing {
                variants {
                    var1 {
                        artifact file('a1.jar')
                        attributes.attribute(flavor, 'one')
                    }
                }
            }
        }
    }
}

project(':ui') {
    artifacts {
        compile file('b2.jar')
    }
}

task show {
    inputs.files configurations.compile
    doLast {
        def artifacts = configurations.compile.incoming.artifactView().attributes({it.attribute(Attribute.of('usage', String), 'transformed')}).artifacts
        println "files: " + artifacts.collect { it.file.name }
        println "components: " + artifacts.collect { it.id.componentIdentifier.displayName }
        println "variants: " + artifacts.collect { it.variant.attributes }
    }
}
"""

        when:
        m1.ivy.expectGet()
        m1.jar.expectGet()
        run 'show'

        then:
        outputContains("files: [transformed-test-lib.jar, transformed-a1.jar, transformed-b2.jar, transformed-test-1.0.jar]")
        outputContains("components: [test-lib.jar, project :lib, project :ui, org:test:1.0]")
        outputContains("variants: [{artifactType=jar, usage=transformed}, {artifactType=jar, buildType=debug, flavor=one, usage=transformed}, {artifactType=jar, usage=transformed}, {artifactType=jar, usage=transformed}]")
    }

    def "can query the content of view before task graph is calculated"() {
        given:
        def m1 = ivyHttpRepo.module('org', 'test', '1.0')
                    .artifact(name: 'some-jar', type: 'jar')
                    .publish()
        def m2 = ivyHttpRepo.module('org', 'test2', '1.0')
                    .artifact(name: 'some-classes', type: 'classes')
                    .publish()

        buildFile << """
            project(':lib') {
                dependencies {
                    compile utilJar.outputs.files
                    compile utilClasses.outputs.files
                    compile utilDir.outputs.files
                    compile 'org:test:1.0'
                    compile 'org:test2:1.0'
                }
                configurations {
                    compile {
                        outgoing {
                            variants {
                                jarFormat {
                                    artifact file: file('lib.jar'), builtBy: tasks.jar
                                }
                                classesFormat {
                                    artifact file: file('lib.classes'), builtBy: tasks.classes
                                }
                                dirFormat {
                                    artifact file: file('lib'), builtBy: tasks.dir
                                }
                            }
                        }
                    }
                }
            }
            project(':ui') {
                artifacts {
                    compile file: file('ui.classes'), builtBy: classes
                }
            }

            project(':app') {
                configurations {
                    compile {
                        attributes { attribute(artifactType, 'jar') }
                    }
                }

                dependencies {
                    compile project(':lib'), project(':ui')
                }

                task resolve {
                    def files = configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'classes') }.files
                    files.each { println it.name }
                    inputs.files files
                    doLast {
                        assert files.collect { it.name } == ['lib-util.classes', 'lib.classes', 'ui.classes', 'some-classes-1.0.classes']
                    }
                }
            }
        """

        m1.ivy.expectGet()
        m2.ivy.expectGet()
        m2.getArtifact(name: 'some-classes', type: 'classes').expectGet()

        expect:
        succeeds "resolve"
        // Currently builds all file dependencies
        result.assertTasksExecuted(":lib:classes", ":lib:utilClasses", ":lib:utilDir", ":lib:utilJar", ":ui:classes", ":app:resolve")
    }

    def "can create a view for configuration that has no attributes"() {
        given:
        buildFile << """
            project(':lib') {
                configurations {
                    compile {
                        outgoing {
                            variants {
                                jarFormat {
                                    artifact file: file('lib.jar'), builtBy: tasks.jar
                                }
                                classesFormat {
                                    artifact file: file('lib.classes'), builtBy: tasks.classes
                                }
                                dirFormat {
                                    artifact file: file('lib'), builtBy: tasks.dir
                                }
                            }
                        }
                    }
                }
            }

            project(':app') {
                configurations {
                    noAttributes
                }

                dependencies {
                    noAttributes project(path: ':lib', configuration: 'compile')
                }

                task resolve {
                    def files = configurations.noAttributes.incoming.artifactView().attributes { it.attribute(artifactType, 'classes') }.files
                    inputs.files files
                    doLast {
                        assert files.collect { it.name } == ['lib.classes']
                    }
                }
            }
        """

        expect:
        succeeds "resolve"
        result.assertTasksExecuted(":lib:classes", ":app:resolve")
    }
}
