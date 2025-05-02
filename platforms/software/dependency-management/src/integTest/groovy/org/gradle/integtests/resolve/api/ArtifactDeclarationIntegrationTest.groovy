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

package org.gradle.integtests.resolve.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture

class ArtifactDeclarationIntegrationTest extends AbstractIntegrationSpec {
    ResolveTestFixture resolve = new ResolveTestFixture(buildFile, "compile")

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
    }

    String getHeader() {
        """
            def usage = Attribute.of('usage', String)
            dependencies {
                attributesSchema {
                    attribute(usage)
                }
            }
            configurations { compile { attributes.attribute(usage, 'for-compile') } }
        """
    }

    def "artifact file may have no extension"() {
        settingsFile << "include 'a', 'b'"

        file("a/build.gradle") << """
            $header

            artifacts {
                compile file("foo")
                compile file("foo.txt")
            }
            assert configurations.compile.artifacts.files.collect { it.name } == ["foo", "foo.txt"]
            assert configurations.compile.artifacts.collect { it.file.name } == ["foo", "foo.txt"]
            assert configurations.compile.artifacts.collect { "\$it.name:\$it.extension:\$it.type" } == ["foo::", "foo:txt:txt"]
            assert configurations.compile.artifacts.collect { it.classifier } == [null, null]
        """

        file("b/build.gradle") << """
            $header

            dependencies {
                compile project(':a')
            }
        """

        resolve.prepare()

        expect:
        succeeds "b:checkDeps"
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                    artifact(name: 'foo', type: '')
                    artifact(name: 'foo', type: 'txt')
                }
            }
        }
    }

    def "can define artifact using file and configure other properties using a map or closure or action"() {
        settingsFile << "include 'a', 'b'"

        file("a/build.gradle") << """
            $header

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
            assert configurations.compile.artifacts.files.collect { it.name } == ["a", "b", "c"]
            assert configurations.compile.artifacts.collect { it.file.name } == ["a", "b", "c"]
            assert configurations.compile.artifacts.collect { "\$it.name:\$it.extension:\$it.type" } == ["thing-a:txt:report", "thing-b:txt:report", "thing-c::report"]
            assert configurations.compile.artifacts.collect { it.classifier } == ["report", "report", "report"]
        """

        file("b/build.gradle") << """
            $header

            dependencies {
                compile project(':a')
            }
        """

        resolve.prepare()

        expect:
        succeeds "b:checkDeps"
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                    artifact(name: 'thing-a', classifier: 'report', extension: 'txt', type: 'report', fileName: 'a')
                    artifact(name: 'thing-b', classifier: 'report', extension: 'txt', type: 'report', fileName: 'b')
                    artifact(name: 'thing-c', classifier: 'report', extension: '', type: 'report', fileName: 'c')
                }
            }
        }
    }

    def "can define outgoing artifacts for configuration"() {
        given:
        settingsFile << "include 'a', 'b'"

        file("a/build.gradle") << """
            $header

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
            assert configurations.compile.artifacts.size() == 2
        """

        file("b/build.gradle") << """
            $header

            dependencies {
                compile project(':a')
            }
        """

        resolve.prepare()

        expect:
        succeeds(":b:checkDeps")
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                    artifact(name: "lib1")
                    artifact(name: "not-a-lib", extension: "zip", type: "not-a-lib", fileName: "lib2.zip")
                }
            }
        }
    }

    def "can define outgoing variants and artifacts for configuration"() {
        given:
        buildFile << """
            $header

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
            def classes = configurations.compile.outgoing.variants['classes']
            classes.attributes.keySet().collect { it.name } == ['usage', 'format']
        """

        resolve.prepare()

        expect:
        succeeds()
    }

    def "can declare build dependency of artifact using String notation"() {
        given:
        settingsFile << "include 'a', 'b'"

        file("a/build.gradle") << """
            $header

            artifacts {
               compile file:file('lib1.jar'), builtBy: 'jar'
            }
            task jar {}
        """

        file("b/build.gradle") << """
            $header

            dependencies {
                compile project(':a')
            }
            task jar {} // ignored
        """

        resolve.prepare()

        when:
        succeeds ':b:checkDeps'

        then:
        result.assertTasksExecuted(":a:jar", ":b:checkDeps")
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                    artifact(name: "lib1")
                }
            }
        }
    }

    def "can declare build dependency of outgoing artifact using String notation"() {
        given:
        settingsFile << "include 'a', 'b'"

        file("a/build.gradle") << """
            $header

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
        """

        file("b/build.gradle") << """
            $header

            dependencies {
                compile project(':a')
            }
            task jar {} // ignored
        """

        resolve.prepare()

        when:
        succeeds ':b:checkDeps'

        then:
        result.assertTasksExecuted(":a:jar", ":b:checkDeps")
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                    artifact(name: "lib1")
                }
            }
        }
    }

    def "can declare build dependency of outgoing variant artifact using String notation"() {
        given:
        settingsFile << "include 'a', 'b'"

        file("a/build.gradle") << """
            $header

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
        """

        file("b/build.gradle") << """
            $header

            dependencies {
                compile project(':a')
            }
            task classes {} // ignored
        """

        resolve.prepare()

        when:
        succeeds ':b:checkDeps'

        then:
        result.assertTasksExecuted(":a:classes", ":b:checkDeps")
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                    artifact(name: "classes", type: "")
                }
            }
        }
    }

    def "can define artifact using File provider"() {
        settingsFile << "include 'a', 'b'"

        file("a/build.gradle") << """
            $header

            artifacts {
                def jar = file("a.jar")
                compile providers.provider { jar }
            }
        """

        file("b/build.gradle") << """
            $header

            dependencies {
                compile project(':a')
            }
        """

        resolve.prepare()

        when:
        succeeds ':b:checkDeps'

        then:
        result.assertTasksExecuted(":b:checkDeps")
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                }
            }
        }
    }

    def "can define artifact using RegularFile task output"() {
        settingsFile << "include 'a', 'b'"

        file("a/build.gradle") << """
            $header

            task classes {
                ext.outputFile = project.objects.fileProperty()
                outputs.file(outputFile)
                outputFile.set(layout.buildDirectory.file("a.jar"))
            }
            artifacts {
                compile classes.outputFile
            }
        """

        file("b/build.gradle") << """
            $header

            dependencies {
                compile project(':a')
            }
        """

        resolve.prepare()

        when:
        succeeds ':b:checkDeps'

        then:
        result.assertTasksExecuted(":a:classes", ":b:checkDeps")
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                }
            }
        }
    }

    def "can define artifact using Directory task output"() {
        settingsFile << "include 'a', 'b'"

        file("a/build.gradle") << """
            $header

            task classes {
                ext.outputDir = objects.directoryProperty()
                outputs.dir(outputDir)
                outputDir.set(layout.buildDirectory.dir("classes"))
            }
            artifacts {
                compile classes.outputDir
            }
        """

        file("b/build.gradle") << """
            $header

            dependencies {
                compile project(':a')
            }
        """

        resolve.prepare()

        when:
        succeeds ':b:checkDeps'

        then:
        result.assertTasksExecuted(":a:classes", ":b:checkDeps")
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                    artifact(name: "classes", type: "")
                }
            }
        }
    }

    def "can define artifact using RegularFile type"() {
        settingsFile << "include 'a', 'b'"

        file("a/build.gradle") << """
            $header

            artifacts {
                compile layout.projectDirectory.file('someFile.txt')
            }
        """

        file("b/build.gradle") << """
            $header

            dependencies {
                compile project(':a')
            }
        """

        resolve.prepare()

        expect:
        succeeds ':b:checkDeps'
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                    artifact(name: "someFile", type: "", extension: "txt")
                }
            }
        }
    }

    def "can define artifact using Directory type"() {
        settingsFile << "include 'a', 'b'"

        file("a/build.gradle") << """
            $header

            artifacts {
                compile layout.projectDirectory.dir('someDir')
            }
        """

        file("b/build.gradle") << """
            $header

            dependencies {
                compile project(':a')
            }
        """

        resolve.prepare()

        expect:
        succeeds ':b:checkDeps'
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                    artifact(name: "someDir", type: "")
                }
            }
        }
    }

    // This isn't strictly supported and will be deprecated later
    def "can use a custom PublishArtifact implementation"() {
        given:
        settingsFile << "include 'a', 'b'"

        file("a/build.gradle") << """
            $header

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
            assert configurations.compile.artifacts.collect { it.file.name }  == ["lib1.jar"]
            assert configurations.compile.artifacts.collect { it.name }  == ["thing"]
        """

        file("b/build.gradle") << """
            $header

            dependencies {
                compile project(':a')
            }
        """

        resolve.prepare()

        expect:
        succeeds("b:checkDeps")
        result.assertTasksExecutedInOrder(":a:jar", ":b:checkDeps")
        resolve.expectGraph {
            root(":b", "test:b:") {
                project(":a", "test:a:") {
                    artifact(name: "thing", fileName: "lib1.jar")
                }
            }
        }
    }

}
