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
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
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
        GRADLE_JAVASCRIPT('https://repo.gradle.org/gradle/javascript-public', mirrorUrl('gradlejavascript'), 'maven'),
        KOTLIN_DEV('https://packages.jetbrains.team/maven/p/kt/dev', mirrorUrl('kotlindev'), 'maven')

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

    /**
     * A repository for dependency versions under test that are not in the public repositories.
     * Test builds get it in addition to the repositories that they declare. See {@link RepoScriptBlockUtil#EXTRA_REPOSITORIES}.
     */
    static final class ExtraRepository {
        final String name
        final String url
        /** Gradle searches this repository only for the groups that match these regexes. */
        final List<String> groupRegexes

        ExtraRepository(String name, String url, List<String> groupRegexes) {
            this.name = name
            this.url = url
            this.groupRegexes = groupRegexes
        }
    }

    /**
     * Repositories for dependency versions not in the public repositories. Register an extra repository here,
     * not in the tests. See contributing/Testing.md.
     * Each entry has a condition, which the tests check when they start. A false condition gives {@code null}, and the list drops it.
     * Thus, builds get a repository only while a version under test needs it.
     *
     * The repositories blocks of this class declare these repositories (see {@link #extraRepositoriesDefinition}).
     * An init script declares them in every executer build, smoke test build and performance test build (see {@link #extraRepositoriesInitScript}).
     */
    private static final List<ExtraRepository> EXTRA_REPOSITORIES = ([
        kotlinDevVersionTested() ? new ExtraRepository(MirroredRepository.KOTLIN_DEV.name, MirroredRepository.KOTLIN_DEV.mirrorUrl, [/org\.jetbrains\.kotlin(\..+)?/]) : null
    ].findAll() as List<ExtraRepository>).asImmutable()

    private static boolean kotlinDevVersionTested() {
        new KotlinGradlePluginVersions().latests.any { KotlinGradlePluginVersions.isKotlinDevVersion(it) }
    }

    private static File extraRepositoriesInitScriptFile

    private RepoScriptBlockUtil() {
    }

    static String getMavenCentralMirrorUrl() {
        MirroredRepository.MAVEN_CENTRAL.mirrorUrl
    }

    static String mavenCentralRepository(GradleDsl dsl = GROOVY) {
        repositoriesBlock(dsl, mavenCentralRepositoryDefinition(dsl))
    }

    static String googleRepository(GradleDsl dsl = GROOVY) {
        repositoriesBlock(dsl, googleRepositoryDefinition(dsl))
    }

    /**
     * A repository block with the Plugin Portal and Maven Central. A build that applies {@code kotlin-dsl} resolves its dependencies from these repositories.
     */
    static String gradlePluginAndMavenCentralRepositories(GradleDsl dsl = GROOVY) {
        repositoriesBlock(dsl, gradlePluginRepositoryDefinition(dsl), mavenCentralRepositoryDefinition(dsl))
    }

    // Tooling API builds get no init script, so every repositories block of this class also declares the extra repositories.
    private static String repositoriesBlock(GradleDsl dsl, String... definitions) {
        return """
            repositories {
                ${definitions.join("\n")}
                ${extraRepositoriesDefinition(dsl)}
            }
        """
    }

    /**
     * Declarations of the extra repositories, see {@link #EXTRA_REPOSITORIES}.
     * The repositories blocks of this class, for example {@link #mavenCentralRepository}, include them.
     * The {@code ...RepositoryDefinition()} methods declare one repository without them.
     *
     * <p>{@code GradleExecuter} builds get the extra repositories from an init script anyway. Tooling API builds get no init script:
     * in them, declare the repositories with a repositories block of this class, or add these declarations.</p>
     */
    static String extraRepositoriesDefinition(GradleDsl dsl = GROOVY) {
        EXTRA_REPOSITORIES.collect { extraRepositoryDefinition(dsl, it) }.join("")
    }

    static String extraRepositoryDefinition(GradleDsl dsl = GROOVY, ExtraRepository repository) {
        def includes = repository.groupRegexes.collect { "includeGroupByRegex(\"${escapeBackslashes(it)}\")" }.join("\n")
        // Repository content filtering exists since Gradle 5.1. Cross-version tests run builds on Gradle 4.0 and later.
        // Kotlin DSL builds under test run on Gradle 5.1 or later, so the Kotlin variant needs no version check.
        if (dsl == KOTLIN) {
            """
                    maven {
                        name = "${repository.name}"
                        url = uri("${repository.url}")
                        content {
                            ${includes}
                        }
                    }
                """
        } else {
            """
                    maven {
                        name = '${repository.name}'
                        url = '${repository.url}'
                        if (org.gradle.util.GradleVersion.current() >= org.gradle.util.GradleVersion.version('5.1')) {
                            content {
                                ${includes}
                            }
                        }
                    }
                """
        }
    }

    private static String escapeBackslashes(String regex) {
        regex.replace('\\', '\\\\')
    }

    /** One repository declaration, without the extra repositories (see {@link #extraRepositoriesDefinition}). */
    static String mavenCentralRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.MAVEN_CENTRAL.getRepositoryDefinition(dsl)
    }

    /** One repository declaration, without the extra repositories (see {@link #extraRepositoriesDefinition}). */
    static String lightbendMavenRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.LIGHTBEND_MAVEN.getRepositoryDefinition(dsl)
    }

    /** One repository declaration, without the extra repositories (see {@link #extraRepositoriesDefinition}). */
    static String lightbendIvyRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.LIGHTBEND_IVY.getRepositoryDefinition(dsl)
    }

    static String getGoogleMirrorUrl() {
        MirroredRepository.GOOGLE.mirrorUrl
    }

    /** One repository declaration, without the extra repositories (see {@link #extraRepositoriesDefinition}). */
    static String googleRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.GOOGLE.getRepositoryDefinition(dsl)
    }

    static String gradleRepositoryMirrorUrl() {
        MirroredRepository.GRADLE.mirrorUrl
    }

    /** One repository declaration, without the extra repositories (see {@link #extraRepositoriesDefinition}). */
    static String gradleRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.GRADLE.getRepositoryDefinition(dsl)
    }

    static String gradlePluginRepositoryMirrorUrl() {
        MirroredRepository.GRADLE_PLUGIN.mirrorUrl
    }

    static String kotlinDevRepositoryMirrorUrl() {
        MirroredRepository.KOTLIN_DEV.mirrorUrl
    }

    /** One repository declaration, without the extra repositories (see {@link #extraRepositoriesDefinition}). */
    static String gradlePluginRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.GRADLE_PLUGIN.getRepositoryDefinition(dsl)
    }

    /**
     * Returns the init script that adds the extra repositories to a build. Returns {@code null} if there are no extra repositories.
     */
    static synchronized File extraRepositoriesInitScriptFile() {
        if (EXTRA_REPOSITORIES.empty) {
            return null
        }
        if (extraRepositoriesInitScriptFile == null) {
            File initScript = File.createTempFile("extra-repositories", ".gradle")
            initScript.deleteOnExit()
            initScript << extraRepositoriesInitScript()
            extraRepositoriesInitScriptFile = initScript
        }
        return extraRepositoriesInitScriptFile
    }

    static String extraRepositoriesInitScript(List<ExtraRepository> repositories = EXTRA_REPOSITORIES) {
        def data = repositories.collect { [name: it.name, url: it.url, groupRegexes: it.groupRegexes] }
        // Gradle does not accept a package statement in an init script. Keep the line empty, so line numbers in errors match the file.
        def source = RepoScriptBlockUtil.getResource("ExtraRepositories.groovy").getText().replaceFirst(/(?m)^package .*$/, "")
        return source + "\nExtraRepositories.declareIn(gradle, ${data.inspect()})\n"
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
