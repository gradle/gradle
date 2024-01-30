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

package org.gradle.integtests.resolve.transform

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import spock.lang.Issue

class DisambiguateArtifactTransformIntegrationTest extends AbstractHttpDependencyResolutionTest {

    private static String artifactTransform(String className, String extension = "txt", String message = "Transforming") {
        """
            import org.gradle.api.artifacts.transform.TransformParameters

            abstract class ${className} implements TransformAction<TransformParameters.None> {
                ${className}() {
                    println "Creating ${className}"
                }

                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    def output = outputs.file("\${input.name}.${extension}")
                    println "${message} \${input.name} to \${output.name}"
                    output.text = String.valueOf(input.length())
                }
            }
        """
    }

    def "disambiguates A -> B -> C and B -> C by selecting the latter"() {
        def m1 = mavenRepo.module("test", "test", "1.3").publish()
        m1.artifactFile.text = "1234"

        given:
        createDirs("lib", "app")
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'app'
        """

        file('lib/src/main/java/test/MyClass.java') << """
package test;

public class MyClass {
    public static void main(String[] args) {
        System.out.println("Hello world!");
    }
}
"""

        buildFile << """
def artifactType = Attribute.of('artifactType', String)

allprojects {
    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}
project(':lib') {
    apply plugin: 'java-library'
}

project(':app') {
    apply plugin: 'java'

    dependencies {
        implementation 'test:test:1.3'
        implementation project(':lib')
    }

    dependencies {
        registerTransform(FileSizer) { // B
            from.attribute(artifactType, 'java-classes-directory')
            from.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_API))
            from.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.CLASSES))

            to.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, 'size'))
        }
        registerTransform(FileSizer) { // A
            from.attribute(artifactType, 'jar')
            from.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_API))
            from.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))

            to.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_API))
            to.attribute(artifactType, 'java-classes-directory')
            to.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.CLASSES))
        }
    }

    task resolve(type: Copy) {
        def artifacts = configurations.compileClasspath.incoming.artifactView {
            attributes { it.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, 'size')) }
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
}

${artifactTransform("FileSizer")}
"""

        when:
        run "resolve"

        then:
        output.count("Transforming") == 3
        output.count("Transforming main to main.txt") == 1
        output.count("Transforming test-1.3.jar to test-1.3.jar.txt") == 1
        output.count("Transforming test-1.3.jar.txt to test-1.3.jar.txt.txt") == 1
    }

    def "disambiguates A -> C and B -> C by selecting the latter iff attributes match"() {
        def m1 = mavenRepo.module("test", "test", "1.3").publish()
        m1.artifactFile.text = "1234"

        given:
        createDirs("lib", "app")
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'app'
        """

        file('lib/src/main/java/test/MyClass.java') << """
package test;

public class MyClass {
    public static void main(String[] args) {
        System.out.println("Hello world!");
    }
}
"""

        buildFile << """
def artifactType = Attribute.of('artifactType', String)
def extraAttribute = Attribute.of('extra', String)

allprojects {
    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}
project(':lib') {
    apply plugin: 'java-library'
}

project(':app') {
    apply plugin: 'java'

    dependencies {
        implementation 'test:test:1.3'
        implementation project(':lib')
    }

    def hasExtraAttribute = providers.gradleProperty('extraAttribute').isPresent()

    dependencies {
        registerTransform(TestTransform) { // A
            from.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
            from.attribute(artifactType, 'java-classes-directory')
            to.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
            to.attribute(artifactType, 'final')

            if (hasExtraAttribute) {
                from.attribute(extraAttribute, 'whatever')
                to.attribute(extraAttribute, 'value1')
            }
        }
        registerTransform(TestTransform) { // B
            from.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
            from.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.JAR))
            from.attribute(artifactType, 'jar')
            to.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
            to.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.CLASSES))
            to.attribute(artifactType, 'final')

            if (hasExtraAttribute) {
                from.attribute(extraAttribute, 'whatever')
                to.attribute(extraAttribute, 'value2')
            }
        }
    }

    task resolve(type: Copy) {
        def artifacts = configurations.compileClasspath.incoming.artifactView {
            attributes { it.attribute(artifactType, 'final') }
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
}

${artifactTransform("TestTransform")}
"""

        when:
        run "resolve"

        then:
        output.count("Transforming") == 2
        output.count("Transforming lib.jar to lib.jar.txt")
        output.count("Transforming test-1.3.jar to test-1.3.jar.txt") == 1

        when:
        fails 'resolve', '-PextraAttribute'

        then:
        failureCauseContains('Found multiple transforms')
    }

    def "transform with two attributes will not confuse"() {
        given:
        buildFile << """
${artifactTransform("AarRelease", "release")}
${artifactTransform("AarDebug", "debug")}
${artifactTransform("AarClasses", "classes")}

def buildType = Attribute.of('buildType', String)
def artifactType = Attribute.of('artifactType', String)

projectDir.mkdirs()
def jar1 = file('lib1.jar')
jar1.text = 'some text'

configurations {
    compile
}

dependencies {
    attributesSchema {
        attribute(buildType)
    }

    compile files(jar1)

    registerTransform(AarRelease) {
        from.attribute(artifactType, 'jar')
        to.attribute(artifactType, 'aar')

        from.attribute(buildType, 'default')
        to.attribute(buildType, 'release')
    }

    registerTransform(AarDebug) {
        from.attribute(artifactType, 'jar')
        to.attribute(artifactType, 'aar')

        from.attribute(buildType, 'default')
        to.attribute(buildType, 'debug')
    }

    registerTransform(AarClasses) {
        from.attribute(artifactType, 'aar')
        to.attribute(artifactType, 'classes')
    }
}

task resolveReleaseClasses {
    def artifacts = configurations.compile.incoming.artifactView {
        attributes {
            it.attribute(artifactType, 'classes')
            it.attribute(buildType, 'release')
        }
    }.artifacts
    inputs.files artifacts.artifactFiles
    doLast {
        println "files: " + artifacts.collect { it.file.name }
        println "artifacts: " + artifacts.collect { it.file.name + " (" + it.id.componentIdentifier + ")" }
        println "components: " + artifacts.collect { it.id.componentIdentifier }
        println "variants: " + artifacts.collect { it.variant.attributes }
        println "content: " + artifacts.collect { it.file.text }
    }
}

task resolveTestClasses {
    def artifacts = configurations.compile.incoming.artifactView {
        attributes {
            it.attribute(artifactType, 'classes')
            it.attribute(buildType, 'test')
        }
    }.artifacts
    inputs.files artifacts.artifactFiles
    doLast {
        println "files: " + artifacts.collect { it.file.name }
        println "artifacts: " + artifacts.collect { it.file.name + " (" + it.id.componentIdentifier + ")" }
        println "components: " + artifacts.collect { it.id.componentIdentifier }
        println "variants: " + artifacts.collect { it.variant.attributes }
        println "content: " + artifacts.collect { it.file.text }
    }
}
        """

        when:
        run "resolveReleaseClasses"

        then:
        outputContains("files: [lib1.jar.release.classes]")
        outputContains("artifacts: [lib1.jar.release.classes (lib1.jar)]")
        outputContains("components: [lib1.jar]")
        outputContains("variants: [{artifactType=classes, buildType=release}]")
        outputContains("content: [1]")

        and:
        output.count("Transforming") == 2

        when:
        run "resolveTestClasses"

        then:
        outputContains("files: []")
        outputContains("artifacts: []")
        outputContains("components: []")
        outputContains("variants: []")
        outputContains("content: []")

        and:
        output.count("Transforming") == 0
    }

    @Issue("gradle/gradle#8363")
    def "attribute compatible transform but insufficient will not be selected"() {
        def m1 = mavenRepo.module("test", "test", "1.3").publish()
        m1.artifactFile.text = "1234"

        given:
        buildFile << """
def artifactType = Attribute.of('artifactType', String)
def minified = Attribute.of('minified', Boolean)

apply plugin: 'java'

allprojects {
    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}

dependencies {
    implementation 'test:test:1.3'

    artifactTypes.getByName("jar") {
        attributes.attribute(minified, false)
    }

    registerTransform(FileSizer) {
        from.attribute(artifactType, 'jar')
        to.attribute(artifactType, 'size')
    }

    registerTransform(Minifier) {
        from.attribute(minified, false)
        to.attribute(minified, true)
    }
}

${artifactTransform("Minifier", "min", "Minifying")}
${artifactTransform("FileSizer", "txt", "Sizing")}

task resolve(type: Copy) {
    def artifacts = configurations.compileClasspath.incoming.artifactView {
        attributes { it.attribute(minified, true) }
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
        when:
        succeeds "resolve"

        then:
        output.count("Minifying") == 1
        output.count('minified=true')
        output.count('Sizing') == 0
    }

    def "disambiguation leverages schema rules before doing it size based"() {
        given:
        createDirs("child")
        settingsFile << """
include('child')
"""
        buildFile << """
def artifactType = Attribute.of('artifactType', String)

apply plugin: 'java-library'

allprojects {
    repositories {
        maven { url "${mavenRepo.uri}" }
    }
}

project(':child') {
    configurations {
        runtimeOnly {
            canBeConsumed = false
            canBeResolved = false
        }
        runtimeElements {
            extendsFrom(runtimeOnly)
            assert canBeConsumed
            canBeResolved = false
            attributes {
                attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, Bundling.EXTERNAL))
            }
        }
    }


    artifacts {
        buildDir.mkdirs()
        file("\$buildDir/test.jar").text = "toto"
        runtimeOnly file("\$buildDir/test.jar")
    }
}

dependencies {
    api project(':child')

    artifactTypes.getByName("jar") {
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "weird"))
    }

    if ($apiFirst) {
        registerTransform(Identity) {
            from.attribute(artifactType, 'jar')
            from.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "weird"))
            to.attribute(artifactType, 'jar')
            to.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
        }
    }
    registerTransform(IllegalTransform) {
        from.attribute(artifactType, 'jar')
        from.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "weird"))
        to.attribute(artifactType, 'jar')
        to.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
    }
    if (!$apiFirst) {
        registerTransform(Identity) {
            from.attribute(artifactType, 'jar')
            from.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, "weird"))
            to.attribute(artifactType, 'jar')
            to.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_API))
        }
    }
    registerTransform(FileSizer) {
        from.attribute(artifactType, 'jar')
        to.attribute(artifactType, 'size')
    }
}

import org.gradle.api.artifacts.transform.TransformParameters

abstract class Identity implements TransformAction<TransformParameters.None> {
    @InputArtifact
    abstract Provider<FileSystemLocation> getInputArtifact()

    void transform(TransformOutputs outputs) {
        outputs.file(inputArtifact)
    }
}

abstract class IllegalTransform implements TransformAction<TransformParameters.None> {
    void transform(TransformOutputs outputs) {
        throw new IllegalStateException("IllegalTransform should not be invoked")
    }
}

${artifactTransform("FileSizer")}

task resolve(type: Copy) {
    def artifacts = configurations.compileClasspath.incoming.artifactView {
        attributes { it.attribute(artifactType, 'size') }
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
        when:
        succeeds "resolve"

        then:
        output.contains('variants: [{artifactType=size, org.gradle.dependency.bundling=external, org.gradle.libraryelements=jar, org.gradle.usage=java-api}]')

        where:
        apiFirst << [true, false]
    }
}
