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
        // TODO LJA Investigate, this makes little sense
        executer.expectDeprecationWarning()

        settingsFile << """
            rootProject.name = 'transform-deps'
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
        mavenCentral()
    }
}
project(':lib') {
    apply plugin: 'java-library'

    dependencies {
        api 'org.slf4j:slf4j-api:1.7.25'
    }
}

project(':app') {
    apply plugin: 'java'
    
    dependencies {
        implementation 'junit:junit:4.11'
        implementation project(':lib')
    }

    dependencies {
        // Single step transform
        registerTransform {
            from.attribute(artifactType, 'jar')
            to.attribute(artifactType, 'size')
            artifactTransform(FileSizer)
        }

        // Multi step transform
        registerTransform {
            from.attribute(artifactType, 'jar')
            to.attribute(artifactType, 'inter')
            artifactTransform(FileSizer)
        }
        registerTransform {
            from.attribute(artifactType, 'inter')
            to.attribute(artifactType, 'final')
            artifactTransform(FileSizer)
        }
    }
}

import javax.inject.Inject
import org.gradle.api.artifacts.transform.ArtifactTransformDependencies

class FileSizer extends ArtifactTransform {

    ArtifactTransformDependencies artifactDependencies

    @Inject
    FileSizer(ArtifactTransformDependencies artifactDependencies) {
        this.artifactDependencies = artifactDependencies
        println "Creating FileSizer"
    }
    
    List<File> transform(File input) {
        println "Received dependencies files \${artifactDependencies.files*.name} for processing \${input.name}"

        assert outputDirectory.directory && outputDirectory.list().length == 0
        def output = new File(outputDirectory, input.name + ".txt")
        println "Transforming \${input.name} to \${output.name}"
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
        output.count('Transforming') == 4
        output.contains('Received dependencies files [slf4j-api-1.7.25.jar] for processing lib.jar')
        output.contains('Received dependencies files [hamcrest-core-1.3.jar] for processing junit-4.11.jar')
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
        output.count('Transforming') == 8
        output.contains('Received dependencies files [slf4j-api-1.7.25.jar] for processing lib.jar')
        output.contains('Received dependencies files [slf4j-api-1.7.25.jar.txt] for processing lib.jar.txt')
        output.contains('Received dependencies files [hamcrest-core-1.3.jar] for processing junit-4.11.jar')
        output.contains('Received dependencies files [hamcrest-core-1.3.jar.txt] for processing junit-4.11.jar.txt')
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
        output.count("Transforming") == 4
        // The asserts below should not have the .txt part
        output.contains('Received dependencies files [slf4j-api-1.7.25.jar] for processing lib.jar')
        output.contains('Received dependencies files [hamcrest-core-1.3.jar] for processing junit-4.11.jar')
    }
}
