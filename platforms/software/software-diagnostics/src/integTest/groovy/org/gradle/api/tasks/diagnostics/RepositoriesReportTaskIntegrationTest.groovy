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

/**
 * Tests for {@link org.gradle.api.tasks.diagnostics.RepositoriesReportTask}.
 */
class RepositoriesReportTaskIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final HttpServer server = new HttpServer()

    def setup() {
        settingsFile << """
            rootProject.name = "myLib"
        """
    }

    def "task is registered under help group"() {
        when:
        succeeds ':tasks', '--all'

        then:
        result.assertOutputContains("repositories")
    }

    def "task reports empty when no repositories declared"() {
        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""There are no repositories present.""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

MavenRepo (1)
    Location:   https://repo.maven.apache.org/maven2/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - MavenRepo (1)""")
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

        when:
        succeeds ':repositories', '--offline'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories (o)
--------------------------------------------------------

Gradle Central Plugin Repository (1)
    Location:   ${pluginPortalUrl}
    Type:       MAVEN
    Roles:      PLUGINS
    Defined in: settings > pluginManagement.repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

settings uses
    - Gradle Central Plugin Repository (1)

project ':' uses
    - Gradle Central Plugin Repository (1)

--------------------------------------------------------
Legend
--------------------------------------------------------

(o)  Running in offline mode \u2014 no reachability checks were performed.""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

MavenRepo (1)
    Location:   https://repo.maven.apache.org/maven2/
    Type:       MAVEN
    Roles:      SETTINGS_BUILDSCRIPT_DEPENDENCIES
    Defined in: settings > buildscript.repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

settings uses
    - MavenRepo (1)""")
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

        when:
        succeeds ':repositories', '--offline'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories (o)
--------------------------------------------------------

Gradle Central Plugin Repository (1)
    Location:   ${pluginPortalUrl}
    Type:       MAVEN
    Roles:      PLUGINS
    Defined in: settings > pluginManagement.repositories

MavenRepo (2)
    Location:   https://repo.maven.apache.org/maven2/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: settings > dependencyResolutionManagement.repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

settings uses
    - Gradle Central Plugin Repository (1)
    - MavenRepo (2)

project ':' uses
    - Gradle Central Plugin Repository (1)
    - MavenRepo (2)

project ':app' uses
    - Gradle Central Plugin Repository (1)
    - MavenRepo (2)

project ':lib' uses
    - Gradle Central Plugin Repository (1)
    - MavenRepo (2)

--------------------------------------------------------
Legend
--------------------------------------------------------

(o)  Running in offline mode \u2014 no reachability checks were performed.""")
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
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

Gradle Central Plugin Repository (1)
    Location:   ${pluginPortalUrl}
    Type:       MAVEN
    Roles:      PLUGINS
    Defined in: settings > pluginManagement.repositories

MavenRepo (2)
    Location:   https://repo.maven.apache.org/maven2/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: settings > dependencyResolutionManagement.repositories""")
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':app' uses
    - Gradle Central Plugin Repository (1)
    - MavenRepo (2)
    - Google (3)""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

MavenRepo (1) *
    Location:   https://repo.maven.apache.org/maven2/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':app' > repositories

MavenRepo (2) *
    Location:   https://repo.maven.apache.org/maven2/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':lib' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':app' uses
    - MavenRepo (1)

project ':lib' uses
    - MavenRepo (2)

--------------------------------------------------------
Legend
--------------------------------------------------------

(*) Identical repository declaration found in multiple locations.
    Consider consolidating to settings.dependencyResolutionManagement.repositories
    or settings.pluginManagement.repositories.
    See https://docs.gradle.org/current/userguide/centralizing_repositories.html""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1)
    Location:   https://example.com/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Content:    includeGroup("com.example")
    Defined in: project ':app' > repositories

maven (2)
    Location:   https://example.com/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':lib' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':app' uses
    - maven (1)

project ':lib' uses
    - maven (2)""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1)
    Location:   https://corp.example.com/ (ur)
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Auth:       DefaultBasicAuthentication_Decorated
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - maven (1)

--------------------------------------------------------
Legend
--------------------------------------------------------

(ur) Unreachable \u2014 the URL could not be contacted.""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1)
    Location:   https://corp.example.com/ (ur)
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Credentials: PRESENT
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - maven (1)

--------------------------------------------------------
Legend
--------------------------------------------------------

(ur) Unreachable \u2014 the URL could not be contacted.""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1)
    Location:   https://open.example.com/ (ur)
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - maven (1)

--------------------------------------------------------
Legend
--------------------------------------------------------

(ur) Unreachable \u2014 the URL could not be contacted.""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1)
    Location:   http://legacy.example.com/ (ur)
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Secure:     false
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - maven (1)

--------------------------------------------------------
Legend
--------------------------------------------------------

(ur) Unreachable \u2014 the URL could not be contacted.""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1)
    Location:   https://example.com/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Content:    includeGroup("com.example"), excludeModule("com.other", "bad"), onlyForConfigurations([compile])
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - maven (1)""")
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
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

MavenRepo (1)
    Location:   https://repo.maven.apache.org/maven2/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - MavenRepo (1)""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""    Type:       FLAT_DIR
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - flatDir (1)""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

ivy (1)
    Location:   https://example.com/ivy/ (ur)
    Type:       IVY
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - ivy (1)

--------------------------------------------------------
Legend
--------------------------------------------------------

(ur) Unreachable \u2014 the URL could not be contacted.""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

ivy (1)
    Location:   https://example.com/ivy/ (ur)
    Type:       IVY
    Roles:      PROJECT_DEPENDENCIES
    Content:    includeGroup("com.example")
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - ivy (1)

--------------------------------------------------------
Legend
--------------------------------------------------------

(ur) Unreachable \u2014 the URL could not be contacted.""")
    }

    def "reports mavenLocal repository with MAVEN_LOCAL type"() {
        given:
        buildFile << """
            repositories {
                mavenLocal()
            }
        """

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""    Type:       MAVEN_LOCAL
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - MavenLocal (1)""")
    }

    def "project buildscript repo gets PROJECT_BUILDSCRIPT_DEPENDENCIES role"() {
        given:
        buildFile.text = """
            buildscript {
                repositories {
                    mavenCentral()
                }
            }
        """

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

MavenRepo (1)
    Location:   https://repo.maven.apache.org/maven2/
    Type:       MAVEN
    Roles:      PROJECT_BUILDSCRIPT_DEPENDENCIES
    Defined in: project ':' > buildscript.repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - MavenRepo (1)""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

MavenRepo (1)
    Location:   https://repo.maven.apache.org/maven2/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - MavenRepo (1)""")
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

        when:
        succeeds ':repositories', '--offline'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""Gradle Central Plugin Repository (2)
    Location:   ${pluginPortalUrl}
    Type:       MAVEN
    Roles:      PLUGINS
    Defined in: settings > pluginManagement.repositories""")
        result.groupedOutput.task(":repositories").assertOutputContains("""MavenRepo (4) *
    Location:   https://repo.maven.apache.org/maven2/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: settings > dependencyResolutionManagement.repositories""")
        result.groupedOutput.task(":repositories").assertOutputContains("""ivy (9)
    Location:   https://app-ivy.example.com/
    Type:       IVY
    Roles:      PROJECT_DEPENDENCIES
    Credentials: PRESENT
    Defined in: project ':app' > repositories""")
        result.groupedOutput.task(":repositories").assertOutputContains("""settings uses
    - maven (1)
    - Gradle Central Plugin Repository (2)
    - maven (3)
    - MavenRepo (4)
    - Google (5)

project ':' uses
    - Gradle Central Plugin Repository (2)
    - maven (3)
    - MavenRepo (4)
    - Google (5)

project ':app' uses
    - Gradle Central Plugin Repository (2)
    - maven (3)
    - MavenRepo (4)
    - Google (5)
    - maven (6)
    - maven (7)
    - maven2 (8)
    - ivy (9)

project ':lib' uses
    - Gradle Central Plugin Repository (2)
    - maven (3)
    - MavenRepo (4)
    - Google (5)
    - maven (10)
    - MavenRepo (11)
    - flatDir (12)""")
        result.groupedOutput.task(":repositories").assertOutputContains("""(*) Identical repository declaration found in multiple locations.
    Consider consolidating to settings.dependencyResolutionManagement.repositories
    or settings.pluginManagement.repositories.
    See https://docs.gradle.org/current/userguide/centralizing_repositories.html
(o)  Running in offline mode \u2014 no reachability checks were performed.""")
        // Included build must NOT be descended
        outputDoesNotContain('included.example.com')
        outputDoesNotContain("project ':included'")
        // Credential values must never leak into the output
        outputDoesNotContain('ivyUser')
        outputDoesNotContain('ivyPass')
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1)
    Location:   https://project-plugin-repo.example.com/ (ur)
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':app' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':app' uses
    - maven (1)

--------------------------------------------------------
Legend
--------------------------------------------------------

(ur) Unreachable \u2014 the URL could not be contacted.""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1)
    Location:   https://settings-plugin-drm.example.com/ (ur)
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: settings > dependencyResolutionManagement.repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

settings uses
    - maven (1)

project ':' uses
    - maven (1)

project ':app' uses
    - maven (1)

project ':lib' uses
    - maven (1)

--------------------------------------------------------
Legend
--------------------------------------------------------

(ur) Unreachable \u2014 the URL could not be contacted.""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1)
    Location:   https://settings-plugin-drm.example.com/ (ur)
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: settings > dependencyResolutionManagement.repositories

maven (2)
    Location:   https://project-plugin-repo.example.com/ (ur)
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':app' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

settings uses
    - maven (1)

project ':' uses
    - maven (1)

project ':app' uses
    - maven (1)
    - maven (2)

--------------------------------------------------------
Legend
--------------------------------------------------------

(ur) Unreachable \u2014 the URL could not be contacted.""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1) *
    Location:   https://settings-plugin-per-project.example.com/ (ur)
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':' > repositories

maven (2) *
    Location:   https://settings-plugin-per-project.example.com/ (ur)
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':app' > repositories

maven (3) *
    Location:   https://settings-plugin-per-project.example.com/ (ur)
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':lib' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - maven (1)

project ':app' uses
    - maven (2)

project ':lib' uses
    - maven (3)

--------------------------------------------------------
Legend
--------------------------------------------------------

(*) Identical repository declaration found in multiple locations.
    Consider consolidating to settings.dependencyResolutionManagement.repositories
    or settings.pluginManagement.repositories.
    See https://docs.gradle.org/current/userguide/centralizing_repositories.html
(ur) Unreachable \u2014 the URL could not be contacted.""")
        outputDoesNotContain("Defined in: settings >")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1)
    Location:   http://127.0.0.1:1/ (ur)
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Secure:     false
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - maven (1)

--------------------------------------------------------
Legend
--------------------------------------------------------

(ur) Unreachable \u2014 the URL could not be contacted.""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1)
    Location:   ${server.uri}/ (ua)
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Secure:     false
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - maven (1)

--------------------------------------------------------
Legend
--------------------------------------------------------

(ua) Unauthorized \u2014 the URL returned 401/403; credentials were not sent.""")
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

        when:
        succeeds ':repositories', '--offline'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories (o)
--------------------------------------------------------

maven (1)
    Location:   https://example.com/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - maven (1)

--------------------------------------------------------
Legend
--------------------------------------------------------

(o)  Running in offline mode \u2014 no reachability checks were performed.
""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1)
    Location:   ${server.uri}/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Secure:     false
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - maven (1)""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1)
    Location:   ${server.uri}/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Secure:     false
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - maven (1)""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1)
    Location:   https://example.com/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Content:    notForConfigurations([skipMe])
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - maven (1)""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

maven (1)
    Location:   https://example.com/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Content:    onlyForAttribute(example-attr, [one, two])
    Defined in: project ':' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - maven (1)""")
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

        when:
        succeeds ':repositories'

        then:
        result.groupedOutput.task(":repositories").assertOutputContains("""--------------------------------------------------------
All Repositories
--------------------------------------------------------

MavenRepo (1) *
    Location:   https://repo.maven.apache.org/maven2/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':' > repositories

MavenRepo (2) *
    Location:   https://repo.maven.apache.org/maven2/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':app' > repositories

maven (3) *
    Location:   https://sub.example.com/ (ur)
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':app' > repositories

MavenRepo (4) *
    Location:   https://repo.maven.apache.org/maven2/
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':lib' > repositories

maven (5) *
    Location:   https://sub.example.com/ (ur)
    Type:       MAVEN
    Roles:      PROJECT_DEPENDENCIES
    Defined in: project ':lib' > repositories

--------------------------------------------------------
Repositories by Location
--------------------------------------------------------

project ':' uses
    - MavenRepo (1)

project ':app' uses
    - MavenRepo (2)
    - maven (3)

project ':lib' uses
    - MavenRepo (4)
    - maven (5)

--------------------------------------------------------
Legend
--------------------------------------------------------

(*) Identical repository declaration found in multiple locations.
    Consider consolidating to settings.dependencyResolutionManagement.repositories
    or settings.pluginManagement.repositories.
    See https://docs.gradle.org/current/userguide/centralizing_repositories.html
(ur) Unreachable \u2014 the URL could not be contacted.""")
    }

    /**
     * Returns the URL that the repositories report will print for the given canonical
     * repository factory. On CI the URL is rewritten by {@code mirroring-init-script.gradle}
     * based on the {@code REPO_MIRROR_URLS} env var; locally the original URL is used.
     *
     * @param mirrorKey one of "gradle-prod-plugins", "mavencentral", "google", "jcenter"
     * @param originalUrl the canonical URL used when no mirror is configured
     */
    private static String resolveReportedUrl(String mirrorKey, String originalUrl) {
        def env = System.getenv("REPO_MIRROR_URLS")
        if (!env) return originalUrl
        def entries = env.split(',').collectEntries { it.split(':', 2) as List }
        entries[mirrorKey] ?: originalUrl
    }

    private pluginPortalUrl = resolveReportedUrl("gradle-prod-plugins", "https://plugins.gradle.org/m2")
}
