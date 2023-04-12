/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.publish.maven.AbstractMavenPublishIntegTest
import spock.lang.Issue

class MavenPublishArtifactCustomizationIntegTest extends AbstractMavenPublishIntegTest {

    def "can attach custom artifacts"() {
        given:
        createBuildScripts("""
            file("customFile.foo") << 'some foo'
            file("customFile.bar") << 'some bar'

            publications {
                mavenCustom(MavenPublication) {
                    artifact "customFile.txt"
                    artifact customJar
                    artifact regularFileTask.outputFile
                    artifact provider { file("customFile.foo") }
                    artifact provider { "customFile.bar" }
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module("group", "projectText", "1.0")
        module.assertPublished()
        module.assertArtifactsPublished("projectText-1.0.pom", "projectText-1.0.txt", "projectText-1.0.foo", "projectText-1.0.bar", "projectText-1.0-customjar.jar", "projectText-1.0.reg")
        result.assertTasksExecuted(":customJar", ":regularFileTask", ":generatePomFileForMavenCustomPublication", ":publishMavenCustomPublicationToMavenRepository", ":publish")

        and:
        resolveArtifacts(module) {
            ext = 'txt'
            withModuleMetadata {
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles "projectText-1.0.txt"
            }
        }
        resolveArtifacts(module) {
            ext = 'foo'
            withModuleMetadata {
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles "projectText-1.0.foo"
            }
        }
        resolveArtifacts(module) {
            ext = 'bar'
            withModuleMetadata {
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles "projectText-1.0.bar"
            }
        }
        resolveArtifacts(module) {
            ext = 'reg'
            withModuleMetadata {
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles "projectText-1.0.reg"
            }
        }
        resolveArtifacts(module) {
            ext = 'jar'
            classifier = 'customjar'
            withModuleMetadata {
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles "projectText-1.0-customjar.jar"
            }
        }
    }

    /**
     * Fails with module metadata.
     * @see org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication#checkThatArtifactIsPublishedUnmodified
     */
    def "can modify artifacts added from component"() {
        given:
        createBuildScripts("""
            publications {
                maven(MavenPublication) {
                    from components.java
                }
            }
            publications.maven.artifacts.each {
                if (it.extension == 'jar') {
                    it.classifier = 'classified'
                }
            }
""", 'generateMetadataFileForMavenPublication.enabled = false')

        when:
        run "publish"

        then:
        def module = mavenRepo.module("group", "projectText", "1.0")
        module.assertPublished()
        module.assertArtifactsPublished("projectText-1.0-classified.jar", "projectText-1.0.pom")

        and:
        resolveArtifacts(module) {
            classifier = 'classified'
            ext = 'jar'
            withModuleMetadata {
                // here we have a publication, but artifacts have been modified, which
                // disables publication
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles "projectText-1.0-classified.jar"
            }
        }
    }

    /**
     * Fails with module metadata.
     * @see org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication#checkThatArtifactIsPublishedUnmodified
     */
    def "can override artifacts added from component"() {
        given:
        createBuildScripts("""
            publications {
                mavenCustom(MavenPublication) {
                    from components.java
                    artifacts = ["customFile.txt", customJar]
                }
            }
""", 'generateMetadataFileForMavenCustomPublication.enabled = false')
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module("group", "projectText", "1.0")
        module.assertPublished()
        module.parsedPom.packaging == "txt"
        module.assertArtifactsPublished("projectText-1.0.pom", "projectText-1.0.txt", "projectText-1.0-customjar.jar")

        and:
        resolveArtifacts(module) {
            classifier = 'customjar'
            withModuleMetadata {
                shouldFail {
                    // We have a publication but artifacts have been modified, which currently disables publication
                    assertHasCause 'Could not resolve all files'
                    assertHasCause 'Could not find group:projectText:1.0.'
                }
            }
            withoutModuleMetadata {
                expectFiles "projectText-1.0-customjar.jar"
            }
        }
    }

    /**
     * Cannot publish module metadata for component when artifacts are modified.
     * @see org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication#checkThatArtifactIsPublishedUnmodified
     */
    def "fails when publishing module metadata for component with modified artifacts"() {
        given:
        createBuildScripts("""
            publications {
                mavenCustom(MavenPublication) {
                    from components.java
                    artifacts = ["customFile.txt"]
                }
            }

""")
        when:
        fails 'publish'

        then:
        failure.assertHasCause("Cannot publish module metadata where component artifacts are modified.")
    }

    def "can configure custom artifacts when creating"() {
        given:
        createBuildScripts("""
            publications {
                mavenCustom(MavenPublication) {
                    artifact("customFile.txt") {
                        classifier "output"
                    }
                    artifact(customFileTask.outputFile) {
                        extension "htm"
                        classifier "documentation"
                        builtBy customFileTask
                    }
                    artifact(regularFileTask.outputFile) {
                        classifier "regular"
                        extension "txt"
                    }
                    artifact customJar {
                        archiveClassifier = null
                        archiveExtension = "war"
                    }
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module("group", "projectText", "1.0")
        module.assertPublished()
        module.parsedPom.packaging == "war"
        module.assertArtifactsPublished("projectText-1.0.pom", "projectText-1.0.war", "projectText-1.0-documentation.htm", "projectText-1.0-output.txt", "projectText-1.0-regular.txt")

        and:
        resolveArtifacts(module) {
            classifier = 'documentation'
            ext = 'htm'
            withModuleMetadata {
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles "projectText-1.0-documentation.htm"
            }
        }

        and:
        resolveArtifacts(module) {
            classifier = 'output'
            ext = 'txt'
            withModuleMetadata {
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles "projectText-1.0-output.txt"
            }
        }

        and:
        resolveArtifacts(module) {
            classifier = 'regular'
            ext = 'txt'
            withModuleMetadata {
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles "projectText-1.0-regular.txt"
            }
        }
    }

    def "can attach custom file artifacts with map notation"() {
        given:
        createBuildScripts("""
            publications {
                mavenCustom(MavenPublication) {
                    artifact source: "customFile.txt", classifier: "output"
                    artifact source: customFileTask.outputFile, extension: "htm", classifier: "documentation", builtBy: customFileTask
                    artifact source: regularFileTask.outputFile, extension: "txt", classifier: "regular"
                    artifact source: customJar, extension: "war", classifier: null
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module("group", "projectText", "1.0")
        module.assertPublished()
        module.parsedPom.packaging == "war"
        module.assertArtifactsPublished("projectText-1.0.pom", "projectText-1.0.war", "projectText-1.0-documentation.htm", "projectText-1.0-output.txt", "projectText-1.0-regular.txt")

        and:
        resolveArtifacts(module) {
            classifier = 'documentation'
            ext = 'htm'
            withModuleMetadata {
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles "projectText-1.0-documentation.htm"
            }
        }

        and:
        resolveArtifacts(module) {
            classifier = 'output'
            ext = 'txt'
            withModuleMetadata {
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles "projectText-1.0-output.txt"
            }
        }

        and:
        resolveArtifacts(module) {
            classifier = 'regular'
            ext = 'txt'
            withModuleMetadata {
                noComponentPublished()
            }
            withoutModuleMetadata {
                expectFiles "projectText-1.0-regular.txt"
            }
        }
    }

    def "can configure custom artifacts post creation"() {
        given:
        createBuildScripts("""
            publications {
                mavenCustom(MavenPublication) {
                    artifact "customFile.txt"
                    artifact customFileTask.outputFile
                    artifact customJar
                }
            }
""", """
            publishing.publications.mavenCustom.artifacts.each {
                if (it.extension == "html") {
                    it.classifier = "docs"
                    it.builtBy customFileTask
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module("group", "projectText", "1.0")
        module.assertPublished()
        module.assertArtifactsPublished("projectText-1.0.pom", "projectText-1.0.txt", "projectText-1.0-docs.html", "projectText-1.0-customjar.jar")
    }

    def "can attach artifact with no extension"() {
        given:
        createBuildScripts("""
            publications {
                mavenCustom(MavenPublication) {
                    from components.java
                    artifact source: "customFile.txt", extension: "", classifier: "classified"
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        def module = javaLibrary(mavenRepo.module("group", "projectText", "1.0"))
            .withClassifiedArtifact('classified', '')
        module.assertPublished()

        // TODO Find a way to resolve Maven artifact with no extension
//        and:
//        resolveArtifact(module, '', 'classified') == ["projectText-1.0-classifier"]
    }

    def "reports failure publishing when validation fails"() {
        given:
        file("a-directory.dir").createDir()

        createBuildScripts("""
            publications {
                mavenCustom(MavenPublication) {
                    artifact "a-directory.dir"
                }
            }
""")
        when:
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':publishMavenCustomPublicationToMavenRepository'.")
        failure.assertHasCause("Failed to publish publication 'mavenCustom' to repository 'maven'")
        failure.assertHasCause("Invalid publication 'mavenCustom': artifact file is a directory")
    }

    def "artifact coordinates are evaluated lazily"() {
        given:
        createBuildScripts("""
            publications.create("mavenCustom", MavenPublication) {
                artifact customJar
            }
        """, "version = 2.0")
        when:
        succeeds 'publish'

        then:
        def module = mavenRepo.module("group", "projectText", "2.0")
        module.assertPublished()
        module.assertArtifactsPublished("projectText-2.0.pom", "projectText-2.0-customjar.jar")
    }

    private createBuildScripts(def publications, def append = "") {
        settingsFile << "rootProject.name = 'projectText'"
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'maven-publish'

            group = 'group'
            version = '1.0'

            file("customFile.txt") << 'some content'

            task customFileTask {
                ext.outputFile = file('customFile-1.0-docs.html')
                def outputFile = ext.outputFile
                doLast {
                    outputFile << '<html/>'
                }
            }

            task regularFileTask {
                ext.outputFile = project.objects.fileProperty()
                outputs.file(outputFile)
                outputFile.set(file('regularFile-1.0.reg'))
                def outputFile = outputFile
                doLast {
                    outputFile.get().getAsFile() << 'foo'
                }
            }

            task customJar(type: Jar) {
                from file("customFile.txt")
                archiveClassifier = "customjar"
            }

            publishing {
                repositories {
                    maven { url "${mavenRepo.uri}" }
                }
                $publications
            }

            $append
        """
    }

    def "can attach a task provider as an artifact"() {
        createBuildScripts("""
            def customJar = tasks.register("myJar", Jar) {
                archiveClassifier = 'classy'
            }
            publications {
                mavenCustom(MavenPublication) {
                    artifact(customJar)
                }
            }
        """)

        when:
        succeeds(":publish")

        then:
        executedAndNotSkipped ":myJar", ":publish"
    }

    def "can attach an arbitrary task provider as an artifact if it has a single output file"() {
        createBuildScripts("""
            def customTask = tasks.register("myTask") {
                def buildDir = buildDir
                outputs.file("\${buildDir}/output.txt")
                def outputFile = file("\${buildDir}/output.txt")
                doLast {
                    outputFile << 'custom task'
                }
            }
            publications {
                mavenCustom(MavenPublication) {
                    artifact(customTask)
                }
            }
        """)

        when:
        succeeds(":publish")

        then:
        executedAndNotSkipped ":myTask", ":publish"
    }

    def "reasonable error message when an arbitrary task provider as an artifact has more than one output file"() {
        createBuildScripts("""
            def customTask = tasks.register("myTask") {
                outputs.file("\${buildDir}/output.txt")
                outputs.file("\${buildDir}/output2.txt")
                doLast {
                    file("\${buildDir}/output.txt") << 'custom task'
                    file("\${buildDir}/output2.txt") << 'custom task'
                }
            }
            publications {
                mavenCustom(MavenPublication) {
                    artifact(customTask)
                }
            }
        """)

        when:
        fails(":publish")

        then:
        failure.assertHasCause "Expected task 'myTask' output files to contain exactly one file, however, it contains more than one file."
    }

    def "can attach a mapped task provider output as an artifact"() {
        createBuildScripts("""
            def customJar = tasks.register("myJar", Jar) {
                archiveClassifier = 'classy'
            }
            publications {
                mavenCustom(MavenPublication) {
                    artifact(customJar.flatMap { it.archiveFile })
                }
            }
        """)

        when:
        succeeds(":publish")

        then:
        executedAndNotSkipped ":myJar", ":publish"
    }

    @Issue("https://github.com/gradle/gradle/issues/10960")
    def "can consume an arbitrary output from another project using the artifact notation"() {
        settingsFile << """
            rootProject.name = 'repro'
            include 'lib'
        """

        file('lib/build.gradle') << '''
            plugins {
                id 'java'
            }

            configurations.create("srcLicense") {
                canBeResolved = false
                assert canBeConsumed
            }

            def srcLicenseDir = tasks.register("srcLicenseDir", Sync) {
                into("$buildDir/$name")
                from("$rootDir/gradle")
            }

            artifacts {
                srcLicense(srcLicenseDir)
            }
        '''

        file("build.gradle") << """
            configurations {
                foo
            }
            dependencies {
                foo(project(path: ':lib', configuration: 'srcLicense'))
            }

            task resolve {
                def foo = configurations.foo
                doLast {
                    println "Output: \${foo.files.name}"
                    assert foo.files*.name.contains("srcLicenseDir")
                }
            }
        """

        when:
        succeeds ':resolve'

        then:
        outputContains('Output: [srcLicenseDir]')
    }

}
