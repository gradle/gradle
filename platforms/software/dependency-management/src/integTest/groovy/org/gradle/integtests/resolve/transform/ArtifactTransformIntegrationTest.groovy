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
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.internal.component.ResolutionFailureHandler
import org.gradle.internal.file.FileType
import org.gradle.operations.dependencies.transforms.ExecutePlannedTransformStepBuildOperationType
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.hamcrest.Matcher
import spock.lang.Issue

import static org.gradle.util.Matchers.matchesRegexp

class ArtifactTransformIntegrationTest extends AbstractHttpDependencyResolutionTest implements ArtifactTransformTestFixture {
    def setup() {
        createDirs("lib", "app")
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'app'
        """

        buildFile << """
            import org.gradle.api.artifacts.transform.TransformParameters

            def usage = Attribute.of('usage', String)
            def artifactType = Attribute.of('artifactType', String)
            def extraAttribute = Attribute.of('extra', String)

            allprojects {

                dependencies {
                    attributesSchema {
                        attribute(usage)
                    }
                }
                configurations {
                    compile {
                        attributes { attribute usage, 'api' }
                    }
                }
            }

            $fileSizer
        """

        file("gradle.properties").text = "${ResolutionFailureHandler.FULL_FAILURES_MESSAGE_PROPERTY}=true"
    }

    private static String getFileSizer() {
        """
            import org.gradle.api.artifacts.transform.InputArtifact
            import org.gradle.api.artifacts.transform.TransformAction
            import org.gradle.api.artifacts.transform.TransformOutputs
            import org.gradle.api.artifacts.transform.TransformParameters
            import org.gradle.api.file.FileSystemLocation
            import org.gradle.api.provider.Provider

            abstract class FileSizer implements TransformAction<TransformParameters.None> {
                FileSizer() {
                    println "Creating FileSizer"
                }

                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    def output = outputs.file(input.name + ".txt")
                    assert output.parentFile.directory && output.parentFile.list().length == 0
                    println "Transforming \${input.name} to \${output.name}"
                    output.text = String.valueOf(input.length())
                }
            }
        """
    }

    def "applies transforms to artifacts for external dependencies matching on implicit format attribute"() {
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

            ${configurationAndTransform('FileSizer')}
        """

        when:
        run "resolve"

        then:
        outputContains("variants: [{artifactType=size, org.gradle.status=release}, {artifactType=size, org.gradle.status=release}]")
        outputContains("capabilities: [[capability group='test', name='test', version='1.3'], [capability group='test', name='test2', version='2.3']]")
        // transformed outputs should belong to same component as original
        outputContains("ids: [test-1.3.jar.txt (test:test:1.3), test2-2.3.jar.txt (test:test2:2.3)]")
        outputContains("components: [test:test:1.3, test:test2:2.3]")
        file("build/libs").assertHasDescendants("test-1.3.jar.txt", "test2-2.3.jar.txt")
        file("build/libs/test-1.3.jar.txt").text == "4"
        file("build/libs/test2-2.3.jar.txt").text == "2"

        and:
        output.count("Transforming") == 2
        output.count("Transforming test-1.3.jar to test-1.3.jar.txt") == 1
        output.count("Transforming test2-2.3.jar to test2-2.3.jar.txt") == 1

        when:
        run "resolve"

        then:
        output.count("Transforming") == 0
    }

    def "can use transformations in build script dependencies"() {
        file("buildSrc/src/main/groovy/FileSizer.groovy") << fileSizer

        file("script-with-buildscript-block.gradle") << """
            buildscript {

                def artifactType = Attribute.of('artifactType', String)
                dependencies {

                    registerTransform(FileSizer) {
                        from.attribute(artifactType, 'jar')
                        to.attribute(artifactType, 'size')
                    }

                    classpath 'org.apache.commons:commons-math3:3.6.1'
                }
                ${mavenCentralRepository()}
                println(
                    configurations.classpath.incoming.artifactView {
                            attributes.attribute(artifactType, "size")
                        }.files.files
                )
                println(
                    configurations.classpath.incoming.artifactView {
                            attributes.attribute(artifactType, "size")
                        }.files.files
                )
            }
        """

        buildFile << """
            apply from: 'script-with-buildscript-block.gradle'
        """

        expect:
        succeeds("help", "--info")
        output.count("Creating FileSizer") == 1
        output.count("Transforming commons-math3-3.6.1.jar to commons-math3-3.6.1.jar.txt") == 1
    }

    def "applies transforms to files from file dependencies matching on implicit format attribute"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'
            def b = file('b.jar')
            b.text = '12'
            task jars

            dependencies {
                compile files([a, b]) { builtBy jars }
            }

            ${configurationAndTransform('FileSizer')}
        """

        when:
        run "resolve"

        then:
        executed(":jars", ":resolve")

        and:
        outputContains("variants: [{artifactType=size}, {artifactType=size}]")
        outputContains("capabilities: [[], []]")
        // transformed outputs should belong to same component as original
        outputContains("ids: [a.jar.txt (a.jar), b.jar.txt (b.jar)]")
        outputContains("components: [a.jar, b.jar]")
        file("build/libs").assertHasDescendants("a.jar.txt", "b.jar.txt")
        file("build/libs/a.jar.txt").text == "4"
        file("build/libs/b.jar.txt").text == "2"

        and:
        output.count("Transforming") == 2
        output.count("Transforming a.jar to a.jar.txt") == 1
        output.count("Transforming b.jar to b.jar.txt") == 1

        when:
        run "resolve"

        then:
        executed(":jars", ":resolve")

        and:
        output.count("Transforming") == 0
    }

    def "applies transforms to artifacts from local projects matching on implicit format attribute"() {
        given:
        buildFile << """
            project(':lib') {
                task jar1(type: Jar) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib1.jar'
                }
                task jar2(type: Jar) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib2.jar'
                }

                artifacts {
                    compile jar1, jar2
                }
            }

            project(':app') {

                dependencies {
                    compile project(':lib')
                }

                ${configurationAndTransform('FileSizer')}
            }
        """

        when:
        run "resolve"

        then:
        executed(":lib:jar1", ":lib:jar2", ":app:resolve")

        and:
        outputContains("variants: [{artifactType=size, usage=api}, {artifactType=size, usage=api}]")
        outputContains("capabilities: [[capability group='root', name='lib', version='unspecified'], [capability group='root', name='lib', version='unspecified']]")
        // transformed outputs should belong to same component as original
        outputContains("ids: [lib1.jar.txt (project :lib), lib2.jar.txt (project :lib)]")
        outputContains("components: [project :lib, project :lib]")
        file("app/build/libs").assertHasDescendants("lib1.jar.txt", "lib2.jar.txt")
        file("app/build/libs/lib1.jar.txt").text == file("lib/build/lib1.jar").length() as String

        and:
        output.count("Transforming") == 2
        output.count("Transforming lib1.jar to lib1.jar.txt") == 1
        output.count("Transforming lib2.jar to lib2.jar.txt") == 1

        when:
        run "resolve"

        then:
        executed(":lib:jar1", ":lib:jar2", ":app:resolve")

        and:
        output.count("Transforming") == 0
    }

    def "can map artifact extension to implicit attributes"() {
        given:
        createDirs("app2")
        settingsFile << """
            include 'app2'
        """
        taskTypeWithOutputFileProperty()
        taskTypeLogsArtifactCollectionDetails()
        buildFile << """
            def contents = Attribute.of('contents', String)

            project(':lib') {
                task blueThing(type: FileProducer) {
                    output = layout.buildDir.file('lib.blue')
                }

                artifacts {
                    compile blueThing.output
                }
            }

            project(':app') {

                dependencies {
                    compile project(':lib')
                }

                dependencies {
                    artifactTypes {
                        blue {
                            attributes.attribute(contents, 'size')
                        }
                    }
                }

                task resolve(type: ShowArtifactCollection) {
                    collection = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(contents, 'size') }
                    }.artifacts
                }
            }
            project(':app2') {

                dependencies {
                    compile project(':lib')
                }

                dependencies {
                    artifactTypes {
                        blue {
                            // Intentionally use different attributes in app2 vs app's artifactType registry
                            attributes.attribute(contents, 'bin')
                        }
                    }
                    registerTransform(FileSizer) {
                        from.attribute(contents, 'bin')
                        to.attribute(contents, 'size')
                    }
                }

                task resolve(type: ShowArtifactCollection) {
                    collection = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(contents, 'size') }
                    }.artifacts
                }
            }
        """

        when:
        run "resolve"

        then:
        executed(":lib:blueThing", ":app:resolve", ":app2:resolve")

        and:
        def appOutput = result.groupedOutput.task(':app:resolve')
        appOutput.assertOutputContains("variants = [{artifactType=blue, contents=size, usage=api}]")
        appOutput.assertOutputContains("components = [project :lib]")
        appOutput.assertOutputContains("artifacts = [lib.blue (project :lib)]")
        appOutput.assertOutputContains("files = [lib.blue]")

        def app2Output = result.groupedOutput.task(':app2:resolve')
        app2Output.assertOutputContains("variants = [{artifactType=blue, contents=size, usage=api}]")
        app2Output.assertOutputContains("components = [project :lib]")
        app2Output.assertOutputContains("artifacts = [lib.blue.txt (project :lib)]")
        app2Output.assertOutputContains("files = [lib.blue.txt]")

        and:
        output.count("Transforming") == 1
        output.count("Transforming lib.blue to lib.blue.txt") == 1

        when:
        run "resolve"

        then:
        executed(":lib:blueThing", ":app:resolve", ":app2:resolve")

        and:
        output.count("Transforming") == 0
    }

    def "applies transforms to artifacts from local projects, files and external dependencies"() {
        def dependency = mavenRepo.module("test", "test-dependency", "1.3").publish()
        dependency.artifactFile.text = "dependency"
        def binaryDependency = mavenRepo.module("test", "test", "1.3").dependsOn(dependency).publish()
        binaryDependency.artifactFile.text = "1234"

        settingsFile << """
            include 'common'
        """

        given:
        buildFile << """
            allprojects {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
            }

            project(':common') {
                task jar(type: Jar) {
                    destinationDirectory = buildDir
                    archiveFileName = 'common.jar'
                }
                artifacts {
                    compile jar
                    compile file("common-file.jar")
                }
            }

            project(':lib') {
                task jar1(type: Jar) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib1.jar'
                }
                task jar2(type: Jar) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib2.jar'
                }

                dependencies {
                    compile "${binaryDependency.groupId}:${binaryDependency.artifactId}:${binaryDependency.version}"
                    compile project(":common")
                    compile files("file1.jar")
                }

                artifacts {
                    compile jar1, jar2
                }
            }

            project(':app') {

                dependencies {
                    compile project(':lib')
                }

                ${configurationAndTransform('FileSizer')}
            }
        """
        file("lib/file1.jar").text = "first"
        file("common/common-file.jar").text = "first"

        when:
        run "resolve"

        then:
        executed(":common:jar", ":lib:jar1", ":lib:jar2", ":app:resolve")

        and:
        outputContains("variants: [{artifactType=size, usage=api}, {artifactType=size, usage=api}, {artifactType=size}, {artifactType=size, org.gradle.status=release}, {artifactType=size, usage=api}, {artifactType=size, usage=api}, {artifactType=size, org.gradle.status=release}]")
        outputContains("capabilities: [[capability group='root', name='lib', version='unspecified'], [capability group='root', name='lib', version='unspecified'], [], [capability group='test', name='test', version='1.3'], [capability group='root', name='common', version='unspecified'], [capability group='root', name='common', version='unspecified'], [capability group='test', name='test-dependency', version='1.3']]")
        // transformed outputs should belong to same component as original
        outputContains("ids: [lib1.jar.txt (project :lib), lib2.jar.txt (project :lib), file1.jar.txt (file1.jar), test-1.3.jar.txt (test:test:1.3), common.jar.txt (project :common), common-file.jar.txt (project :common), test-dependency-1.3.jar.txt (test:test-dependency:1.3)]")
        outputContains("components: [project :lib, project :lib, file1.jar, test:test:1.3, project :common, project :common, test:test-dependency:1.3]")
        file("app/build/libs").assertHasDescendants("common.jar.txt", "common-file.jar.txt", "file1.jar.txt", "lib1.jar.txt", "lib2.jar.txt", "test-1.3.jar.txt", "test-dependency-1.3.jar.txt")
        file("app/build/libs/lib1.jar.txt").text == file("lib/build/lib1.jar").length() as String

        and:
        output.count("Transforming") == 7
    }

    def "applies transforms to artifacts from local projects matching on explicit format attribute"() {
        given:
        buildFile << """
            project(':lib') {
                task jar1(type: Jar) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib1.jar'
                }
                task zip1(type: Zip) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib2.zip'
                }

                configurations {
                    compile.outgoing.variants {
                        files {
                            attributes.attribute(Attribute.of('artifactType', String), 'jar')
                            artifact jar1
                            artifact zip1
                        }
                    }
                }
            }

            project(':app') {

                dependencies {
                    compile project(':lib')
                }

                ${configurationAndTransform('FileSizer')}
            }
        """

        when:
        run "resolve"

        then:
        executed(":lib:jar1", ":lib:zip1", ":app:resolve")

        and:
        outputContains("variants: [{artifactType=size, usage=api}, {artifactType=size, usage=api}]")
        file("app/build/libs").assertHasDescendants("lib1.jar.txt", "lib2.zip.txt")
        file("app/build/libs/lib1.jar.txt").text == file("lib/build/lib1.jar").length() as String

        and:
        output.count("Transforming") == 2
        output.count("Transforming lib1.jar to lib1.jar.txt") == 1
        output.count("Transforming lib2.zip to lib2.zip.txt") == 1

        when:
        run "resolve"

        then:
        output.count("Transforming") == 0
    }

    def "does not apply transform to variants with requested implicit format attribute"() {
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

                dependencies {
                    compile files(file1, jar1)
                }
                artifacts {
                    compile file2
                }
            }

            project(':app') {
                dependencies {
                    compile project(':lib')
                }
                ${configurationAndTransform('FileSizer')}
            }
        """

        when:
        run "resolve"

        then:
        outputContains("variants: [{artifactType=size, usage=api}, {artifactType=size}, {artifactType=size}]")
        outputContains("ids: [lib2.size (project :lib), lib1.size, lib1.jar.txt (lib1.jar)]")
        outputContains("components: [project :lib, lib1.size, lib1.jar]")
        file("app/build/libs").assertHasDescendants("lib1.jar.txt", "lib1.size", "lib2.size")
        file("app/build/libs/lib1.jar.txt").text == "9"
        file("app/build/libs/lib1.size").text == "some text"

        and:
        output.count("Transforming") == 1

        when:
        run "resolve"

        then:
        output.count("Transforming") == 0
    }

    def "does not apply transforms to artifacts from local projects matching requested format attribute"() {
        given:
        buildFile << """
            project(':lib') {
                task jar1(type: Jar) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib1.jar'
                }
                task jar2(type: Jar) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib2.zip'
                }

                configurations {
                    compile.outgoing.variants {
                        files {
                            attributes.attribute(Attribute.of('artifactType', String), 'size')
                            artifact jar1
                            artifact jar2
                        }
                    }
                }
            }

            project(':app') {

                dependencies {
                    compile project(':lib')
                }

                ${configurationAndTransform('FileSizer')}
            }
        """

        when:
        run "resolve"

        then:
        executed(":lib:jar1", ":lib:jar2", ":app:resolve")

        and:
        outputContains("variants: [{artifactType=size, usage=api}, {artifactType=size, usage=api}]")
        outputContains("ids: [lib1.jar (project :lib), lib2.zip.jar (project :lib)]")
        outputContains("components: [project :lib, project :lib]")
        file("app/build/libs").assertHasDescendants("lib1.jar", "lib2.zip")

        and:
        output.count("Transforming") == 0
    }

    def "applies transforms to artifacts from local projects matching on some variant attributes"() {
        given:
        buildFile << """
            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(Attribute.of('javaVersion', String))
                        attribute(Attribute.of('color', String))
                    }
                }
            }

            project(':lib') {
                task jar1(type: Jar) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib1.jar'
                }
                task jar2(type: Zip) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib2.jar'
                }

                configurations {
                    compile.outgoing.variants {
                        java7 {
                            attributes.attribute(Attribute.of('javaVersion', String), '7')
                            attributes.attribute(Attribute.of('color', String), 'green')
                            artifact jar1
                        }
                        java8 {
                            attributes.attribute(Attribute.of('javaVersion', String), '8')
                            attributes.attribute(Attribute.of('color', String), 'red')
                            artifact jar2
                        }
                    }
                }
            }

            project(':app') {

                dependencies {
                    compile project(':lib')

                    registerTransform(MakeRedThings) {
                        from.attribute(Attribute.of('color', String), "green")
                        to.attribute(Attribute.of('color', String), "red")
                    }
                }

                task resolve(type: Copy) {
                    def artifacts = configurations.compile.incoming.artifactView {
                        attributes {
                            it.attribute(artifactType, 'jar')
                            it.attribute(Attribute.of('javaVersion', String), '7')
                            it.attribute(Attribute.of('color', String), 'red')
                        }
                    }.artifacts
                    from artifacts.artifactFiles
                    into "\${buildDir}/libs"
                    doLast {
                        println "files: " + artifacts.collect { it.file.name }
                        println "variants: " + artifacts.collect { it.variant.attributes }
                    }
                }
            }

            abstract class MakeRedThings implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    def output = outputs.file(input.name + ".red")
                    assert output.parentFile.directory && output.parentFile.list().length == 0
                    println "Transforming \${input.name} to \${output.name}"
                    output.text = String.valueOf(input.length())
                }
            }
        """

        when:
        run "resolve"

        then:
        executed(":lib:jar1", ":app:resolve")

        and:
        outputContains("variants: [{artifactType=jar, color=red, javaVersion=7, usage=api}]")
        file("app/build/libs").assertHasDescendants("lib1.jar.red")

        and:
        output.count("Transforming") == 1
        output.count("Transforming lib1.jar to lib1.jar.red") == 1

        when:
        run "resolve"

        then:
        executed(":lib:jar1", ":app:resolve")

        and:
        output.count("Transforming") == 0
    }

    def "applies chain of transforms to artifacts from local projects matching on some variant attributes"() {
        given:
        buildFile << """
            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(Attribute.of('javaVersion', String))
                        attribute(Attribute.of('color', String))
                    }
                }
            }

            project(':lib') {
                task jar1(type: Jar) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib1.jar'
                }
                task jar2(type: Zip) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib2.jar'
                }

                configurations {
                    compile.outgoing.variants {
                        java7 {
                            attributes.attribute(Attribute.of('javaVersion', String), '7')
                            attributes.attribute(Attribute.of('color', String), 'green')
                            artifact jar1
                        }
                        java8 {
                            attributes.attribute(Attribute.of('javaVersion', String), '8')
                            attributes.attribute(Attribute.of('color', String), 'red')
                            artifact jar2
                        }
                    }
                }
            }

            project(':app') {

                dependencies {
                    compile project(':lib')

                    registerTransform(MakeBlueToRedThings) {
                        from.attribute(Attribute.of('color', String), "blue")
                        to.attribute(Attribute.of('color', String), "red")
                    }
                    registerTransform(MakeGreenToBlueThings) {
                        from.attribute(Attribute.of('color', String), "green")
                        to.attribute(Attribute.of('color', String), "blue")
                    }
                }

                task resolve(type: Copy) {
                    def artifacts = configurations.compile.incoming.artifactView {
                        attributes {
                            it.attribute(artifactType, 'jar')
                            it.attribute(Attribute.of('javaVersion', String), '7')
                            it.attribute(Attribute.of('color', String), 'red')
                        }
                    }.artifacts
                    from artifacts.artifactFiles
                    into "\${buildDir}/libs"
                    doLast {
                        println "files: " + artifacts.collect { it.file.name }
                        println "variants: " + artifacts.collect { it.variant.attributes }
                        println "ids: " + artifacts.collect { it.id }
                        println "components: " + artifacts.collect { it.id.componentIdentifier }
                    }
                }
            }

            abstract class MakeGreenToBlueThings implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    def output = outputs.file(input.name + ".blue")
                    assert output.parentFile.directory && output.parentFile.list().length == 0
                    println "Transforming \${input.name} to \${output.name}"
                    println "Input exists: \${input.exists()}"
                    output.text = String.valueOf(input.length())
                }
            }

            abstract class MakeBlueToRedThings implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    def output = outputs.file(input.name + ".red")
                    assert output.parentFile.directory && output.parentFile.list().length == 0
                    println "Transforming \${input.name} to \${output.name}"
                    println "Input exists: \${input.exists()}"
                    output.text = String.valueOf(input.length())
                }
            }
        """

        when:
        run "resolve"

        then:
        executed(":lib:jar1", ":app:resolve")

        and:
        outputContains("variants: [{artifactType=jar, color=red, javaVersion=7, usage=api}]")
        // Should belong to same component as the originals
        outputContains("ids: [lib1.jar.blue.red (project :lib)]")
        outputContains("components: [project :lib]")
        file("app/build/libs").assertHasDescendants("lib1.jar.blue.red")

        and:
        output.count("Transforming") == 2
        output.count("Transforming lib1.jar to lib1.jar.blue") == 1
        output.count("Transforming lib1.jar.blue to lib1.jar.blue.red") == 1

        when:
        run "resolve"

        then:
        executed(":lib:jar1", ":app:resolve")

        and:
        output.count("Transforming") == 0
    }

    def "transforms can be applied to multiple files with the same name"() {
        given:
        buildFile << """
            def f = file("lib.jar")
            f.text = "1234"

            dependencies {
                compile files(f)
                compile project(':lib')
            }
            project(':lib') {
                def f2 = file("lib.jar")
                f2.parentFile.mkdirs()
                f2.text = "123"
                artifacts { compile f2 }
            }

            dependencies {
                registerTransform(FileSizer) {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'size')
                }
            }

            task resolve {
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts
                inputs.files artifacts.artifactFiles
                doLast {
                    println "files: " + artifacts.collect { it.file.name }
                    println "ids: " + artifacts.collect { it.id }
                    println "components: " + artifacts.collect { it.id.componentIdentifier }
                    println "variants: " + artifacts.collect { it.variant.attributes }
                    println "content: " + artifacts.collect { it.file.text }
                }
            }
        """

        when:
        run "resolve"

        then:
        outputContains("variants: [{artifactType=size}, {artifactType=size, usage=api}]")
        // transformed outputs should belong to same component as original
        outputContains("ids: [lib.jar.txt (lib.jar), lib.jar.txt (project :lib)]")
        outputContains("components: [lib.jar, project :lib]")
        outputContains("files: [lib.jar.txt, lib.jar.txt]")
        outputContains("content: [4, 3]")

        and:
        output.count("Transforming") == 2
        output.count("Transforming lib.jar to lib.jar.txt") == 2

        when:
        run "resolve"

        then:
        output.count("Transforming") == 0
    }

    def "transform can register the input as an output"() {
        buildFile << """
            def f = file("lib.jar")
            f.text = "1234"

            dependencies {
                compile files(f)
            }

            dependencies {
                registerTransform(IdentityTransform) {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'identity')
                }
            }

            abstract class IdentityTransform implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInput()

                void transform(TransformOutputs outputs) {
                    println("Transforming")
                    outputs.file(input)
                }
            }

            task resolve {
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'identity') }
                }.artifacts
                inputs.files artifacts.artifactFiles
                doLast {
                    println "files: " + artifacts.collect { it.file.name }
                }
            }
        """

        when:
        run "resolve"

        then:
        output.count("Transforming") == 1
        output.contains("files: [lib.jar]")
    }

    @Issue("https://github.com/gradle/gradle/issues/16962")
    def "transforms registering the input as an output can use normalization"() {
        file("input1.jar").text = "jar"
        file("input2.jar").text = "jar"
        buildFile("""
            configurations {
                api1 {
                    attributes { attribute usage, 'api' }
                }
                api2 {
                    attributes { attribute usage, 'api' }
                }
            }

            abstract class IdentityTransform implements TransformAction<TransformParameters.None> {
                @Classpath
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    println "Selecting input artifact \${inputArtifact.get().asFile}"
                    outputs.file(inputArtifact)
                }
            }

            dependencies {
                registerTransform(IdentityTransform) {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'transformed')
                }
            }

            task producer1(type: Jar)
            task producer2(type: Jar)
            tasks.withType(Jar).configureEach {
                destinationDirectory = layout.buildDirectory.dir("produced")
                archiveBaseName = name
            }

            ["api1", "api2"].each { conf ->
                tasks.register("resolve\$conf", Copy) {
                    duplicatesStrategy = 'INCLUDE'
                    def artifacts = configurations."\$conf".incoming.artifactView {
                        attributes { it.attribute(artifactType, 'transformed') }
                    }.artifacts
                    from artifacts.artifactFiles
                    into "\${buildDir}/libs1"
                    doLast {
                        println "files: " + artifacts.collect { it.file.name }
                        println "ids: " + artifacts.collect { it.id }
                        println "components: " + artifacts.collect { it.id.componentIdentifier }
                        println "variants: " + artifacts.collect { it.variant.attributes }
                    }
                }
            }

            dependencies {
                api1 files(producer1)
                api2 files(producer2)
            }
        """)

        when:
        run "resolveapi1", "resolveapi2"
        then:
        executedAndNotSkipped(":resolveapi1", ":resolveapi2")
        outputContains("ids: [producer1.jar (producer1.jar)]")
        outputContains("ids: [producer2.jar (producer2.jar)]")
    }

    @Issue("https://github.com/gradle/gradle/issues/22735")
    def "transform selection prioritizes shorter transforms over attribute schema matching"() {

        // The lib project defines two variants:
        // primary:
        // - artifactType=jar
        // - extraAttribute=preferred
        // secondary:
        // - artifactType=intermediate

        // We make a request with the artifactType=final attribute

        // Given the following transforms:
        // A) FileSizer from artifactType=jar to artifactType=intermediate
        // B) FileSizer from artifactType=intermediate to artifactType=final

        // We could potentially construct the following transform chains:
        // 1) primary -> A -> B -> requested
        //  - artifactType=final
        //  - extraAttribute=preferred
        // 2) secondary -> B -> requested
        //  - artifactType=final

        // Both of these potential transforms are compatible, except (1) has a favorable attribute set
        // with respect to the attribute schema. However, we want to choose (2) since it is the shorter chain.

        buildFile << """
            project(':lib') {
                task jar(type: Jar) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib.jar'
                }

                configurations.compile.outgoing.variants{
                    primary {
                        attributes {
                            attribute(artifactType, "jar")
                            attribute(extraAttribute, "preferred")
                        }
                        artifact jar
                    }
                    secondary {
                        attributes {
                            attribute(artifactType, "intermediate")
                        }
                        artifact jar
                    }
                }
            }

            // Provides a default value of `preferred` if a given attribute is not requested.
            abstract class DefaultingDisambiguationRule implements AttributeDisambiguationRule<String>, org.gradle.api.internal.ReusableAction {
                @Inject
                protected abstract ObjectFactory getObjectFactory()
                @Override
                void execute(MultipleCandidatesDetails<String> details) {
                    String consumerValue = details.getConsumerValue()
                    Set<String> candidateValues = details.getCandidateValues()
                    if (consumerValue == null) {
                        // Default to preferred
                        if (candidateValues.contains("preferred")) {
                            details.closestMatch("preferred")
                        }
                        // Otherwise, use the selected value
                    } else if (candidateValues.contains(consumerValue)) {
                        details.closestMatch(consumerValue)
                    }
                }
            }

            project(':app') {
                dependencies {
                    compile project(':lib')

                    attributesSchema {
                        attribute(extraAttribute).disambiguationRules.add(DefaultingDisambiguationRule)
                    }

                    registerTransform(FileSizer) { reg ->
                        reg.getFrom().attribute(artifactType, "jar")
                        reg.getTo().attribute(artifactType, "intermediate")
                    }

                    registerTransform(FileSizer) { reg ->
                        reg.getFrom().attribute(artifactType, "intermediate")
                        reg.getTo().attribute(artifactType, "final")
                    }
                }

                task resolve {
                    def artifactFiles = configurations.compile.incoming.artifactView { config ->
                        config.attributes {
                            attribute(artifactType, "final")
                        }
                    }.files
                    inputs.files(artifactFiles)
                    doLast {
                        println "files: " + artifactFiles.files.collect { it.name }
                    }
                }
            }
        """

        when:
        succeeds ":app:resolve"

        then:
        output.count("Creating FileSizer") == 1
        output.count("Transforming") == 1
        outputContains("files: [lib.jar.txt]")
    }

    def "transform can generate multiple output files for a single input"() {
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

            ${configurationAndTransform('LineSplitter')}

            abstract class LineSplitter implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    File outputA = outputs.file(input.name + ".A.txt")
                    assert outputA.parentFile.directory && outputA.parentFile.list().length == 0
                    outputA.text = "Output A"

                    File outputB = outputs.file(input.name + ".B.txt")
                    outputB.text = "Output B"
                }
            }
"""

        when:
        succeeds "resolve"

        then:
        outputContains("variants: [{artifactType=size, org.gradle.status=release}, {artifactType=size, org.gradle.status=release}, {artifactType=size, org.gradle.status=release}, {artifactType=size, org.gradle.status=release}]")
        outputContains("ids: [test-1.3.jar.A.txt (test:test:1.3), test-1.3.jar.B.txt (test:test:1.3), test2-2.3.jar.A.txt (test:test2:2.3), test2-2.3.jar.B.txt (test:test2:2.3)]")
        outputContains("components: [test:test:1.3, test:test:1.3, test:test2:2.3, test:test2:2.3]")
        file("build/libs").assertHasDescendants("test-1.3.jar.A.txt", "test-1.3.jar.B.txt", "test2-2.3.jar.A.txt", "test2-2.3.jar.B.txt")
        file("build/libs").eachFile {
            assert it.text =~ /Output \w/
        }
    }

    def "transform can generate an empty output"() {
        mavenRepo.module("test", "test", "1.3").publish()
        mavenRepo.module("test", "test2", "2.3").publish()

        given:
        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                compile 'test:test:1.3'
                compile 'test:test2:2.3'
            }

            ${configurationAndTransform('EmptyOutput')}

            abstract class EmptyOutput implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    println "Transforming \${inputArtifact.get().asFile.name}"
                }
            }
"""

        when:
        run "resolve"

        then:
        output.count("Transforming") == 2
        output.count("Transforming test-1.3.jar") == 1
        output.count("Transforming test2-2.3.jar") == 1
        file("build/libs").assertDoesNotExist()

        when:
        run "resolve"

        then:
        file("build/libs").assertDoesNotExist()

        and:
        output.count("Transforming") == 0
    }

    def "user receives reasonable error message when multiple transforms are available to produce requested variant"() {
        given:
        buildFile << """
            project(':lib') {
                task jar1(type: Jar) {
                    destinationDirectory = buildDir
                    archiveBaseName = 'a'
                    archiveExtension = 'custom'
                }

                artifacts {
                    compile(jar1)
                }
            }

            project(':app') {
                dependencies {
                    compile project(':lib')
                }

                dependencies {
                    registerTransform(BrokenTransform) {
                        from.attribute(artifactType, 'custom')
                        to.attribute(artifactType, 'transformed')
                        from.attribute(extraAttribute, 'foo')
                        to.attribute(extraAttribute, 'bar')
                    }
                    registerTransform(BrokenTransform) {
                        from.attribute(artifactType, 'custom')
                        to.attribute(artifactType, 'transformed')
                        from.attribute(extraAttribute, 'foo')
                        to.attribute(extraAttribute, 'baz')
                    }
                }

                task resolve(type: Copy) {
                    def artifacts = configurations.compile.incoming.artifactView {
                        attributes { it.attribute (artifactType, 'transformed') }
                    }.artifacts
                    from artifacts.artifactFiles
                    into "\${buildDir}/libs"
                }
            }

            abstract class BrokenTransform implements TransformAction<TransformParameters.None> {
                void transform(TransformOutputs outputs) {
                    throw new AssertionError("should not be used")
                }
            }
        """

        when:
        fails "resolve"

        then:
        failure.assertHasCause """Found multiple transforms that can produce a variant of project :lib with requested attributes:
  - artifactType 'transformed'
  - usage 'api'
Found the following transforms:
  - From 'configuration ':lib:compile'':
      - With source attributes:
          - artifactType 'custom'
          - usage 'api'
      - Candidate transform(s):
          - Transform 'BrokenTransform' producing attributes:
              - artifactType 'transformed'
              - extra 'bar'
              - usage 'api'
          - Transform 'BrokenTransform' producing attributes:
              - artifactType 'transformed'
              - extra 'baz'
              - usage 'api'"""
    }

    def "user receives reasonable error message when multiple variants can be transformed to produce requested variant"() {
        given:
        buildFile << """
            def buildType = Attribute.of("buildType", String)
            def flavor = Attribute.of("flavor", String)
            allprojects {
                dependencies.attributesSchema.attribute(buildType)
                dependencies.attributesSchema.attribute(flavor)
            }

            project(':lib') {
                task jar1(type: Jar) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib1.jar'
                }

                configurations {
                    compile.outgoing.variants {
                        variant1 {
                            attributes.attribute(buildType, 'release')
                            attributes.attribute(flavor, 'free')
                            artifact jar1
                        }
                        variant2 {
                            attributes.attribute(buildType, 'release')
                            attributes.attribute(flavor, 'paid')
                            artifact jar1
                        }
                        variant3 {
                            attributes.attribute(buildType, 'debug')
                            attributes.attribute(flavor, 'free')
                            artifact jar1
                        }
                    }
                }
            }

            project(':app') {
                dependencies {
                    compile project(':lib')
                }

                dependencies {
                    registerTransform(BrokenTransform) {
                        from.attribute(artifactType, 'jar')
                        from.attribute(buildType, 'release')
                        to.attribute(artifactType, 'transformed')
                    }
                    registerTransform(BrokenTransform) {
                        from.attribute(artifactType, 'jar')
                        from.attribute(buildType, 'debug')
                        to.attribute(artifactType, 'transformed')
                    }
                }

                task resolve(type: Copy) {
                    def artifacts = configurations.compile.incoming.artifactView {
                        attributes {
                            attribute(artifactType, 'transformed')
                        }
                    }.artifacts
                    from artifacts.artifactFiles
                    into "\${buildDir}/libs"
                }
            }

            abstract class BrokenTransform implements TransformAction<TransformParameters.None> {
                void transform(TransformOutputs outputs) {
                    throw new AssertionError("should not be used")
                }
            }
        """

        when:
        fails "resolve"

        then:
        failure.assertHasCause """Found multiple transforms that can produce a variant of project :lib with requested attributes:
  - artifactType 'transformed'
  - usage 'api'
Found the following transforms:
  - From 'configuration ':lib:compile' variant variant1':
      - With source attributes:
          - artifactType 'jar'
          - buildType 'release'
          - flavor 'free'
          - usage 'api'
      - Candidate transform(s):
          - Transform 'BrokenTransform' producing attributes:
              - artifactType 'transformed'
              - buildType 'release'
              - flavor 'free'
              - usage 'api'
  - From 'configuration ':lib:compile' variant variant2':
      - With source attributes:
          - artifactType 'jar'
          - buildType 'release'
          - flavor 'paid'
          - usage 'api'
      - Candidate transform(s):
          - Transform 'BrokenTransform' producing attributes:
              - artifactType 'transformed'
              - buildType 'release'
              - flavor 'paid'
              - usage 'api'
  - From 'configuration ':lib:compile' variant variant3':
      - With source attributes:
          - artifactType 'jar'
          - buildType 'debug'
          - flavor 'free'
          - usage 'api'
      - Candidate transform(s):
          - Transform 'BrokenTransform' producing attributes:
              - artifactType 'transformed'
              - buildType 'debug'
              - flavor 'free'
              - usage 'api'"""
    }

    def "result is applied for all query methods"() {
        def fixture = new ResolveTestFixture(buildFile, "compile")

        given:
        buildFile << """
            project(':lib') {
                projectDir.mkdirs()
                def jar = file('lib.jar')
                jar.text = 'some text'

                artifacts { compile jar }
            }

            project(':app') {
                dependencies {
                    compile project(':lib')
                }
                configurations {
                    compile {
                        attributes.attribute(artifactType, 'size')
                    }
                }
                dependencies {
                    registerTransform(FileSizer) {
                        from.attribute(artifactType, "jar")
                        to.attribute(artifactType, "size")
                    }
                }
            }
        """
        fixture.expectDefaultConfiguration("compile")
        fixture.prepare()

        expect:
        succeeds ":app:checkDeps"
        fixture.expectGraph {
            root(":app", "root:app:") {
                project(":lib", "root:lib:") {
                    artifact(name: "lib.jar", type: "txt")
                }
            }
        }
    }

    def "transforms are applied lazily in file collections"() {
        def m1 = mavenHttpRepo.module('org.test', 'test1', '1.0').publish()
        def m2 = mavenHttpRepo.module('org.test', 'test2', '2.0').publish()

        given:
        buildFile << """
            repositories {
                maven { url '${mavenHttpRepo.uri}' }
            }
            configurations {
                config1 {
                    attributes { attribute(artifactType, 'size') }
                }
                config2
            }
            dependencies {
                config1 'org.test:test1:1.0'
                config2 'org.test:test2:2.0'
            }

            ${configurationAndTransform('FileSizer')}

            def configFiles = configurations.config1.incoming.files
            def configView = configurations.config2.incoming.artifactView {
                attributes { it.attribute(artifactType, 'size') }
            }.files

            task queryFiles {
                doLast {
                    println configFiles.collect { it.name }
                }
            }

            task queryView {
                doLast {
                    println configView.collect { it.name }
                }
            }
        """

        when:
        succeeds "help"

        then:
        output.count("Transforming") == 0
        output.count("Creating") == 0

        when:
        server.resetExpectations()
        m1.pom.expectGet()
        m1.artifact.expectGet()

        succeeds "queryFiles"

        then:
        output.count("Creating FileSizer") == 1
        output.count("Transforming") == 1
        output.count("Transforming test1-1.0.jar to test1-1.0.jar.txt") == 1

        when:
        server.resetExpectations()
        m2.pom.expectGet()
        m2.artifact.expectGet()

        succeeds "queryView"

        then:
        output.count("Creating FileSizer") == 1
        output.count("Transforming") == 1
        output.count("Transforming test2-2.0.jar to test2-2.0.jar.txt") == 1

        when:
        server.resetExpectations()

        succeeds "queryView"

        then:
        output.count("Creating FileSizer") == 0
        output.count("Transforming") == 0
    }

    @ToBeFixedForConfigurationCache(because = "task that uses file collection containing transforms but does not declare this as an input may be encoded before the transform nodes it references")
    def "transforms are created as required and a new instance created for each file"() {
        given:
        buildFile << """
            dependencies {
                compile project(':lib')
            }
            project(':lib') {
                task jar1(type: Jar) { archiveFileName = 'jar1.jar' }
                task jar2(type: Jar) { archiveFileName = 'jar2.jar' }
                tasks.withType(Jar) { destinationDirectory = buildDir }
                artifacts { compile jar1, jar2 }
            }

            abstract class Hasher implements TransformAction<TransformParameters.None> {
                private int count

                Hasher() {
                    println "Creating Transform"
                }

                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    def output = outputs.file(input.name + ".txt")
                    assert output.parentFile.directory && output.parentFile.list().length == 0
                    count++
                    println "Transforming \${input.name} to \${output.name} with count \${count}"
                    output.text = String.valueOf(count)
                }
            }

            ${configurationAndTransform('Hasher')}

            def configFiles = configurations.compile.incoming.files
            def configView = configurations.compile.incoming.artifactView {
                attributes { it.attribute(artifactType, 'size') }
            }.files

            task queryFiles {
                doLast {
                    println "files: " + configFiles.collect { it.name }
                }
            }

            task queryView {
                doLast {
                    println "files: " + configView.collect { it.name }
                }
            }
        """

        when:
        succeeds "help"

        then:
        output.count("Transforming") == 0
        output.count("Creating Transform") == 0

        when:
        succeeds "queryFiles"

        then:
        output.count("Transforming") == 0
        output.count("Creating Transform") == 0
        outputContains("files: [jar1.jar, jar2.jar]")

        when:
        succeeds "queryView"

        then:
        output.count("Creating Transform") == 2
        output.count("Transforming") == 2
        output.count("Transforming jar1.jar to jar1.jar.txt with count 1") == 1
        output.count("Transforming jar2.jar to jar2.jar.txt with count 1") == 1
        outputContains("files: [jar1.jar.txt, jar2.jar.txt]")

        when:
        succeeds "queryView"

        then:
        output.count("Creating Transform") == 0
        output.count("Transforming") == 0
        outputContains("files: [jar1.jar.txt, jar2.jar.txt]")
    }

    def "user gets a reasonable error message when a transform throws exception and continues with other inputs"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a << '1234'
            def b = file('b.jar')
            b << '321'

            dependencies {
                compile files(a, b)
            }

            abstract class TransformWithIllegalArgumentException implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    if (input.name == 'a.jar') {
                        throw new IllegalArgumentException("broken")
                    }
                    println "Transforming " + input.name
                    outputs.file(input)
                }
            }
            ${configurationAndTransform('TransformWithIllegalArgumentException')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Failed to transform a.jar to match attributes {artifactType=size}")
        failure.assertHasCause("broken")

        and:
        outputContains("Transforming b.jar")

        when:
        executer.withArgument("-Plenient=true")
        succeeds("resolve")

        then:
        outputContains("files: [b.jar]")
    }

    @ToBeFixedForConfigurationCache(because = "treating file collection visit failures as a configuration cache problem adds an additional failure to the build summary; exception chain is different when transform input cannot be resolved")
    def "user gets a reasonable error message when a transform input cannot be downloaded and proceeds with other inputs"() {
        def m1 = ivyHttpRepo.module("test", "test", "1.3")
            .artifact(type: 'jar', name: 'test-api')
            .artifact(type: 'jar', name: 'test-impl')
            .artifact(type: 'jar', name: 'test-impl2')
            .publish()
        def m2 = ivyHttpRepo.module("test", "test-2", "0.1")
            .publish()

        given:
        buildFile << """
            ${configurationAndTransform('FileSizer')}

            repositories {
                ivy { url "${ivyHttpRepo.uri}" }
            }

            dependencies {
                compile "test:test:1.3"
                compile "test:test-2:0.1"
            }
        """

        when:
        m1.ivy.expectGet()
        m1.getArtifact(name: 'test-api').expectGet()
        m1.getArtifact(name: 'test-impl').expectGetBroken()
        m1.getArtifact(name: 'test-impl2').expectGet()
        m2.ivy.expectGet()
        m2.jar.expectGet()

        fails "resolve"

        then:
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Could not download test-impl-1.3.jar (test:test:1.3)")

        and:
        outputContains("Transforming test-api-1.3.jar to test-api-1.3.jar.txt")
        outputContains("Transforming test-impl2-1.3.jar to test-impl2-1.3.jar.txt")
        outputContains("Transforming test-2-0.1.jar to test-2-0.1.jar.txt")

        when:
        m1.getArtifact(name: 'test-impl').expectGetBroken()

        executer.withArguments("-Plenient=true")
        succeeds("resolve")

        then:
        outputContains("files: [test-api-1.3.jar.txt, test-impl2-1.3.jar.txt, test-2-0.1.jar.txt]")
    }

    @ToBeFixedForConfigurationCache(because = "treating file collection visit failures as a configuration cache problem adds an additional failure to the build summary; exception chain is different when transform input cannot be resolved")
    def "user gets a reasonable error message when file dependency cannot be listed and continues with other inputs"() {
        given:
        buildFile << """
            ${configurationAndTransform('FileSizer')}

            def broken = false
            gradle.taskGraph.whenReady { broken = true }

            dependencies {
                compile files('thing1.jar')
                compile files { if (broken) { throw new RuntimeException("broken") }; [] }
                compile files('thing2.jar')
            }
        """

        when:
        fails "resolve"

        then:
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("broken")

        and:
        outputContains("Transforming thing1.jar to thing1.jar.txt")
        outputContains("Transforming thing2.jar to thing2.jar.txt")

        when:
        executer.withArguments("-Plenient=true")
        succeeds("resolve")

        then:
        outputContains("files: [thing1.jar.txt, thing2.jar.txt]")
    }

    def "user gets a reasonable error message when null is registered via outputs.#method"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            abstract class ToNullTransform implements TransformAction<TransformParameters.None> {
                void transform(TransformOutputs outputs) {
                    outputs.${method}(null)
                }
            }
            ${configurationAndTransform('ToNullTransform')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Failed to transform a.jar to match attributes {artifactType=size}")
        failure.assertHasCause("Execution failed for ToNullTransform: ${file("a.jar").absolutePath}.")
        failure.assertHasCause("path may not be null or empty string. path='null'")

        where:
        method << ['dir', 'file']
    }

    def "user gets a reasonable error message when transform returns a non-existing file"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            abstract class NoExistTransform implements TransformAction<TransformParameters.None> {
                void transform(TransformOutputs outputs) {
                    outputs.file('this_file_does_not.exist')
                }
            }
            ${configurationAndTransform('NoExistTransform')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Failed to transform a.jar to match attributes {artifactType=size}")
        failure.assertHasCause("Transform output this_file_does_not.exist must exist.")

        when:
        executer.withArguments("-Plenient=true")
        succeeds("resolve")

        then:
        outputContains(":resolve NO-SOURCE")
    }

    def "user gets a reasonable error message when transform registers a #type output via #method"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            abstract class FailingTransform implements TransformAction<TransformParameters.None> {
                void transform(TransformOutputs outputs) {
                    ${
            switch (type) {
                case FileType.Missing:
                    return """
                                outputs.${method}('this_file_does_not.exist').delete()

                            """
                case FileType.Directory:
                    return """
                                def output = outputs.${method}('directory')
                                output.mkdirs()
                            """
                case FileType.RegularFile:
                    return """
                                def output = outputs.${method}('file')
                                output.delete()
                                output.text = 'some text'
                            """
            }
        }
                }
            }
            ${declareTransformAction('FailingTransform')}

            task resolve(type: Copy) {
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                    lenient(providers.gradleProperty("lenient").present)
                }.artifacts
                from artifacts.artifactFiles
                into "\${buildDir}/libs"
            }
        """

        when:
        fails "resolve"

        then:
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Failed to transform a.jar to match attributes {artifactType=size}")
        failure.assertThatCause(matchesRegexp("Transform ${failureMessage}."))

        when:
        executer.withArguments("-Plenient=true")
        succeeds("resolve")

        then:
        outputContains(":resolve NO-SOURCE")

        where:
        method | type                 | failureMessage
        'file' | FileType.Directory   | 'output file .*directory must be a file, but is not'
        'file' | FileType.Missing     | 'output .*this_file_does_not.exist must exist'
        'dir'  | FileType.RegularFile | 'output directory .*file must be a directory, but is not'
        'dir'  | FileType.Missing     | 'output .*this_file_does_not.exist must exist'
    }

    def "directories are created for outputs in the workspace"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            abstract class DirectoryTransform implements TransformAction<TransformParameters.None> {
                void transform(TransformOutputs outputs) {
                    def outputFile = outputs.file("some/dir/output.txt")
                    assert outputFile.parentFile.directory
                    outputFile.text = "output"
                    def outputDir = outputs.dir("another/output/dir")
                    assert outputDir.directory
                    new File(outputDir, "in-dir.txt").text = "another output"
                }
            }
            ${declareTransformAction('DirectoryTransform')}

            task resolve(type: Copy) {
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts
                from artifacts.artifactFiles
                into "\${buildDir}/libs"
            }
        """

        expect:
        succeeds "resolve"
    }

    def "directories are not created for output #method which is part of the input"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.mkdirs()
            new File(a, "subdir").mkdirs()
            new File(a, "subfile.txt").text = "input file"

            dependencies {
                compile files(a)
            }

            abstract class MyTransform implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInput()

                void transform(TransformOutputs outputs) {
                    println "Hello?"
                    def output = outputs.${method}(new File(input.get().asFile, "some/dir/does-not-exist"))
                    assert !output.parentFile.directory
                }
            }
            dependencies {
                registerTransform(MyTransform) {
                    from.attribute(artifactType, 'directory')
                    to.attribute(artifactType, 'size')
                }
            }

            task resolve(type: Copy) {
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts
                from artifacts.artifactFiles
                into "\${buildDir}/libs"
            }
        """

        expect:
        fails "resolve"
        failure.assertThatCause(matchesRegexp('Transform output .*does-not-exist must exist.'))

        where:
        method << ["file", "dir"]
    }

    def "user gets a reasonable error message when transform returns a file that is not part of the input artifact or in the output directory"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            SomewhereElseTransform.output = file("other.jar")

            abstract class SomewhereElseTransform implements TransformAction<TransformParameters.None> {
                static def output
                void transform(TransformOutputs outputs) {
                    outputs.file(output)
                    output.text = "123"
                }
            }
            ${configurationAndTransform('SomewhereElseTransform')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Failed to transform a.jar to match attributes {artifactType=size}")
        failure.assertHasCause("Transform output ${testDirectory.file('other.jar')} must be a part of the input artifact or refer to a relative path.")
    }

    def "user gets a reasonable error message when transform registers an output that is not part of the input artifact or in the output directory"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            SomewhereElseTransform.output = file("other.jar")

            abstract class SomewhereElseTransform implements TransformAction<TransformParameters.None> {
                static def output
                void transform(TransformOutputs outputs) {
                    def outputFile = outputs.file(output)
                    outputFile.text = "123"
                }
            }
            ${declareTransformAction('SomewhereElseTransform')}

            task resolve(type: Copy) {
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts
                from artifacts.artifactFiles
                into "\${buildDir}/libs"
            }
        """

        when:
        fails "resolve"

        then:
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Failed to transform a.jar to match attributes {artifactType=size}")
        failure.assertHasCause("Transform output ${testDirectory.file('other.jar')} must be a part of the input artifact or refer to a relative path.")
    }

    def "user gets a reasonable error message when transform cannot be instantiated"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            abstract class BrokenTransform implements TransformAction<TransformParameters.None> {
                BrokenTransform() {
                    throw new RuntimeException("broken")
                }
                void transform(TransformOutputs outputs) {
                    throw new IllegalArgumentException("broken")
                }
            }
            ${configurationAndTransform('BrokenTransform')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Failed to transform a.jar to match attributes {artifactType=size}")
        failure.assertHasCause("Could not create an instance of type BrokenTransform.")
        failure.assertHasCause("broken")
    }

    @ToBeFixedForConfigurationCache(because = "treating file collection visit failures as a configuration cache problem adds an additional failure to the build summary; exception chain is different when transform input cannot be resolved")
    def "collects multiple failures"() {
        def m1 = mavenHttpRepo.module("test", "a", "1.3").publish()
        def m2 = mavenHttpRepo.module("test", "broken", "2.0").publish()
        def m3 = mavenHttpRepo.module("test", "c", "2.0").publish()

        given:
        buildFile << """
            repositories {
                maven { url '$mavenHttpRepo.uri' }
            }

            def a = file("a.jar")
            a.text = '123'
            def b = file("broken.jar")
            b.text = '123'
            def c = file("c.jar")
            c.text = '123'

            dependencies {
                compile files(a, b, c)
                compile 'test:a:1.3'
                compile 'test:broken:2.0'
                compile 'test:c:2.0'
            }

            abstract class TransformWithIllegalArgumentException implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    if (input.name.contains('broken')) {
                        throw new IllegalArgumentException("broken: " + input.name)
                    }
                    println "Transforming " + input.name
                    outputs.file(inputArtifact)
                }
            }
            ${configurationAndTransform('TransformWithIllegalArgumentException')}
        """

        when:
        m1.pom.expectGet()
        m1.artifact.expectGetBroken()
        m2.pom.expectGet()
        m2.artifact.expectGet()
        m3.pom.expectGet()
        m3.artifact.expectGet()

        fails "resolve"

        then:
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertHasCause("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Failed to transform broken.jar to match attributes {artifactType=size}")
        failure.assertHasCause("broken: broken.jar")
        failure.assertHasCause("Could not download a-1.3.jar (test:a:1.3)")
        failure.assertHasCause("Failed to transform broken-2.0.jar (test:broken:2.0) to match attributes {artifactType=size, org.gradle.status=release}")
        failure.assertHasCause("broken: broken-2.0.jar")

        and:
        outputContains("Transforming a.jar")
        outputContains("Transforming c.jar")
        outputContains("Transforming c-2.0.jar")

        when:
        m1.artifact.expectGetBroken()

        executer.withArguments("-Plenient=true")
        succeeds("resolve")

        then:
        outputContains("files: [a.jar, c.jar, c-2.0.jar]")
    }

    def "provides useful error message when registration action fails"() {
        when:
        buildFile << """
            dependencies {
                registerTransform(FileSizer) {
                    throw new Exception("Bad registration")
                }
            }
"""
        then:
        fails "help"

        and:
        failure.assertHasDescription("A problem occurred evaluating root project 'root'.")
        failure.assertHasCause("Bad registration")
    }

    def "provides useful error message when parameter value cannot be isolated for #type transform"() {
        mavenRepo.module("test", "a", "1.3").publish()
        settingsFile << "include 'lib'"

        buildFile << """
            project(':lib') {
                task jar(type: Jar) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib.jar'
                }
                artifacts {
                    compile jar
                }
            }

            repositories {
                maven { url '$mavenRepo.uri' }
            }

            dependencies {
                compile ${dependency}
            }

            // Not serializable
            class CustomType {
                String toString() { return "<custom>" }
            }

            abstract class Custom implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    @Input
                    CustomType getInput()
                    void setInput(CustomType input)
                }

                void transform(TransformOutputs outputs) {  }
            }

            dependencies {
                registerTransform(Custom) {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'size')
                    parameters {
                        input = new CustomType()
                    }
                }
            }

            task resolve(type: Copy) {
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts
                from artifacts.artifactFiles
                into "\${buildDir}/libs"
            }
        """

        when:
        fails "resolve"
        then:
        Matcher<String> matchesCannotIsolate = matchesRegexp("Could not isolate parameters Custom\\\$Parameters_Decorated@.* of artifact transform Custom")
        failure.assertHasDescription("Execution failed for task ':resolve'.")
        failure.assertThatCause(matchesCannotIsolate)
        failure.assertHasCause("Could not serialize value of type CustomType")

        where:
        scheduled | dependency
        true      | 'project(":lib")'
        false     | '"test:a:1.3"'
        type = scheduled ? 'scheduled' : 'immediate'
    }

    def "transformation attribute cycles are permitted"() {
        mavenRepo.module("test", "a", "1.0").publish()

        buildFile << """
            repositories {
                maven { url '$mavenRepo.uri' }
            }

            dependencies {
                compile 'test:a:1.0'
            }

            dependencies {
                // A circular "chain" of depth 2
                registerTransform(FileSizer) {
                    from.attribute(artifactType, "final")
                    to.attribute(artifactType, "somewhere")
                }
                registerTransform(FileSizer) {
                    from.attribute(artifactType, "somewhere")
                    to.attribute(artifactType, "final")
                }

                // A linear chain of depth 3
                registerTransform(FileSizer) {
                    from.attribute(artifactType, "jar")
                    to.attribute(artifactType, "intermediate")
                }
                registerTransform(FileSizer) {
                    from.attribute(artifactType, "intermediate")
                    to.attribute(artifactType, "semi-final")
                }
                registerTransform(FileSizer) {
                    from.attribute(artifactType, "semi-final")
                    to.attribute(artifactType, "final")
                }
            }

            task resolve {
                def artifactFiles = configurations.compile.incoming.artifactView { config ->
                    config.attributes {
                        attribute(artifactType, "final")
                    }
                }.files
                inputs.files(artifactFiles)
                doLast {
                    println "files: " + artifactFiles.files.collect { it.name }
                }
            }
        """

        when:
        succeeds "resolve"

        then:
        output.count("Creating FileSizer") == 3
        output.count("Transforming") == 3
        outputContains("files: [a-1.0.jar.txt.txt.txt]")
    }

    def "artifacts with same component id and extension, but different classifier remain distinguishable after transformation"() {
        def module = mavenRepo.module("test", "test", "1.3").publish()
        module.getArtifactFile(classifier: "foo").text = "1234"
        module.getArtifactFile(classifier: "bar").text = "5678"

        given:
        buildFile << """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                compile 'test:test:1.3:foo'
                compile 'test:test:1.3:bar'
            }

            /*
             * This transform creates a name that is independent of
             * the original file name, thus losing the classifier that
             * was encoded in it.
             */
            abstract class NameManglingTransform implements TransformAction<TransformParameters.None> {
                NameManglingTransform() {
                    println "Creating NameManglingTransform"
                }

                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def output = outputs.file("out.txt")
                    output.text = inputArtifact.get().asFile.text
                }
            }

            ${configurationAndTransform('NameManglingTransform')}
        """

        when:
        run "resolve"

        then:
        outputContains("ids: [out-foo.txt (test:test:1.3), out-bar.txt (test:test:1.3)]")
    }

    def "artifact excludes applied to external dependency on different graphs are honored"() {
        def m1 = ivyRepo.module("test", "test", "1.3")
        m1.artifact(name: "test-one", conf: "*")
        m1.artifact(name: "test-two", conf: "*")
        m1.publish()
        def m2 = ivyRepo.module("test", "test2", "2.3").dependsOn(m1).exclude(module: "test", artifact: "test-one")
        m2.publish()
        def m3 = ivyRepo.module("test", "test3", "3.4").dependsOn(m1).exclude(module: "test", artifact: "test-two")
        m3.publish()

        given:
        taskTypeLogsArtifactCollectionDetails()
        buildFile << """
            repositories {
                ivy { url "${ivyRepo.uri}" }
            }
            configurations {
                compile1 {
                    attributes { attribute usage, 'api' }
                }
                compile2 {
                    attributes { attribute usage, 'api' }
                }
            }
            dependencies {
                compile1 'test:test2:2.3'
                compile2 'test:test3:3.4'
            }

            ${declareTransform('FileSizer')}

            task resolve1(type: ShowArtifactCollection) {
                collection = configurations.compile1.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts
            }

            task resolve2(type: ShowArtifactCollection) {
                collection = configurations.compile2.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                }.artifacts
            }
        """

        when:
        run "resolve1", "resolve2"

        then:
        def task1 = result.groupedOutput.task(":resolve1")
        task1.assertOutputContains("variants = [{artifactType=size, org.gradle.status=integration}, {artifactType=size, org.gradle.status=integration}]")
        task1.assertOutputContains("components = [test:test2:2.3, test:test:1.3]")
        task1.assertOutputContains("artifacts = [test2-2.3.jar.txt (test:test2:2.3), test-two-1.3.jar.txt (test:test:1.3)]")
        task1.assertOutputContains("files = [test2-2.3.jar.txt, test-two-1.3.jar.txt]")

        def task2 = result.groupedOutput.task(":resolve2")
        task2.assertOutputContains("variants = [{artifactType=size, org.gradle.status=integration}, {artifactType=size, org.gradle.status=integration}]")
        task2.assertOutputContains("components = [test:test3:3.4, test:test:1.3]")
        task2.assertOutputContains("artifacts = [test3-3.4.jar.txt (test:test3:3.4), test-one-1.3.jar.txt (test:test:1.3)]")
        task2.assertOutputContains("files = [test3-3.4.jar.txt, test-one-1.3.jar.txt]")

        and:
        output.count("Transforming") == 4
        output.count("Transforming test-one-1.3.jar to test-one-1.3.jar.txt") == 1
        output.count("Transforming test-two-1.3.jar to test-two-1.3.jar.txt") == 1
        output.count("Transforming test2-2.3.jar to test2-2.3.jar.txt") == 1
        output.count("Transforming test3-3.4.jar to test3-3.4.jar.txt") == 1

        when:
        run "resolve1", "resolve2"

        then:
        output.count("Transforming") == 0
    }

    def "artifacts with the same id but different content are transformed independently"() {
        def repo1 = new MavenFileRepository(testDirectory.file("repo1"))
        def repo2 = new MavenFileRepository(testDirectory.file("repo2"))
        def m1 = repo1.module("test", "test", "1.3").publish()
        m1.artifactFile.text = "1234"
        def m2 = repo2.module("test", "test", "1.3").publish()
        m2.artifactFile.text = "12345"

        given:
        createDirs("a", "b")
        settingsFile << """
            include "a"
            include "b"
        """
        buildFile << """
            project(":a") {
                repositories {
                    maven { url "${repo1.uri}" }
                }
            }
            project(":b") {
                repositories {
                    maven { url "${repo2.uri}" }
                }
            }
            allprojects {
                dependencies {
                    compile 'test:test:1.3'
                }
                ${configurationAndTransform('FileSizer')}
            }
        """

        when:
        run "a:resolve", "b:resolve"

        then:
        def task1 = result.groupedOutput.task(":a:resolve")
        task1.assertOutputContains("variants: [{artifactType=size, org.gradle.status=release}]")
        task1.assertOutputContains("components: [test:test:1.3]")
        task1.assertOutputContains("ids: [test-1.3.jar.txt (test:test:1.3)]")
        task1.assertOutputContains("files: [test-1.3.jar.txt]")

        def task2 = result.groupedOutput.task(":b:resolve")
        task2.assertOutputContains("variants: [{artifactType=size, org.gradle.status=release}]")
        task2.assertOutputContains("components: [test:test:1.3]")
        task2.assertOutputContains("ids: [test-1.3.jar.txt (test:test:1.3)]")
        task2.assertOutputContains("files: [test-1.3.jar.txt]")

        file("a/build/libs/test-1.3.jar.txt").text == "4"
        file("b/build/libs/test-1.3.jar.txt").text == "5"

        and:
        output.count("Transforming") == 2
        output.count("Transforming test-1.3.jar to test-1.3.jar.txt") == 2

        when:
        run "a:resolve", "b:resolve"

        then:
        output.count("Transforming") == 0
    }

    def "transform runs only once even when variant is consumed from multiple projects"() {
        given:
        createDirs("app2")
        settingsFile << """
            include 'app2'
        """
        buildFile << """
            project(':lib') {
                projectDir.mkdirs()
                def file1 = file('lib1.size')
                file1.text = 'some text'

                task lib1(type: Jar) {
                    destinationDirectory = buildDir
                }

                dependencies {
                    compile files(lib1)
                }
                artifacts {
                    compile file1
                }
            }

            project(':app') {
                dependencies {
                    compile project(':lib')
                }
                ${configurationAndTransform('FileSizer')}
            }

            project(':app2') {
                dependencies {
                    compile project(':lib')
                }
                ${configurationAndTransform('FileSizer')}
            }
        """

        when:
        run "app:resolve", "app2:resolve"

        then:
        output.count("Transforming") == 1
    }

    def "can resolve transformed variant during configuration time"() {
        given:
        buildFile << """
            project(':lib') {
                projectDir.mkdirs()
                def jar1 = file('lib1.jar')
                jar1.text = 'some text'
                def file1 = file('lib1.size')
                file1.text = 'some text'

                dependencies {
                    compile files(jar1)
                }
                artifacts {
                    compile file1
                }
            }

            project(':app') {
                dependencies {
                    compile project(':lib')
                }
                ${declareTransform('FileSizer')}

                task resolve(type: Copy) {
                    def artifacts = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                        if (project.hasProperty("lenient")) {
                            lenient(true)
                        }
                    }.artifacts
                    // Resolve during configuration
                    from artifacts.artifactFiles.files
                    into "\${buildDir}/libs"
                    doLast {
                        // Do nothing
                    }
                }
            }
        """

        when:
        run "app:resolve"

        then:
        output.count("Transforming") == 1
    }

    def "notifies transform listeners and build operation listeners on successful execution"() {
        def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.transform.TransformExecutionListener
            import org.gradle.internal.event.ListenerManager

            project.services.get(ListenerManager).addListener(new TransformExecutionListener() {
                @Override
                void beforeTransformExecution(Describable transformer, Describable subject) {
                    println "Before transformer \${transformer.displayName} on \${subject.displayName}"
                }

                @Override
                void afterTransformExecution(Describable transformer, Describable subject) {
                    println "After transformer \${transformer.displayName} on \${subject.displayName}"
                }
            })

            project(":lib") {
                task jar(type: Jar) {
                    archiveFileName = 'lib.jar'
                    destinationDirectory = buildDir
                }
                artifacts {
                    compile jar
                }
            }

            project(":app") {
                dependencies {
                    compile project(":lib")
                }
                ${configurationAndTransform('FileSizer')}
            }
        """

        when:
        run "app:resolve"

        then:
        outputContains("Before transformer FileSizer on lib.jar (project :lib)")
        outputContains("After transformer FileSizer on lib.jar (project :lib)")

        and:
        def executeTransformationOp = buildOperations.only(ExecutePlannedTransformStepBuildOperationType)
        executeTransformationOp.failure == null
        executeTransformationOp.displayName == "Transform lib.jar (project :lib) with FileSizer"
        with(executeTransformationOp.details) {
            transformerName == "FileSizer"
            subjectName == "lib.jar (project :lib)"
            with(plannedTransformStepIdentity) {
                nodeType == "TRANSFORM_STEP"
                consumerBuildPath == ":"
                consumerProjectPath == ":app"
                componentId == [buildPath: ":", projectPath: ":lib"]
                sourceAttributes == [artifactType: "jar", usage: "api"]
                targetAttributes == [artifactType: "size", usage: "api"]
                capabilities == [[group: "root", name: "lib", version: "unspecified"]]
                artifactName == "lib.jar"
                dependenciesConfigurationIdentity == null
            }
            transformActionClass == "FileSizer"
        }
    }

    def "notifies transform listeners and build operation listeners on failed execution"() {
        def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

        given:
        buildFile << """
            import org.gradle.api.internal.artifacts.transform.TransformExecutionListener
            import org.gradle.internal.event.ListenerManager

            project.services.get(ListenerManager).addListener(new TransformExecutionListener() {
                @Override
                void beforeTransformExecution(Describable transformer, Describable subject) {
                    println "Before transformer \${transformer.displayName} on \${subject.displayName}"
                }

                @Override
                void afterTransformExecution(Describable transformer, Describable subject) {
                    println "After transformer \${transformer.displayName} on \${subject.displayName}"
                }
            })

            project(":lib") {
                task jar(type: Jar) {
                    archiveFileName = 'lib.jar'
                    destinationDirectory = buildDir
                }
                artifacts {
                    compile jar
                }
            }

            project(":app") {
                dependencies {
                    compile project(":lib")
                }
                ${configurationAndTransform('BrokenTransform')}
            }

            abstract class BrokenTransform implements TransformAction<TransformParameters.None> {
                void transform(TransformOutputs outputs) {
                    throw new GradleException('broken')
                }
            }
        """

        when:
        fails "app:resolve"

        then:
        outputContains("Before transformer BrokenTransform on lib.jar (project :lib)")
        outputContains("After transformer BrokenTransform on lib.jar (project :lib)")

        and:
        def executeTransformationOp = buildOperations.only(ExecutePlannedTransformStepBuildOperationType)
        executeTransformationOp.failure != null
        executeTransformationOp.displayName == "Transform lib.jar (project :lib) with BrokenTransform"
        with(executeTransformationOp.details) {
            transformerName == "BrokenTransform"
            subjectName == "lib.jar (project :lib)"
            with(plannedTransformStepIdentity) {
                nodeType == "TRANSFORM_STEP"
                consumerBuildPath == ":"
                consumerProjectPath == ":app"
                componentId == [buildPath: ":", projectPath: ":lib"]
                sourceAttributes == [artifactType: "jar", usage: "api"]
                targetAttributes == [artifactType: "size", usage: "api"]
                capabilities == [[group: "root", name: "lib", version: "unspecified"]]
                artifactName == "lib.jar"
                dependenciesConfigurationIdentity == null
            }
            transformActionClass == "BrokenTransform"
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/6156")
    def "stops resolving dependencies of task when artifact transforms are encountered"() {
        given:
        buildFile << """
            project(':lib') {
                task jar(type: Jar) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib1.jar'
                }

                artifacts {
                    compile jar
                }
            }

            project(':app') {

                dependencies {
                    compile project(':lib')
                }

                ${configurationAndTransform()}

                task dependent {
                    dependsOn resolve
                }
            }

            gradle.taskGraph.whenReady { taskGraph ->
                taskGraph.allTasks.each { task ->
                    task.taskDependencies.getDependencies(task).each { dependency ->
                        println "> Dependency: \${task} -> \${dependency}"
                    }
                }
            }
        """

        when:
        run "dependent"

        then:
        output.count("> Dependency:") == 1
        output.contains("> Dependency: task ':app:dependent' -> task ':app:resolve'")
        output.contains("> Transform lib1.jar (project :lib) with FileSizer")
        output.contains("> Task :app:resolve")
    }

    def declareTransform(String transformImplementation) {
        """
            dependencies {
                registerTransform(${transformImplementation}) {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'size')
                }
            }
        """
    }

    def declareTransformAction(String transformActionImplementation) {
        """
            dependencies {
                registerTransform($transformActionImplementation) {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'size')
                }
            }
        """
    }

    def configurationAndTransform(String transformImplementation = "FileSizer") {
        """
            ${declareTransform(transformImplementation)}

            task resolve(type: Copy) {
                duplicatesStrategy = 'INCLUDE'
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                    lenient(providers.gradleProperty("lenient").present)
                }.artifacts
                from artifacts.artifactFiles
                into "\${buildDir}/libs"
                doLast {
                    println "files: " + artifacts.collect { it.file.name }
                    println "ids: " + artifacts.collect { it.id }
                    println "components: " + artifacts.collect { it.id.componentIdentifier }
                    println "variants: " + artifacts.collect { it.variant.attributes }
                    println "capabilities: " + artifacts.collect { it.variant.capabilities }
                }
            }
        """
    }
}
