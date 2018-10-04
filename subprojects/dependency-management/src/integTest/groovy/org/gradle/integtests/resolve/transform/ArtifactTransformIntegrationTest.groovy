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
import spock.lang.Issue

class ArtifactTransformIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
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
            attributes { attribute usage, 'api' }
        }
    }
}

class FileSizer extends ArtifactTransform {
    FileSizer() {
        println "Creating FileSizer"
    }
    
    List<File> transform(File input) {
        assert outputDirectory.directory && outputDirectory.list().length == 0
        def output = new File(outputDirectory, input.name + ".txt")
        println "Transforming \${input.name} to \${output.name}"
        output.text = String.valueOf(input.length())
        return [output]
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
        outputContains("variants: [{artifactType=size}, {artifactType=size}]")
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

                ${configurationAndTransform('FileSizer')}
            }
        """

        when:
        run "resolve"

        then:
        executed(":lib:jar1", ":lib:jar2", ":app:resolve")

        and:
        outputContains("variants: [{artifactType=size, usage=api}, {artifactType=size, usage=api}]")
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
                    destinationDir = buildDir
                    archiveName = 'common.jar'
                }
                artifacts {
                    compile jar
                    compile file("common-file.jar")
                }
            }

            project(':lib') {
                task jar1(type: Jar) {
                    destinationDir = buildDir
                    archiveName = 'lib1.jar'
                }
                task jar2(type: Jar) {
                    destinationDir = buildDir
                    archiveName = 'lib2.jar'
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
        outputContains("variants: [{artifactType=size, usage=api}, {artifactType=size, usage=api}, {artifactType=size}, {artifactType=size}, {artifactType=size, usage=api}, {artifactType=size, usage=api}, {artifactType=size}]")
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
                    destinationDir = buildDir
                    archiveName = 'lib1.jar'
                }
                task zip1(type: Zip) {
                    destinationDir = buildDir
                    archiveName = 'lib2.zip'
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
                    destinationDir = buildDir
                    archiveName = 'lib1.jar'
                }
                task jar2(type: Jar) {
                    destinationDir = buildDir
                    archiveName = 'lib2.zip'
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
                    destinationDir = buildDir
                    archiveName = 'lib1.jar'
                }
                task jar2(type: Zip) {
                    destinationDir = buildDir
                    archiveName = 'lib2.jar'
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

                    registerTransform {
                        from.attribute(Attribute.of('color', String), "green")
                        to.attribute(Attribute.of('color', String), "red")
                        artifactTransform(MakeRedThings)
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

            class MakeRedThings extends ArtifactTransform {
                List<File> transform(File input) {
                    assert outputDirectory.directory && outputDirectory.list().length == 0
                    def output = new File(outputDirectory, input.name + ".red")
                    println "Transforming \${input.name} to \${output.name}"
                    output.text = String.valueOf(input.length())
                    return [output]
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
                    destinationDir = buildDir
                    archiveName = 'lib1.jar'
                }
                task jar2(type: Zip) {
                    destinationDir = buildDir
                    archiveName = 'lib2.jar'
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
                    
                    registerTransform {
                        from.attribute(Attribute.of('color', String), "blue")
                        to.attribute(Attribute.of('color', String), "red")
                        artifactTransform(MakeBlueToRedThings)
                    }
                    registerTransform {
                        from.attribute(Attribute.of('color', String), "green")
                        to.attribute(Attribute.of('color', String), "blue")
                        artifactTransform(MakeGreenToBlueThings)
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

            class MakeGreenToBlueThings extends ArtifactTransform {
                List<File> transform(File input) {
                    assert outputDirectory.directory && outputDirectory.list().length == 0
                    def output = new File(outputDirectory, input.name + ".blue")
                    println "Transforming \${input.name} to \${output.name}"
                    println "Input exists: \${input.exists()}"
                    output.text = String.valueOf(input.length())
                    return [output]
                }
            }

            class MakeBlueToRedThings extends ArtifactTransform {
                List<File> transform(File input) {
                    assert outputDirectory.directory && outputDirectory.list().length == 0
                    def output = new File(outputDirectory, input.name + ".red")
                    println "Transforming \${input.name} to \${output.name}"
                    println "Input exists: \${input.exists()}"
                    output.text = String.valueOf(input.length())
                    return [output]
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
                registerTransform {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'size')
                    artifactTransform(FileSizer)
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

            class LineSplitter extends ArtifactTransform {
                List<File> transform(File input) {
                    assert outputDirectory.directory && outputDirectory.list().length == 0
                    File outputA = new File(outputDirectory, input.name + ".A.txt")
                    outputA.text = "Output A"
            
                    File outputB = new File(outputDirectory, input.name + ".B.txt")
                    outputB.text = "Output B"
                    return [outputA, outputB]
                }
            }
"""

        when:
        succeeds "resolve"

        then:
        outputContains("variants: [{artifactType=size}, {artifactType=size}, {artifactType=size}, {artifactType=size}]")
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

            class EmptyOutput extends ArtifactTransform {
                List<File> transform(File input) {
                    assert outputDirectory.directory && outputDirectory.list().length == 0
                    println "Transforming \$input.name"
                    return []
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
                    destinationDir = buildDir
                    baseName = 'a'
                    extension = 'custom'
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
                    registerTransform {
                        from.attribute(artifactType, 'custom')
                        to.attribute(artifactType, 'transformed')
                        artifactTransform(BrokenTransform)
                    }
                    registerTransform {
                        from.attribute(artifactType, 'custom')
                        to.attribute(artifactType, 'transformed')
                        artifactTransform(BrokenTransform)
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
    
            class BrokenTransform extends ArtifactTransform {
                List<File> transform(File input) {
                    throw new AssertionError("should not be used")
                }
            }
        """

        when:
        fails "resolve"

        then:
        failure.assertHasCause """Found multiple transforms that can produce a variant of project :lib for consumer attributes:
  - artifactType 'transformed'
  - usage 'api'
Found the following transforms:
  - Transform from configuration ':lib:compile':
      - artifactType 'custom'
      - usage 'api'
  - Transform from configuration ':lib:compile':
      - artifactType 'custom'
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
                    destinationDir = buildDir
                    archiveName = 'lib1.jar'
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
                    registerTransform {
                        from.attribute(artifactType, 'jar')
                        from.attribute(buildType, 'release')
                        to.attribute(artifactType, 'transformed')
                        artifactTransform(BrokenTransform)
                    }
                    registerTransform {
                        from.attribute(artifactType, 'jar')
                        from.attribute(buildType, 'debug')
                        to.attribute(artifactType, 'transformed')
                        artifactTransform(BrokenTransform)
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

            class BrokenTransform extends ArtifactTransform {
                List<File> transform(File input) {
                    throw new AssertionError("should not be used")
                }
            }
        """

        when:
        fails "resolve"

        then:
        failure.assertHasCause """Found multiple transforms that can produce a variant of project :lib for consumer attributes:
  - artifactType 'transformed'
  - usage 'api'
Found the following transforms:
  - Transform from configuration ':lib:compile' variant variant1:
      - artifactType 'jar'
      - buildType 'release'
      - flavor 'free'
      - usage 'api'
  - Transform from configuration ':lib:compile' variant variant2:
      - artifactType 'jar'
      - buildType 'release'
      - flavor 'paid'
      - usage 'api'
  - Transform from configuration ':lib:compile' variant variant3:
      - artifactType 'jar'
      - buildType 'debug'
      - usage 'api'"""
    }

    def "result is applied for all query methods"() {
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
                    registerTransform {
                        from.attribute(artifactType, "jar")
                        to.attribute(artifactType, "size")
                        artifactTransform(FileSizer)
                    }
                }
                ext.checkArtifacts = { artifacts ->
                    assert artifacts.collect { it.id.displayName } == ['lib.jar.txt (project :lib)']
                    assert artifacts.collect { it.file.name } == ['lib.jar.txt']
                }
                ext.checkLegacyArtifacts = { artifacts ->
                    assert artifacts.collect { it.id.displayName } == ['lib.jar.txt (project :lib)']
                    assert artifacts.collect { it.file.name } == ['lib.jar.txt']
                }
                ext.checkFiles = { config ->
                    assert config.collect { it.name } == ['lib.jar.txt']
                }
                task resolve {
                    doLast {
                        checkFiles configurations.compile
                        checkFiles configurations.compile.files
                        checkFiles configurations.compile.incoming.files
                        checkFiles configurations.compile.resolvedConfiguration.files
                        
                        checkFiles configurations.compile.resolvedConfiguration.lenientConfiguration.files
                        checkFiles configurations.compile.resolve()
                        checkFiles configurations.compile.files { true }
                        checkFiles configurations.compile.fileCollection { true }
                        checkFiles configurations.compile.resolvedConfiguration.getFiles { true }
                        checkFiles configurations.compile.resolvedConfiguration.lenientConfiguration.getFiles { true }

                        checkLegacyArtifacts configurations.compile.resolvedConfiguration.resolvedArtifacts
                        checkLegacyArtifacts configurations.compile.resolvedConfiguration.lenientConfiguration.artifacts

                        checkArtifacts configurations.compile.incoming.artifacts
                        checkArtifacts configurations.compile.incoming.artifactView { }.artifacts
                    }
                }
            }
        """

        expect:
        succeeds "resolve"
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

    def "transforms are created as required and a new instance created for each file"() {
        given:
        buildFile << """
            dependencies {
                compile project(':lib')
            }
            project(':lib') {
                task jar1(type: Jar) { archiveName = 'jar1.jar' }
                task jar2(type: Jar) { archiveName = 'jar2.jar' }
                artifacts { compile jar1, jar2 }
            }

            class Hasher extends ArtifactTransform {
                int count

                Hasher() {
                    println "Creating Transform"
                }
                
                List<File> transform(File input) {
                    assert outputDirectory.directory && outputDirectory.list().length == 0
                    def output = new File(outputDirectory, input.name + ".txt")
                    count++
                    println "Transforming \${input.name} to \${output.name} with count \${count}"
                    output.text = String.valueOf(count)
                    return [output]
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

            class TransformWithIllegalArgumentException extends ArtifactTransform {
                List<File> transform(File input) {
                    if (input.name == 'a.jar') {
                        throw new IllegalArgumentException("broken")
                    }
                    println "Transforming " + input.name
                    return [input]
                }
            }
            ${configurationAndTransform('TransformWithIllegalArgumentException')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasDescription("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Failed to transform file 'a.jar' to match attributes {artifactType=size} using transform TransformWithIllegalArgumentException")
        failure.assertHasCause("broken")

        and:
        outputContains("Transforming b.jar")

        when:
        executer.withArgument("-Plenient=true")
        succeeds("resolve")

        then:
        outputContains("files: [b.jar]")
    }

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
        failure.assertHasDescription("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Could not download test-impl.jar (test:test:1.3)")

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
        failure.assertHasDescription("Could not resolve all files for configuration ':compile'.")
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

    def "user gets a reasonable error message when a output property returns null"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            class ToNullTransform extends ArtifactTransform {
                List<File> transform(File input) {
                    return null
                }
            }
            ${configurationAndTransform('ToNullTransform')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasDescription("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Failed to transform file 'a.jar' to match attributes {artifactType=size} using transform ToNullTransform")
        failure.assertHasCause("Transform returned null result.")
    }

    def "user gets a reasonable error message when transform returns a non-existing file"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            class NoExistTransform extends ArtifactTransform {
                List<File> transform(File input) {
                    return [new File('this_file_does_not.exist')]
                }
            }
            ${configurationAndTransform('NoExistTransform')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasDescription("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Failed to transform file 'a.jar' to match attributes {artifactType=size} using transform NoExistTransform")
        failure.assertHasCause("Transform output file this_file_does_not.exist does not exist.")

        when:
        executer.withArguments("-Plenient=true")
        succeeds("resolve")

        then:
        outputContains(":resolve NO-SOURCE")
    }

    def "user gets a reasonable error message when transform returns a file that is not input or in output directory"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            SomewhereElseTransform.output = file("other.jar")

            class SomewhereElseTransform extends ArtifactTransform {
                static def output
                List<File> transform(File input) {
                    output.text = "123"
                    return [output]
                }
            }
            ${configurationAndTransform('SomewhereElseTransform')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasDescription("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Failed to transform file 'a.jar' to match attributes {artifactType=size} using transform SomewhereElseTransform")
        failure.assertHasCause("Transform output file ${testDirectory.file('other.jar')} is not a child of the transform's input file or output directory.")
    }

    def "user gets a reasonable error message when transform cannot be instantiated"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            class BrokenTransform extends ArtifactTransform {
                BrokenTransform() {
                    throw new RuntimeException("broken")
                }
                List<File> transform(File input) {
                    throw new IllegalArgumentException("broken")
                }
            }
            ${configurationAndTransform('BrokenTransform')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasDescription("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Failed to transform file 'a.jar' to match attributes {artifactType=size} using transform BrokenTransform")
        failure.assertHasCause("Could not create an instance of type BrokenTransform.")
        failure.assertHasCause("broken")
    }

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

            class TransformWithIllegalArgumentException extends ArtifactTransform {
                List<File> transform(File input) {
                    assert outputDirectory.directory && outputDirectory.list().length == 0
                    if (input.name.contains('broken')) {
                        throw new IllegalArgumentException("broken: " + input.name)
                    }
                    println "Transforming " + input.name
                    return [input]
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
        failure.assertHasDescription("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Failed to transform file 'broken.jar' to match attributes {artifactType=size} using transform TransformWithIllegalArgumentException")
        failure.assertHasCause("broken: broken.jar")
        failure.assertHasCause("Could not download a.jar (test:a:1.3)")
        failure.assertHasCause("Failed to transform file 'broken-2.0.jar' to match attributes {artifactType=size} using transform TransformWithIllegalArgumentException")
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
                registerTransform {
                    throw new Exception("Bad registration")
                }
            }
"""
        then:
        fails "resolve"

        and:
        failure.assertHasDescription("A problem occurred evaluating root project 'root'.")
        failure.assertHasCause("Bad registration")
    }

    def "provides useful error message when configuration value cannot be serialized"() {
        when:
        buildFile << """
            // Not serializable  
            class CustomType {
                String toString() { return "<custom>" }
            }

            class Custom extends ArtifactTransform { 
                Custom(CustomType value) { }
                List<File> transform(File input) { [] }
            }
            
            dependencies {
                registerTransform {
                    from.attribute(usage, 'any')
                    to.attribute(usage, 'any')
                    artifactTransform(Custom) { params(new CustomType()) }
                }
            }
"""
        then:
        fails "resolve"

        and:
        failure.assertHasDescription("A problem occurred evaluating root project 'root'.")
        failure.assertHasCause("Could not snapshot configuration values for transform Custom: [<custom>]")
        failure.assertHasCause("java.io.NotSerializableException: CustomType")
    }

    def "artifacts with same component id and extension, but different classifier remain distinguishable after transformation"() {
        def module = mavenRepo.module("test", "test", "1.3").publish()
        module.getArtifactFile(classifier:"foo").text = "1234"
        module.getArtifactFile(classifier:"bar").text = "5678"

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
            class NameManglingTransform extends ArtifactTransform {
                NameManglingTransform() {
                    println "Creating NameManglingTransform"
                }
                
                List<File> transform(File input) {
                    def output = new File(outputDirectory, "out.txt")
                    output.text = input.text
                    return [output]
                }
            }

            ${configurationAndTransform('NameManglingTransform')}
        """

        when:
        run "resolve"

        then:
        outputContains("ids: [out-foo.txt (test:test:1.3), out-bar.txt (test:test:1.3)]")
    }

    def "transform runs only once even when variant is consumed from multiple projects"() {
        given:
        settingsFile << """
            include 'app2'
        """
        buildFile << """
            project(':lib') {
                projectDir.mkdirs()
                def file1 = file('lib1.size')
                file1.text = 'some text'

                task lib1(type: Jar) {}
    
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

    @Issue("https://github.com/gradle/gradle/issues/6156")
    def "stops resolving dependencies of task when artifact transforms are encountered"() {
        given:
        buildFile << """
            project(':lib') {
                task jar(type: Jar) {
                    destinationDir = buildDir
                    archiveName = 'lib1.jar'
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
        output.contains("> Transform artifact lib1.jar (project :lib) with FileSizer")
        output.contains("> Task :app:resolve")
    }

    def declareTransform(String transformImplementation) {
        """
            dependencies {
                registerTransform {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'size')
                    artifactTransform(${transformImplementation})
                }
            }
        """
    }

    def configurationAndTransform(String transformImplementation = "FileSizer") {
        """
            ${declareTransform(transformImplementation)}

            task resolve(type: Copy) {
                def artifacts = configurations.compile.incoming.artifactView {
                    attributes { it.attribute(artifactType, 'size') }
                    if (project.hasProperty("lenient")) {
                        lenient(true)
                    }
                }.artifacts
                from artifacts.artifactFiles
                into "\${buildDir}/libs"
                doLast {
                    println "files: " + artifacts.collect { it.file.name }
                    println "ids: " + artifacts.collect { it.id }
                    println "components: " + artifacts.collect { it.id.componentIdentifier }
                    println "variants: " + artifacts.collect { it.variant.attributes }
                }
            }
"""
    }
}
