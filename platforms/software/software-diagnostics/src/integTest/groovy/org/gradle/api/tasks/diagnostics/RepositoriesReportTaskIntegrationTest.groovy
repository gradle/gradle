/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.api.tasks.diagnostics

import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.AbstractHandler
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class RepositoriesReportTaskIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final HttpServer server = new HttpServer()

    def setup() {
        settingsFile << """
            rootProject.name = "myLib"
        """
    }

    def "task is registered under help group"() {
        expect:
        succeeds ':tasks', '--all'
        outputContains('repositories -')
    }

    def "task reports empty when no repositories declared"() {
        expect:
        succeeds ':repositories'
        outputContains('There are no repositories present.')
        outputDoesNotContain('All Repositories')
        outputDoesNotContain('Repositories by Location')
        outputDoesNotContain('(none)')
        outputDoesNotContain('Legend')
    }

    def "task reports a single mavenCentral repository in All Repositories"() {
        given:
        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('All Repositories')
        outputContains('''MavenRepo (1)
    Location:   https://repo.maven.apache.org/maven2/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':' > repositories''')
    }

    def "task reports settings pluginManagement repo under All Repositories with PLUGINS role"() {
        given:
        settingsFile.text = """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                }
            }
            rootProject.name = "myLib"
        """

        expect:
        succeeds ':repositories'
        outputContains('All Repositories')
        outputContains('PLUGINS')
    }

    def "task reports settings.buildscript repo in All Repositories and under settings uses"() {
        given:
        settingsFile.text = """
            buildscript {
                repositories {
                    mavenCentral()
                }
            }
            rootProject.name = "myLib"
        """

        expect:
        succeeds ':repositories'
        outputContains('All Repositories')
        outputContains('SETTINGS_BUILDSCRIPT_DEPENDENCIES')
        outputContains('settings uses')
    }

    def "multi-project: PLUGINS and DRM repos appear under every project's reference list"() {
        given:
        settingsFile.text = """
            pluginManagement {
                repositories { gradlePluginPortal() }
            }
            dependencyResolutionManagement {
                repositories { mavenCentral() }
            }
            rootProject.name = "myLib"
            include ":app"
            include ":lib"
        """
        createDirs("app", "lib")

        expect:
        succeeds ':repositories'
        outputContains("project ':app' uses")
        outputContains("project ':lib' uses")
        // each project should reference at least the DRM and PLUGINS entries
        result.output.count('- Gradle Central Plugin Repository') >= 2 ||
            result.output.count('- MavenRepo') >= 2
    }

    def "--project filter limits Repositories by Location to the single project"() {
        given:
        settingsFile.text = """
            pluginManagement {
                repositories { gradlePluginPortal() }
            }
            dependencyResolutionManagement {
                repositories { mavenCentral() }
            }
            rootProject.name = "myLib"
            include ":app"
            include ":other"
        """
        createDirs("app", "other")
        file('app/build.gradle') << 'repositories { google() }'

        when:
        succeeds ':repositories', '--project', ':app'

        then:
        outputContains('All Repositories')
        outputContains('Google')
        outputContains('Repositories by Location')
        outputContains("project ':app' uses")
        outputDoesNotContain('settings uses')
        outputDoesNotContain("project ':other'")
    }

    def "--project with unknown project path fails with clear error"() {
        given:
        settingsFile.text = """
            rootProject.name = "myLib"
            include ":app"
        """
        createDirs("app")

        when:
        fails ':repositories', '--project', ':nope'

        then:
        failure.assertHasCause("Project ':nope' not found")
    }

    def "identical declarations in multiple projects mark entries with star and render Legend"() {
        given:
        settingsFile.text = """
            rootProject.name = "myLib"
            include ":app"
            include ":lib"
        """
        createDirs("app", "lib")
        file('app/build.gradle') << 'repositories { mavenCentral() }'
        file('lib/build.gradle') << 'repositories { mavenCentral() }'

        expect:
        succeeds ':repositories'
        result.output.count('MavenRepo') >= 2
        outputContains('*')
        outputContains('Legend')
        outputContains('Identical repository declaration')
    }

    def "non-identical declarations do NOT trigger Legend"() {
        given:
        settingsFile.text = """
            rootProject.name = "myLib"
            include ":app"
            include ":lib"
        """
        createDirs("app", "lib")
        file('app/build.gradle') << '''
            repositories {
                maven {
                    url = uri("https://example.com/")
                    content { includeGroup("com.example") }
                }
            }
        '''
        file('lib/build.gradle') << '''
            repositories {
                maven {
                    url = uri("https://example.com/")
                }
            }
        '''

        expect:
        succeeds ':repositories'
        outputDoesNotContain('Legend')
    }

    def "reports authentication scheme class name"() {
        given:
        buildFile << """
            repositories {
                maven {
                    url = uri("https://corp.example.com/")
                    credentials(PasswordCredentials)
                    authentication {
                        basic(BasicAuthentication)
                    }
                }
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('Auth:')
        outputContains('BasicAuthentication')
    }

    def "reports Credentials: PRESENT for repo with declared credentials"() {
        given:
        // Use distinctive, non-substring credential values so leak detection is unambiguous —
        // e.g. "user" would collide with the "/userguide/" links that appear in --warning-mode output.
        buildFile << """
            repositories {
                maven {
                    url = uri("https://corp.example.com/")
                    credentials {
                        username = "cred-username-xyzzy"
                        password = "cred-password-xyzzy"
                    }
                }
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('Credentials: PRESENT')
        // ensure the actual credential values never leak
        outputDoesNotContain('cred-username-xyzzy')
        outputDoesNotContain('cred-password-xyzzy')
    }

    def "omits Credentials line when no credentials declared"() {
        given:
        buildFile << """
            repositories {
                maven {
                    url = uri("https://open.example.com/")
                }
            }
        """

        expect:
        succeeds ':repositories'
        outputDoesNotContain('Credentials:')
    }

    def "reports Secure: false when allowInsecureProtocol = true"() {
        given:
        buildFile << """
            repositories {
                maven {
                    url = uri("http://legacy.example.com/")
                    allowInsecureProtocol = true
                }
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('Secure:     false')
    }

    def "renders content filter with include and onlyForConfigurations"() {
        given:
        buildFile << """
            configurations.create("compile")
            repositories {
                maven {
                    url = uri("https://example.com/")
                    content {
                        includeGroup("com.example")
                        excludeModule("com.other", "bad")
                        onlyForConfigurations("compile")
                    }
                }
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('Content:    includeGroup("com.example"), excludeModule("com.other", "bad")')
        outputContains('onlyForConfigurations')
    }

    def "task is configuration-cache compatible"() {
        given:
        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        when:
        succeeds ':repositories', '--configuration-cache'

        then:
        postBuildOutputContains('Configuration cache entry stored')

        when:
        succeeds ':repositories', '--configuration-cache'

        then:
        postBuildOutputContains('Configuration cache entry reused')
    }

    def "reports FlatDir repository with dirs location"() {
        given:
        file('libs').mkdirs()
        buildFile << """
            repositories {
                flatDir {
                    dirs 'libs'
                }
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('FLAT_DIR')
        outputContains('dirs:[')
        outputContains('libs')
    }

    def "reports Ivy repository"() {
        given:
        buildFile << """
            repositories {
                ivy {
                    url = uri("https://example.com/ivy/")
                }
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('Location:   https://example.com/ivy/')
        outputContains('Type:       IVY')
    }

    def "reports Ivy repository with content filter includeGroup"() {
        given:
        buildFile << """
            repositories {
                ivy {
                    url = uri("https://example.com/ivy/")
                    content {
                        includeGroup("com.example")
                    }
                }
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('Type:       IVY')
        outputContains('Location:   https://example.com/ivy/')
        outputContains('Content:    includeGroup("com.example")')
        outputContains("project ':' uses")
    }

    def "reports mavenLocal repository with MAVEN_LOCAL type"() {
        given:
        buildFile << """
            repositories {
                mavenLocal()
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('MAVEN_LOCAL')
    }

    def "project buildscript repo gets both PROJECT_LEGACY_PLUGINS and PROJECT_BUILDSCRIPT_DEPENDENCIES roles"() {
        given:
        buildFile.text = """
            buildscript {
                repositories {
                    mavenCentral()
                }
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('Roles:      PROJECT_LEGACY_PLUGINS, PROJECT_BUILDSCRIPT_DEPENDENCIES')
    }

    def "does not list repositories from included builds"() {
        given:
        settingsFile.text = """
            rootProject.name = "myLib"
            includeBuild 'included'
        """
        file('included/settings.gradle') << "rootProject.name = 'included'"
        file('included/build.gradle') << '''
            repositories {
                maven { url = uri("https://included.example.com/") }
            }
        '''
        buildFile << """
            repositories {
                mavenCentral()
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('MavenRepo')
        outputDoesNotContain('included.example.com')
        outputDoesNotContain("project ':included' uses")
    }

    def "complex example reports properly"() {
        given:
        // Settings plugin: adds a repo via settings.dependencyResolutionManagement.
        file('settings-plugin.gradle') << '''
            dependencyResolutionManagement {
                repositories {
                    maven { url = uri("https://settings-plugin-drm.example.com/") }
                }
            }
        '''
        settingsFile.text = """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                }
            }
            buildscript {
                repositories {
                    maven { url = uri("https://settings-buildscript.example.com/") }
                }
            }
            apply from: 'settings-plugin.gradle'
            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                    google()
                }
            }
            rootProject.name = "myLib"
            include ':app'
            include ':lib'
            includeBuild 'included'
        """
        createDirs('app', 'lib')
        buildFile << '''
            subprojects {
                repositories {
                    maven { url = uri("https://subprojects.example.com/") }
                }
            }
        '''
        // Project plugin: applied to :app, adds a repo to :app's own repositories container.
        file('app/project-plugin.gradle') << '''
            repositories {
                maven { url = uri("https://project-plugin-repo.example.com/") }
            }
        '''
        file('app/build.gradle') << '''
            buildscript {
                repositories {
                    maven { url = uri("https://app-buildscript.example.com/") }
                }
            }
            apply from: 'project-plugin.gradle'
            repositories {
                ivy {
                    url = uri("https://app-ivy.example.com/")
                    credentials {
                        username = "ivyUser"
                        password = "ivyPass"
                    }
                }
            }
        '''
        file('lib/build.gradle') << '''
            repositories {
                mavenCentral()
            }
            repositories {
                flatDir { dirs 'libs' }
            }
        '''
        file('included/settings.gradle') << "rootProject.name = 'included'"
        file('included/build.gradle') << '''
            repositories {
                maven { url = uri("https://included.example.com/") }
            }
        '''

        expect:
        succeeds ':repositories'
        // Section headers
        outputContains('All Repositories')
        // Settings DRM repositories
        outputContains('https://repo.maven.apache.org/maven2/')
        outputContains('https://dl.google.com/dl/android/maven2/')
        // Settings pluginManagement repository
        outputContains('https://plugins.gradle.org/m2')
        // Settings buildscript repository
        outputContains('https://settings-buildscript.example.com/')
        // Project buildscript repository (app)
        outputContains('https://app-buildscript.example.com/')
        // subprojects { } repository
        outputContains('https://subprojects.example.com/')
        // app's Ivy repository
        outputContains('https://app-ivy.example.com/')
        outputContains('Type:       IVY')
        // lib's flatDir repository
        outputContains('Type:       FLAT_DIR')
        outputContains('dirs:[')
        // Settings-plugin-added repo: PROJECT_DEPENDENCIES, attributed to settings DRM
        outputContains('https://settings-plugin-drm.example.com/')
        outputContains('Defined in: settings > dependencyResolutionManagement.repositories')
        // Project-plugin-added repo: PROJECT_DEPENDENCIES, attributed to :app
        outputContains('https://project-plugin-repo.example.com/')
        outputContains("Defined in: project ':app' > repositories")
        // All 5 RepositoryRole values appear
        outputContains('PLUGINS')
        outputContains('SETTINGS_BUILDSCRIPT_DEPENDENCIES')
        outputContains('PROJECT_LEGACY_PLUGINS')
        outputContains('PROJECT_BUILDSCRIPT_DEPENDENCIES')
        outputContains('PROJECT_DEPENDENCIES')
        // Per-project reference blocks
        outputContains("project ':app' uses")
        outputContains("project ':lib' uses")
        outputContains("project ':' uses")
        // Included build must NOT be descended
        outputDoesNotContain('included.example.com')
        outputDoesNotContain("project ':included'")
        // Ivy repo carried credentials — Credentials line should render
        outputContains('Credentials: PRESENT')
        // Credential values must never leak into the output
        outputDoesNotContain('ivyUser')
        outputDoesNotContain('ivyPass')
        // Legend present (mavenCentral declared in both DRM and :lib triggers duplicate marker)
        outputContains('Legend')
        outputContains('centralizing_repositories.html')
        outputContains('*')
    }

    def "project plugin adds repositories to its host project"() {
        given:
        settingsFile.text = """
            rootProject.name = "myLib"
            include ":app"
        """
        createDirs("app")
        // Script plugin acting as a project plugin: when applied to a project,
        // it adds a repository to that project's `repositories` container.
        file('app/project-repo-plugin.gradle') << '''
            repositories {
                maven { url = uri("https://project-plugin-repo.example.com/") }
            }
        '''
        file('app/build.gradle') << '''
            apply from: 'project-repo-plugin.gradle'
        '''

        expect:
        succeeds ':repositories'
        outputContains('All Repositories')
        outputContains('https://project-plugin-repo.example.com/')
        outputContains('Roles:      PROJECT_DEPENDENCIES')
        outputContains("Defined in: project ':app' > repositories")
        outputContains("project ':app' uses")
    }

    def "settings plugin adds repositories via dependencyResolutionManagement"() {
        given:
        // Script plugin acting as a settings plugin: when applied to settings,
        // it adds a repository to settings.dependencyResolutionManagement.repositories.
        file('settings-drm-plugin.gradle') << '''
            dependencyResolutionManagement {
                repositories {
                    maven { url = uri("https://settings-plugin-drm.example.com/") }
                }
            }
        '''
        settingsFile.text = """
            apply from: 'settings-drm-plugin.gradle'
            rootProject.name = "myLib"
            include ":app"
            include ":lib"
        """
        createDirs("app", "lib")

        expect:
        succeeds ':repositories'
        outputContains('All Repositories')
        outputContains('https://settings-plugin-drm.example.com/')
        outputContains('Roles:      PROJECT_DEPENDENCIES')
        outputContains('Defined in: settings > dependencyResolutionManagement.repositories')
        // The DRM repo should appear under every project's "uses" block.
        outputContains("project ':' uses")
        outputContains("project ':app' uses")
        outputContains("project ':lib' uses")
    }

    def "both project plugin and settings plugin add repositories"() {
        given:
        file('settings-drm-plugin.gradle') << '''
            dependencyResolutionManagement {
                repositories {
                    maven { url = uri("https://settings-plugin-drm.example.com/") }
                }
            }
        '''
        settingsFile.text = """
            apply from: 'settings-drm-plugin.gradle'
            rootProject.name = "myLib"
            include ":app"
        """
        createDirs("app")
        file('app/project-repo-plugin.gradle') << '''
            repositories {
                maven { url = uri("https://project-plugin-repo.example.com/") }
            }
        '''
        file('app/build.gradle') << '''
            apply from: 'project-repo-plugin.gradle'
        '''

        expect:
        succeeds ':repositories'
        outputContains('All Repositories')
        // Settings-plugin-added repo: PROJECT_DEPENDENCIES, declared in settings DRM
        outputContains('https://settings-plugin-drm.example.com/')
        outputContains('Defined in: settings > dependencyResolutionManagement.repositories')
        // Project-plugin-added repo: PROJECT_DEPENDENCIES, declared in project ':app'
        outputContains('https://project-plugin-repo.example.com/')
        outputContains("Defined in: project ':app' > repositories")
        // Both repos carry the PROJECT_DEPENDENCIES role
        outputContains('Roles:      PROJECT_DEPENDENCIES')
        // :app should reference both repos in its "uses" block
        outputContains("project ':app' uses")
    }

    def "settings plugin adds repositories directly to each project"() {
        given:
        // Script plugin acting as a settings plugin that uses gradle.beforeProject
        // to mutate each project's own `repositories` container.
        file('settings-per-project-plugin.gradle') << '''
            gradle.beforeProject { project ->
                project.repositories {
                    maven { url = uri("https://settings-plugin-per-project.example.com/") }
                }
            }
        '''
        settingsFile.text = """
            apply from: 'settings-per-project-plugin.gradle'
            rootProject.name = "myLib"
            include ":app"
            include ":lib"
        """
        createDirs("app", "lib")

        expect:
        succeeds ':repositories'
        outputContains('All Repositories')
        outputContains('https://settings-plugin-per-project.example.com/')
        outputContains('Roles:      PROJECT_DEPENDENCIES')
        // Repo is attributed to each project, not to settings.
        outputContains("Defined in: project ':' > repositories")
        outputContains("Defined in: project ':app' > repositories")
        outputContains("Defined in: project ':lib' > repositories")
        outputDoesNotContain("Defined in: settings >")
        // Each project's "uses" list should reference the repo.
        outputContains("project ':' uses")
        outputContains("project ':app' uses")
        outputContains("project ':lib' uses")
    }

    def "reports (ur) marker for unreachable URL"() {
        given:
        // Port 1 is a reserved "tcpmux" port that virtually nothing listens on — a connect there
        // will fail immediately with ECONNREFUSED, giving us a deterministic unreachable URL.
        buildFile << """
            repositories {
                maven {
                    url = uri("http://127.0.0.1:1/")
                    allowInsecureProtocol = true
                }
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('(ur)')
        outputContains('Legend')
        outputContains('(ur) Unreachable')
    }

    def "reports (ua) marker for URL requiring authentication"() {
        given:
        server.addHandler(new AbstractHandler() {
            @Override
            void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
                baseRequest.handled = true
            }
        })
        server.start()
        buildFile << """
            repositories {
                maven {
                    url = uri("${server.uri}/")
                    allowInsecureProtocol = true
                }
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('(ua)')
        outputContains('Legend')
        outputContains('(ua) Unauthorized')
        outputDoesNotContain('(ur)')
    }

    def "reports (o) marker on All Repositories heading in offline mode"() {
        given:
        buildFile << """
            repositories {
                maven {
                    url = uri("https://example.com/")
                }
            }
        """

        expect:
        succeeds ':repositories', '--offline'
        outputContains('All Repositories (o)')
        outputContains('Legend')
        outputContains('(o)  Running in offline mode')
        // Offline suppresses per-repo reachability markers.
        outputDoesNotContain('(ur)')
        outputDoesNotContain('(ua)')
    }

    def "no reachability markers when URL is reachable"() {
        given:
        server.addHandler(new AbstractHandler() {
            @Override
            void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
                response.setStatus(HttpServletResponse.SC_OK)
                baseRequest.handled = true
            }
        })
        server.start()
        buildFile << """
            repositories {
                maven {
                    url = uri("${server.uri}/")
                    allowInsecureProtocol = true
                }
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('All Repositories')
        outputDoesNotContain('(ur)')
        outputDoesNotContain('(ua)')
        outputDoesNotContain('(o)')
        outputDoesNotContain('Legend')
    }

    def "no reachability markers when server returns 405 on HEAD but 200 on GET (fallback classifies as REACHABLE)"() {
        given:
        server.addHandler(new AbstractHandler() {
            @Override
            void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
                if ("HEAD".equalsIgnoreCase(request.method)) {
                    response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
                } else {
                    response.setStatus(HttpServletResponse.SC_OK)
                }
                baseRequest.handled = true
            }
        })
        server.start()
        buildFile << """
            repositories {
                maven {
                    url = uri("${server.uri}/")
                    allowInsecureProtocol = true
                }
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('All Repositories')
        outputDoesNotContain('(ur)')
        outputDoesNotContain('(ua)')
        outputDoesNotContain('(m)')
    }

    def "renders notForConfigurations content filter branch"() {
        given:
        buildFile << """
            configurations.create("skipMe")
            repositories {
                maven {
                    url = uri("https://example.com/")
                    content {
                        notForConfigurations("skipMe")
                    }
                }
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('notForConfigurations')
    }

    def "renders onlyForAttribute content filter branch"() {
        given:
        buildFile << """
            import org.gradle.api.attributes.Attribute
            def attr = Attribute.of("example-attr", String)
            repositories {
                maven {
                    url = uri("https://example.com/")
                    content {
                        onlyForAttribute(attr, "one", "two")
                    }
                }
            }
        """

        expect:
        succeeds ':repositories'
        outputContains('onlyForAttribute')
    }

    def "repos declared via allprojects and subprojects are shown under each project"() {
        given:
        settingsFile.text = """
            rootProject.name = "myLib"
            include ":app"
            include ":lib"
        """
        createDirs("app", "lib")
        buildFile << '''
            allprojects {
                repositories {
                    mavenCentral()
                }
            }
            subprojects {
                repositories {
                    maven {
                        url = uri("https://sub.example.com/")
                    }
                }
            }
        '''

        expect:
        succeeds ':repositories'
        outputContains("All Repositories")
        outputContains("Repositories by Location")
        outputContains("project ':' uses")
        outputContains("project ':app' uses")
        outputContains("project ':lib' uses")
        // mavenCentral applied by allprojects — visible to root and both subprojects
        outputContains("https://repo.maven.apache.org/maven2/")
        // sub.example.com applied only to subprojects
        outputContains("https://sub.example.com/")
        // Root has mavenCentral only; each subproject has both — so there should be
        // at least 3 "- MavenRepo" reference lines across the per-project blocks.
        result.output.count('- MavenRepo') >= 3
    }
}
