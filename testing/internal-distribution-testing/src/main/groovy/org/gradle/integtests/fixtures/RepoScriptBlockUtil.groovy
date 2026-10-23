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

package org.gradle.integtests.fixtures

import groovy.transform.CompileStatic
import org.gradle.test.fixtures.dsl.GradleDsl

import static org.gradle.api.artifacts.ArtifactRepositoryContainer.GOOGLE_URL
import static org.gradle.api.artifacts.ArtifactRepositoryContainer.MAVEN_CENTRAL_URL
import static org.gradle.test.fixtures.dsl.GradleDsl.GROOVY
import static org.gradle.test.fixtures.dsl.GradleDsl.KOTLIN

@CompileStatic
class RepoScriptBlockUtil {
    static boolean isMirrorEnabled() {
        return !Boolean.parseBoolean(System.getenv("IGNORE_MIRROR"))
    }

    private static String mirrorUrl(String name) {
        String mirrorUrl = System.getProperty("org.gradle.integtest.mirrors." + name)
        if (mirrorUrl || !isMirrorEnabled()) {
            return mirrorUrl
        }
        return repoMirrorUrlsFromEnvironment()[name]
    }

    private static Map<String, String> repoMirrorUrlsFromEnvironment() {
        Map<String, String> result = [:]
        String repoMirrorUrls = System.getenv("REPO_MIRROR_URLS")
        if (repoMirrorUrls) {
            repoMirrorUrls.split(',').each { String nameToUrl ->
                String[] parts = nameToUrl.split(':', 2)
                if (parts.length == 2) {
                    result[parts[0]] = parts[1]
                }
            }
        }
        return result
    }

    static String repositoryDefinition(GradleDsl dsl = GROOVY, String type, String name, String url) {
        if (dsl == KOTLIN) {
            """
                    ${type} {
                        name = "${name}"
                        url = uri("${url}")
                    }
                """
        } else {
            """
                    ${type} {
                        name = '${name}'
                        url = '${url}'
                    }
                """
        }
    }

    private static enum MirroredRepository {
        MAVEN_CENTRAL(MAVEN_CENTRAL_URL, mirrorUrl('mavencentral'), "maven"),
        GOOGLE(GOOGLE_URL, mirrorUrl('google'), "maven"),
        LIGHTBEND_MAVEN("https://repo.lightbend.com/lightbend/maven-releases", mirrorUrl('lightbendmaven'), "maven"),
        LIGHTBEND_IVY("https://repo.lightbend.com/lightbend/ivy-releases", mirrorUrl('lightbendivy'), "ivy"),
        SPRING_RELEASES('https://repo.spring.io/release', mirrorUrl('springreleases'), 'maven'),
        SPRING_SNAPSHOTS('https://repo.spring.io/snapshot/', mirrorUrl('springsnapshots'), 'maven'),
        GRADLE('https://repo.gradle.org/gradle/repo', mirrorUrl('gradle'), 'maven'),
        JBOSS('https://repository.jboss.org/maven2/', mirrorUrl('jboss'), 'maven'),
        GRADLE_PLUGIN("https://plugins.gradle.org/m2", mirrorUrl('gradle-prod-plugins'), 'maven'),
        GRADLE_LIB_RELEASES('https://repo.gradle.org/gradle/libs-releases', mirrorUrl('gradle'), 'maven'),
        GRADLE_LIB_MILESTONES('https://repo.gradle.org/gradle/libs-milestones', mirrorUrl('gradle'), 'maven'),
        GRADLE_LIB_SNAPSHOTS('https://repo.gradle.org/gradle/libs-snapshots', mirrorUrl('gradle'), 'maven'),
        GRADLE_JAVASCRIPT('https://repo.gradle.org/gradle/javascript-public', mirrorUrl('gradlejavascript'), 'maven')

        String originalUrl
        String mirrorUrl
        String type

        private MirroredRepository(String originalUrl, String mirrorUrl, String type) {
            this.originalUrl = originalUrl
            this.mirrorUrl = mirrorUrl ?: originalUrl
            this.type = type
        }

        String getRepositoryDefinition(GradleDsl dsl = GROOVY) {
            repositoryDefinition(dsl, type, getName(), mirrorUrl)
        }

        String getName() {
            return mirrorUrl ? name() + "_MIRROR" : name()
        }
    }

    private RepoScriptBlockUtil() {
    }

    static String getMavenCentralMirrorUrl() {
        MirroredRepository.MAVEN_CENTRAL.mirrorUrl
    }

    static String mavenCentralRepository(GradleDsl dsl = GROOVY) {
        return """
            repositories {
                ${mavenCentralRepositoryDefinition(dsl)}
            }
        """
    }

    static String googleRepository(GradleDsl dsl = GROOVY) {
        return """
            repositories {
                ${googleRepositoryDefinition(dsl)}
            }
        """
    }

    static String mavenCentralRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.MAVEN_CENTRAL.getRepositoryDefinition(dsl)
    }

    static String lightbendMavenRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.LIGHTBEND_MAVEN.getRepositoryDefinition(dsl)
    }

    static String lightbendIvyRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.LIGHTBEND_IVY.getRepositoryDefinition(dsl)
    }

    static String getGoogleMirrorUrl() {
        MirroredRepository.GOOGLE.mirrorUrl
    }

    static String googleRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.GOOGLE.getRepositoryDefinition(dsl)
    }

    static String gradleRepositoryMirrorUrl() {
        MirroredRepository.GRADLE.mirrorUrl
    }

    static String gradleRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.GRADLE.getRepositoryDefinition(dsl)
    }

    static String gradlePluginRepositoryMirrorUrl() {
        MirroredRepository.GRADLE_PLUGIN.mirrorUrl
    }

    static String gradlePluginRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.GRADLE_PLUGIN.getRepositoryDefinition(dsl)
    }

    static File createMirrorInitScript() {
        File mirrors = File.createTempFile("mirrors", ".gradle")
        mirrors.deleteOnExit()
        mirrors << mirrorInitScript()
        return mirrors
    }

    static String mirrorInitScript() {
        def mirrorConditions = MirroredRepository.values().collect { MirroredRepository mirror ->
            """
                if (normalizeUrl(repo.url) == normalizeUrl('${mirror.originalUrl}')) {
                    repo.url = '${mirror.mirrorUrl}'
                }
            """
        }.join("")
        return """
            import groovy.transform.CompileStatic
            import groovy.transform.CompileDynamic
            import org.gradle.util.GradleVersion

            apply plugin: MirrorPlugin

            @CompileStatic
            class MirrorPlugin implements Plugin<Gradle> {
                void apply(Gradle gradle) {
                    def mirrorClosure = { Project project ->
                        project.buildscript.configurations["classpath"].incoming.beforeResolve {
                            withMirrors(project.buildscript.repositories)
                        }
                        project.afterEvaluate {
                            withMirrors(project.repositories)
                        }
                    }
                    applyToAllProjects(gradle, mirrorClosure)
                    maybeConfigurePluginManagement(gradle)
                    maybeConfigureDependencyResolutionManagement(gradle)
                }

                @CompileDynamic
                void applyToAllProjects(Gradle gradle, Closure projectClosure) {
                    if (GradleVersion.version(gradle.gradleVersion) >= GradleVersion.version("8.8")) {
                        gradle.lifecycle.beforeProject(projectClosure)
                    } else {
                        gradle.allprojects(projectClosure)
                    }
                }

                @CompileDynamic
                void maybeConfigurePluginManagement(Gradle gradle) {
                    if (GradleVersion.version(gradle.gradleVersion) >= GradleVersion.version("6.0")) {
                        gradle.beforeSettings { Settings settings ->
                            withMirrors(settings.pluginManagement.repositories)
                        }
                    } else if (GradleVersion.version(gradle.gradleVersion) >= GradleVersion.version("4.4")) {
                        gradle.settingsEvaluated { Settings settings ->
                            withMirrors(settings.pluginManagement.repositories)
                        }
                    }
                }

                @CompileDynamic
                void maybeConfigureDependencyResolutionManagement(Gradle gradle) {
                    if (GradleVersion.version(gradle.gradleVersion) >= GradleVersion.version("6.8")) {
                        gradle.beforeSettings { Settings settings ->
                            withMirrors(settings.dependencyResolutionManagement.repositories)
                        }
                    }
                }

                static void withMirrors(RepositoryHandler repos) {
                    repos.all { repo ->
                        if (repo instanceof MavenArtifactRepository) {
                            mirror(repo)
                        } else if (repo instanceof IvyArtifactRepository) {
                            mirror(repo)
                        }
                    }
                }

                static void mirror(MavenArtifactRepository repo) {
                    ${mirrorConditions}
                }

                static void mirror(IvyArtifactRepository repo) {
                    ${mirrorConditions}
                }

                // We see them as equal:
                // https://repo.maven.apache.org/maven2/ and http://repo.maven.apache.org/maven2
                static String normalizeUrl(Object url) {
                    String result = url.toString().replace('https://', 'http://')
                    return result.endsWith("/") ? result : result + "/"
                }
            }
        """
    }
}
