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

class ArtifactTransformWithDependenciesIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def "transform can access artifact dependencies as FileCollection when using ArtifactView"() {

        given:
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
        registerTransform {
            from.attribute(artifactType, 'jar')
            to.attribute(artifactType, 'size')
            artifactTransform(FileSizer)
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

        // TODO LJA Investigate, this makes little sense
        executer.expectDeprecationWarning()


        when:
        run "resolve"

        then:
        output.count("Transforming") == 4
    }
}
