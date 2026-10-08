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
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.test.fixtures.dsl.GradleDsl

import java.util.regex.Pattern

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
        /**
         * Text in a build's scripts or version catalogs that shows the build needs this repository.
         * Without it, and unless {@link #neededByKotlinDslBuilds}, every build gets the repository.
         */
        final Pattern neededWhenBuildMentions
        /**
         * Whether every build with a Kotlin DSL script needs this repository, whatever its scripts mention.
         */
        final boolean neededByKotlinDslBuilds
        /**
         * Plugin versions this repository serves, by plugin id. A build on the Gradle version under test that requests
         * one of these plugins at the version Gradle applies for {@code kotlin-dsl} gets the version given here instead.
         * Such a repository is only added by the init script, as a repository block cannot carry that rule.
         */
        final Map<String, String> pluginVersions
        private final Closure<Boolean> active

        ExtraRepository(String name, String url, List<String> groupRegexes, Pattern neededWhenBuildMentions = null, boolean neededByKotlinDslBuilds = false, Closure<Boolean> active = { true }, Map<String, String> pluginVersions = [:]) {
            this.name = name
            this.url = url
            this.groupRegexes = groupRegexes
            this.neededWhenBuildMentions = neededWhenBuildMentions
            this.neededByKotlinDslBuilds = neededByKotlinDslBuilds
            this.active = active
            this.pluginVersions = pluginVersions
        }

        boolean isActive() {
            active.call()
        }
    }

    /**
     * Register extra repositories here, no test needs a repository declaration of its own. See contributing/Testing.md.
     *
     * Active ones are included in the repository blocks produced by this class (see {@link #extraRepositoriesDefinition}),
     * and injected by an init script (see {@link #extraRepositoriesInitScript}) into every smoke test build and into
     * the builds of a {@code GradleExecuter} that need them (see {@link #extraRepositoriesNeededBy}).
     */
    private static final List<ExtraRepository> EXTRA_REPOSITORIES = [
        // Kotlin DSL scripts pin the Kotlin libraries on their classpath to the embedded Kotlin version
        new ExtraRepository(MirroredRepository.KOTLIN_DEV.name, MirroredRepository.KOTLIN_DEV.mirrorUrl, [/org\.jetbrains\.kotlin(\..+)?/], ~/(?i)kotlin/, true, {
            new KotlinGradlePluginVersions().latests.any { KotlinGradlePluginVersions.isKotlinDevVersion(it) }
        }),
        // The published Kotlin DSL plugins are built with an older Kotlin than the one under test, so builds that apply them
        // use the ones built from this source instead, in the projects that publish them to their local repository for tests
        new ExtraRepository("LOCALLY_BUILT_KOTLIN_DSL_PLUGINS", IntegrationTestBuildContext.INSTANCE.localRepository?.toURI()?.toString(), [/org\.gradle\.kotlin(\..+)?/], ~/kotlin-dsl|embedded-kotlin/, false, {
            !locallyBuiltKotlinDslPluginVersions().isEmpty()
        }, locallyBuiltKotlinDslPluginVersions())
    ]

    private static Map<String, String> locallyBuiltKotlinDslPluginVersions() {
        def resource = RepoScriptBlockUtil.getResource("/future-plugin-versions.properties")
        if (resource == null || IntegrationTestBuildContext.INSTANCE.localRepository == null) {
            return [:]
        }
        def properties = new Properties()
        resource.withInputStream { properties.load(it) }
        return properties.collectEntries { key, value -> [(key as String): value as String] } as Map<String, String>
    }

    private static final List<String> BUILD_FILE_SUFFIXES = [".gradle", ".gradle.kts", ".gradle.dcl", ".toml"]
    private static final Set<String> NON_BUILD_DIRECTORIES = ["build", ".gradle", ".kotlin", "node_modules"] as Set

    @Lazy
    static List<ExtraRepository> activeExtraRepositories = EXTRA_REPOSITORIES.findAll { it.active }.asImmutable()

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
     * The Plugin Portal and Maven Central, which is what a build applying {@code kotlin-dsl} resolves from.
     */
    static String gradlePluginAndMavenCentralRepositories(GradleDsl dsl = GROOVY) {
        repositoriesBlock(dsl, gradlePluginRepositoryDefinition(dsl), mavenCentralRepositoryDefinition(dsl))
    }

    private static String repositoriesBlock(GradleDsl dsl, String... definitions) {
        return """
            repositories {
                ${definitions.join("\n")}
                ${extraRepositoriesDefinition(dsl)}
            }
        """
    }

    static String extraRepositoriesDefinition(GradleDsl dsl = GROOVY) {
        activeExtraRepositories.findAll { it.pluginVersions.isEmpty() }.collect { extraRepositoryDefinition(dsl, it) }.join("")
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

    static String kotlinDevRepositoryMirrorUrl() {
        MirroredRepository.KOTLIN_DEV.mirrorUrl
    }

    static String gradlePluginRepositoryDefinition(GradleDsl dsl = GROOVY) {
        MirroredRepository.GRADLE_PLUGIN.getRepositoryDefinition(dsl)
    }

    /**
     * Whether a build in the given directory needs one of the active extra repositories, judging by its scripts and version catalogs.
     * The init script adding them is visible to the build, e.g. as build operations, so it is only added where it is needed.
     */
    static boolean extraRepositoriesNeededBy(File buildDirectory, Collection<File> excludedDirectories) {
        if (activeExtraRepositories.any { it.neededWhenBuildMentions == null && !it.neededByKotlinDslBuilds }) {
            return true
        }
        List<Pattern> patterns = activeExtraRepositories.collect { it.neededWhenBuildMentions }.findAll { it != null }
        boolean kotlinDslBuildsNeedThem = activeExtraRepositories.any { it.neededByKotlinDslBuilds }
        // The declarations of the extra repositories that this class writes into build scripts do not count
        List<String> ownDeclarations = activeExtraRepositories.collectMany { repository ->
            [repository.name] + repository.groupRegexes.collectMany { [escapeBackslashes(it), it] }
        }
        return anyBuildFileNeeds(buildDirectory, patterns, kotlinDslBuildsNeedThem, ownDeclarations, excludedDirectories.collect { it.absoluteFile } as Set<File>)
    }

    private static boolean anyBuildFileNeeds(File directory, List<Pattern> patterns, boolean kotlinDslBuildsNeedThem, List<String> ignoredText, Set<File> excludedDirectories) {
        if (excludedDirectories.contains(directory.absoluteFile)) {
            return false
        }
        File[] children = directory.listFiles()
        if (children == null) {
            return false
        }
        for (File child : children) {
            if (child.directory) {
                if (!NON_BUILD_DIRECTORIES.contains(child.name) && anyBuildFileNeeds(child, patterns, kotlinDslBuildsNeedThem, ignoredText, excludedDirectories)) {
                    return true
                }
            } else if (kotlinDslBuildsNeedThem && child.name.endsWith(".gradle.kts")) {
                return true
            } else if (BUILD_FILE_SUFFIXES.any { child.name.endsWith(it) }) {
                String text = ignoredText.inject(child.text) { String result, String ignored -> result.replace(ignored, "") }
                if (patterns.any { it.matcher(text).find() }) {
                    return true
                }
            }
        }
        return false
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
            def condition = repository.pluginVersions.isEmpty() ? "" : "UNDER_TEST && "
            """
                    if (${condition}!repos.any { it instanceof MavenArtifactRepository && normalizeUrl(it.url) == normalizeUrl('${repository.url}') }) {
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
        def pluginVersions = repositories.collectEntries { it.pluginVersions }
        def pluginVersionsLiteral = pluginVersions.isEmpty() ? "[:]" : "[" + pluginVersions.collect { id, version -> "'${id}': '${version}'" }.join(", ") + "]"
        return """
            import org.gradle.util.GradleVersion

            apply plugin: ExtraRepositoriesPlugin

            class ExtraRepositoriesPlugin implements Plugin<Gradle> {

                static final boolean SUPPORTS_CONTENT_FILTERING = GradleVersion.current() >= GradleVersion.version("5.1")
                static boolean UNDER_TEST

                void apply(Gradle gradle) {
                    UNDER_TEST = gradle.gradleHomeDir?.canonicalPath == '${escapeBackslashes(String.valueOf(IntegrationTestBuildContext.INSTANCE.gradleHomeDir?.canonicalPath))}'
                    if (GradleVersion.current() >= GradleVersion.version("6.0")) {
                        gradle.beforeSettings { Settings settings ->
                            def repos = settings.pluginManagement.repositories
                            ExtraRepositoriesPlugin.addTo(repos)
                            ExtraRepositoriesPlugin.usePluginVersions(settings)
                            // The default plugin repository only applies while none is declared. Settings plugins resolve
                            // while the settings script still runs, so it has to go as soon as the build declares one.
                            def portal = repos.gradlePluginPortal()
                            repos.whenObjectAdded { if (!it.is(portal)) repos.remove(portal) }
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

                // Only requests at the version Gradle applies for `kotlin-dsl` by default, explicit versions are left alone
                static void usePluginVersions(Settings settings) {
                    Map<String, String> versions = ${pluginVersionsLiteral}
                    if (!UNDER_TEST || versions.isEmpty()) {
                        return
                    }
                    def defaultVersion = Class.forName('org.gradle.kotlin.dsl.support.KotlinDslPluginsKt').getMethod('getExpectedKotlinDslPluginsVersion').invoke(null)
                    settings.pluginManagement.resolutionStrategy.eachPlugin { details ->
                        def version = versions[details.requested.id.id]
                        if (version != null && details.requested.version == defaultVersion) {
                            details.useVersion(version)
                        }
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
