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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.ivy.IvyFileModule
import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.spockframework.util.TextUtil
import spock.lang.Issue

public class IvyPublishIssuesIntegTest extends AbstractIvyPublishIntegTest {

    @Issue("GRADLE-2456")
    def "generates SHA1 file with leading zeros"() {
        given:
        def module = ivyRepo.module("org.gradle", "publish", "2")
        byte[] jarBytes = [0, 0, 0, 5]
        def artifactFile = file("testfile.bin")
        artifactFile << jarBytes
        def artifactPath = TextUtil.escape(artifactFile.path)
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
            apply plugin: 'ivy-publish'

            group = "org.gradle"
            version = '2'

            publishing {
                repositories {
                    ivy {
                        url = "${ivyRepo.uri}"
                    }
                }
                publications {
                    ivy(IvyPublication) {
                        configurations {
                            custom {}
                        }
                        artifact source: file("${artifactPath}"), name: 'testfile', type: 'bin'
                    }
                }
            }
        """
        when:
        succeeds 'publish'

        then:
        def shaOneFile = module.moduleDir.file("testfile-2.bin.sha1")
        shaOneFile.exists()
        shaOneFile.text == "00e14c6ef59816760e2c9b5a57157e8ac9de4012"
    }

    @Issue("https://github.com/gradle/gradle/issues/5136")
    void "doesn't publish if main artifact is missing"() {
        settingsFile << 'rootProject.name = "test"'
        buildFile << """
            apply plugin: "java-library"
            apply plugin: "ivy-publish"

            group = "org.gradle"
            version = "1.0"

            jar {
                enabled = Boolean.parseBoolean(project.getProperty("jarEnabled"))
            }

            publishing {
                repositories {
                    ivy { url = layout.buildDirectory.dir("repo") }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """
        file("src/main/java/hello/Hello.java") << """package hello;
            public class Hello {}
        """

        when:
        succeeds "publish", "-PjarEnabled=true"

        then:
        file("build/repo/org.gradle/test/1.0/test-1.0.jar").exists()
        file("build/repo/org.gradle/test/1.0/test-1.0.jar.sha1").exists()
        file("build/repo/org.gradle/test/1.0/test-1.0.jar.sha256").exists()
        file("build/repo/org.gradle/test/1.0/test-1.0.jar.sha512").exists()

        when:
        fails "publish", "-PjarEnabled=false"

        then:
        skipped(":jar")
        failure.assertHasCause("Artifact test-1.0.jar wasn't produced by this build.")
    }

    @Issue("https://github.com/gradle/gradle/issues/5136")
    void "doesn't publish stale files"() {
        IvyFileModule publishedModule

        settingsFile << 'rootProject.name = "test"'
        buildFile << """
            apply plugin: "java-library"
            apply plugin: "ivy-publish"

            group = "org.gradle"
            version = "1.0"

            java {
                withJavadocJar()
            }

            javadocJar {
                enabled = Boolean.parseBoolean(project.getProperty("javadocEnabled"))
            }

            publishing {
                repositories {
                    ivy { url = layout.buildDirectory.dir("repo") }
                }
                publications {
                    ivy(IvyPublication) {
                        from components.java
                    }
                }
            }
        """
        file("src/main/java/hello/Hello.java") << """package hello;
            public class Hello {}
        """

        when:
        succeeds "publish", "-PjavadocEnabled=true"
        publishedModule = new IvyFileRepository(new TestFile(file("build/repo"))).module("org.gradle", "test")

        then:
        file("build/repo/org.gradle/test/1.0/test-1.0-javadoc.jar").exists()
        file("build/repo/org.gradle/test/1.0/test-1.0-javadoc.jar.sha1").exists()
        file("build/repo/org.gradle/test/1.0/test-1.0-javadoc.jar.sha256").exists()
        file("build/repo/org.gradle/test/1.0/test-1.0-javadoc.jar.sha512").exists()
        publishedModule.parsedModuleMetadata.variant("javadocElements") {
            assert files*.name == ['test-1.0-javadoc.jar']
        }

        when:
        file("build/repo").deleteDir()
        succeeds "publish", "-PjavadocEnabled=false"
        publishedModule = new IvyFileRepository(new TestFile(file("build/repo"))).module("org.gradle", "test")

        then:
        skipped(":javadocJar")
        !file("build/repo/org.gradle/test/1.0/test-1.0-javadoc.jar").exists()
        !file("build/repo/org.gradle/test/1.0/test-1.0-javadoc.jar.sha1").exists()
        !file("build/repo/org.gradle/test/1.0/test-1.0-javadoc.jar.sha256").exists()
        !file("build/repo/org.gradle/test/1.0/test-1.0-javadoc.jar.sha512").exists()
        publishedModule.parsedModuleMetadata.variant("javadocElements") {
            assert files*.name == []
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/20581")
    void "warn deprecated behavior when GMM is modified after an Ivy publication is populated"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
                id("ivy-publish")
            }
            publishing {
                publications {
                    ivy(IvyPublication) {
                        from(components.java)
                    }
                }
            }
            publishing.publications.ivy.artifacts // Realize publication component
            components.java.withVariantsFromConfiguration(configurations.apiElements) { skip() }
        """

        when:
        executer.expectDocumentedDeprecationWarning(
            "Gradle Module Metadata is modified after an eagerly populated publication. " +
                "This behavior has been deprecated. This will fail with an error in Gradle 9.0. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_8.html#gmm_modification_after_publication_populated"
        )

        then:
        succeeds "help"
    }
}
