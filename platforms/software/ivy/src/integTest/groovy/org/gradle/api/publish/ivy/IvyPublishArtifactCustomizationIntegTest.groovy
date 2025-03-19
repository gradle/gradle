/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.ivy.IvyDescriptorArtifact

class IvyPublishArtifactCustomizationIntegTest extends AbstractIvyPublishIntegTest {

    def module = ivyRepo.module("org.gradle.test", "ivyPublish", "2.4")

    void "can publish custom artifacts"() {
        given:
        createBuildScripts("""
            file("customFile.foo") << 'some foo'
            file("customFile.bar") << 'some bar'

            publications {
                ivy(IvyPublication) {
                    artifact "customFile.txt"
                    artifact customDocsTask.outputFile
                    artifact regularFileTask.outputFile
                    artifact customJar
                    artifact provider { file("customFile.foo") }
                    artifact provider { "customFile.bar" }
                }
            }
""", """
        model {
            tasks.publishIvyPublicationToIvyRepository {
              dependsOn "customDocsTask"
            }
        }
""")

        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "ivyPublish-2.4.txt",  "ivyPublish-2.4.foo", "ivyPublish-2.4.bar", "ivyPublish-2.4.html", "ivyPublish-2.4.reg", "ivyPublish-2.4.jar")
        result.assertTasksExecuted(":customDocsTask", ":customJar", ":regularFileTask", ":generateDescriptorFileForIvyPublication", ":publishIvyPublicationToIvyRepository", ":publish")

        and:
        def ivy = module.parsedIvy
        ivy.expectArtifact('ivyPublish', 'txt').hasType("txt").hasConf(null)
        ivy.expectArtifact('ivyPublish', 'html').hasType("html").hasConf(null)
        ivy.expectArtifact('ivyPublish', 'jar').hasType("jar").hasConf(null)
        ivy.expectArtifact('ivyPublish', 'reg').hasType("reg").hasConf(null)
        ivy.expectArtifact('ivyPublish', 'foo').hasType("foo").hasConf(null)
        ivy.expectArtifact('ivyPublish', 'bar').hasType("bar").hasConf(null)

        and:
        resolveArtifacts(module) {
            withoutModuleMetadata {
                expectFiles "ivyPublish-2.4.html", "ivyPublish-2.4.jar", "ivyPublish-2.4.reg", "ivyPublish-2.4.txt",  "ivyPublish-2.4.foo", "ivyPublish-2.4.bar"
            }
            withModuleMetadata {
                noComponentPublished()
            }
        }
    }

    def "can configure custom artifacts when creating"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    configurations {
                        foo {}
                        bar {}
                        "default" {
                            extend "foo"
                        }
                    }
                    artifact("customFile.txt") {
                        name = "customFile"
                        classifier = "classified"
                        conf = "foo,bar"
                    }
                    artifact(customDocsTask.outputFile) {
                        name = "docs"
                        extension = "htm"
                        builtBy customDocsTask
                    }
                    artifact(regularFileTask.outputFile) {
                        name = "regular"
                        extension = "txt"
                    }
                    artifact(customJar) {
                        extension = "war"
                        type = "web-archive"
                        conf = "*"
                    }
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "docs-2.4.htm", "customFile-2.4-classified.txt", "ivyPublish-2.4.war", "regular-2.4.txt")

        and:
        def ivy = module.parsedIvy
        ivy.expectArtifact("ivyPublish", "war").hasType("web-archive").hasConf(["*"])
        ivy.expectArtifact("docs", "htm").hasType("html").hasConf(null)
        ivy.expectArtifact("customFile", "txt", "classified").hasType("txt").hasConf(["foo", "bar"])
        ivy.expectArtifact("regular", "txt").hasType("reg").hasConf(null)

        and:
        resolveArtifacts(module){
            withoutModuleMetadata {
                expectFiles "customFile-2.4-classified.txt", "docs-2.4.htm", "ivyPublish-2.4.war", "regular-2.4.txt"
            }
            withModuleMetadata {
                noComponentPublished()
            }
        }
    }

    def "can publish custom file artifacts with map notation"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    configurations {
                        foo {}
                        bar {}
                        "default" {
                            extend "foo"
                        }
                    }
                    artifact source: "customFile.txt", name: "customFile", classifier: "classified", conf: "foo,bar"
                    artifact source: customDocsTask.outputFile, name: "docs", extension: "htm", builtBy: customDocsTask
                    artifact source: regularFileTask.outputFile, name: "regular", extension: "txt"
                    artifact source: customJar, extension: "war", type: "web-archive", conf: "*"
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "docs-2.4.htm", "customFile-2.4-classified.txt", "ivyPublish-2.4.war", "regular-2.4.txt")

        and:
        def ivy = module.parsedIvy
        ivy.expectArtifact("ivyPublish", "war").hasType("web-archive").hasConf(["*"])
        ivy.expectArtifact("docs", "htm").hasType("html").hasConf(null)
        ivy.expectArtifact("customFile", "txt", "classified").hasType("txt").hasConf(["foo", "bar"])
        ivy.expectArtifact("regular", "txt").hasType("reg").hasConf(null)

        and:
        resolveArtifacts(module) {
            withoutModuleMetadata {
                expectFiles "customFile-2.4-classified.txt", "docs-2.4.htm", "ivyPublish-2.4.war", "regular-2.4.txt"
            }
            withModuleMetadata {
                noComponentPublished()
            }
        }
    }

    def "can set custom artifacts to override component artifacts"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    from components.java
                    artifacts = ["customFile.txt", customDocsTask.outputFile, customJar]
                }
            }
""", """
            model {
                tasks.publishIvyPublicationToIvyRepository {
                    dependsOn("customDocsTask")
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "ivyPublish-2.4.module", "ivyPublish-2.4.txt", "ivyPublish-2.4.html", "ivyPublish-2.4.jar")
        module.parsedIvy.artifacts.collect({"${it.name}.${it.ext}"}) as Set == ["ivyPublish.txt", "ivyPublish.html", "ivyPublish.jar"] as Set
    }

    def "can configure custom artifacts post creation"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    configurations {
                        mod_conf {}
                        other {}
                    }
                    artifact source: "customFile.txt", name: "customFile"
                    artifact source: customDocsTask.outputFile, name: "docs", builtBy: customDocsTask
                    artifact source: customJar
                }
            }
""", """
            publishing.publications.ivy.artifacts.each {
                it.extension = "mod"
                it.conf = "mod_conf"
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "customFile-2.4.mod", "docs-2.4.mod", "ivyPublish-2.4.mod")

        for (IvyDescriptorArtifact artifact : module.parsedIvy.artifacts) {
            artifact.ext == "mod"
            artifact.conf == "mod-conf"
        }
    }

    def "can publish artifact with no extension"() {
        given:
        file("no-extension") << "some content"
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    artifact source: 'no-extension', name: 'no-extension', type: 'ext-less'
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "no-extension-2.4")
        module.parsedIvy.expectArtifact("no-extension").hasAttributes("", "ext-less", null)

        and:
        resolveArtifacts(module) {
            withoutModuleMetadata {
                expectFiles "no-extension-2.4"
            }
            withModuleMetadata {
                noComponentPublished()
            }
        }
    }

    def "can publish artifact with classifier"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    artifact source: customJar, classifier: "classy"
                }
            }
""")
        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.4.xml", "ivyPublish-2.4-classy.jar")
        module.parsedIvy.expectArtifact("ivyPublish").hasAttributes("jar", "jar", null, "classy")

        and:
        resolveArtifacts(module)  {
            withoutModuleMetadata {
                expectFiles "ivyPublish-2.4-classy.jar"
            }
            withModuleMetadata {
                noComponentPublished()
            }
        }
    }

    def "can add custom configurations"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    configurations {
                        runtime {}
                        base {}
                        custom {
                            extend "runtime"
                            extend "base"
                        }
                    }
                }
            }
""")

        when:
        succeeds 'publish'

        then:
        module.assertPublished()
        def ivy = module.parsedIvy
        ivy.configurations.keySet() == ["base", "custom", "runtime"] as Set
        ivy.configurations["runtime"].extend == null
        ivy.configurations["base"].extend == null
        ivy.configurations["custom"].extend == ["runtime", "base"] as Set
    }

    def "reports failure publishing when validation fails"() {
        given:
        file("a-directory.dir").createDir()

        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    artifact "a-directory.dir"
                }
            }
""")
        when:
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':publishIvyPublicationToIvyRepository'.")
        failure.assertHasCause("Failed to publish publication 'ivy' to repository 'ivy'")
        failure.assertHasCause("Invalid publication 'ivy': artifact file is a directory")
    }

    def "cannot publish when artifact does not exist"() {
        given:
        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    artifact source: "no-exist", type: "jar"
                }
            }
""")
        when:
        fails 'publish'

        then:
        failure.assertHasDescription("Execution failed for task ':publishIvyPublicationToIvyRepository'.")
        failure.assertHasCause("Failed to publish publication 'ivy' to repository 'ivy'")
        failure.assertHasCause("Could not read '${file('no-exist')}' as it does not exist.")
    }

    def "reports failure to convert artifact notation"() {
        given:
        file("a-directory.dir").createDir()

        createBuildScripts("""
            publications {
                ivy(IvyPublication) {
                    artifact 12
                }
            }
""")
        when:
        fails 'publish'

        then:
        failure.assertHasCause("""Cannot convert the provided notation to an object of type IvyArtifact: 12.
The following types/formats are supported:
  - Instances of IvyArtifact.
  - Instances of AbstractArchiveTask.
  - Instances of PublishArtifact.
  - Instances of Provider.
  - Maps containing a 'source' entry, for example [source: '/path/to/file', extension: 'zip'].
  - Anything that can be converted to a file, as per Project.file()""")
    }

    def "artifact coordinates are evaluated lazily"() {
        given:
        createBuildScripts("""
            publications.create("ivyCustom", IvyPublication) {
                artifact customJar
            }
        """, "version = 2.0")
        when:
        succeeds 'publish'

        then:
        def module = ivyRepo.module("org.gradle.test", "ivyPublish", "2.0")
        module.assertPublished()
        module.assertArtifactsPublished("ivy-2.0.xml", "ivyPublish-2.0.jar")
    }

    def "can attach an archive task provider as an artifact"() {
        createBuildScripts("""
            def customJar = tasks.register("myJar", Jar) {
                archiveClassifier = 'classy'
            }
            publications {
                mavenCustom(IvyPublication) {
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
                mavenCustom(IvyPublication) {
                    artifact(customTask)
                }
            }
        """)

        when:
        succeeds(":publish")

        then:
        executedAndNotSkipped ":myTask", ":publish"
    }

    private createBuildScripts(def publications, def append = "") {
        file("customFile.txt") << "some content"
        settingsFile << "rootProject.name = 'ivyPublish'"
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'ivy-publish'

            group = 'org.gradle.test'
            version = '2.4'

            task customDocsTask {
                ext.outputFile = file('customDocs.html')
                def outputFile = outputFile
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
                archiveBaseName = "customJar"
            }

            publishing {
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
                }
                $publications
            }

            $append
        """
    }

    @ToBeFixedForConfigurationCache
    def "dependencies with multiple dependency artifacts are mapped to multiple dependency declarations in GMM"() {
        def repoModule = javaLibrary(ivyRepo.module('group', 'root', '1.0'))

        given:
        settingsFile << "rootProject.name = 'root'"
        buildFile << """
            apply plugin: "java-library"
            apply plugin: "ivy-publish"

            group = 'group'
            version = '1.0'

            dependencies {
                implementation "org:foo:1.0"
                implementation("org:foo:1.0:classy") {
                    artifact {
                        name = "tarified"
                        type = "tarfile"
                        extension = "tar"
                        classifier = "ctar"
                        url = "http://new.home/tar"
                    }
                }
            }

            publishing {
                repositories {
                    ivy { url = "${ivyRepo.uri}" }
                }
                publications {
                    maven(IvyPublication) {
                        from components.java
                    }
                }
            }
        """

        when:
        succeeds "publish"

        then:
        repoModule.assertPublished()
        repoModule.assertApiDependencies()
        repoModule.parsedModuleMetadata.variant("runtimeElements") {
            dependency("org:foo:1.0") {
                // first dependency
                exists()
                noAttributes()
                // second dependency
                next()
                exists()
                noAttributes()
                artifactSelector.name == 'foo'
                artifactSelector.type == 'jar'
                artifactSelector.extension == 'jar'
                artifactSelector.classifier == 'classy'
                // third dependency
                next()
                exists()
                noAttributes()
                artifactSelector.name == 'foo'
                artifactSelector.type == 'jar'
                artifactSelector.extension == 'jar'
                artifactSelector.classifier == 'ctar'
                isLast()
            }
        }
    }

}
