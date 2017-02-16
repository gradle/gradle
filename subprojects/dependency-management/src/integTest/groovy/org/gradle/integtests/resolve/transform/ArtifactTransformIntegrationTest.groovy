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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest

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
    List<File> transform(File input) {
        def output = new File(outputDirectory, input.name + ".txt")
        if (!output.exists()) {
            println "Transforming \${input.name} to \${output.name}"
            output.text = String.valueOf(input.length())
        } else {
            println "Transforming \${input.name} to \${output.name} (cached)"
        }
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
        succeeds "resolve"

        then:
        outputContains("variants: [{artifactType=size}, {artifactType=size}]")
        file("build/libs").assertHasDescendants("test-1.3.jar.txt", "test2-2.3.jar.txt")
        file("build/libs/test-1.3.jar.txt").text == "4"
        file("build/libs/test2-2.3.jar.txt").text == "2"
        file("build/transformed").assertHasDescendants("test-1.3.jar.txt", "test2-2.3.jar.txt")
        file("build/transformed/test-1.3.jar.txt").text == "4"
        file("build/transformed/test2-2.3.jar.txt").text == "2"

        and:
        outputContains("Transforming test-1.3.jar to test-1.3.jar.txt")
        outputContains("Transforming test2-2.3.jar to test2-2.3.jar.txt")
    }

    def "applies transforms to files from file dependencies matching on implicit format attribute"() {
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

            ${configurationAndTransform('FileSizer')}
        """

        succeeds "resolve"

        then:
        result.assertTasksExecuted(":jars", ":resolve")

        and:
        outputContains("variants: [{artifactType=size}, {artifactType=size}]")
        file("build/libs").assertHasDescendants("a.jar.txt", "b.jar.txt")
        file("build/libs/a.jar.txt").text == "4"
        file("build/libs/b.jar.txt").text == "2"
        file("build/transformed").assertHasDescendants("a.jar.txt", "b.jar.txt")
        file("build/transformed/a.jar.txt").text == "4"
        file("build/transformed/b.jar.txt").text == "2"

        and:
        outputContains("Transforming a.jar to a.jar.txt")
        outputContains("Transforming b.jar to b.jar.txt")
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
        succeeds "resolve"

        then:
        result.assertTasksExecuted(":lib:jar1", ":lib:jar2", ":app:resolve")

        and:
        outputContains("variants: [{artifactType=size, usage=api}, {artifactType=size, usage=api}]")
        file("app/build/libs").assertHasDescendants("lib1.jar.txt", "lib2.jar.txt")
        file("app/build/libs/lib1.jar.txt").text == file("lib/build/lib1.jar").length() as String
        file("app/build/transformed").assertHasDescendants("lib1.jar.txt", "lib2.jar.txt")
        file("app/build/transformed/lib1.jar.txt").text == file("lib/build/lib1.jar").length() as String

        and:
        outputContains("Transforming lib1.jar to lib1.jar.txt")
        outputContains("Transforming lib2.jar to lib2.jar.txt")
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
        succeeds "resolve"

        then:
        result.assertTasksExecuted(":lib:jar1", ":lib:zip1", ":app:resolve")

        and:
        outputContains("variants: [{artifactType=size, usage=api}, {artifactType=size, usage=api}]")
        file("app/build/libs").assertHasDescendants("lib1.jar.txt", "lib2.zip.txt")
        file("app/build/libs/lib1.jar.txt").text == file("lib/build/lib1.jar").length() as String
        file("app/build/transformed").assertHasDescendants("lib1.jar.txt", "lib2.zip.txt")
        file("app/build/transformed/lib1.jar.txt").text == file("lib/build/lib1.jar").length() as String

        and:
        outputContains("Transforming lib1.jar to lib1.jar.txt")
        outputContains("Transforming lib2.zip to lib2.zip.txt")
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
        succeeds "resolve"

        then:
        outputContains("variants: [{artifactType=size}, {artifactType=size}, {artifactType=size, usage=api}]")
        file("app/build/libs").assertHasDescendants("lib1.jar.txt", "lib1.size", "lib2.size")
        file("app/build/libs/lib1.jar.txt").text == "9"
        file("app/build/libs/lib1.size").text == "some text"
        file("app/build/transformed").assertHasDescendants("lib1.jar.txt")
        file("app/build/transformed/lib1.jar.txt").text == "9"
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
        succeeds "resolve"

        then:
        result.assertTasksExecuted(":lib:jar1", ":lib:jar2", ":app:resolve")

        and:
        outputContains("variants: [{artifactType=size, usage=api}, {artifactType=size, usage=api}]")
        file("app/build/libs").assertHasDescendants("lib1.jar", "lib2.zip")
        file("app/build/transformed").assertDoesNotExist()
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
                    def artifacts = configurations.compile.incoming.artifactView().attributes { 
                        it.attribute(artifactType, 'jar') 
                        it.attribute(Attribute.of('javaVersion', String), '7') 
                        it.attribute(Attribute.of('color', String), 'red') 
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
                    def output = new File(outputDirectory, input.name + ".red")
                    println "Transforming \${input.name} to \${output.name}"
                    output.text = String.valueOf(input.length())
                    return [output]
                }
            }
        """

        when:
        succeeds "resolve"

        then:
        result.assertTasksExecuted(":lib:jar1", ":app:resolve")

        and:
        outputContains("variants: [{artifactType=jar, color=red, javaVersion=7, usage=api}]")
        file("app/build/libs").assertHasDescendants("lib1.jar.red")
        file("app/build/transformed").assertHasDescendants("lib1.jar.red")

        and:
        outputContains("Transforming lib1.jar to lib1.jar.red")
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
                    def artifacts = configurations.compile.incoming.artifactView().attributes { 
                        it.attribute(artifactType, 'jar') 
                        it.attribute(Attribute.of('javaVersion', String), '7') 
                        it.attribute(Attribute.of('color', String), 'red') 
                    }.artifacts
                    from artifacts.artifactFiles
                    into "\${buildDir}/libs"
                    doLast {
                        println "files: " + artifacts.collect { it.file.name }
                        println "variants: " + artifacts.collect { it.variant.attributes }
                    }
                }
            }

            class MakeGreenToBlueThings extends ArtifactTransform {
                List<File> transform(File input) {
                    def output = new File(outputDirectory, input.name + ".blue")
                    println "Transforming \${input.name} to \${output.name}"
                    output.text = String.valueOf(input.length())
                    return [output]
                }
            }

            class MakeBlueToRedThings extends ArtifactTransform {
                List<File> transform(File input) {
                    def output = new File(outputDirectory, input.name + ".red")
                    println "Transforming \${input.name} to \${output.name}"
                    output.text = String.valueOf(input.length())
                    return [output]
                }
            }
        """

        when:
        succeeds "resolve"

        then:
        result.assertTasksExecuted(":lib:jar1", ":app:resolve")

        and:
        outputContains("variants: [{artifactType=jar, color=red, javaVersion=7, usage=api}]")
        file("app/build/libs").assertHasDescendants("lib1.jar.blue.red")
        file("app/build/transformed").assertHasDescendants("lib1.jar.blue", "lib1.jar.blue.red")

        and:
        outputContains("Transforming lib1.jar to lib1.jar.blue")
        outputContains("Transforming lib1.jar.blue to lib1.jar.blue.red")
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
                    println "Transforming \$input.name"
                    return []
                }
            }
"""

        when:
        succeeds "resolve"

        then:
        outputContains("Transforming test-1.3.jar")
        outputContains("Transforming test2-2.3.jar")
        file("build/libs").assertIsEmptyDir()
    }

    def "can transform based on consumer-only attributes"() {
        mavenRepo.module("test", "test", "1.3").publish()

        given:
        buildFile << """
            def viewType = Attribute.of('viewType', String)

            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            dependencies {
                compile 'test:test:1.3'
                attributesSchema {
                    attribute(viewType)
                }
                
                registerTransform {
                    from.attribute(artifactType, 'jar')
                    to.attribute(viewType, "transformed")
                      .attribute(artifactType, "txt")
                    artifactTransform(ViewTransform) {
                        params("transformed.txt")
                    }
                }
                registerTransform {
                    from.attribute(artifactType, 'jar')
                    to.attribute(viewType, "modified")
                      .attribute(artifactType, "txt")
                    artifactTransform(ViewTransform) {
                        params("modified.txt")
                    }
                }
            }

            task checkFiles {
                doLast {
                    assert configurations.compile.collect { it.name } == ['test-1.3.jar']
                    def transformed = configurations.compile.incoming.artifactView().attributes{ it.attribute(viewType, 'transformed') }.artifacts
                    assert transformed.collect { it.file.name } == ['transformed.txt']
                    assert transformed.collect { it.variant.attributes.toString() } == ['{artifactType=txt, viewType=transformed}']
                    def modified = configurations.compile.incoming.artifactView().attributes{ it.attribute(viewType, 'modified') }.artifacts
                    assert modified.collect { it.file.name } == ['modified.txt']
                    assert modified.collect { it.variant.attributes.toString() } == ['{artifactType=txt, viewType=modified}']
                }
            }

            class ViewTransform extends ArtifactTransform {
                private String outputName
                ViewTransform(String outputName) {
                    this.outputName = outputName
                }
                List<File> transform(File input) {
                    def output = new File(outputDirectory, outputName)
                    output << "content"
                    return [output]
                }
            }
"""

        expect:
        succeeds "checkFiles"
    }

    def "can use transform to include a subset of transformed artifacts based on arbitrary criteria"() {
        mavenRepo.module("test", "to-keep", "1.3").publish()
        mavenRepo.module("test", "to-exclude", "2.3").publish()

        given:
        buildFile << """
            def viewType = Attribute.of('viewType', String)

            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            configurations {
                selection
            }
            dependencies {
                selection 'test:to-keep:1.3'
                selection 'test:to-exclude:2.3'
                attributesSchema {
                    attribute(viewType)
                }
                
                registerTransform {
                    from.attribute(Attribute.of('artifactType', String), "jar")
                    to.attribute(viewType, "filtered")
                    artifactTransform(ArtifactFilter) {
                        params(true)
                    }
                }
                registerTransform {
                    from.attribute(Attribute.of('artifactType', String), "jar")
                    to.attribute(viewType, "unfiltered")
                    artifactTransform(ArtifactFilter) {
                        params(false)
                    }
                }
            }

            def filteredView = configurations.selection.incoming.artifactView().attributes { it.attribute(viewType, 'filtered') }.files
            def unfilteredView = configurations.selection.incoming.artifactView().attributes { it.attribute(viewType, 'unfiltered') }.files

            task checkFiles {
                doLast {
                    assert configurations.selection.collect { it.name } == ['to-keep-1.3.jar', 'to-exclude-2.3.jar']
                    assert filteredView.collect { it.name } == ['to-keep-1.3.jar']
                    assert unfilteredView.collect {it.name} == ['to-keep-1.3.jar', 'to-exclude-2.3.jar']
                }
            }

            class ArtifactFilter extends ArtifactTransform {
                boolean enableFilter
                ArtifactFilter(boolean enableFilter) {
                    this.enableFilter = enableFilter
                }
                
                List<File> transform(File input) {
                    if (!enableFilter) {
                        return [input]
                    }
                    if (input.name.startsWith('to-keep')) {
                        return [input]
                    }
                    return []
                }
            }
"""

        expect:
        succeeds "checkFiles"
    }

    // Documents current behaviour
    def "selects arbitrary transform when multiple can be applied"() {
        given:
        buildFile << """
            project(':lib') {
                task jar1(type: Jar) {
                    destinationDir = buildDir
                    archiveName = 'lib1.jar'
                }

                artifacts {
                    compile(jar1) {
                        type 'type1'
                    }
                }
            }

            project(':app') {
                dependencies {
                    compile project(':lib')
                }

                dependencies {
                    registerTransform {
                        from.attribute(artifactType, 'type1')
                        to.attribute(artifactType, 'transformed')
                    
                        artifactTransform(Type1Transform) {
                            params project.file("\${buildDir}/transform1")
                        }
                    }
                    registerTransform {
                        from.attribute(artifactType, 'type1')
                        to.attribute(artifactType, 'transformed')
                    
                        artifactTransform(Type2Transform) {
                            params project.file("\${buildDir}/transform2")
                        }
                    }
                }
    
                task resolve(type: Copy) {
                    def artifacts = configurations.compile.incoming.artifactView().attributes { it.attribute (artifactType, 'transformed') }.artifacts
                    from artifacts.artifactFiles
                    into "\${buildDir}/libs"
                    doLast {
                        println "files: " + artifacts.collect { it.file.name }
                        println "variants: " + artifacts.collect { it.variant.attributes }
                    }
                }
            }
    
            class Type1Transform extends ArtifactTransform {
                File outputDir
                Type1Transform(File outputDir) {
                    this.outputDir = outputDir
                    outputDir.mkdirs()
                }
                List<File> transform(File input) {
                    def output = new File(outputDir, 'out1')
                    if (!output.exists()) {
                        output << "content1"
                    }
                    return [output]
                }
            }

            class Type2Transform extends ArtifactTransform {
                File outputDir
                Type2Transform(File outputDir) {
                    this.outputDir = outputDir
                    outputDir.mkdirs()
                }
                List<File> transform(File input) {
                    def output = new File(outputDir, 'out2')
                    if (!output.exists()) {
                        output << "content2"
                    }
                    return [output]
                }
            }
        """

        when:
        succeeds "resolve"

        then:
        outputContains("variants: [{artifactType=transformed, usage=api}]")
        def buildDir = file('app/build')
        buildDir.eachFileRecurse {
            println it
        }
        buildDir.file('transform1').assertHasDescendants('out1')
        buildDir.file('libs').assertHasDescendants('out1')

        buildDir.file('libs/out1').text == "content1"
    }

    //TODO JJ: we currently ignore all configuration attributes for view creation - need to use incoming.getFiles(attributes) / incoming.getArtifacts(attributes) to create a view
    @NotYetImplemented
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
                    }
                }
                def artifactType = Attribute.of('artifactType', String)
                dependencies {
                    registerTransform {
                        from.attribute(artifactType, "jar")
                        to.attribute(artifactType, "size")
                        artifactTransform(FileSizer)
                    }
                }
                ext.checkArtifacts = { artifacts ->
                    assert artifacts.collect { it.id.displayName } == ['lib.size (project :lib)', 'lib.jar.txt (project :lib)']
                    assert artifacts.collect { it.file.name } == ['lib.size', 'lib.jar.txt']
                }
                ext.checkFiles = { config ->
                    assert config.collect { it.name } == ['lib.size', 'lib.jar.txt']
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

                        checkArtifacts configurations.compile.incoming.artifacts
                        checkArtifacts configurations.compile.resolvedConfiguration.resolvedArtifacts
                        checkArtifacts configurations.compile.resolvedConfiguration.lenientConfiguration.artifacts
                        checkArtifacts configurations.compile.resolvedConfiguration.lenientConfiguration.getArtifacts { true }
                    }
                }
            }
        """

        when:
        succeeds "resolve"

        then:
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
                    }
                }
                dependencies {
                    registerTransform {
                        from.attribute(artifactType, "jar")
                        to.attribute(artifactType, "size")
                        artifactTransform(FileSizer)
                    }
                }
                task resolve {
                    doLast {
                        // Query a bunch of times (without transform)
                        configurations.compile.collect { it.name }
                        configurations.compile.files.collect { it.name }
                        configurations.compile.resolvedConfiguration.files.collect { it.name }
                        configurations.compile.resolvedConfiguration.lenientConfiguration.files.collect { it.name }
                        configurations.compile.resolve().collect { it.name }
                        configurations.compile.files { true }.collect { it.name }
                        configurations.compile.fileCollection { true }.collect { it.name }

                        // Query a bunch of times (with transform)
                        configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'size') }.files.collect { it.name }
                        configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'size') }.artifacts.collect { it.file.name }
                        configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'size') }.artifacts.collect { it.id }
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

    def "Transform is executed twice for the same file with two different parameters"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            class TransformWithMultipleTargets extends ArtifactTransform {
                private String target
                TransformWithMultipleTargets(String target) {
                    this.target = target
                }
                
                List<File> transform(File input) {
                    if (target.equals("size")) {
                        def outSize = new File(outputDirectory, input.name + ".size")
                        if (!outSize.exists()) {
                            outSize.text = String.valueOf(input.length())
                            println "Transforming to size"
                        } 
                        return [outSize]
                    }
                    if (target.equals("hash")) {
                        def outHash = new File(outputDirectory, input.name + ".hash")
                        if (!outHash.exists()) {
                            outHash.text = 'hash'
                            println "Transforming to hash"
                        } 
                        return [outHash]
                    }             
                }
            }
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
                    assert configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'size') }.files.collect { it.name } == ['a.jar.size']
                    assert configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'hash') }.files.collect { it.name } == ['a.jar.hash']
                }
            }
        """

        when:
        succeeds "resolve"

        then:
        output.count("Transforming to size") == 1
        output.count("Transforming to hash") == 1
    }

    def "transformations are applied lazily in file collections"() {
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
            def configView = configurations.config2.incoming.artifactView().attributes { it.attribute(artifactType, 'size') }.files

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

        when:
        server.resetExpectations()
        m1.pom.expectGet()
        m1.artifact.expectGet()

        succeeds "queryFiles"

        then:
        output.count("Transforming") == 0

        when:
        server.resetExpectations()
        m2.pom.expectGet()
        m2.artifact.expectGet()

        succeeds "queryView"

        then:
        output.count("Transforming") == 1
        output.contains("Transforming test2-2.0.jar to test2-2.0.jar.txt")
    }

    def "user gets a reasonable error message when a transformation throws exception and continues with other inputs"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'
            def b = file('b.jar')
            b.text = '321'

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
        failure.assertHasCause("Error while transforming 'a.jar' to match attributes '{artifactType=size}' using 'TransformWithIllegalArgumentException'")
        failure.assertHasCause("broken")

        and:
        outputContains("Transforming b.jar")
    }

    def "user gets a reasonable error message when a transformation input cannot be downloaded and proceeds with other inputs"() {
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
        failure.assertHasCause("Error while transforming 'a.jar' to match attributes '{artifactType=size}' using 'ToNullTransform'")
        failure.assertHasCause("Illegal null output from ArtifactTransform")
    }

    def "user gets a reasonable error message when a output property returns a non-existing file"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            class ToNullTransform extends ArtifactTransform {
                List<File> transform(File input) {
                    return [new File('this_file_does_not.exist')]
                }
            }
            ${configurationAndTransform('ToNullTransform')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasDescription("Could not resolve all files for configuration ':compile'.")
        failure.assertHasCause("Error while transforming 'a.jar' to match attributes '{artifactType=size}' using 'ToNullTransform'")
        failure.assertHasCause("ArtifactTransform output 'this_file_does_not.exist' does not exist")
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
        failure.assertHasCause("Error while transforming 'broken.jar' to match attributes '{artifactType=size}' using 'TransformWithIllegalArgumentException'")
        failure.assertHasCause("broken: broken.jar")
        failure.assertHasCause("Could not download a.jar (test:a:1.3)")
        failure.assertHasCause("Error while transforming 'broken-2.0.jar' to match attributes '{artifactType=size}' using 'TransformWithIllegalArgumentException'")
        failure.assertHasCause("broken: broken-2.0.jar")

        and:
        outputContains("Transforming a.jar")
        outputContains("Transforming c.jar")
        outputContains("Transforming c-2.0.jar")
    }

    def configurationAndTransform(String transformImplementation) {
        """configurations {
                compile {
                }
            }
            dependencies {
                registerTransform {
                    from.attribute(artifactType, 'jar')
                    to.attribute(artifactType, 'size')
                    artifactTransform(${transformImplementation})
                }
            }

            task resolve(type: Copy) {
                def artifacts = configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'size') }.artifacts
                from artifacts.artifactFiles
                into "\${buildDir}/libs"
                doLast {
                    println "files: " + artifacts.collect { it.file.name }
                    println "variants: " + artifacts.collect { it.variant.attributes }
                }
            }
"""
    }


    }
