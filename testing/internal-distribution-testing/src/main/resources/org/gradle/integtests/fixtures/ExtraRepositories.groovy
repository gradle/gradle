package org.gradle.integtests.fixtures

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.util.GradleVersion

// The build only copies this file.
// RepoScriptBlockUtil.extraRepositoriesInitScript reads the file as text and adds a call to declareIn with the extra repositories.
// Each repository is a map with the keys 'name', 'url' and 'groupRegexes'.
// All methods are static and get the repositories as an argument: Gradle isolates the actions for projects, so they must not capture an object.
@SuppressWarnings("unused") // Only the generated init script calls this class.
class ExtraRepositories {

    static final boolean SUPPORTS_CONTENT_FILTERING = GradleVersion.current() >= GradleVersion.version("5.1")

    static void declareIn(Gradle gradle, List<Map<String, Object>> repositories) {
        declareInPluginManagement(gradle, repositories)
        declareInDependencyResolutionManagement(gradle, repositories)
        declareInProjects(gradle, repositories)
    }

    static void declareInPluginManagement(Gradle gradle, List<Map<String, Object>> repositories) {
        if (GradleVersion.current() < GradleVersion.version("6.0")) {
            return
        }
        gradle.beforeSettings { Settings settings ->
            def repos = settings.pluginManagement.repositories
            ExtraRepositories.declare(repos, repositories)
            // By default, Gradle searches the Gradle Plugin Portal for plugins. A declared plugin repository turns that default off, so declare the Gradle Plugin Portal too.
            // When the build declares its own plugin repository, remove the Gradle Plugin Portal at once. Settings plugins resolve while the settings script runs.
            def portal = repos.gradlePluginPortal()
            repos.whenObjectAdded { if (!it.is(portal)) repos.remove(portal) }
        }
    }

    static void declareInDependencyResolutionManagement(Gradle gradle, List<Map<String, Object>> repositories) {
        if (GradleVersion.current() < GradleVersion.version("6.8")) {
            return
        }
        gradle.settingsEvaluated { Settings settings ->
            ExtraRepositories.declareUnlessEmpty(settings.dependencyResolutionManagement.repositories, repositories)
        }
    }

    // Declare the repositories when a configuration resolves, not earlier. Then builds that resolve nothing do not see them.
    static void declareInProjects(Gradle gradle, List<Map<String, Object>> repositories) {
        def projectClosure = { Project project ->
            project.buildscript.configurations["classpath"].incoming.beforeResolve {
                ExtraRepositories.declareUnlessEmpty(project.buildscript.repositories, repositories)
            }
            def declareOnResolve = { Configuration configuration ->
                configuration.incoming.beforeResolve {
                    ExtraRepositories.declareUnlessEmpty(project.repositories, repositories)
                }
            }
            if (GradleVersion.current() >= GradleVersion.version("4.9")) {
                project.configurations.configureEach(declareOnResolve)
            } else {
                project.configurations.all(declareOnResolve)
            }
        }
        if (GradleVersion.current() >= GradleVersion.version("8.8")) {
            gradle.lifecycle.beforeProject(projectClosure)
        } else {
            gradle.allprojects(projectClosure)
        }
    }

    // Repositories declared in a project override those declared in settings. Thus, do not declare a repository in a project that declares none.
    static void declareUnlessEmpty(RepositoryHandler repos, List<Map<String, Object>> repositories) {
        if (!repos.isEmpty()) {
            declare(repos, repositories)
        }
    }

    static void declare(RepositoryHandler repos, List<Map<String, Object>> repositories) {
        repositories.each { Map<String, Object> repository ->
            if (ExtraRepositories.isDeclared(repos, repository.url as String)) {
                return
            }
            repos.maven { MavenArtifactRepository maven ->
                maven.name = repository.name
                maven.url = repository.url
                if (ExtraRepositories.SUPPORTS_CONTENT_FILTERING) {
                    maven.content { descriptor ->
                        repository.groupRegexes.each { descriptor.includeGroupByRegex(it as String) }
                    }
                }
            }
        }
    }

    static boolean isDeclared(RepositoryHandler repos, String url) {
        repos.any { it instanceof MavenArtifactRepository && normalizeUrl(it.url) == normalizeUrl(url) }
    }

    static String normalizeUrl(Object url) {
        String result = url.toString().replace('https://', 'http://')
        return result.endsWith("/") ? result : result + "/"
    }
}
