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
    private File output

    void configure(AttributeContainer from, ArtifactTransformTargets targets) {
        from.attribute(Attribute.of('artifactType', String), "jar")
        targets.newTarget().attribute(Attribute.of('artifactType', String), "size")
    }

    List<File> transform(File input, AttributeContainer target) {
        output = new File(outputDirectory, input.name + ".txt")
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

    def "does not apply transform when not required to satisfy attributes"() {
        ivyRepo.module("test", "test", "1.3")
            .publish()
        ivyRepo.module("test", "test2", "2.3")
            .artifact(type: 'zip').publish()

        given:
        settingsFile << "include 'lib'"
        buildFile << """
            project(':lib') {
                task jar(type: Jar) {
                    destinationDir = buildDir
                    archiveName = 'lib1.jar'
                }
                task classes {
                    outputs.file("lib2.classes")
                }

                artifacts {
                    compile jar
                    compile file: file('lib2.classes'), builtBy: tasks.classes
                }
            }

            repositories {
                ivy { url "${ivyRepo.uri}" }
            }
            dependencies {
                compile 'test:test:1.3'
                compile 'test:test2:2.3'
                compile project(':lib')
                compile files('file1.jar', 'file2.txt')
            }

            ${registerTransform('FileSizer')}

            task resolve {
                doLast {
                    assert configurations.compile.files.collect { it.name } == ['file1.jar', 'file2.txt', 'test-1.3.jar', 'test2-2.3.zip', 'lib1.jar', 'lib2.classes']
                    assert configurations.compile.resolvedConfiguration.resolvedArtifacts.collect { it.file.name } == ['test-1.3.jar', 'test2-2.3.zip', 'lib1.jar', 'lib2.classes']
                }
            }
        """

        when:
        succeeds "resolve"

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
            
                void configure(AttributeContainer from, ArtifactTransformTargets targets) {
                    from.attribute(Attribute.of('artifactType', String), "jar")
                    targets.newTarget().attribute(Attribute.of('artifactType', String), "size")
                }
            
                List<File> transform(File input, AttributeContainer target) {
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
            
                void configure(AttributeContainer from, ArtifactTransformTargets targets) {
                    from.attribute(Attribute.of('artifactType', String), "jar")
                    targets.newTarget().attribute(Attribute.of('artifactType', String), "size")
                }
            
                List<File> transform(File input, AttributeContainer target) {
                    return []
                }
            }
"""

        when:
        succeeds "resolve"

        then:
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
            }
            
            ${registerTransform('ViewTransform')}

            task checkFiles {
                doLast {
                    assert configurations.compile.collect { it.name } == ['test-1.3.jar']
                    assert configurations.compile.incoming.artifactView().attributes{ it.attribute(viewType, 'transformed') }.files.collect { it.name } == ['transformed.txt']
                    assert configurations.compile.incoming.artifactView().attributes{ it.attribute(viewType, 'modified') }.files.collect { it.name } == ['modified.txt']
                }
            }

            class ViewTransform extends ArtifactTransform {
                void configure(AttributeContainer from, ArtifactTransformTargets targets) {
                    from.attribute(Attribute.of('artifactType', String), "jar")
                    targets.newTarget()
                        .attribute(Attribute.of('viewType', String), "transformed")
                        .attribute(Attribute.of('artifactType', String), "txt")
                    targets.newTarget()
                        .attribute(Attribute.of('viewType', String), "modified")
                        .attribute(Attribute.of('artifactType', String), "txt")
                }
            
                List<File> transform(File input, AttributeContainer target) {
                    String outputName = target.getAttribute(Attribute.of('viewType', String))
                    def output = new File(outputDirectory, outputName + ".txt")
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
            }
            
            ${registerTransform('ArtifactFilter')}

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
                void configure(AttributeContainer from, ArtifactTransformTargets targets) {
                    from.attribute(Attribute.of('artifactType', String), "jar")

                    targets.newTarget().attribute(Attribute.of('viewType', String), "filtered")
                    targets.newTarget().attribute(Attribute.of('viewType', String), "unfiltered")
                }
            
                List<File> transform(File input, AttributeContainer target) {
                    if (target.getAttribute(Attribute.of('viewType', String)) == "unfiltered") {
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

    def "transform can produce multiple outputs with different attributes for a single input"() {
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
                    compile(jar1) {
                        type 'type2'
                    }
                }
            }

            project(':app') {
                dependencies {
                    compile project(':lib')
                }

                dependencies {
                    registerTransform(Type1Transform) {
                        outputDirectory = project.file("\${buildDir}/transform1")
                    }
                    registerTransform(Type2Transform) {
                        outputDirectory = project.file("\${buildDir}/transform2")
                    }
                }
    
                task resolve(type: Copy) {
                    from configurations.compile.incoming.artifactView().attributes { it.attribute (artifactType, 'transformed') }.files
                    into "\${buildDir}/libs"
                }
            }
    
            class Type1Transform extends ArtifactTransform {
                void configure(AttributeContainer from, ArtifactTransformTargets targets) {
                    from.attribute(Attribute.of('artifactType', String), "type1")
                    targets.newTarget().attribute(Attribute.of('artifactType', String), "transformed")
                }
            
                List<File> transform(File input, AttributeContainer target) {
                    def output = new File(outputDirectory, 'out1')
                    if (!output.exists()) {
                        output << "content1"
                    }
                    return [output]
                }
            }

            class Type2Transform extends ArtifactTransform {
                void configure(AttributeContainer from, ArtifactTransformTargets targets) {
                    from.attribute(Attribute.of('artifactType', String), "type2")
                    targets.newTarget().attribute(Attribute.of('artifactType', String), "transformed")
                }
            
                List<File> transform(File input, AttributeContainer target) {
                    def output = new File(outputDirectory, 'out2')
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
        def buildDir = file('app/build')
        buildDir.eachFileRecurse {
            println it
        }
        buildDir.file('transform1').assertHasDescendants('out1')
        buildDir.file('transform2').assertHasDescendants('out2')
        buildDir.file('libs').assertHasDescendants('out1', 'out2')

        buildDir.file('libs/out1').text == "content1"
        buildDir.file('libs/out2').text == "content2"
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
                dependencies {
                    registerTransform(FileSizer) {
                        outputDirectory = project.file("\${buildDir}/transformed")
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
                    registerTransform(FileSizer) {
                        outputDirectory = project.file("\${buildDir}/transformed")
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

    def "Transform is executed twice for the same file for two different targets"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            class TransformWithMultipleTargets extends ArtifactTransform {

                void configure(AttributeContainer from, ArtifactTransformTargets targets) {
                    from.attribute(Attribute.of('artifactType', String), "jar")

                    targets.newTarget().attribute(Attribute.of('artifactType', String), "size")
                    targets.newTarget().attribute(Attribute.of('artifactType', String), "hash")
                }

                List<File> transform(File input, AttributeContainer target) {
                    if (target.getAttribute(Attribute.of('artifactType', String)).equals("size")) {
                        def outSize = new File(outputDirectory, input.name + ".size")
                        if (!outSize.exists()) {
                            outSize.text = String.valueOf(input.length())
                            println "Transforming to size"
                        } 
                        return [outSize]
                    }
                    if (target.getAttribute(Attribute.of('artifactType', String)).equals("hash")) {
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
                registerTransform(TransformWithMultipleTargets) {
                        outputDirectory = project.file("\${buildDir}/transformed")
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

            ${fileSizeConfigurationAndTransform()}

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

    def "User gets a reasonable error message when a transformation throws exception"() {
        given:
        buildFile << """
            def a = file('a.jar')
            a.text = '1234'

            dependencies {
                compile files(a)
            }

            class TransformWithIllegalArgumentException extends ArtifactTransform {

                void configure(AttributeContainer from, ArtifactTransformTargets targets) {
                    from.attribute(Attribute.of('artifactType', String), "jar")
                    targets.newTarget().attribute(Attribute.of('artifactType', String), "size")
                }

                List<File> transform(File input, AttributeContainer target) {
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

                void configure(AttributeContainer from, ArtifactTransformTargets targets) {
                    from.attribute(Attribute.of('artifactType', String), "jar")
                    targets.newTarget().attribute(Attribute.of('artifactType', String), "size")
                }

                List<File> transform(File input, AttributeContainer target) {
                    return null
                }
            }
            ${configurationAndTransform('ToNullTransform')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasCause("Error while transforming 'a.jar' to match attributes '{artifactType=size}' using 'ToNullTransform'")
        failure.assertHasCause("Illegal null output from ArtifactTransform")
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

                void configure(AttributeContainer from, ArtifactTransformTargets targets) {
                    from.attribute(Attribute.of('artifactType', String), "jar")
                    targets.newTarget().attribute(Attribute.of('artifactType', String), "size")
                }

                List<File> transform(File input, AttributeContainer target) {
                    return [new File('this_file_does_not.exist')]
                }
            }
            ${configurationAndTransform('ToNullTransform')}
        """

        when:
        fails "resolve"

        then:
        failure.assertHasCause("Error while transforming 'a.jar' to match attributes '{artifactType=size}' using 'ToNullTransform'")
        failure.assertHasCause("ArtifactTransform output 'this_file_does_not.exist' does not exist")
    }

    def configurationAndTransform(String transformImplementation) {
        """configurations {
                compile {
                }
            }
            ${registerTransform(transformImplementation)}

            task resolve(type: Copy) {
                from configurations.compile.incoming.artifactView().attributes { it.attribute(artifactType, 'size') }.files
                into "\${buildDir}/libs"
            }
"""
    }

    def registerTransform(String implementation) {
        """
            dependencies {
                registerTransform($implementation) {
                    outputDirectory = project.file("\${buildDir}/transformed")
                }  
            }
"""

    }

    def fileSizeConfigurationAndTransform() {
        configurationAndTransform('FileSizer')
    }
}
