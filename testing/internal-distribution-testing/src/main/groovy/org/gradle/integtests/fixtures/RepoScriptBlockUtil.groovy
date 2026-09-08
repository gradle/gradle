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
        MAVEN_CENTRAL(MAVEN_CENTRAL_URL, System.getProperty('org.gradle.integtest.mirrors.mavencentral'), "maven"),
        GOOGLE(GOOGLE_URL, System.getProperty('org.gradle.integtest.mirrors.google'), "maven"),
        LIGHTBEND_MAVEN("https://repo.lightbend.com/lightbend/maven-releases", System.getProperty('org.gradle.integtest.mirrors.lightbendmaven'), "maven"),
        LIGHTBEND_IVY("https://repo.lightbend.com/lightbend/ivy-releases", System.getProperty('org.gradle.integtest.mirrors.lightbendivy'), "ivy"),
        SPRING_RELEASES('https://repo.spring.io/release', System.getProperty('org.gradle.integtest.mirrors.springreleases'), 'maven'),
        SPRING_SNAPSHOTS('https://repo.spring.io/snapshot/', System.getProperty('org.gradle.integtest.mirrors.springsnapshots'), 'maven'),
        GRADLE('https://repo.gradle.org/gradle/repo', System.getProperty('org.gradle.integtest.mirrors.gradle'), 'maven'),
        JBOSS('https://repository.jboss.org/maven2/', System.getProperty('org.gradle.integtest.mirrors.jboss'), 'maven'),
        GRADLE_PLUGIN("https://plugins.gradle.org/m2", System.getProperty('org.gradle.integtest.mirrors.gradle-prod-plugins'), 'maven'),
        GRADLE_LIB_RELEASES('https://repo.gradle.org/gradle/libs-releases', System.getProperty('org.gradle.integtest.mirrors.gradle'), 'maven'),
        GRADLE_LIB_MILESTONES('https://repo.gradle.org/gradle/libs-milestones', System.getProperty('org.gradle.integtest.mirrors.gradle'), 'maven'),
        GRADLE_LIB_SNAPSHOTS('https://repo.gradle.org/gradle/libs-snapshots', System.getProperty('org.gradle.integtest.mirrors.gradle'), 'maven'),
        GRADLE_JAVASCRIPT('https://repo.gradle.org/gradle/javascript-public', System.getProperty('org.gradle.integtest.mirrors.gradlejavascript'), 'maven'),
        KOTLIN_DEV('https://packages.jetbrains.team/maven/p/kt/dev', System.getProperty('org.gradle.integtest.mirrors.kotlindev'), 'maven')

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
     * A repository that test builds need on top of the ones they declare, typically because a tested
     * dependency version is not published to the public repositories. Only the matching groups are looked up in it.
     */
    static final class ExtraRepository {
        final String name
        final String url
        final List<String> groupRegexes
        private final Closure<Boolean> active

        ExtraRepository(String name, String url, List<String> groupRegexes, Closure<Boolean> active = { true }) {
            this.name = name
            this.url = url
            this.groupRegexes = groupRegexes
            this.active = active
        }

        boolean isActive() {
            active.call()
        }
    }

    /**
     * Active extra repositories are injected into every build run through a {@code GradleExecuter} or a smoke test runner
     * (see {@link #extraRepositoriesInitScript}), and into the repository blocks produced by this class
     * (see {@link #extraRepositoriesDefinition}) for builds driven through the Tooling API.
     */
    private static final List<ExtraRepository> EXTRA_REPOSITORIES = [
        new ExtraRepository(MirroredRepository.KOTLIN_DEV.name, MirroredRepository.KOTLIN_DEV.mirrorUrl, [/org\.jetbrains\.kotlin(\..+)?/], {
            KotlinGradlePluginVersions.isKotlinDevVersion(new KotlinGradlePluginVersions().latest)
        })
    ]

    @Lazy
    static List<ExtraRepository> activeExtraRepositories = EXTRA_REPOSITORIES.findAll { it.active }.asImmutable()

    private static File extraRepositoriesInitScriptFile

    private RepoScriptBlockUtil() {
    }

    static String getMavenCentralMirrorUrl() {
        MirroredRepository.MAVEN_CENTRAL.mirrorUrl
    }

    static String mavenCentralRepository(GradleDsl dsl = GROOVY) {
        return """
            repositories {
                ${mavenCentralRepositoryDefinition(dsl)}
                ${extraRepositoriesDefinition(dsl)}
            }
        """
    }

    static String googleRepository(GradleDsl dsl = GROOVY) {
        return """
            repositories {
                ${googleRepositoryDefinition(dsl)}
                ${extraRepositoriesDefinition(dsl)}
            }
        """
    }

    static String extraRepositoriesDefinition(GradleDsl dsl = GROOVY) {
        activeExtraRepositories.collect { extraRepositoryDefinition(dsl, it) }.join("")
    }

    static String extraRepositoryDefinition(GradleDsl dsl = GROOVY, ExtraRepository repository) {
        def includes = repository.groupRegexes.collect { "includeGroupByRegex(\"${escapeBackslashes(it)}\")" }.join("\n")
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
                        content {
                            ${includes}
                        }
                    }
                """
        }
    }

    private static String escapeBackslashes(String regex) {
        regex.replace('\\', '\\\\')
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

    /**
     * The init script adding the active extra repositories to a build, or {@code null} when there is none.
     */
    static synchronized File extraRepositoriesInitScriptFile() {
        if (activeExtraRepositories.empty) {
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

    static String extraRepositoriesInitScript(List<ExtraRepository> repositories = activeExtraRepositories) {
        def declarations = repositories.collect { ExtraRepository repository ->
            def includes = repository.groupRegexes.collect { "includeGroupByRegex('${escapeBackslashes(it)}')" }.join("\n")
            """
                    if (!repos.any { it instanceof MavenArtifactRepository && normalizeUrl(it.url) == normalizeUrl('${repository.url}') }) {
                        repos.maven {
                            name = '${repository.name}'
                            url = '${repository.url}'
                            if (SUPPORTS_CONTENT_FILTERING) {
                                content {
                                    ${includes}
                                }
                            }
                        }
                    }
            """
        }.join("")
        return """
            import org.gradle.util.GradleVersion

            apply plugin: ExtraRepositoriesPlugin

            class ExtraRepositoriesPlugin implements Plugin<Gradle> {

                static final boolean SUPPORTS_CONTENT_FILTERING = GradleVersion.current() >= GradleVersion.version("5.1")

                void apply(Gradle gradle) {
                    if (GradleVersion.current() >= GradleVersion.version("6.0")) {
                        gradle.beforeSettings { Settings settings ->
                            def repos = settings.pluginManagement.repositories
                            def before = repos.size()
                            ExtraRepositoriesPlugin.addTo(repos)
                            // the default plugin repository only applies while none is declared
                            def portal = repos.gradlePluginPortal()
                            def injected = repos.size() - before
                            gradle.settingsEvaluated { Settings evaluated ->
                                if (evaluated == settings && repos.size() > injected) {
                                    repos.remove(portal)
                                }
                            }
                        }
                    }
                    if (GradleVersion.current() >= GradleVersion.version("6.8")) {
                        gradle.settingsEvaluated { Settings settings ->
                            ExtraRepositoriesPlugin.addUnlessEmpty(settings.dependencyResolutionManagement.repositories)
                        }
                    }
                    def projectClosure = { Project project ->
                        project.buildscript.configurations["classpath"].incoming.beforeResolve {
                            ExtraRepositoriesPlugin.addUnlessEmpty(project.buildscript.repositories)
                        }
                        project.afterEvaluate {
                            ExtraRepositoriesPlugin.addUnlessEmpty(project.repositories)
                        }
                    }
                    if (GradleVersion.current() >= GradleVersion.version("8.8")) {
                        gradle.lifecycle.beforeProject(projectClosure)
                    } else {
                        gradle.allprojects(projectClosure)
                    }
                }

                // a repository declared where there was none would replace the settings repositories
                static void addUnlessEmpty(RepositoryHandler repos) {
                    if (!repos.isEmpty()) {
                        addTo(repos)
                    }
                }

                static void addTo(RepositoryHandler repos) {
                    ${declarations}
                }

                static String normalizeUrl(Object url) {
                    String result = url.toString().replace('https://', 'http://')
                    return result.endsWith("/") ? result : result + "/"
                }
            }
        """
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
                    if (GradleVersion.version(gradle.gradleVersion) >= GradleVersion.version("4.4")) {
                        gradle.settingsEvaluated { Settings settings ->
                            withMirrors(settings.pluginManagement.repositories)
                        }
                    }
                }

                @CompileDynamic
                void maybeConfigureDependencyResolutionManagement(Gradle gradle) {
                    if (GradleVersion.version(gradle.gradleVersion) >= GradleVersion.version("6.8")) {
                        gradle.settingsEvaluated { Settings settings ->
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
