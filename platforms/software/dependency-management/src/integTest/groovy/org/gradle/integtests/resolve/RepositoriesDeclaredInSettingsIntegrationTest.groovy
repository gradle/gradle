/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE
import static org.gradle.integtests.fixtures.SuggestionsMessages.repositoryHint

// Restrict the number of combinations because that's not really what we want to test
@RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class RepositoriesDeclaredInSettingsIntegrationTest extends AbstractModuleDependencyResolveTest implements PluginDslSupport {
    boolean isDeclareRepositoriesInSettings() {
        true
    }

    def "can declare dependency in settings for a single-project build"() {
        repository {
            'org:module:1.0'()
        }

        buildFile << """
            dependencies {
                conf 'org:module:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:module:1.0' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:module:1.0')
            }
        }
    }

    def "can declare dependency in settings for a multi-project build"() {
        repository {
            "org:module-lib1:1.0"()
            "org:module-lib2:1.0"()
        }
        settingsFile << """
            include 'lib1', 'lib2'
        """

        buildFile << """
            dependencies {
                conf project(path:":lib1", configuration: 'conf')
                conf project(path:":lib2", configuration: 'conf')
            }
        """

        ['lib1', 'lib2'].each {
            file("${it}/build.gradle") << """
                configurations {
                    conf
                }

                dependencies {
                    conf 'org:module-${it}:1.0'
                }
            """
        }

        when:
        repositoryInteractions {
            'org:module-lib1:1.0' {
                expectResolve()
            }
            'org:module-lib2:1.0' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                project(":lib1", "test:lib1:") {
                    configuration = 'conf'
                    noArtifacts()
                    module('org:module-lib1:1.0')
                }
                project(":lib2", "test:lib2:") {
                    configuration = 'conf'
                    noArtifacts()
                    module('org:module-lib2:1.0')
                }
            }
        }
    }

    def "project local repositories override whatever is in settings"() {
        repository {
            'org:module:1.0'()
        }

        buildFile << """
            dependencies {
                conf 'org:module:1.0'
            }

            repositories {
                maven { url 'dummy' }
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause("Could not find org:module:1.0.")
    }

    def "project local repositories can be ignored if we prefer settings repositories"() {
        repository {
            'org:module:1.0'()
        }

        settingsFile << """

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
            }

        """

        buildFile << """
            dependencies {
                conf 'org:module:1.0'
            }

            repositories {
                maven { url 'dummy' }
            }
        """

        when:
        repositoryInteractions {
            'org:module:1.0' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:module:1.0')
            }
        }

        and:
        outputContains "Build was configured to prefer settings repositories over project repositories but repository 'maven' was added by build file 'build.gradle'"
    }

    def "can fail the build if a project declares a repository"() {
        repository {
            'org:module:1.0'()
        }

        settingsFile << """

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            }

        """

        buildFile << """
            dependencies {
                conf 'org:module:1.0'
            }

            repositories {
                maven { url 'dummy' }
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause("Build was configured to prefer settings repositories over project repositories but repository 'maven' was added by build file 'build.gradle'")
    }

    def "can fail the build if repositories are declared in a subproject block"() {
        createDirs("lib1", "lib2")
        settingsFile << """

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            }

            include 'lib1', 'lib2'

        """

        buildFile << """
            gradle.beforeProject {
                println "Before project \$it"
            }
            subprojects {
                configurations {
                    conf
                }

                dependencies {
                    conf 'org:module:1.0'
                }

                repositories {
                    maven { url 'dummy' }
                }
                println "Repository registered in \$it"
            }
        """

        when:
        fails ':lib1:checkDeps'

        then:
        failure.assertHasCause("Build was configured to prefer settings repositories over project repositories but repository 'maven' was added by build file 'build.gradle'")

    }

    def "can detect a repository added by a plugin"() {
        withProjectPluginAddingRepository()

        repository {
            'org:module:1.0'()
        }

        settingsFile.text = """
            pluginManagement {
                includeBuild 'my-plugin'
            }

            $settingsFile.text

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            }

        """

        withPlugins(['org.gradle.repo-conventions': '1.0'])
        buildFile << """
            dependencies {
                conf 'org:module:1.0'
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause("Build was configured to prefer settings repositories over project repositories but repository 'maven' was added by plugin 'org.gradle.repo-conventions'")
    }

    def "repositories declared in settings are used to resolve dependencies from included builds"() {
        repository {
            'org:module:1.0'()
        }
        file("included/build.gradle") << """
            group = 'com.acme'
            version = '0.x'

            configurations {
                    create 'default'
                }

                dependencies {
                    add('default', 'org:module:1.0')
                }
        """
        file("included/settings.gradle") << """
            rootProject.name = 'included'
        """
        buildFile << """
            dependencies {
                conf 'com.acme:included:1.0'
            }
        """
        settingsFile << """
            includeBuild 'included'
        """

        when:
        repositoryInteractions {
            'org:module:1.0' {
                expectResolve()
            }
        }
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("com.acme:included:1.0", ":included", "com.acme:included:0.x") {
                    configuration = 'default'
                    compositeSubstitute()
                    noArtifacts()
                    module('org:module:1.0')
                }
            }
        }
    }

    def "repositories declared in settings are used to resolve dependencies from nested included builds"() {
        repository {
            'org:module:1.0'()
        }
        file("included/build.gradle") << """
            group = 'com.acme'
            version = '0.x'

            configurations {
                    create 'default'
                }

                dependencies {
                    add('default', 'com.acme:nested:1.0')
                }
        """
        file("included/settings.gradle") << """
            rootProject.name = 'included'
            includeBuild '../nested'
        """

        file("nested/build.gradle") << """
            group = 'com.acme'
            version = '0.x'

            configurations {
                    create 'default'
                }

                dependencies {
                    add('default', 'org:module:1.0')
                }
        """
        file("nested/settings.gradle") << """
            rootProject.name = 'nested'
        """

        buildFile << """
            dependencies {
                conf 'com.acme:included:1.0'
            }
        """
        settingsFile << """
            includeBuild 'included'
        """

        when:
        repositoryInteractions {
            'org:module:1.0' {
                expectResolve()
            }
        }
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("com.acme:included:1.0", ":included", "com.acme:included:0.x") {
                    configuration = 'default'
                    compositeSubstitute()
                    noArtifacts()
                    edge("com.acme:nested:1.0", ":nested", "com.acme:nested:0.x") {
                        configuration = 'default'
                        compositeSubstitute()
                        noArtifacts()
                        module('org:module:1.0')
                    }
                }
            }
        }
    }

    def "repositories declared in nested included build settings are ignored"() {
        repository {
            'org:module:1.0'()
        }
        file("included/build.gradle") << """
            group = 'com.acme'
            version = '0.x'

            configurations {
                    create 'default'
                }

                dependencies {
                    add('default', 'com.acme:nested:1.0')
                }
        """
        file("included/settings.gradle") << """
            rootProject.name = 'included'
            includeBuild '../nested'
        """

        file("nested/build.gradle") << """
            group = 'com.acme'
            version = '0.x'

            configurations {
                    create 'default'
                }

                dependencies {
                    add('default', 'org:module:1.0')
                }
        """
        file("nested/settings.gradle") << """
            rootProject.name = 'nested'

            dependencyResolutionManagement {
                repositories {
                    maven {
                        url "this should be ignored"
                    }
                }
            }
        """

        buildFile << """
            dependencies {
                conf 'com.acme:included:1.0'
            }
        """
        settingsFile << """
            includeBuild 'included'
        """

        when:
        repositoryInteractions {
            'org:module:1.0' {
                expectResolve()
            }
        }
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("com.acme:included:1.0", ":included", "com.acme:included:0.x") {
                    configuration = 'default'
                    compositeSubstitute()
                    noArtifacts()
                    edge("com.acme:nested:1.0", ":nested", "com.acme:nested:0.x") {
                        configuration = 'default'
                        compositeSubstitute()
                        noArtifacts()
                        module('org:module:1.0')
                    }
                }
            }
        }
    }

    def "repositories declared in settings are ignored when resolving dependencies from included builds with explicit project repositories"() {
        repository {
            'org:module:1.0'()
        }
        file("included/build.gradle") << """
            group = 'com.acme'
            version = '0.x'

            configurations {
                    create 'default'
                }

                dependencies {
                    add('default', 'org:module:1.0')
                }
        """
        file("included/settings.gradle") << """
            rootProject.name = 'included'
        """
        buildFile << """
            dependencies {
                conf 'com.acme:included:1.0'
            }

            repositories {
                maven { url 'dummy' }
            }
        """
        settingsFile << """
            includeBuild 'included'
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause("Could not find org:module:1.0.")
    }

    def "mutation of repositories is project local"() {
        repository {
            'org:module:1.0'()
        }

        buildFile << """
            dependencies {
                conf 'org:module:1.0'
            }
            repositories.all {
                throw new RuntimeException("Shouldn't be called because no repositories are defined for this project")
            }
        """

        when:
        repositoryInteractions {
            'org:module:1.0' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:module:1.0')
            }
        }
    }

    @ToBeFixedForConfigurationCache(because = "task uses dependency resolution API")
    def "mutation of settings repositories after settings have been evaluated is disallowed"() {

        buildFile << """
            tasks.register('mutateSettings') {
                doLast {
                    gradle.settings.dependencyResolutionManagement {
                        repositories {
                            maven { url = 'dummy' }
                        }
                    }
                }
            }
        """

        when:
        fails ':mutateSettings'

        then:
        failure.assertHasCause("Mutation of repositories declared in settings is only allowed during settings evaluation")
    }

    /**
     * the `buildSrc` directory behaves like an included build. As such, it may have its own settings,
     * so repositories declared in the main build shouldn't be visible to buildSrc.
     */
    def "repositories declared in settings shouldn't be used to resolve dependencies in buildSrc"() {
        repository {
            'org:module:1.0'()
        }

        file("buildSrc/build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
            }

            dependencies {
                implementation 'org:module:1.0'
            }
        """

        when:
        fails ':help'

        then:
        result.assertTaskExecuted(':buildSrc:jar')
        result.assertTaskNotExecuted(':help')
        failure.assertHasCause('Cannot resolve external dependency org:module:1.0 because no repositories are defined.')
    }

    // fails to delete directory under Windows otherwise
    @Requires(UnitTestPreconditions.NotWindows)
    def "can use a published settings plugin which will apply to both the main build and buildSrc"() {
        def pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)
        pluginPortal.start()
        withSettingsPlugin()
        def plugin = new PluginBuilder(file("settings-plugin"))
            .addPluginId("org.gradle.repo-conventions", "RepoConventionPlugin")
            .publishAs("g", "a", "1.0", pluginPortal, createExecuter())

        // make sure we don't use the default fixture which already adds repositories
        settingsFile.text = """
            plugins {
                id "org.gradle.repo-conventions" version "1.0"
            }

            rootProject.name = 'test'
            includeBuild 'settings-plugin'
        """
        repository {
            'org:from-buildsrc:1.0'()
            'org:from-main-build:1.0'()
        }
        file("buildSrc/settings.gradle") << """
            plugins {
                id "org.gradle.repo-conventions"
            }
        """
        file("buildSrc/build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
            }

            dependencies {
                implementation 'org:from-buildsrc:1.0'
            }
        """

        buildFile << """
            dependencies {
                conf 'org:from-main-build:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:from-buildsrc:1.0' {
                expectResolve()
            }
            'org:from-main-build:1.0' {
                expectResolve()
            }
        }
        plugin.allowAll()
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:from-main-build:1.0')
            }
        }

        cleanup:
        pluginPortal.stop()
    }

    /**
     * This test exercises something which should probably work but doesn't now, because when we evaluate settings,
     * we start with the plugins block, but the said plugin is provided by the composite.
     *
     * It actually works for the buildSrc application, but not for the main build
     */
    @ToBeImplemented
    def "can use a composite build to write a settings plugin which will apply to both the main build and buildSrc"() {
        withSettingsPlugin()
        // make sure we don't use the default fixture which already adds repositories
        settingsFile.text = """
            plugins {
                id 'org.gradle.repo-conventions'
            }
            rootProject.name = 'test'
            includeBuild 'settings-plugin'
        """
        repository {
            'org:from-buildsrc:1.0'()
            'org:from-main-build:1.0'()
        }
        file("buildSrc/settings.gradle") << """
            plugins {
                id 'org.gradle.repo-conventions'
            }
        """
        file("buildSrc/build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
            }

            dependencies {
                implementation 'org:from-buildsrc:1.0'
            }
        """

        buildFile << """
            dependencies {
                conf 'org:from-main-build:1.0'
            }
        """

        when:
        fails ':checkDeps'

        then:
        errorOutput.contains("Plugin [id: 'org.gradle.repo-conventions'] was not found in any of the following sources")

        // real expectations below

        /*
        when:
        repositoryInteractions {
            'org:from-buildsrc:1.0' {
                expectResolve()
            }
            'org:from-main-build:1.0' {
                expectResolve()
            }
        }
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:from-main-build:1.0')
            }
        }
         */
    }

    // fails to delete directory under Windows otherwise
    @Requires(UnitTestPreconditions.NotWindows)
    void "repositories declared in settings shouldn't be used to resolve plugins"() {
        def pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)
        pluginPortal.start()
        def taskName = 'pluginTask'
        def message = 'hello from plugin'
        def plugin = new PluginBuilder(testDirectory.file("some-plugin"))
            .addPluginWithPrintlnTask(taskName, message, 'org.gradle.test.hello-world')
            .publishAs("g", "a", "1.0", pluginPortal, createExecuter())

        // If we use the same repositories for project resolution and plugin resolution
        // the build will fail saying that it cannot find our settings plugin
        settingsFile << """
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            }
        """

        repository {
            'org:module:1.0'()
        }

        withPlugins(['org.gradle.test.hello-world': '1.0'])

        when:
        plugin.allowAll()
        succeeds taskName

        then:
        outputContains message

        cleanup:
        pluginPortal.stop()
    }

    @Issue("https://github.com/gradle/gradle/issues/15336")
    def "reasonable error message if a dependency cannot be resolved because local repositories differ"() {
        buildFile << """
            repositories {
                maven {
                    url "dummy"
                }
            }

            dependencies {
                conf 'org:module:1.0'
            }

        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Could not resolve all dependencies for configuration ':conf'.""")
            .assertHasResolutions("""The project declares repositories, effectively ignoring the repositories you have declared in the settings.
   To determine how project repositories are declared, configure your build to fail on project repositories.
   ${documentationRegistry.getDocumentationRecommendationFor("information", "declaring_repositories", "sub:fail_build_on_project_repositories")}""",
                repositoryHint("Maven POM"),
                STACKTRACE_MESSAGE,
                INFO_DEBUG,
                SCAN,
                GET_HELP)
    }

    @Issue("https://github.com/gradle/gradle/issues/15772")
    def "can add settings repositories in an init script"() {
        given:
        repository {
            'org:module:1.0'()
        }

        buildFile << """
            dependencies {
                conf 'org:module:1.0'
            }
        """

        file("init.gradle") << """
settingsEvaluated {
  it.dependencyResolutionManagement {
    repositories {
      maven { url '/doesnt/matter'}
    }
  }
}
"""

        when:
        repositoryInteractions {
            'org:module:1.0' {
                expectResolve()
            }
        }
        executer.usingInitScript(file("init.gradle"))

        then:
        succeeds ':checkDeps'
    }

    void withSettingsPlugin() {
        file("settings-plugin/src/main/java/org/gradle/test/RepoConventionPlugin.java") << """package org.gradle.test;
        import org.gradle.api.Plugin;
        import org.gradle.api.initialization.Settings;

        public class RepoConventionPlugin implements Plugin<Settings> {
            public void apply(Settings settings) {
                settings.getDependencyResolutionManagement().getRepositories().maven(mvn -> mvn.setUrl(\"${mavenHttpRepo.uri}\"));
            }
        }
        """
    }

    void withProjectPluginAddingRepository() {
        file("my-plugin/build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
            }

            gradlePlugin {
                plugins {
                    myPlugin {
                        id = 'org.gradle.repo-conventions'
                        implementationClass = 'org.gradle.test.RepoConventionPlugin'
                    }
                }
            }
        """
        file("my-plugin/src/main/java/org/gradle/test/RepoConventionPlugin.java") << """package org.gradle.test;
        import org.gradle.api.Plugin;
        import org.gradle.api.Project;

        public class RepoConventionPlugin implements Plugin<Project> {
            public void apply(Project project) {
                project.getRepositories().maven(mvn -> mvn.setUrl(\"${mavenHttpRepo.uri}\"));
            }
        }
        """
    }


}
