/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ArtifactDeclarationIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            def usage = Attribute.of('usage', String)
            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(usage)
                    }
                }
                configurations { compile { attributes.attribute(usage, 'for-compile') } }
            }
        """
    }

    def "artifact file may have no extension"() {
        settingsFile << "include 'a', 'b'"
        buildFile << """
            project(':a') {
                artifacts {
                    compile file("foo")
                    compile file("foo.txt")
                }
                task checkArtifacts {
                    doLast {
                        assert configurations.compile.artifacts.files.collect { it.name } == ["foo", "foo.txt"]
                        assert configurations.compile.artifacts.collect { it.file.name } == ["foo", "foo.txt"]
                        assert configurations.compile.artifacts.collect { "\$it.name:\$it.extension:\$it.type" } == ["foo::", "foo:txt:txt"]
                        assert configurations.compile.artifacts.collect { it.classifier } == [null, null]
                    }
                }
            }
            project(':b') {
                dependencies {
                    compile project(':a')
                }
                task checkArtifacts {
                    doLast {
                        assert configurations.compile.files.collect { it.name } == ["foo", "foo.txt"]
                        assert configurations.compile.files { true }.collect { it.name } == ["foo", "foo.txt"]
                        assert configurations.compile.fileCollection { true }.collect { it.name } == ["foo", "foo.txt"]
                        assert configurations.compile.incoming.artifacts.collect { it.file.name } == ["foo", "foo.txt"]
                        assert configurations.compile.resolvedConfiguration.files.collect { it.name } == ["foo", "foo.txt"]
                        assert configurations.compile.resolvedConfiguration.getFiles { true }.collect { it.name } == ["foo", "foo.txt"]
                        assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { it.file.name } == ["foo", "foo.txt"]
                        assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { "\$it.name:\$it.extension:\$it.type" } == ["foo::", "foo:txt:txt"]
                        assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { it.classifier } == [null, null]
                        assert configurations.compile.resolvedConfiguration.lenientConfiguration.files.collect { it.name } == ["foo", "foo.txt"]
                        assert configurations.compile.resolvedConfiguration.lenientConfiguration.getFiles { true }.collect { it.name } == ["foo", "foo.txt"]
                    }
                }
            }
        """

        expect:
        succeeds "checkArtifacts"
    }

    def "can define artifact using file and configure other properties using a map or closure or action"() {
        settingsFile << "include 'a', 'b'"
        buildFile << """
            project(':a') {
                artifacts {
                    compile file: file("a"), name: "thing-a", type: "report", extension: "txt", classifier: "report"
                    compile file("b"), {
                        name = "thing-b"
                        type = "report"
                        extension = "txt"
                        classifier = "report"
                    }
                    add('compile', file("c"), {
                        name = "thing-c"
                        type = "report"
                        extension = ""
                        classifier = "report"
                    })
                }
                task checkArtifacts {
                    doLast {
                        assert configurations.compile.artifacts.files.collect { it.name } == ["a", "b", "c"]
                        assert configurations.compile.artifacts.collect { it.file.name } == ["a", "b", "c"]
                        assert configurations.compile.artifacts.collect { "\$it.name:\$it.extension:\$it.type" } == ["thing-a:txt:report", "thing-b:txt:report", "thing-c::report"]
                        assert configurations.compile.artifacts.collect { it.classifier } == ["report", "report", "report"]
                    }
                }
            }
            project(':b') {
                dependencies {
                    compile project(':a')
                }
                task checkArtifacts {
                    doLast {
                        assert configurations.compile.incoming.artifacts.collect { it.file.name } == ["a", "b", "c"]
                        assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { it.file.name } == ["a", "b", "c"]
                        assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { "\$it.name:\$it.extension:\$it.type" } == ["thing-a:txt:report", "thing-b:txt:report", "thing-c::report"]
                        assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { it.classifier } == ["report", "report", "report"]
                    }
                }
            }
        """

        expect:
        succeeds("checkArtifacts")
    }

    def "can define outgoing artifacts for configuration"() {
        given:
        settingsFile << "include 'a', 'b'"
        buildFile << """
            project(':a') {
                configurations {
                    compile {
                        outgoing {
                            artifact file('lib1.jar')
                            artifact(file('lib2.zip')) {
                                name = 'not-a-lib'
                                type = 'not-a-lib'
                            }
                        }
                    }
                }
                task checkArtifacts {
                    doLast {
                        assert configurations.compile.artifacts.size() == 2
                    }
                }
            }
            project(':b') {
                dependencies {
                    compile project(':a')
                }
                task checkArtifacts {
                    doLast {
                        assert configurations.compile.incoming.artifacts.collect { it.file.name } == ["lib1.jar", "lib2.zip"]
                        assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { it.file.name } == ["lib1.jar", "lib2.zip"]
                    }
                }
            }
"""

        expect:
        succeeds("checkArtifacts")
    }

    def "can define outgoing variants and artifacts for configuration"() {
        given:
        buildFile << """
configurations {
    compile {
        attributes.attribute(usage, 'for compile')
        outgoing {
            artifact file('lib1.jar')
            variants {
                classes {
                    attributes.attribute(Attribute.of('format', String), 'classes-dir')
                    artifact file('classes')
                }
                jar {
                    attributes.attribute(Attribute.of('format', String), 'classes-jar')
                    artifact file('lib.jar')
                }
                sources {
                    attributes.attribute(Attribute.of('format', String), 'source-jar')
                    artifact file('source.zip')
                }
            }
        }
    }
}
task checkArtifacts {
    doLast {
        def classes = configurations.compile.outgoing.variants['classes']
        classes.attributes.keySet().collect { it.name } == ['usage', 'format']
    }
}
"""

        expect:
        succeeds("checkArtifacts")
    }

    def "can declare build dependency of artifact using String notation"() {
        given:
        settingsFile << "include 'a', 'b'"
        buildFile << """
            project(':a') {
                artifacts {
                   compile file:file('lib1.jar'), builtBy: 'jar'
                }
                task jar {}
            }
            
            project(':b') {
                dependencies {
                    compile project(':a')
                }
                task checkArtifacts {
                    inputs.files configurations.compile
                    doLast {
                        assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { 
                            it.buildDependencies.getDependencies(null) 
                        }*.path.flatten() == [':a:jar']
                    }
                }
            }
        """

        when:
        succeeds ':b:checkArtifacts'

        then:
        result.assertTasksExecuted(":a:jar", ":b:checkArtifacts")
    }

    def "can declare build dependency of outgoing artifact using String notation"() {
        given:
        settingsFile << "include 'a', 'b'"
        buildFile << """
            project(':a') {
                configurations {
                    compile {
                        outgoing {
                            artifact(file('lib1.jar')) {
                                builtBy 'jar'
                            }
                        }
                    }
                }
                task jar {}
            }
            
            project(':b') {
                dependencies {
                    compile project(':a')
                }
                task checkArtifacts {
                    inputs.files configurations.compile
                    doLast {
                        assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { 
                            it.buildDependencies.getDependencies(null) 
                        }*.path.flatten() == [':a:jar']
                    }
                }
            }
"""

        when:
        succeeds ':b:checkArtifacts'

        then:
        result.assertTasksExecuted(":a:jar", ":b:checkArtifacts")
    }

    def "can declare build dependency of outgoing variant artifact using String notation"() {
        given:
        settingsFile << "include 'a', 'b'"
        buildFile << """
            project(':a') {
                configurations {
                    compile {
                        outgoing {
                            variants {
                                classes {
                                    artifact(file('classes')) {
                                        builtBy 'classes'
                                    }
                                }
                            }
                        }
                    }
                }
                task classes {}
            }
            
            project(':b') {
                dependencies {
                    compile project(':a')
                }
                task checkArtifacts {
                    inputs.files configurations.compile
                    doLast {
                        assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { 
                            it.buildDependencies.getDependencies(null) 
                        }*.path.flatten() == [':a:classes']
                    }
                }
            }
"""

        when:
        succeeds ':b:checkArtifacts'

        then:
        result.assertTasksExecuted(":a:classes", ":b:checkArtifacts")
    }

    def "can define artifact using RegularFile task output"() {
        settingsFile << "include 'a', 'b'"
        buildFile << """
            project(':a') {
                task classes {
                    ext.outputFile = newOutputFile()
                    outputFile.set(layout.buildDirectory.file("a.jar"))
                }
                artifacts {
                    compile classes.outputFile
                }
            }
            project(':b') {
                dependencies {
                    compile project(':a')
                }
                task checkArtifacts {
                    inputs.files configurations.compile
                    doLast {
                        assert configurations.compile.incoming.artifacts.collect { it.file.name } == ["a.jar"]
                    }
                }
            }
        """

        when:
        succeeds ':b:checkArtifacts'

        then:
        result.assertTasksExecuted(":a:classes", ":b:checkArtifacts")
    }

    def "can define artifact using Directory task output"() {
        settingsFile << "include 'a', 'b'"
        buildFile << """
            project(':a') {
                task classes {
                    ext.outputDir = newOutputDirectory()
                    outputDir.set(layout.buildDirectory.dir("classes"))
                }
                artifacts {
                    compile classes.outputDir
                }
            }
            project(':b') {
                dependencies {
                    compile project(':a')
                }
                task checkArtifacts {
                    inputs.files configurations.compile
                    doLast {
                        assert configurations.compile.incoming.artifacts.collect { it.file.name } == ["classes"]
                    }
                }
            }
        """

        when:
        succeeds ':b:checkArtifacts'

        then:
        result.assertTasksExecuted(":a:classes", ":b:checkArtifacts")
    }

    // This isn't strictly supported and will be deprecated later
    def "can use a custom PublishArtifact implementation"() {
        given:
        settingsFile << "include 'a', 'b'"
        buildFile << """
            project(':a') {
                artifacts {
                    def artifact = new PublishArtifact() {
                        String name = "ignore-me"
                        String extension = "jar"
                        String type = "jar"
                        String classifier
                        File file
                        Date date
                        TaskDependency buildDependencies = new org.gradle.api.internal.tasks.DefaultTaskDependency()
                    }
                    artifact.file = file("lib1.jar")
                    task jar
                    compile(artifact) {
                        name = "thing"
                        builtBy jar
                    }
                }
                task checkArtifacts {
                    doLast {
                        assert configurations.compile.artifacts.collect { it.file.name }  == ["lib1.jar"]
                        assert configurations.compile.artifacts.collect { it.name }  == ["thing"]
                    }
                }
            }
            project(':b') {
                dependencies {
                    compile project(':a')
                }
                task checkArtifacts {
                    inputs.files configurations.compile
                    doLast {
                        assert configurations.compile.incoming.artifacts.collect { it.file.name } == ["lib1.jar"]
                        assert configurations.compile.incoming.artifacts.collect { it.id.displayName } == ["thing.jar (project :a)"]
                        assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { it.name } == ["thing"]
                    }
                }
            }
"""

        expect:
        succeeds("checkArtifacts")
        result.assertTasksExecutedInOrder(":a:checkArtifacts", ":a:jar", ":b:checkArtifacts")
    }

}
