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
 * - AarTransform                   extracts 'jar', 'classes', 'classpath' and 'android-manifest' from AAR + predex
 * - JarTransform                   extracts 'classes' from jar, and allows 'jar' artifact to be used as 'classpath' type + predex
 * - ClassFolderTransform           allows 'classes' artifact to be used as 'classpath' type + predex
 */
abstract class AbstractAndroidFilterAndTransformIntegrationTest extends AbstractDependencyResolutionTest {

    def setup() {
        settingsFile << "rootProject.name = 'fake-android-build'"

        buildFile << """
            import java.util.regex.Pattern
            
            ${localJavaLibrary('java-lib')}
            ${localAndroidLibrary('android-lib')}
            ${localAndroidApp('android-app')}

            ${aarTransform()}
            ${jarTransform()}
            ${classFolderTransform()}
            ${preDexTool()}
        """.stripIndent()

        publishJavaModule("ext-java-lib")
        publishAndroidLibrary("ext-android-lib", false)
        publishAndroidLibrary("ext-android-lib-with-jars", true)
    }

    private MavenModule publishJavaModule(String name) {
        mavenRepo.module("org.gradle", name).publish()
    }

    private void publishAndroidLibrary(String name, boolean includeLibs) {
        // "Source" code
        file("$name/classes/main/foo.txt") << "something"
        file("$name/classes/main/bar/baz.txt") << "something"
        // Manifest and zipped code
        def aarImage = file(name + '/aar-image')
        aarImage.file('AndroidManifest.xml') << "<AndroidManifest/>"
        file(name + '/classes').zipTo(aarImage.file('classes.jar'))
        if (includeLibs) {
            file("$name/libs/jar-content.txt") << "jar-content"
            file("$name/libs").zipTo(aarImage.file('libs/dep1.jar'))
            file("$name/libs").zipTo(aarImage.file('libs/dep2.jar'))
        }

        // Publish an external version as AAR
        def module = mavenRepo.module("org.gradle", name).hasType('aar').publish()
        module.artifactFile.delete()
        aarImage.zipTo(module.artifactFile)
    }

    def localJavaLibrary(String name) {
        // "Source" code
        file("$name/classes/main/foo.txt") << "something"
        file("$name/classes/main/bar/baz.txt") << "something"

        settingsFile << "\ninclude '$name'"

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

        settingsFile << "\ninclude '$name'"

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
            
            task libs(type: Copy) {
                from file('libs')
                into file('aar-image/libs')
            }

            task aar(type: Zip) {
                dependsOn jar, libs
                from file('aar-image')
                destinationDir = file('build')
                extension = 'aar'
            }

            configurations {
                getByName("default").outgoing.variants {
                    classesOnly {
                        artifact(classes.destinationDir) {
                            type 'classes'
                            builtBy classes
                        }
                    }
                    manifestOnly {
                        artifact(file('aar-image/AndroidManifest.xml')) {
                            type 'android-manifest'
                        }
                    }
                }
                runtime.outgoing {
                    artifact jar
                }
                publish.outgoing {
                    artifact aar
                }
            }
        }
        """
    }

    def localAndroidApp(String name) {
        file(name).mkdirs()
        file("$name/classes/main/app/Main.txt") << "something"

        settingsFile << "\ninclude '$name'"

        """
        project(':$name') {
            apply plugin: 'base'

            configurations {
                resolve
            }
            
            def artifactType = Attribute.of('artifactType', String)
            
            boolean preDexLibrariesProp = findProperty('preDexLibraries') == null ? true : findProperty('preDexLibraries').toBoolean()
            boolean jumboModeProp = findProperty('jumboMode') == null ? false : findProperty('jumboMode').toBoolean()

            dependencies {
                ${registerTransform('AarTransform')}
                ${registerTransform('JarTransform')}
                ${registerTransform('ClassFolderTransform')}
            }

            repositories {
                maven { url '${mavenRepo.uri}' }
            }

            def requestedArtifactType = findProperty('requestedArtifactType')
            def configurationView = 
                requestedArtifactType == null 
                    ? configurations.resolve.incoming.getFiles()
                    : configurations.resolve.incoming.artifactView().attributes { it.attribute(artifactType, requestedArtifactType) }.files
            
            task printArtifacts {
                dependsOn configurationView
                doLast {
                    configurationView.each { println it.absolutePath - rootDir }
                }
            }
            
            def predexView = configurations.resolve.incoming.artifactView().attributes { it.attribute(artifactType, 'predex') }.files
            
            task classes(type: Copy) {
                from file('classes/main')
                into file('build/classes/main')
            }
            
            task dex {
                dependsOn predexView
                dependsOn classes
                doLast {
                    predexView.each { println it.absolutePath - rootDir }
                    def preDexedClasses = PreDexTool.preDex(project, [classes.destinationDir], project.file("transformed"), preDexLibrariesProp, jumboModeProp)[0]
                    println preDexedClasses.absolutePath - rootDir 
                }
            }
        }
        """
    }

    def aarTransform() {
        """
        class AarTransform extends ArtifactTransform {
            private Project files
            private boolean preDexLibraries
            private boolean jumboMode
            
            void configure(AttributeContainer from, ArtifactTransformTargets targets) {
                def typeAttribute = Attribute.of("artifactType", String)
                
                from.attribute(typeAttribute, "aar")
        
                targets.newTarget().attribute(typeAttribute, "jar")
                targets.newTarget().attribute(typeAttribute, "android-manifest")
                targets.newTarget().attribute(typeAttribute, "classes")
                targets.newTarget().attribute(typeAttribute, "predex")
                targets.newTarget().attribute(typeAttribute, "classpath")
            }
            
            List<File> transform(File input, AttributeContainer target) {
                File explodedAar = new File(outputDirectory, input.name + '/explodedAar')
                List<File> explodedJarList = []

                if (!explodedAar.exists()) {
                    files.copy {
                        from files.zipTree(input)   
                        into explodedAar
                    }
                }
                for (final File jarFile : findAllJars(explodedAar)) {
                    File explodedJar = new File(getOutputDirectory(), "expandedArchives/" + (input.path - files.rootDir).split(Pattern.quote(File.separator))[1] + "_" + input.name + "_" + jarFile.name)
                    explodedJarList.add(explodedJar)
                    if (!explodedJar.exists()) {
                        files.copy {
                            from files.zipTree(jarFile)
                            into explodedJar
                        }
                    }
                }
                    
                String targetType = target.getAttribute(Attribute.of("artifactType", String))
                switch (targetType) {
                    case 'jar':
                        return findAllJars(explodedAar)
                    case 'classes':
                        return explodedJarList
                    case 'predex':
                        return PreDexTool.preDex(files, explodedJarList, getOutputDirectory(), preDexLibraries, jumboMode)
                    case 'android-manifest':
                        return [new File(explodedAar, "AndroidManifest.xml")]
                    case 'classpath':
                        return findAllJars(explodedAar)
                    default:
                        throw new IllegalArgumentException("Not a supported target type: " + targetType)
                }
            }
            
            private List<File> findAllJars(File explodedAar) {
                List<File> allFiles = []
                allFiles.add(new File(explodedAar, "classes.jar"))
                File libsFolder = new File(explodedAar, "libs")
                if (libsFolder.exists()) {
                    for (File file : libsFolder.listFiles()) {
                        if (file.name.endsWith("jar")) {
                            allFiles.add(file)
                        }
                    }
                }
                return allFiles.sort()
            }
        }
        """
    }

    def jarTransform() {
        """
        class JarTransform extends ArtifactTransform {
            private Project files
            private boolean preDexLibraries
            private boolean jumboMode

            void configure(AttributeContainer from, ArtifactTransformTargets targets) {
                def typeAttribute = Attribute.of("artifactType", String)
                
                from.attribute(typeAttribute, "jar")
        
                targets.newTarget().attribute(typeAttribute, "classes")
                targets.newTarget().attribute(typeAttribute, "predex")
                targets.newTarget().attribute(typeAttribute, "classpath")
            }
        
            List<File> transform(File input, AttributeContainer target) {
                File classesFolder = new File(getOutputDirectory(), "expandedArchives/" + (input.path - files.rootDir).split(Pattern.quote(File.separator))[1] + "_" + input.name)
                if (!classesFolder.exists()) {
                    files.copy {
                        from files.zipTree(input)
                        into classesFolder
                    }
                }
                
                String targetType = target.getAttribute(Attribute.of("artifactType", String))
                switch (targetType) {
                    case 'classes':
                        return [classesFolder]
                    case 'predex':
                        return PreDexTool.preDex(files, [classesFolder], getOutputDirectory(), preDexLibraries, jumboMode)
                    case 'classpath':
                        return [input]
                    default:
                        throw new IllegalArgumentException("Not a supported target type: " + targetType)
                }
            }
        }
        """
    }

    def classFolderTransform() {
        """
        public class ClassFolderTransform extends ArtifactTransform {
            private Project files
            private boolean preDexLibraries
            private boolean jumboMode
            
            void configure(AttributeContainer from, ArtifactTransformTargets targets) {
                def typeAttribute = Attribute.of("artifactType", String)
                
                from.attribute(typeAttribute, "classes")
        
                targets.newTarget().attribute(typeAttribute, "classpath")
                targets.newTarget().attribute(typeAttribute, "predex")
            }
        
            List<File> transform(File input, AttributeContainer target) {        
                String targetType = target.getAttribute(Attribute.of("artifactType", String))
                switch (targetType) {
                    case 'predex':
                        return PreDexTool.preDex(files, [input], getOutputDirectory(), preDexLibraries, jumboMode)
                    case 'classpath':
                        return [input]
                    default:
                        throw new IllegalArgumentException("Not a supported target type: " + targetType)
                }
            }
        }
        """
    }

    def preDexTool() {
        """
        class PreDexTool {
            static List<File> preDex(Project files, List<File> input, File out, boolean preDexLibraries, boolean jumboMode)  {
                println "Running Predex for: " + input.collect { it.getPath() - files.rootDir }
                if (!preDexLibraries) {
                    return input
                }
        
                String jumbo = jumboMode ? "jumbo" : "noJumbo"
        
                File preDexFile = new File(out, "pre-dexed/" + (input[0].path - files.rootDir).split(Pattern.quote(File.separator))[1] + "_" + input[0].name + "_" + jumbo + ".predex")
                preDexFile.getParentFile().mkdirs()
                if (!preDexFile.exists()) {
                    preDexFile << "Predexed from: " + input.collect { it.getPath() - files.rootDir }
                }
                return [preDexFile]
            }
        }
        """
    }

    def registerTransform(String implementationName) {
        """
        registerTransform($implementationName) {
            outputDirectory = project.file("transformed")
            files = project
            preDexLibraries = preDexLibrariesProp
            jumboMode = jumboModeProp
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

    def libDependency(String libName, String notation) {
        buildFile << """
            project(':$libName') {
                dependencies {
                    compile ${notation}
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

    def dex(boolean preDexLibraries = true, boolean jumboMode = false) {
        executer.withArgument("-PpreDexLibraries=$preDexLibraries").withArgument("-PjumboMode=$jumboMode")

        assert succeeds('dex')

        def result = []
        TextUtil.normaliseFileSeparators(output).eachLine { line ->
            if (line.startsWith("/")) {
                result.add(line)
            }
        }
        result
    }

    int preDexExecutions() {
        int count = 0
        output.eachLine { line ->
            if (line.startsWith("Running Predex for:")) {
                count++
            }
        }
        count
    }
}
