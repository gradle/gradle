/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

import java.util.regex.Pattern

class ArtifactTransformCachingIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            include 'lib'
            include 'util'
            include 'app'
        """
    }

    def "transform is applied to each file once per build"() {
        given:
        buildFile << declareAttributes() << multiProjectWithJarSizeTransform() << withJarTasks()

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [lib1.jar.txt, lib2.jar.txt]") == 2
        output.count("ids: [lib1.jar.txt (project :lib), lib2.jar.txt (project :lib)]") == 2
        output.count("components: [project :lib, project :lib]") == 2

        output.count("Transformed") == 2
        isTransformed("lib1.jar", "lib1.jar.txt")
        isTransformed("lib2.jar", "lib2.jar.txt")

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [lib1.jar.txt, lib2.jar.txt]") == 2

        output.count("Transformed") == 0
    }

    def "early-discovered transform is applied before consuming task is executed"() {
        given:
        buildFile << declareAttributes() << multiProjectWithJarSizeTransform() << withJarTasks()

        when:
        succeeds ":util:resolve"

        def transformationPosition1 = output.indexOf("> Transform lib1.jar (project :lib) with FileSizer")
        def transformationPosition2 = output.indexOf("> Transform lib2.jar (project :lib) with FileSizer")
        def taskPosition = output.indexOf("> Task :util:resolve")

        then:
        transformationPosition1 >= 0
        transformationPosition2 >= 0
        taskPosition >= 0
        transformationPosition1 < taskPosition
        transformationPosition2 < taskPosition
    }

    def "each file is transformed once per set of configuration parameters"() {
        given:
        buildFile << declareAttributes() << """
            class TransformWithMultipleTargets extends ArtifactTransform {
                private String target
                
                @javax.inject.Inject
                TransformWithMultipleTargets(String target) {
                    this.target = target
                }
                
                List<File> transform(File input) {
                    assert input.exists()
                    assert outputDirectory.directory && outputDirectory.list().length == 0
                    if (target.equals("size")) {
                        def outSize = new File(outputDirectory, input.name + ".size")
                        println "Transformed \$input.name to \$outSize.name into \$outputDirectory"
                        outSize.text = String.valueOf(input.length())
                        return [outSize]
                    }
                    if (target.equals("hash")) {
                        def outHash = new File(outputDirectory, input.name + ".hash")
                        println "Transformed \$input.name to \$outHash.name into \$outputDirectory"
                        outHash.text = 'hash'
                        return [outHash]
                    }             
                }
            }
            
            allprojects {
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
                    def size = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts
                    def hash = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'hash') }
                    }.artifacts

                    inputs.files(size.artifactFiles)
                    inputs.files(hash.artifactFiles)

                    doLast {
                        println "files 1: " + size.collect { it.file.name }
                        println "ids 1: " + size.collect { it.id }
                        println "components 1: " + size.collect { it.id.componentIdentifier }
                        println "files 2: " + hash.collect { it.file.name }
                        println "ids 2: " + hash.collect { it.id }
                        println "components 2: " + hash.collect { it.id.componentIdentifier }
                    }
                }
            }

            project(':lib') {
                task jar1(type: Jar) {            
                    archiveName = 'lib1.jar'
                }
                task jar2(type: Jar) {            
                    archiveName = 'lib2.jar'
                }
                artifacts {
                    compile jar1
                    compile jar2
                }
            }
            
            project(':util') {
                dependencies {
                    compile project(':lib')
                }
            }

            project(':app') {
                dependencies {
                    compile project(':util')
                }
            }
        """

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files 1: [lib1.jar.size, lib2.jar.size]") == 2
        output.count("ids 1: [lib1.jar.size (project :lib), lib2.jar.size (project :lib)]") == 2
        output.count("components 1: [project :lib, project :lib]") == 2
        output.count("files 2: [lib1.jar.hash, lib2.jar.hash]") == 2
        output.count("ids 2: [lib1.jar.hash (project :lib), lib2.jar.hash (project :lib)]") == 2
        output.count("components 2: [project :lib, project :lib]") == 2

        output.count("Transformed") == 4
        isTransformed("lib1.jar", "lib1.jar.size")
        isTransformed("lib2.jar", "lib2.jar.size")
        isTransformed("lib1.jar", "lib1.jar.hash")
        isTransformed("lib2.jar", "lib2.jar.hash")

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files 1: [lib1.jar.size, lib2.jar.size]") == 2

        output.count("Transformed") == 0
    }

    def "can use custom type that does not implement equals() for transform configuration"() {
        given:
        buildFile << declareAttributes() << """
            class CustomType implements Serializable {
                String value
            }
            
            class TransformWithMultipleTargets extends ArtifactTransform {
                private CustomType target
                
                @javax.inject.Inject
                TransformWithMultipleTargets(CustomType target) {
                    this.target = target
                }
                
                List<File> transform(File input) {
                    assert input.exists()
                    assert outputDirectory.directory && outputDirectory.list().length == 0
                    if (target.value == "size") {
                        def outSize = new File(outputDirectory, input.name + ".size")
                        println "Transformed \$input.name to \$outSize.name into \$outputDirectory"
                        outSize.text = String.valueOf(input.length())
                        return [outSize]
                    }
                    if (target.value == "hash") {
                        def outHash = new File(outputDirectory, input.name + ".hash")
                        println "Transformed \$input.name to \$outHash.name into \$outputDirectory"
                        outHash.text = 'hash'
                        return [outHash]
                    }             
                }
            }
            
            allprojects {
                dependencies {
                    registerTransform {
                        from.attribute(artifactType, 'jar')
                        to.attribute(artifactType, 'size')
                        artifactTransform(TransformWithMultipleTargets) {
                            params(new CustomType(value: 'size'))
                        }
                    }
                    registerTransform {
                        from.attribute(artifactType, 'jar')
                        to.attribute(artifactType, 'hash')
                        artifactTransform(TransformWithMultipleTargets) {
                            params(new CustomType(value: 'hash'))
                        }
                    }
                }
                task resolve {
                    def size = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts
                    def hash = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'hash') }
                    }.artifacts

                    inputs.files(size.artifactFiles)
                    inputs.files(hash.artifactFiles)

                    doLast {
                        println "files 1: " + size.collect { it.file.name }
                        println "files 2: " + hash.collect { it.file.name }
                    }
                }
            }

            project(':lib') {
                task jar1(type: Jar) {            
                    archiveName = 'lib1.jar'
                }
                task jar2(type: Jar) {            
                    archiveName = 'lib2.jar'
                }
                artifacts {
                    compile jar1
                    compile jar2
                }
            }
            
            project(':util') {
                dependencies {
                    compile project(':lib')
                }
            }

            project(':app') {
                dependencies {
                    compile project(':util')
                }
            }
        """

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files 1: [lib1.jar.size, lib2.jar.size]") == 2
        output.count("files 2: [lib1.jar.hash, lib2.jar.hash]") == 2

        output.count("Transformed") == 4
        isTransformed("lib1.jar", "lib1.jar.size")
        isTransformed("lib2.jar", "lib2.jar.size")
        isTransformed("lib1.jar", "lib1.jar.hash")
        isTransformed("lib2.jar", "lib2.jar.hash")

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files 1: [lib1.jar.size, lib2.jar.size]") == 2

        output.count("Transformed") == 0
    }

    @Unroll
    def "can use configuration parameter of type #type"() {
        given:
        buildFile << declareAttributes() << """
            class TransformWithMultipleTargets extends ArtifactTransform {
                private $type target
                
                @javax.inject.Inject
                TransformWithMultipleTargets($type target) {
                    this.target = target
                }
                
                List<File> transform(File input) {
                    assert input.exists()
                    assert outputDirectory.directory && outputDirectory.list().length == 0
                    def outSize = new File(outputDirectory, input.name + ".value")
                    println "Transformed \$input.name to \$outSize.name into \$outputDirectory"
                    outSize.text = String.valueOf(input.length()) + String.valueOf(target)
                    return [outSize]
                }
            }
            
            allprojects {
                dependencies {
                    registerTransform {
                        from.attribute(artifactType, 'jar')
                        to.attribute(artifactType, 'value')
                        artifactTransform(TransformWithMultipleTargets) {
                            params($value)
                        }
                    }
                }
                task resolve {
                    def values = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'value') }
                    }.artifacts

                    inputs.files(values.artifactFiles)

                    doLast {
                        println "files 1: " + values.collect { it.file.name }
                        println "files 2: " + values.collect { it.file.name }
                    }
                }
            }

            project(':lib') {
                task jar1(type: Jar) {            
                    archiveName = 'lib1.jar'
                }
                task jar2(type: Jar) {            
                    archiveName = 'lib2.jar'
                }
                artifacts {
                    compile jar1
                    compile jar2
                }
            }
            
            project(':util') {
                dependencies {
                    compile project(':lib')
                }
            }

            project(':app') {
                dependencies {
                    compile project(':util')
                }
            }
        """

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files 1: [lib1.jar.value, lib2.jar.value]") == 2
        output.count("files 2: [lib1.jar.value, lib2.jar.value]") == 2

        output.count("Transformed") == 2
        isTransformed("lib1.jar", "lib1.jar.value")
        isTransformed("lib2.jar", "lib2.jar.value")

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files 1: [lib1.jar.value, lib2.jar.value]") == 2

        output.count("Transformed") == 0

        where:
        type           | value
        "boolean"      | "true"
        "int"          | "123"
        "List<Object>" | "[123, 'abc']"
        "Named"        | "objects.named(Named, 'abc')"
    }

    def "each file is transformed once per transform class"() {
        given:
        buildFile << declareAttributes() << """
            class Sizer extends ArtifactTransform {
                @javax.inject.Inject
                Sizer(String target) {
                    // ignore config
                }
                
                List<File> transform(File input) {
                    assert input.exists()
                    assert outputDirectory.directory && outputDirectory.list().length == 0
                    def outSize = new File(outputDirectory, input.name + ".size")
                    println "Transformed \$input.name to \$outSize.name into \$outputDirectory"
                    outSize.text = String.valueOf(input.length())
                    return [outSize]
                }
            }
            class Hasher extends ArtifactTransform {
                private String target
                
                @javax.inject.Inject
                Hasher(String target) {
                    // ignore config
                }
                
                List<File> transform(File input) {
                    assert input.exists()
                    assert outputDirectory.directory && outputDirectory.list().length == 0
                    def outHash = new File(outputDirectory, input.name + ".hash")
                    println "Transformed \$input.name to \$outHash.name into \$outputDirectory"
                    outHash.text = 'hash'
                    return [outHash]
                }
            }
            
            allprojects {
                dependencies {
                    registerTransform {
                        from.attribute(artifactType, 'jar')
                        to.attribute(artifactType, 'size')
                        artifactTransform(Sizer) { params('size') }
                    }
                    registerTransform {
                        from.attribute(artifactType, 'jar')
                        to.attribute(artifactType, 'hash')
                        artifactTransform(Hasher) { params('hash') }
                    }
                }
                task resolve {
                    def size = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts
                    def hash = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'hash') }
                    }.artifacts

                    inputs.files(size.artifactFiles)
                    inputs.files(hash.artifactFiles)

                    doLast {
                        println "files 1: " + size.collect { it.file.name }
                        println "ids 1: " + size.collect { it.id }
                        println "components 1: " + size.collect { it.id.componentIdentifier }
                        println "files 2: " + hash.collect { it.file.name }
                        println "ids 2: " + hash.collect { it.id }
                        println "components 2: " + hash.collect { it.id.componentIdentifier }
                    }
                }
            }

            project(':lib') {
                task jar1(type: Jar) {            
                    archiveName = 'lib1.jar'
                }
                task jar2(type: Jar) {            
                    archiveName = 'lib2.jar'
                }
                artifacts {
                    compile jar1
                    compile jar2
                }
            }
            
            project(':util') {
                dependencies {
                    compile project(':lib')
                }
            }

            project(':app') {
                dependencies {
                    compile project(':util')
                }
            }
        """

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files 1: [lib1.jar.size, lib2.jar.size]") == 2
        output.count("ids 1: [lib1.jar.size (project :lib), lib2.jar.size (project :lib)]") == 2
        output.count("components 1: [project :lib, project :lib]") == 2
        output.count("files 2: [lib1.jar.hash, lib2.jar.hash]") == 2
        output.count("ids 2: [lib1.jar.hash (project :lib), lib2.jar.hash (project :lib)]") == 2
        output.count("components 2: [project :lib, project :lib]") == 2

        output.count("Transformed") == 4
        isTransformed("lib1.jar", "lib1.jar.size")
        isTransformed("lib2.jar", "lib2.jar.size")
        isTransformed("lib1.jar", "lib1.jar.hash")
        isTransformed("lib2.jar", "lib2.jar.hash")

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files 1: [lib1.jar.size, lib2.jar.size]") == 2

        output.count("Transformed") == 0
    }

    def "transform is run again and old output is removed after it failed in previous build"() {
        given:
        buildFile << declareAttributes() << multiProjectWithJarSizeTransform() << withJarTasks()

        when:
        executer.withArgument("-Dbroken=true")
        fails ":app:resolve"

        then:
        failure.assertHasCause("Could not resolve all files for configuration ':app:compile'.")
        failure.assertHasCause("Failed to transform file 'lib1.jar' to match attributes {artifactType=size} using transform FileSizer")
        failure.assertHasCause("Failed to transform file 'lib2.jar' to match attributes {artifactType=size} using transform FileSizer")
        def outputDir1 = outputDir("lib1.jar", "lib1.jar.txt")
        def outputDir2 = outputDir("lib2.jar", "lib2.jar.txt")

        when:
        succeeds ":app:resolve"

        then:
        output.count("files: [lib1.jar.txt, lib2.jar.txt]") == 1

        output.count("Transformed") == 2
        isTransformed("lib1.jar", "lib1.jar.txt")
        isTransformed("lib2.jar", "lib2.jar.txt")
        outputDir("lib1.jar", "lib1.jar.txt") == outputDir1
        outputDir("lib2.jar", "lib2.jar.txt") == outputDir2

        when:
        succeeds ":app:resolve"

        then:
        output.count("files: [lib1.jar.txt, lib2.jar.txt]") == 1

        output.count("Transformed") == 0
    }

    def "transform is supplied with a different output directory when input file content changes between builds"() {
        given:
        buildFile << declareAttributes() << multiProjectWithJarSizeTransform() << withClassesSizeTransform() << withLibJarDependency()

        file("lib/lib1.jar").text = "123"
        file("lib/dir1.classes").file("child").createFile()

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir, lib1.jar.txt]") == 2

        output.count("Transformed") == 2
        isTransformed("dir1.classes", "dir1.classes.dir")
        isTransformed("lib1.jar", "lib1.jar.txt")
        def outputDir1 = outputDir("dir1.classes", "dir1.classes.dir")
        def outputDir2 = outputDir("lib1.jar", "lib1.jar.txt")

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir, lib1.jar.txt]") == 2

        output.count("Transformed") == 0

        when:
        file("lib/lib1.jar").text = "abc"
        file("lib/dir1.classes").file("child2").createFile()

        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir, lib1.jar.txt]") == 2

        output.count("Transformed") == 2
        isTransformed("dir1.classes", "dir1.classes.dir")
        isTransformed("lib1.jar", "lib1.jar.txt")
        outputDir("dir1.classes", "dir1.classes.dir") != outputDir1
        outputDir("lib1.jar", "lib1.jar.txt") != outputDir2

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir, lib1.jar.txt]") == 2

        output.count("Transformed") == 0
    }

    def "transform is rerun when output is removed between builds"() {
        given:
        buildFile << declareAttributes() << multiProjectWithJarSizeTransform() << withClassesSizeTransform() << withLibJarDependency()

        file("lib/lib1.jar").text = "123"
        file("lib/dir1.classes").file("child").createFile()

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir, lib1.jar.txt]") == 2

        output.count("Transformed") == 2
        isTransformed("dir1.classes", "dir1.classes.dir")
        isTransformed("lib1.jar", "lib1.jar.txt")
        def outputDir1 = outputDir("dir1.classes", "dir1.classes.dir")
        def outputDir2 = outputDir("lib1.jar", "lib1.jar.txt")

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir, lib1.jar.txt]") == 2

        output.count("Transformed") == 0

        when:
        outputDir1.deleteDir()
        outputDir2.deleteDir()

        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir, lib1.jar.txt]") == 2

        output.count("Transformed") == 2
        isTransformed("dir1.classes", "dir1.classes.dir")
        isTransformed("lib1.jar", "lib1.jar.txt")
        outputDir("dir1.classes", "dir1.classes.dir") == outputDir1
        outputDir("lib1.jar", "lib1.jar.txt") == outputDir2

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir, lib1.jar.txt]") == 2

        output.count("Transformed") == 0
    }

    def "transform is supplied with a different output directory when transform implementation changes"() {
        given:
        buildFile << declareAttributes() << multiProjectWithJarSizeTransform() << withClassesSizeTransform()

        file("lib/dir1.classes").file("child").createFile()

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir]") == 2

        output.count("Transformed") == 1
        isTransformed("dir1.classes", "dir1.classes.dir")
        def outputDir1 = outputDir("dir1.classes", "dir1.classes.dir")

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir]") == 2

        output.count("Transformed") == 0

        when:
        // change the implementation
        buildFile.text = ""
        buildFile << declareAttributes() << multiProjectWithJarSizeTransform(fileValue:  "'new value'") << withClassesSizeTransform()
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir]") == 2

        output.count("Transformed") == 1
        isTransformed("dir1.classes", "dir1.classes.dir")
        outputDir("dir1.classes", "dir1.classes.dir") != outputDir1

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir]") == 2

        output.count("Transformed") == 0
    }

    def "transform is supplied with a different output directory when configuration parameters change"() {
        given:
        // Use another script to define the value, so that transform implementation does not change when the value is changed
        def otherScript = file("other.gradle")
        otherScript.text = "ext.value = 123"

        buildFile << """
            apply from: 'other.gradle'
        """ << declareAttributes() << multiProjectWithJarSizeTransform(paramValue: "ext.value") << withClassesSizeTransform()

        file("lib/dir1.classes").file("child").createFile()

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir]") == 2

        output.count("Transformed") == 1
        isTransformed("dir1.classes", "dir1.classes.dir")
        def outputDir1 = outputDir("dir1.classes", "dir1.classes.dir")

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir]") == 2

        output.count("Transformed") == 0

        when:
        otherScript.replace('123', '123.4')
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir]") == 2

        output.count("Transformed") == 1
        isTransformed("dir1.classes", "dir1.classes.dir")
        outputDir("dir1.classes", "dir1.classes.dir") != outputDir1

        when:
        succeeds ":util:resolve", ":app:resolve"

        then:
        output.count("files: [dir1.classes.dir]") == 2

        output.count("Transformed") == 0
    }

    def "transform is supplied with a different output directory when external dependency changes"() {
        def m1 = mavenHttpRepo.module("test", "changing", "1.2").publish()
        def m2 = mavenHttpRepo.module("test", "snapshot", "1.2-SNAPSHOT").publish()

        given:
        buildFile << declareAttributes() << multiProjectWithJarSizeTransform() << """
            allprojects {
                repositories {
                    maven { url '$ivyHttpRepo.uri' }
                }
                configurations.all {
                    resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")
                    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
                }
            }

            project(':lib') {
                dependencies {
                    compile("test:changing:1.2") { changing = true }
                    compile("test:snapshot:1.2-SNAPSHOT")
                }
            }
        """

        when:
        m1.pom.expectGet()
        m1.artifact.expectGet()
        m2.metaData.expectGet()
        m2.pom.expectGet()
        m2.artifact.expectGet()

        succeeds ":app:resolve"

        then:
        output.count("files: [changing-1.2.jar.txt, snapshot-1.2-SNAPSHOT.jar.txt]") == 1

        output.count("Transformed") == 2
        isTransformed("changing-1.2.jar", "changing-1.2.jar.txt")
        isTransformed("snapshot-1.2-SNAPSHOT.jar", "snapshot-1.2-SNAPSHOT.jar.txt")
        def outputDir1 = outputDir("changing-1.2.jar", "changing-1.2.jar.txt")
        def outputDir2 = outputDir("snapshot-1.2-SNAPSHOT.jar", "snapshot-1.2-SNAPSHOT.jar.txt")

        when:
        // No changes
        server.resetExpectations()
        m1.pom.expectHead()
        m1.artifact.expectHead()
        m2.metaData.expectHead()
        // TODO - these should not be required for unique versions
        m2.pom.expectHead()
        m2.artifact.expectHead()

        succeeds ":app:resolve"

        then:
        output.count("files: [changing-1.2.jar.txt, snapshot-1.2-SNAPSHOT.jar.txt]") == 1

        output.count("Transformed") == 0

        when:
        // changing module has been changed
        server.resetExpectations()
        m1.publishWithChangedContent()
        m1.pom.expectHead()
        m1.pom.sha1.expectGet()
        m1.pom.expectGet()
        m1.artifact.expectHead()
        m1.artifact.sha1.expectGet()
        m1.artifact.expectGet()
        m2.metaData.expectHead()
        // TODO - these should not be required for unique versions
        m2.pom.expectHead()
        m2.artifact.expectHead()

        succeeds ":app:resolve"

        then:
        output.count("files: [changing-1.2.jar.txt, snapshot-1.2-SNAPSHOT.jar.txt]") == 1

        output.count("Transformed") == 1
        isTransformed("changing-1.2.jar", "changing-1.2.jar.txt")
        outputDir("changing-1.2.jar", "changing-1.2.jar.txt") != outputDir1

        when:
        // No changes
        server.resetExpectations()
        m1.pom.expectHead()
        m1.artifact.expectHead()
        m2.metaData.expectHead()
        // TODO - these should not be required for unique versions
        m2.pom.expectHead()
        m2.artifact.expectHead()

        succeeds ":app:resolve"

        then:
        output.count("files: [changing-1.2.jar.txt, snapshot-1.2-SNAPSHOT.jar.txt]") == 1

        output.count("Transformed") == 0

        when:
        // new snapshot version
        server.resetExpectations()
        m1.pom.expectHead()
        m1.artifact.expectHead()
        m2.publishWithChangedContent()
        m2.metaData.expectHead()
        m2.metaData.expectGet()
        m2.pom.expectHead()
        m2.pom.sha1.expectGet()
        m2.pom.expectGet()
        m2.artifact.expectHead()
        m2.artifact.sha1.expectGet()
        m2.artifact.expectGet()

        succeeds ":app:resolve"

        then:
        output.count("files: [changing-1.2.jar.txt, snapshot-1.2-SNAPSHOT.jar.txt]") == 1

        output.count("Transformed") == 1
        isTransformed("snapshot-1.2-SNAPSHOT.jar", "snapshot-1.2-SNAPSHOT.jar.txt")
        outputDir("snapshot-1.2-SNAPSHOT.jar", "snapshot-1.2-SNAPSHOT.jar.txt") != outputDir2
    }

    def multiProjectWithJarSizeTransform(Map options = [:]) {
        def paramValue = options.paramValue ?: "1"
        def fileValue = options.fileValue ?: "String.valueOf(input.length())"

        """
            ext.paramValue = $paramValue

            class FileSizer extends ArtifactTransform {
                @javax.inject.Inject
                FileSizer(Number value) {
                }

                List<File> transform(File input) {
                    assert outputDirectory.directory && outputDirectory.list().length == 0

                    assert input.exists()
                    
                    File output
                    if (input.file) {
                        output = new File(outputDirectory, input.name + ".txt")
                        output.text = $fileValue
                    } else {
                        output = new File(outputDirectory, input.name + ".dir")
                        output.mkdirs()
                        new File(output, "child.txt").text = "transformed"
                    }
                    println "Transformed \$input.name to \$output.name into \$outputDirectory"

                    if (System.getProperty("broken")) {
                        new File(outputDirectory, "some-garbage").text = "delete-me"
                        throw new RuntimeException("broken")
                    }

                    return [output]
                }
            }
    
            allprojects {
                dependencies {
                    registerTransform {
                        from.attribute(artifactType, "jar")
                        to.attribute(artifactType, "size")
                        artifactTransform(FileSizer) { params(paramValue) }
                    }
                }
                task resolve {
                    def size = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts

                    inputs.files size.artifactFiles

                    doLast {
                        println "files: " + size.artifactFiles.collect { it.name }
                        println "ids: " + size.collect { it.id.displayName }
                        println "components: " + size.collect { it.id.componentIdentifier }
                    }
                }
            }
    
            project(':util') {
                dependencies {
                    compile project(':lib')
                }
            }
    
            project(':app') {
                dependencies {
                    compile project(':util')
                }
            }
        """
    }

    def withJarTasks() {
        """
            project(':lib') {
                task jar1(type: Jar) {
                    archiveName = 'lib1.jar'
                }
                task jar2(type: Jar) {
                    archiveName = 'lib2.jar'
                }
                artifacts {
                    compile jar1
                    compile jar2
                }
            }
        """
    }

    def withClassesSizeTransform() {
        """
            allprojects {
                dependencies {
                    registerTransform {
                        from.attribute(artifactType, "classes")
                        to.attribute(artifactType, "size")
                        artifactTransform(FileSizer) { params(paramValue) }
                    }
                }
            }
            project(':lib') {
                artifacts {
                    compile file("dir1.classes")
                }
            }
        """
    }

    def withLibJarDependency() {
        """
            project(':lib') {
                dependencies {
                    compile files("lib1.jar")
                }
            }
        """
    }

    def declareAttributes() {
        """
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
                        attributes.attribute usage, 'api'
                    }
                }
            }
        """
    }

    void isTransformed(String from, String to) {
        def dirs = outputDirs(from, to)
        if (dirs.size() == 0) {
            throw new AssertionError("Could not find $from -> $to in output: $output")
        }
        if (dirs.size() > 1) {
            throw new AssertionError("Found $from -> $to more than once in output: $output")
        }
        assert output.count("into " + dirs.first()) == 1
    }

    TestFile outputDir(String from, String to) {
        def dirs = outputDirs(from, to)
        if (dirs.size() == 1) {
            return dirs.first()
        }
        throw new AssertionError("Could not find exactly one output directory for $from -> $to in output: $output")
    }

    Set<TestFile> outputDirs(String from, String to) {
        Set<TestFile> dirs = []
        def baseDir = executer.gradleUserHomeDir.file("/caches/transforms-1/files-1.1/" + from).absolutePath + File.separator
        def pattern = Pattern.compile("Transformed " + Pattern.quote(from) + " to " + Pattern.quote(to) + " into (" + Pattern.quote(baseDir) + "\\w+)")
        for (def line : output.readLines()) {
            def matcher = pattern.matcher(line)
            if (matcher.matches()) {
                dirs.add(new TestFile(matcher.group(1)))
            }
        }
        return dirs
    }

}
