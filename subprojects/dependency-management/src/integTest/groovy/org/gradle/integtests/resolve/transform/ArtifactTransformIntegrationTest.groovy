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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

class ArtifactTransformIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'app'
        """

        buildFile << """
allprojects {
    configurations {
        compile {
            attributes usage: 'api'
        }
    }
}

class FileSizer extends ArtifactTransform {
    private File output

    void configure(AttributeContainer from, AttributeTransformTargetRegistry targetRegistry) {
        from.attribute(Attribute.of('artifactType', String), "jar")
        targetRegistry.newTarget().attribute(Attribute.of('artifactType', String), "size")
    }

    File transform(File input, AttributeContainer target) {
        output = new File(outputDirectory, input.name + ".txt")
        if (!output.exists()) {
            println "Transforming \${input.name} to \${output.name}"
            output.text = String.valueOf(input.length())
        } else {
            println "Transforming \${input.name} to \${output.name} (cached)"
        }
        return output
    }
}

"""
    }

    def "applies transforms to artifacts for external dependencies"() {
        def m1 = mavenRepo.module("test", "test", "1.3").publish()
        m1.artifactFile.text = "1234"
        def m2 = mavenRepo.module("test", "test2", "2.3").publish()
        m2.artifactFile.text = "12"

        given:
        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                compile 'test:test:1.3'
                compile 'test:test2:2.3'
            }

            ${fileSizeConfigurationAndTransform()}
        """

        when:
        succeeds "resolve"

        then:
        file("build/libs").assertHasDescendants("test-1.3.jar.txt", "test2-2.3.jar.txt")
        file("build/libs/test-1.3.jar.txt").text == "4"
        file("build/libs/test2-2.3.jar.txt").text == "2"
        file("build/transformed").assertHasDescendants("test-1.3.jar.txt", "test2-2.3.jar.txt")
        file("build/transformed/test-1.3.jar.txt").text == "4"
        file("build/transformed/test2-2.3.jar.txt").text == "2"
    }

    def "applies transforms to files from file dependencies"() {
        when:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'
            def b = file('b.jar')
            b.text = '12'
            task jars

            dependencies {
                compile files([a, b]) { builtBy jars }
            }

            ${fileSizeConfigurationAndTransform()}
        """

        succeeds "resolve"

        then:
        result.assertTasksExecuted(":jars", ":resolve")

        and:
        file("build/libs").assertHasDescendants("a.jar.txt", "b.jar.txt")
        file("build/libs/a.jar.txt").text == "4"
        file("build/libs/b.jar.txt").text == "2"
        file("build/transformed").assertHasDescendants("a.jar.txt", "b.jar.txt")
        file("build/transformed/a.jar.txt").text == "4"
        file("build/transformed/b.jar.txt").text == "2"
    }

    def "applies transforms to artifacts from project dependencies"() {
        given:
        buildFile << """
            project(':lib') {
                task jar1(type: Jar) {
                    destinationDir = buildDir
                    archiveName = 'lib1.jar'
                }
                task jar2(type: Jar) {
                    destinationDir = buildDir
                    archiveName = 'lib2.jar'
                }

                artifacts {
                    compile jar1, jar2
                }
            }

            project(':app') {

                dependencies {
                    compile project(':lib')
                }

                ${fileSizeConfigurationAndTransform()}
            }
        """

        when:
        succeeds "resolve"

        then:
        result.assertTasksExecuted(":lib:jar1", ":lib:jar2", ":app:resolve")

        and:
        file("app/build/libs").assertHasDescendants("lib1.jar.txt", "lib2.jar.txt")
        file("app/build/libs/lib1.jar.txt").text == file("lib/build/lib1.jar").length() as String
        file("app/build/transformed").assertHasDescendants("lib1.jar.txt", "lib2.jar.txt")
        file("app/build/transformed/lib1.jar.txt").text == file("lib/build/lib1.jar").length() as String
    }

    def "does not apply transform to file with requested format"() {
        given:
        buildFile << """
            project(':lib') {
                projectDir.mkdirs()
                def file1 = file('lib1.size')
                file1.text = 'some text'
                def file2 = file('lib2.size')
                file2.text = 'some text'
                def jar1 = file('lib1.jar')
                jar1.text = 'some text'
                def jar2 = file('lib2.jar')
                jar2.text = 'some text'

                dependencies {
                    compile files(file1, jar1)
                }
                artifacts {
                    compile file2, jar2
                }
            }

            project(':app') {
                dependencies {
                    compile project(':lib')
                }
                ${fileSizeConfigurationAndTransform()}
            }
        """

        when:
        succeeds "resolve"

        then:
        file("app/build/libs").assertHasDescendants("lib1.jar.txt", "lib1.size", "lib2.jar.txt", "lib2.size")
        file("app/build/libs/lib1.jar.txt").text == "9"
        file("app/build/libs/lib1.size").text == "some text"
        file("app/build/transformed").assertHasDescendants("lib1.jar.txt", "lib2.jar.txt")
        file("app/build/transformed/lib1.jar.txt").text == "9"
    }

    def "result is applied for all query methods"() {
        given:
        buildFile << """
            project(':lib') {
                projectDir.mkdirs()
                def txt = file('lib.size')
                txt.text = 'some text'
                def jar = file('lib.jar')
                jar.text = 'some text'

                artifacts {
                    compile txt, jar
                }
            }

            project(':app') {
                dependencies {
                    compile project(':lib')
                }
                configurations {
                    compile {
                        attributes artifactType: 'size'
                        resolutionStrategy.registerTransform(FileSizer) {
                            outputDirectory = project.file("\${buildDir}/transformed")
                        }
                    }
                }
                task resolve {
                    doLast {
                        println "files 1: " + configurations.compile.collect { it.name }
                        println "files 2: " + configurations.compile.files.collect { it.name }
                        println "files 3: " + configurations.compile.incoming.files.collect { it.name }
                        println "files 4: " + configurations.compile.incoming.artifacts.collect { it.file.name }
                        println "files 5: " + configurations.compile.resolvedConfiguration.files.collect { it.name }
                        println "files 6: " + configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { it.file.name }
                        println "files 7: " + configurations.compile.resolvedConfiguration.lenientConfiguration.files.collect { it.name }
                        println "files 8: " + configurations.compile.resolvedConfiguration.lenientConfiguration.artifacts.collect { it.file.name }
                        println "files 9: " + configurations.compile.resolve().collect { it.name }
                        println "files 10: " + configurations.compile.files { true }.collect { it.name }
                        println "files 11: " + configurations.compile.fileCollection { true }.collect { it.name }
                        println "files 12: " + configurations.compile.resolvedConfiguration.getFiles { true }.collect { it.name }
                        println "files 13: " + configurations.compile.resolvedConfiguration.lenientConfiguration.getFiles { true }.collect { it.name }
                        println "files 14: " + configurations.compile.resolvedConfiguration.lenientConfiguration.getArtifacts { true }.collect { it.file.name }

                        println "artifacts 1: " + configurations.compile.incoming.artifacts.collect { it.id }
                        println "artifacts 2: " + configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { it.id }
                        println "artifacts 3: " + configurations.compile.resolvedConfiguration.lenientConfiguration.artifacts.collect { it.id }
                    }
                }
            }
        """

        when:
        succeeds "resolve"

        then:
        output.contains("files 1: [lib.size, lib.jar.txt]")
        output.contains("files 2: [lib.size, lib.jar.txt]")
        output.contains("files 3: [lib.size, lib.jar.txt]")
        output.contains("files 4: [lib.size, lib.jar.txt]")
        output.contains("files 5: [lib.size, lib.jar.txt]")
        output.contains("files 6: [lib.size, lib.jar.txt]")
        output.contains("files 7: [lib.size, lib.jar.txt]")
        output.contains("files 8: [lib.size, lib.jar.txt]")
        output.contains("files 9: [lib.size, lib.jar.txt]")
        output.contains("files 10: [lib.size, lib.jar.txt]")
        output.contains("files 11: [lib.size, lib.jar.txt]")
        output.contains("files 12: [lib.size, lib.jar.txt]")
        output.contains("files 13: [lib.size, lib.jar.txt]")
        output.contains("artifacts 1: [lib.size (project :lib), lib.jar (project :lib)]")
        output.contains("artifacts 2: [lib.size (project :lib), lib.jar (project :lib)]")
        output.contains("artifacts 3: [lib.size (project :lib), lib.jar (project :lib)]")

        file("app/build/transformed").assertHasDescendants("lib.jar.txt")
        file("app/build/transformed/lib.jar.txt").text == "9"
    }

    def "transformation is applied once only to each file"() {
        given:
        buildFile << """
            project(':lib') {
                projectDir.mkdirs()
                def jar1 = file('lib-1.jar')
                jar1.text = 'some text'
                def jar2 = file('lib-2.jar')
                jar2.text = 'some text'
                dependencies {
                    compile files(jar2)
                }
                artifacts {
                    compile jar1
                }
            }

            project(':app') {
                dependencies {
                    compile project(':lib')
                }
                configurations {
                    compile {
                        attributes artifactType: 'size'
                        resolutionStrategy.registerTransform(FileSizer) {
                            outputDirectory = project.file("\${buildDir}/transformed")
                        }
                    }
                }
                task resolve {
                    doLast {
                        // Query a bunch of times
                        println "files 1: " + configurations.compile.collect { it.name }
                        println "files 2: " + configurations.compile.files.collect { it.name }
                        println "files 3: " + configurations.compile.incoming.files.collect { it.name }
                        println "files 4: " + configurations.compile.resolvedConfiguration.files.collect { it.name }
                        println "files 5: " + configurations.compile.resolvedConfiguration.lenientConfiguration.files.collect { it.name }
                        println "files 6: " + configurations.compile.resolve().collect { it.name }
                        println "files 7: " + configurations.compile.files { true }.collect { it.name }
                        println "files 8: " + configurations.compile.fileCollection { true }.collect { it.name }
                        println "files 9: " + configurations.compile.incoming.artifacts.collect { it.file.name }
                        println "artifacts 1: " + configurations.compile.incoming.artifacts.collect { it.id }
                    }
                }
            }
        """

        when:
        succeeds "resolve"

        then:
        output.count("Transforming lib-1.jar to lib-1.jar.txt") == 1
        output.count("Transforming lib-2.jar to lib-2.jar.txt") == 1
    }

    def "files are lazily downloaded and transformed when using ResolvedArtifact methods"() {
        def m1 = mavenHttpRepo.module('org.test', 'test1', '1.0').publish()
        def m2 = mavenHttpRepo.module('org.test', 'test2', '2.0').publish()

        given:
        buildFile << """
            repositories {
                maven { url '${mavenHttpRepo.uri}' }
            }
            dependencies {
                compile 'org.test:test1:1.0'
                compile 'org.test:test2:2.0'
            }

            ${fileSizeConfigurationAndTransform()}

            def artifacts = configurations.compile.resolvedConfiguration.resolvedArtifacts as List
            artifacts[0].file

            task query {
                doLast {
                    artifacts[1].file
                }
            }
        """

        when:
        m1.pom.expectGet()
        m1.artifact.expectGet()
        m2.pom.expectGet()

        succeeds "help"

        then:
        output.count("Transforming") == 1

        when:
        server.resetExpectations()
        m2.artifact.expectGet()

        succeeds "query"

        then:
        output.count("Transforming") == 2
    }

    def "User gets a reasonable error message when a transformation throws exception"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            class TransformWithIllegalArgumentException extends ArtifactTransform {

                void configure(AttributeContainer from, AttributeTransformTargetRegistry targetRegistry) {
                    from.attribute(Attribute.of('artifactType', String), "jar")
                    targetRegistry.newTarget().attribute(Attribute.of('artifactType', String), "size")
                }

                File transform(File input, AttributeContainer target) {
                    throw new IllegalArgumentException("Transform Implementation Missing!")
                }
            }
            ${configurationAndTransform('TransformWithIllegalArgumentException')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasCause("Error while transforming 'a.jar' to match attributes '{artifactType=size}' using 'TransformWithIllegalArgumentException'")
        failure.assertHasCause("Transform Implementation Missing!")
    }

    def "User gets a reasonable error message when a output property returns null"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            class ToNullTransform extends ArtifactTransform {

                void configure(AttributeContainer from, AttributeTransformTargetRegistry targetRegistry) {
                    from.attribute(Attribute.of('artifactType', String), "jar")
                    targetRegistry.newTarget().attribute(Attribute.of('artifactType', String), "size")
                }

                File transform(File input, AttributeContainer target) {
                    return null
                }
            }
            ${configurationAndTransform('ToNullTransform')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasCause("Error while transforming 'a.jar' to match attributes '{artifactType=size}' using 'ToNullTransform'")
        failure.assertHasCause("No output file created")
    }

    def "User gets a reasonable error message when a output property returns a non-existing file"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            class ToNullTransform extends ArtifactTransform {

                void configure(AttributeContainer from, AttributeTransformTargetRegistry targetRegistry) {
                    from.attribute(Attribute.of('artifactType', String), "jar")
                    targetRegistry.newTarget().attribute(Attribute.of('artifactType', String), "size")
                }

                File transform(File input, AttributeContainer target) {
                    return new File('this_file_does_not.exist')
                }
            }
            ${configurationAndTransform('ToNullTransform')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasCause("Error while transforming 'a.jar' to match attributes '{artifactType=size}' using 'ToNullTransform'")
        failure.assertHasCause("Expected output file 'this_file_does_not.exist' was not created")
    }

    def configurationAndTransform(String transformImplementation) {
        """
            configurations {
                compile {
                    attributes artifactType: 'size'
                    resolutionStrategy.registerTransform($transformImplementation) {
                            outputDirectory = project.file("\${buildDir}/transformed")
                    }
                }
            }

            task resolve(type: Copy) {
                from configurations.compile
                into "\${buildDir}/libs"
            }
"""
    }

    def fileSizeConfigurationAndTransform() {
        configurationAndTransform('FileSizer')
    }
}
