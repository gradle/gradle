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

class ArtifactTransformWithDependenciesIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def setup() {
        settingsFile << """
            rootProject.name = 'transform-deps'
            include 'common', 'lib', 'app'
        """

        def classContent = """
package test;

public class MyClass {
    public static void main(String[] args) {
        System.out.println("Hello world!");
    }
}
"""
        file('lib/src/main/java/test/MyClass.java') << classContent
        file('common/src/main/java/test/MyClass.java') << classContent

        buildFile << """
def artifactType = Attribute.of('artifactType', String)

allprojects {
    repositories {
        mavenCentral()
    }
}
project(':common') {
    apply plugin: 'java-library'
}

project(':lib') {
    apply plugin: 'java-library'

    dependencies {
        if (rootProject.hasProperty("useOldDependencyVersion")) {
            api 'org.slf4j:slf4j-api:1.7.24'
        } else {
            api 'org.slf4j:slf4j-api:1.7.25'
        }
        api project(':common')
    }
}

project(':app') {
    apply plugin: 'java'
    
    dependencies {
        implementation 'junit:junit:4.11'
        implementation project(':lib')
    }

    dependencies {
        registerTransform {
            from.attribute(artifactType, 'jar')
            to.attribute(artifactType, 'size')
            artifactTransform(TestTransform) {
                params('Single step transform')
            }
        }

        // Multi step transform
        registerTransform {
            from.attribute(artifactType, 'jar')
            to.attribute(artifactType, 'inter')
            artifactTransform(TestTransform) {
                params('Transform step 1')
            }
        }
        registerTransform {
            from.attribute(artifactType, 'inter')
            to.attribute(artifactType, 'final')
            artifactTransform(TestTransform) {
                params('Transform step 2')
            }
        }

        //Multi step transform, without dependencies at step1
        registerTransform {
            from.attribute(artifactType, 'jar')
            to.attribute(artifactType, 'middle')
            artifactTransform(SimpleTransform)
        }
        registerTransform {
            from.attribute(artifactType, 'middle')
            to.attribute(artifactType, 'end')
            artifactTransform(TestTransform) {
                params('Transform step 2')
            }
        }

    }
}

import javax.inject.Inject
import org.gradle.api.artifacts.transform.ArtifactTransformDependencies

class TestTransform extends ArtifactTransform {

    ArtifactTransformDependencies artifactDependencies
    String transformName

    @Inject
    TestTransform(String transformName, ArtifactTransformDependencies artifactDependencies) {
        this.transformName = transformName
        this.artifactDependencies = artifactDependencies
    }
    
    List<File> transform(File input) {
        println "\${transformName} received dependencies files \${artifactDependencies.files*.name} for processing \${input.name}"
        assert artifactDependencies.files.every { it.exists() }

        assert outputDirectory.directory && outputDirectory.list().length == 0
        def output = new File(outputDirectory, input.name + ".txt")
        println "Transforming \${input.name} to \${output.name}"
        output.text = String.valueOf(input.length())
        return [output]
    }
}

class SimpleTransform extends ArtifactTransform {

    List<File> transform(File input) {
        assert outputDirectory.directory && outputDirectory.list().length == 0
        def output = new File(outputDirectory, input.name + ".txt")
        println "Transforming without dependencies \${input.name} to \${output.name}"
        output.text = String.valueOf(input.length())
        return [output]
    }
}

"""
    }

    def "transform can access artifact dependencies as FileCollection when using ArtifactView"() {

        given:

        buildFile << """
project(':app') {
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
}

"""

        when:
        run "resolve"

        then:
        output.count('Transforming') == 5
        output.contains('Single step transform received dependencies files [slf4j-api-1.7.25.jar, common.jar] for processing lib.jar')
        output.contains('Single step transform received dependencies files [hamcrest-core-1.3.jar] for processing junit-4.11.jar')
    }

    def "transform can access artifact dependencies as FileCollection when using ArtifactView, even if first step did not use dependencies"() {

        given:

        buildFile << """
project(':app') {
    task resolve(type: Copy) {
        def artifacts = configurations.compileClasspath.incoming.artifactView {
            attributes { it.attribute(artifactType, 'end') }
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

"""

        when:
        run "resolve"

        then:
        output.count('Transforming') == 10
        output.contains('Transform step 2 received dependencies files [slf4j-api-1.7.25.jar.txt, common.jar.txt] for processing lib.jar.txt')
        output.contains('Transform step 2 received dependencies files [hamcrest-core-1.3.jar.txt] for processing junit-4.11.jar.txt')
    }

    def "transform can access artifact dependencies, in previous transform step, as FileCollection when using ArtifactView"() {

        given:

        buildFile << """
project(':app') {
    task resolve(type: Copy) {
        def artifacts = configurations.compileClasspath.incoming.artifactView {
            attributes { it.attribute(artifactType, 'final') }
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

"""

        when:
        run "resolve"

        then:
        output.count('Transforming') == 10
        output.contains('Transform step 1 received dependencies files [slf4j-api-1.7.25.jar, common.jar] for processing lib.jar')
        output.contains('Transform step 2 received dependencies files [slf4j-api-1.7.25.jar.txt, common.jar.txt] for processing lib.jar.txt')
        output.contains('Transform step 1 received dependencies files [hamcrest-core-1.3.jar] for processing junit-4.11.jar')
        output.contains('Transform step 2 received dependencies files [hamcrest-core-1.3.jar.txt] for processing junit-4.11.jar.txt')
    }

    def "transform can access artifact dependencies as FileCollection when using configuration attributes"() {

        given:

        buildFile << """
project(':app') {
    configurations {
        sizeConf {
            attributes.attribute(artifactType, 'size')
            extendsFrom compileClasspath
        }
    }

    task resolve(type: Copy) {
        def artifacts = configurations.sizeConf.incoming.artifacts
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
"""

        when:
        run "resolve"

        then:
        output.count("Transforming") == 5
        output.contains('Single step transform received dependencies files [slf4j-api-1.7.25.jar, common.jar] for processing lib.jar')
        output.contains('Single step transform received dependencies files [hamcrest-core-1.3.jar] for processing junit-4.11.jar')
    }

    def "transform with non-ABI change in dependencies are up-to-date"() {
        given:
        buildFile << """
project(':app') {
    task resolve(type: Copy) {
        def artifacts = configurations.compileClasspath.incoming.artifactView {
            attributes { it.attribute(artifactType, 'size') }
        }.artifacts
        from artifacts.artifactFiles
        into "\${buildDir}/libs"
    }
}
"""
        run "resolve", "-PuseOldDependencyVersion"

        when:
        run "resolve", "-PuseOldDependencyVersion", "--info"
        def outputLines = output.readLines()

        then:
        outputLines.count { it ==~ /Skipping TestTransform: .* as it is up-to-date./ } == 5
        outputLines.any { it ==~ /Skipping TestTransform: .*lib.jar as it is up-to-date./ }
        outputLines.any { it ==~ /Skipping TestTransform: .*slf4j-api-1.7.24.jar as it is up-to-date./ }
        outputLines.any { it ==~ /Skipping TestTransform: .*junit-4.11.jar as it is up-to-date./ }
        outputLines.any { it ==~ /Skipping TestTransform: .*hamcrest-core-1.3.jar as it is up-to-date./ }

        outputLines.count { it ==~ /TestTransform: .* is not up-to-date because:/ } == 0

        when:
        run "resolve", "--info"
        outputLines = output.readLines()

        then:
        outputLines.count { it ==~ /Skipping TestTransform: .* as it is up-to-date./ } == 4
        outputLines.any { it ==~ /Skipping TestTransform: .*junit-4.11.jar as it is up-to-date./ }
        outputLines.any { it ==~ /Skipping TestTransform: .*hamcrest-core-1.3.jar as it is up-to-date./ }
        outputLines.any { it ==~ /Skipping TestTransform: .*lib.jar as it is up-to-date./ }

        outputLines.count { it ==~ /TestTransform: .* is not up-to-date because:/ } == 1
        outputLines.any { it == "Single step transform received dependencies files [] for processing slf4j-api-1.7.25.jar" }
        outputLines.any { it ==~ /TestTransform: .*slf4j-api-1.7.25.jar is not up-to-date because:/ }
    }

    def "transform with ABI-change in dependencies are re-executed"() {
        given:
        buildFile << """
project(':app') {
    task resolve(type: Copy) {
        def artifacts = configurations.compileClasspath.incoming.artifactView {
            attributes { it.attribute(artifactType, 'size') }
        }.artifacts
        from artifacts.artifactFiles
        into "\${buildDir}/libs"
    }
}

project(':lib') {
    dependencies {
        if (rootProject.hasProperty("useOldABIDependencyVersion")) {
            api 'com.google.guava:guava:19.0'
        } else {
            api 'com.google.guava:guava:21.0'
        }         
    }
}
"""
        run "resolve", "-PuseOldABIDependencyVersion"

        when:
        run "resolve", "-PuseOldABIDependencyVersion", "--info"
        def outputLines = output.readLines()

        then:
        outputLines.count { it ==~ /Skipping TestTransform: .* as it is up-to-date./ } == 6
        outputLines.any { it ==~ /Skipping TestTransform: .*lib.jar as it is up-to-date./ }
        outputLines.any { it ==~ /Skipping TestTransform: .*slf4j-api-1.7.25.jar as it is up-to-date./ }
        outputLines.any { it ==~ /Skipping TestTransform: .*junit-4.11.jar as it is up-to-date./ }
        outputLines.any { it ==~ /Skipping TestTransform: .*hamcrest-core-1.3.jar as it is up-to-date./ }
        outputLines.any { it ==~ /Skipping TestTransform: .*guava-19.0.jar as it is up-to-date./ }

        outputLines.count { it ==~ /TestTransform: .* is not up-to-date because:/ } == 0

        when:
        run "resolve", "--info"
        outputLines = output.readLines()

        then:
        outputLines.count { it ==~ /Skipping TestTransform: .* as it is up-to-date./ } == 4
        outputLines.any { it ==~ /Skipping TestTransform: .*junit-4.11.jar as it is up-to-date./ }
        outputLines.any { it ==~ /Skipping TestTransform: .*hamcrest-core-1.3.jar as it is up-to-date./ }
        outputLines.any { it ==~ /Skipping TestTransform: .*slf4j-api-1.7.25.jar as it is up-to-date./ }

        outputLines.count { it ==~ /TestTransform: .* is not up-to-date because:/ } == 2
        outputLines.any { it ==~ /TestTransform: .*guava-21.0.jar is not up-to-date because:/ }
        outputLines.any { it == "Single step transform received dependencies files [] for processing guava-21.0.jar" }
        outputLines.any { it ==~ /TestTransform: .*lib.jar is not up-to-date because:/ }
        outputLines.any { it == "Single step transform received dependencies files [slf4j-api-1.7.25.jar, common.jar, guava-21.0.jar] for processing lib.jar" }
    }
}
