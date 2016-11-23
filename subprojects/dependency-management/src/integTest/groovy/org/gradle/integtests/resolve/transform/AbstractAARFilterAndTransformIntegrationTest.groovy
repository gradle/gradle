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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.util.TextUtil

/**
 * Overview of Configurations and 'formats' used in this scenario:
 *
 * Formats:
 * - aar                aar file
 * - jar                jar file
 * - classes            classes folder
 * - android-manifest   AndroidManifest.xml
 * - classpath          everything that can be in a JVM classpath (jar files, class folders, files treated as resources)
 *
 * Configurations:
 * - runtime                        behaves as runtime in Java plugin (e.g. packages classes in jars locally)
 * - compileClassesAndResources     provides all artifacts in its raw format (e.g. class folders, not jars)
 *
 * - processClasspath               filters and transforms to 'classpath' format (e.g. keeps jars, but extracts 'classes.jar' from external AAR)
 * - processClasses                 filters and transforms to 'classes' format (e.g. extracts jars to class folders)
 * - processManifests               filters for 'android-manifest' format (no transformation for local libraries, extraction from aar)
 */
abstract public class AbstractAARFilterAndTransformIntegrationTest extends AbstractDependencyResolutionTest {

    enum Feature {
        FILTER_LOCAL,       // Filter functionality - local artifacts (performance/issues/205)
        FILTER_EXTERNAL,    // Filter functionality - external artifacts (performance/issues/206)
        TRANSFORM           // Transform artifacts on-demand during filtering (performance/issues/206)
    }

    /**
     * Override to reduce the test scenario to only use a limited set of filter/transform features
     */
    def enabledFeatures() {
        [Feature.FILTER_LOCAL, Feature.FILTER_EXTERNAL, Feature.TRANSFORM]
    }

    def setup() {
        settingsFile << """
            rootProject.name = 'fake-android-build'
            include 'java-lib'
            include 'android-lib'
            include 'android-app'
        """.stripIndent()

        buildFile << """
            ${javaLibWithClassFolderArtifact('java-lib')}
            ${mockedAndroidLib('android-lib')}
            ${mockedAndroidApp('android-app')}

            ${aarTransform()}
            ${jarTransform()}
            ${classFolderTransform()}
        """.stripIndent()
    }

    def javaLibWithClassFolderArtifact(String name) {
        // "Source" code
        file("$name/classes/main/foo.txt") << "something"
        file("$name/classes/main/bar/baz.txt") << "something"
        // Publish an external version as JAR
        mavenRepo.module("org.gradle", "ext-$name").publish()

        """
        project(':$name') {
            configurations.create('default')
            configurations {
                compileClassesAndResources
                runtime
            }
            configurations.default.extendsFrom = [configurations.compileClassesAndResources]


            task classes(type: Copy) {
                from file('classes/main')
                into file('build/classes/main')
            }

            task jar(type: Zip) {
                dependsOn classes
                from classes.destinationDir
                destinationDir = file('build/libs')
                baseName = '$name'
                extension = 'jar'
            }

            artifacts {
                compileClassesAndResources(classes.destinationDir) {
                    ${formatInLocalArtifact('classes')}
                    builtBy classes
                }

                runtime jar
            }
        }
        """
    }

    def mockedAndroidLib(String name) {
        // "Source" code
        file("$name/classes/main/foo.txt") << "something"
        file("$name/classes/main/bar/baz.txt") << "something"
        // Manifest and zipped code
        def aarImage = file('android-lib/aar-image')
        aarImage.file('AndroidManifest.xml') << "<AndroidManifest/>"
        file('android-lib/classes').zipTo(aarImage.file('classes.jar'))

        // Publish an external version as AAR
        def module = mavenRepo.module("org.gradle", "ext-$name").hasType('aar').publish()
        module.artifactFile.delete()
        aarImage.zipTo(module.artifactFile)

        """
        project(':$name') {
            apply plugin: 'base'

            configurations {
                compileClassesAndResources
                runtime //compiles JAR as in Java plugin
                compileAAR
            }
            configurations.default.extendsFrom = [configurations.compileClassesAndResources]

            task classes(type: Copy) {
                from file('classes/main')
                into file('build/classes/main')
            }

            task jar(type: Zip) {
                dependsOn classes
                from classes.destinationDir
                destinationDir = file('aar-image')
                baseName = 'classes'
                extension = 'jar'
            }

            task aar(type: Zip) {
                dependsOn jar
                from file('aar-image')
                destinationDir = file('build')
                extension = 'aar'
            }

            artifacts {
                compileClassesAndResources(classes.destinationDir) {
                    ${formatInLocalArtifact('classes')}
                    builtBy classes
                }
                compileClassesAndResources(file('aar-image/AndroidManifest.xml')) {
                    ${formatInLocalArtifact('android-manifest')}
                }

                runtime jar

                compileAAR aar
            }
        }
        """
    }

    def mockedAndroidApp(String name) {
        file('android-app').mkdirs()

        """
        project(':$name') {
            apply plugin: 'base'

            configurations {
                compileClassesAndResources
                runtime

                // configurations with filtering/transformation over 'compile'
                processClasspath {
                    extendsFrom(compileClassesAndResources)
                    ${formatInConfiguration('classpath')} // 'classes' or 'jar'
                    resolutionStrategy {
                        ${registerTransform('AarExtractor')}
                        ${registerTransform('JarTransform')}
                        ${registerTransform('ClassesFolderClasspathTransform')}
                    }
                }
                processClasses {
                    extendsFrom(compileClassesAndResources)
                    ${formatInConfiguration('classes')}
                    resolutionStrategy {
                        ${registerTransform('AarExtractor')}
                        ${registerTransform('JarTransform')}
                    }
                }
                processJar {
                    extendsFrom(compileClassesAndResources)
                    ${formatInConfiguration('jar')}
                    resolutionStrategy {
                        ${registerTransform('AarExtractor')}
                    }
                }
                processManifests {
                    extendsFrom(compileClassesAndResources)
                    ${formatInConfiguration('android-manifest')}
                    resolutionStrategy {
                        ${registerTransform('AarExtractor')}
                    }
                }
            }

            repositories {
                maven { url '${mavenRepo.uri}' }
            }

            task printArtifacts {
                dependsOn configurations[configuration]
                doLast {
                    configurations[configuration].incoming.artifacts.each { println it.file.absolutePath - rootDir }
                }
            }
        }
        """
    }

    def aarTransform() {
        if (!enabledFeatures().contains(Feature.TRANSFORM)) {
            return ""
        }
        """
        @TransformInput(format = 'aar')
        class AarExtractor extends ArtifactTransform {
            private Project files

            private File explodedAar
            private File explodedJar

            @TransformOutput(format = 'jar')
            File getClassesJar() {
                new File(explodedAar, "classes.jar")
            }

            @TransformOutput(format = 'classpath')
            File getClasspathElement() {
                getClassesJar()
            }

            @TransformOutput(format = 'classes')
            File getClassesFolder() {
                explodedJar
            }

            @TransformOutput(format = 'android-manifest')
            File getManifest() {
                new File(explodedAar, "AndroidManifest.xml")
            }

            void transform(File input) {
                assert input.name.endsWith('.aar')

                explodedAar = new File(outputDirectory, input.name + '/explodedAar')
                explodedJar = new File(outputDirectory, input.name + '/explodedClassesJar')

                if (!explodedAar.exists()) {
                    files.copy {
                        from files.zipTree(input)
                        into explodedAar
                    }
                }
                if (!explodedJar.exists()) {
                    files.copy {
                        from files.zipTree(new File(explodedAar, 'classes.jar'))
                        into explodedJar
                    }
                }
            }
        }
        """
    }

    def jarTransform() {
        if (!enabledFeatures().contains(Feature.TRANSFORM)) {
            return ""
        }
        """
        @TransformInput(format = 'jar')
        class JarTransform extends ArtifactTransform {
            private Project files

            private File jar
            private File classesFolder

            @TransformOutput(format = 'classpath')
            File getClasspathElement() {
                jar
            }

            @TransformOutput(format = 'classes')
            File getClassesFolder() {
                classesFolder
            }

            void transform(File input) {
                jar = input

                //We could use a location based on the input, since the classes folder is similar for all consumers.
                //Maybe the output should not be configured from the outside, but the context of the consumer should
                //be always passed in automatically (as we do with "Project files") here. Then the consumer and
                //properties of it (e.g. dex options) can be used in the output location
                classesFolder = new File(outputDirectory, input.name + "/classes")
                if (!classesFolder.exists()) {
                    files.copy {
                        from files.zipTree(input)
                        into classesFolder
                    }
                }
            }
        }
        """
    }

    def classFolderTransform() {
        if (!enabledFeatures().contains(Feature.TRANSFORM)) {
            return ""
        }
        """
        @TransformInput(format = 'classes')
        class ClassesFolderClasspathTransform extends ArtifactTransform {
            private Project files

            private File classesFolder

            @TransformOutput(format = 'classpath')
            File getClasspathElement() {
                classesFolder
            }

            void transform(File input) {
                classesFolder = input
            }
        }
        """
    }

    def  registerTransform(String implementationName) {
        if (!enabledFeatures().contains(Feature.TRANSFORM)) {
            return ""
        }
        """
        registerTransform($implementationName) {
            outputDirectory = project.file("transformed")
            files = project
        }
        """
    }

    def formatInConfiguration(String formatName) {
        if (!enabledFeatures().contains(Feature.FILTER_LOCAL) && !enabledFeatures().contains(Feature.FILTER_EXTERNAL)) {
            return ""
        }
        """
        attributes artifactType: '$formatName'
        """
    }

    def formatInLocalArtifact(String formatName) {
        if (!enabledFeatures().contains(Feature.FILTER_LOCAL)) {
            return ""
        }
        """
        type '$formatName'
        """
    }

    def dependency(String notation) {
        dependency('compileClassesAndResources', notation)
    }

    def dependency(String configuration, String notation) {
        buildFile << """
            project(':android-app') {
                dependencies {
                    $configuration $notation
                }
            }
        """
    }

    def artifacts(String configuration) {
        executer.withArgument("-Pconfiguration=$configuration")

        assert succeeds('printArtifacts')

        def result = []
        TextUtil.normaliseFileSeparators(output).eachLine { line ->
            if (line.startsWith("/")) {
                result.add(line)
            }
        }
        result
    }
}
