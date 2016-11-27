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
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.util.TextUtil

/**
 * Overview of Configurations and 'formats' used in this scenario:
 *
 * Artifact types:
 * - aar                aar file
 * - jar                jar file
 * - classes            classes folder
 * - android-manifest   AndroidManifest.xml
 * - classpath          everything that can be in a JVM classpath (jar files, class folders, files treated as resources)
 *
 * Producer configurations:
 * - compile                        may provide classes-directory or jar file
 * - runtime                        always provides jar file
 * - publish                        provides jar file or AAR file for publishing
 *
 * Registered transforms:
 * - AarTransform                       extracts 'jar', 'classes', 'classpath' and 'android-manifest' from AAR
 * - JarTransform                       extracts 'classes' from jar, and allows 'jar' artifact to be used as 'classpath' type
 * - ClassesFolderClasspathTransform    allows 'classes' artifact to be used as 'classpath' type
 */
abstract class AbstractAARFilterAndTransformIntegrationTest extends AbstractDependencyResolutionTest {

    def setup() {
        settingsFile << """
            rootProject.name = 'fake-android-build'
            include 'java-lib'
            include 'android-lib'
            include 'android-app'
        """.stripIndent()

        buildFile << """
            ${localJavaLibrary('java-lib')}
            ${localAndroidLibrary('android-lib')}
            ${localAndroidApp('android-app')}

            ${aarTransform()}
            ${jarTransform()}
            ${classFolderTransform()}
        """.stripIndent()

        publishJavaModule("ext-java-lib")
        publishAndroidLibrary("ext-android-lib")
    }

    private MavenModule publishJavaModule(String name) {
        mavenRepo.module("org.gradle", name).publish()
    }

    private void publishAndroidLibrary(String name) {
        // "Source" code
        file("$name/classes/main/foo.txt") << "something"
        file("$name/classes/main/bar/baz.txt") << "something"
        // Manifest and zipped code
        def aarImage = file(name + '/aar-image')
        aarImage.file('AndroidManifest.xml') << "<AndroidManifest/>"
        file(name + '/classes').zipTo(aarImage.file('classes.jar'))

        // Publish an external version as AAR
        def module = mavenRepo.module("org.gradle", name).hasType('aar').publish()
        module.artifactFile.delete()
        aarImage.zipTo(module.artifactFile)
    }

    def localJavaLibrary(String name) {
        // "Source" code
        file("$name/classes/main/foo.txt") << "something"
        file("$name/classes/main/bar/baz.txt") << "something"

        """
        project(':$name') {
            configurations.create('default')
            configurations {
                compile
                runtime
                publish
            }
            configurations.default.extendsFrom = [configurations.compile]


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
                compile(classes.destinationDir) {
                    type 'classes'
                    builtBy classes
                }

                runtime jar
                publish jar
            }
        }
        """
    }

    def localAndroidLibrary(String name) {
        // "Source" code
        file("$name/classes/main/foo.txt") << "something"
        file("$name/classes/main/bar/baz.txt") << "something"

        """
        project(':$name') {
            apply plugin: 'base'

            configurations {
                compile
                runtime
                publish
            }
            configurations.default.extendsFrom = [configurations.compile]

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
                compile(classes.destinationDir) {
                    type 'classes'
                    builtBy classes
                }
                compile(file('aar-image/AndroidManifest.xml')) {
                    type 'android-manifest'
                }

                runtime jar
                publish aar
            }
        }
        """
    }

    def localAndroidApp(String name) {
        file('android-app').mkdirs()

        """
        project(':$name') {
            apply plugin: 'base'

            configurations {
                resolve
            }

            configurations.all {
                resolutionStrategy {
                    ${registerTransform('AarExtractor')}
                    ${registerTransform('JarTransform')}
                    ${registerTransform('ClassesFolderClasspathTransform')}
                }
            }

            repositories {
                maven { url '${mavenRepo.uri}' }
            }

            def requestedArtifactType = findProperty('requestedArtifactType')
            def configurationView = 
                requestedArtifactType == null 
                    ? configurations.resolve.incoming.getFiles()
                    : configurations.resolve.incoming.getFiles(artifactType: requestedArtifactType)
            
            task printArtifacts {
                dependsOn configurationView
                doLast {
                    configurationView.each { println it.absolutePath - rootDir }
                }
            }
        }
        """
    }

    def aarTransform() {
        """
        class AarExtractor extends ArtifactTypeTransform {
            private Project files

            private File explodedAar
            private File explodedJar
            
            String inputType = 'aar'
            List<String> outputTypes = ['jar', 'classpath', 'classes', 'android-manifest']
            
            File transform(File input, String targetType) {
                explodeAar(input)
                switch (targetType) {
                    case 'jar':
                    case 'classpath':
                        return new File(explodedAar, "classes.jar")
                    case 'classes':
                        return explodedJar
                    case 'android-manifest':
                        return new File(explodedAar, "AndroidManifest.xml")
                    default:
                        throw new IllegalArgumentException("Not a supported target type: " + targetType)
                }
            }

            private void explodeAar(File input) {
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
        """
        class JarTransform extends ArtifactTypeTransform {
            private Project files

            private File jar
            private File classesFolder

            String inputType = 'jar'
            List<String> outputTypes = ['classpath', 'classes']
            
            File transform(File input, String targetType) {
                explodeJar(input)
                switch (targetType) {
                    case 'classpath':
                        return jar
                    case 'classes':
                        return classesFolder
                    default:
                        throw new IllegalArgumentException("Not a supported target type: " + targetType)
                }
            }
            
            private void explodeJar(File input) {
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
        """
        class ClassesFolderClasspathTransform extends ArtifactTypeTransform {
            private Project files

            private File classesFolder
            
            String inputType = 'classes'
            List<String> outputTypes = ['classpath']
            
            File transform(File input, String targetType) {
                assert targetType == 'classpath'
                return input
            }
        }
        """
    }

    def  registerTransform(String implementationName) {
        """
        registerTransform($implementationName) {
            outputDirectory = project.file("transformed")
            files = project
        }
        """
    }

    def dependency(String notation) {
        buildFile << """
            project(':android-app') {
                dependencies {
                    resolve ${notation}
                }
            }
        """
    }

    def artifacts(String artifactType = null) {
        if (artifactType != null) {
            executer.withArgument("-PrequestedArtifactType=$artifactType")
        }

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
