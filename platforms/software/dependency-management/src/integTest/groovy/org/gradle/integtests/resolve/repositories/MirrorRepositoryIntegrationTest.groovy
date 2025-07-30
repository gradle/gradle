/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.resolve.repositories

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule

class MirrorRepositoryIntegrationTest extends AbstractIntegrationSpec {

    def initScript = file("init.gradle")

    @Rule
    HttpServer server
    def module

    def setup() {
        server.start()
        MavenHttpRepository repo = new MavenHttpRepository(server, mavenRepo)
        module = repo.module("com.example", "example", "1.0").publish()

        propertiesFile << """
            systemProp.org.gradle.mirror.mavenCentral=${repo.uri}
        """

        initScript << """
            def canBeMirrored = r -> {
                // TODO: Plugin portal
                // TODO: Disallow mirroring if incompatible features are used
                return r.getName().equals(ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME) ||
                       r.getName().equals(org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler.GRADLE_PLUGIN_PORTAL_REPO_NAME)
            }
            def useMirror = r -> {
                String mirrorAvailable = System.getProperty("org.gradle.mirror.mavenCentral")
                if (mirrorAvailable != null) {
                    r.setUrl(mirrorAvailable)
                }
            }
            def configureMirror = repositories -> {
                repositories.withType(MavenArtifactRepository.class)
                    .matching(canBeMirrored)
                    .configureEach(useMirror)
            }
            gradle.beforeSettings(settings -> {
                configureMirror(settings.getBuildscript().getRepositories())
                configureMirror(settings.getPluginManagement().getRepositories())
            })
            gradle.settingsEvaluated(settings -> {
                configureMirror(settings.getDependencyResolutionManagement().getRepositories())
            })
            gradle.beforeProject(p -> {
                configureMirror(p.getBuildscript().getRepositories())
            })
            gradle.afterProject(p -> {
                configureMirror(p.getRepositories())
            })
        """
        buildFile << """
            configurations {
                resolve
            }

            dependencies {
                resolve 'com.example:example:1.0'
            }
        """
        executer.requireOwnGradleUserHomeDir("need to check behavior on the first use of any dependency resolution")
        executer.usingInitScript(initScript)
    }

    def "check resolution with mirror repository in pluginManagement for projects"() {
        settingsFile << """
            pluginManagement {
                ${mavenCentral()}
            }
        """
        buildFile.text = """
            plugins {
                id("com.example.example") version "1.0"
            }
        """ + buildFile.text
        expect:
        succeeds("buildEnvironment", "dependencies")
    }

    def "check resolution with mirror repository for projects for plugin portal"() {
        buildFile.text = """
            plugins {
                id("com.example.example") version "1.0"
            }
        """ + buildFile.text
        expect:
        succeeds("buildEnvironment", "dependencies")
    }

    def "check resolution with mirror repository for settings for plugin portal"() {
        settingsFile << """
            plugins {
                id("com.example.example") version "1.0"
            }
        """
        expect:
        succeeds("buildEnvironment", "dependencies")
    }

    def "check resolution with mirror repository for settings"() {
        settingsFile << """
            pluginManagement {
                ${mavenCentral()}
            }
            plugins {
                id("com.example.example") version "1.0"
            }
        """
        expect:
        succeeds("buildEnvironment", "dependencies")
    }

    def "check resolution with mirror repository for settings buildscript"() {
        settingsFile << """
            buildscript {
                ${mavenCentral()}

                dependencies {
                    classpath 'com.example:example:1.0'
                }
            }
        """
        module.pom.expectGet()
        module.artifact.expectGet()
        expect:
        succeeds("buildEnvironment", "dependencies")
    }

    def "check resolution with mirror repository for centralized repositories"() {
        settingsFile << """
            dependencyResolutionManagement {
                ${mavenCentral()}
            }
        """
        module.pom.expectGet()
        expect:
        succeeds("buildEnvironment", "dependencies")
    }

    def "check resolution with mirror repository for projects"() {
        buildFile << mavenCentral()
        module.pom.expectGet()
        expect:
        succeeds("buildEnvironment", "dependencies")
    }

    def "check resolution with mirror repository for project buildscript"() {
        buildFile.text = """
            buildscript {
                ${mavenCentral()}

                dependencies {
                    classpath 'com.example:example:1.0'
                }
            }
        """ + buildFile.text
        module.pom.expectGet()
        module.artifact.expectGet()
        expect:
        succeeds("buildEnvironment", "dependencies")
    }

    private static String mavenCentral() {
        """
            repositories {
                // NOTE: This must use mavenCentral() and not our test fixture for defining a repository
                mavenCentral()
            }
        """
    }
}
