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

package org.gradle.xdcl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

/**
 * Smoke coverage for the {@code .gradle.xdcl} scripting language: proves the distribution under
 * test routes xdcl settings/build scripts natively and that script failures surface through the
 * Problems API with file:line:column coordinates. Broader functional coverage builds on this.
 */
class XdclScriptingSmokeIntegrationTest extends AbstractIntegrationSpec {

    def "routes settings.gradle.xdcl and binds include"() {
        given:
        file("app").createDir()
        xdclSettingsFile '''
            settings {
              include ["app"]
            }
        '''

        when:
        succeeds("projects")

        then:
        outputContains("Project ':app'")
    }

    def "an evaluation error fails the build as a located problem"() {
        given:
        enableProblemsApiCheck()
        xdclSettingsFile '''
            settings {
              includ ["app"]
            }
        '''

        when:
        fails("help")

        then:
        failure.assertHasDescription("${file('settings.gradle.xdcl')}:3:15")

        and:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:xdcl:xdcl-evaluation-error'
            contextualLabel.contains("includ")
        }
    }

    def "can apply settings plugin from included build"() {
        given:
        xdclSettingsFile '''
            settings {
                pluginManagement {
                  includedBuilds ["build-logic"]
                }
                plugins [
                  { id "local-settings-plugin" }
                ]
            }
        '''
        xdclFile 'build-logic/settings.gradle.xdcl', '''
            settings {}
        '''
        buildFile 'build-logic/build.gradle', '''
            plugins {
              id "java-gradle-plugin"
            }
            gradlePlugin {
              plugins {
                localSettingsPlugin {
                  id = "local-settings-plugin"
                  implementationClass = "my.LocalSettingsPlugin"
                }
              }
            }
        '''
        javaFile 'build-logic/src/main/java/my/LocalSettingsPlugin.java', """
            package my;

            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;

            public class LocalSettingsPlugin implements Plugin<Settings> {
                @Override public void apply(Settings target) {
                    System.out.println("LocalSettingsPlugin applied!");
                }
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("LocalSettingsPlugin applied!")
    }

    def "can register reactions via settings plugin from included build"() {
        given:
        xdclSettingsFile '''
            settings {
                pluginManagement {
                  includedBuilds ["build-logic"]
                }
                plugins [
                  { id "settings-message" }
                ]
                message { // extension
                  text "Yes!"
                }
            }
        '''
        xdclFile 'build-logic/settings.gradle.xdcl', '''
            settings {}
        '''
        buildFile 'build-logic/build.gradle', '''
            plugins {
              id "java-gradle-plugin"
              id "xdcl-gradle-plugin" // xdcl facades generator plugin is available in the Gradle plugins classpath just like any other builtin plugin
            }
            gradlePlugin {
              plugins {
                messagePlugin {
                  id = "settings-message"
                  implementationClass = "my.SettingsMessagePlugin"
                }
                unusedPlugin {
                  id = "unused-plugin"
                  implementationClass = "my.UnusedPlugin"
                }
              }
            }
        '''
        xdslFile 'build-logic/src/main/xdcl/my.xdsl', '''
            package my.dsl

            import xdcl.gradle.bootstrap // Settings

            extension Message for Settings {
              message {
                text: String
              }
            }
        '''
        javaFile 'build-logic/src/main/java/my/SettingsMessagePlugin.java', """
            package my;

            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;
            import org.gradle.api.xdcl.*; // xdcl API is visible as part of the Gradle API
            import my.dsl.*; // generated facades dir is automatically wired in as a generated source-set

            @BindReaction(SettingsMessagePlugin.MessageReaction.class)
            public class SettingsMessagePlugin implements Plugin<Settings> {
                static class MessageReaction implements Reaction<Message, Settings> {
                    @Override public void on(Message data, Settings context, ReactionScope scope) {
                        System.out.println("on: " + data.text().get());
                    }
                }

                @Override public void apply(Settings target) {
                    System.out.println("SettingsMessagePlugin applied!");
                }
            }
        """
        javaFile 'build-logic/src/main/java/my/UnusedPlugin.java', """
            package my;

            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;
            import org.gradle.api.xdcl.*;
            import my.dsl.*;

            @BindReaction(UnusedPlugin.UnusedReaction.class)
            public class UnusedPlugin implements Plugin<Settings> {

                static class UnusedReaction implements Reaction<Message, Settings> {
                    @Override public void on(Message data, Settings context, ReactionScope scope) {
                        System.out.println("ERROR: unused: " + data.text().get());
                    }
                }

                @Override public void apply(Settings target) {
                    System.out.println("UnusedPlugin applied!");
                }
            }
        """

        when:
        succeeds("help")

        then: 'ReactionsPlugin is applied'
        outputContains("SettingsMessagePlugin applied!")

        and: 'its reactions are fired'
        outputContains("on: Yes!")

        and: 'UnusedPlugin is NOT applied'
        outputDoesNotContain("UnusedPlugin applied!")

        and: 'since it has not been applied, its reactions are NOT fired'
        outputDoesNotContain("ERROR: unused")
    }

    def "can register project template and associated reactions via settings plugin from included build"() {
        given:
        xdclSettingsFile '''
            settings {
                pluginManagement {
                  includedBuilds ["build-logic"]
                }
                plugins [
                  { id "project-templates" }
                ]
                include [
                  "app",
                  "lib",
                ]
            }
        '''
        xdclFile 'app/build.gradle.xdcl', '''
            application {}
        '''
        xdclFile 'lib/build.gradle.xdcl', '''
            library {}
        '''
        xdclFile 'build-logic/settings.gradle.xdcl', '''
            settings {}
        '''
        buildFile 'build-logic/build.gradle', '''
            plugins {
              id "java-gradle-plugin"
              id "xdcl-gradle-plugin"
            }
            gradlePlugin {
              plugins {
                projectTemplatesPlugin {
                  id = "project-templates"
                  implementationClass = "my.ProjectTemplatesPlugin"
                }
              }
            }
        '''
        xdslFile 'build-logic/src/main/xdcl/my.xdsl', '''
            package my.dsl

            template MyApplication {
              application {}
            }

            template MyLibrary {
              library {}
            }
        '''
        javaFile 'build-logic/src/main/java/my/ProjectTemplatesPlugin.java', """
            package my;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.initialization.Settings;
            import org.gradle.api.xdcl.*; // xdcl API is visible as part of the Gradle API
            import my.dsl.*; // generated facades dir is automatically wired in as a generated source-set

            @BindReaction(ProjectTemplatesPlugin.ApplicationReaction.class)
            @BindReaction(ProjectTemplatesPlugin.LibraryReaction.class)
            public class ProjectTemplatesPlugin implements Plugin<Settings> {

                static class ApplicationReaction implements Reaction<MyApplication, Project> {
                    @Override public void on(MyApplication data, Project context, ReactionScope scope) {
                        System.out.println("application: " + context.getName());
                    }
                }

                static class LibraryReaction implements Reaction<MyLibrary, Project> {
                    @Override public void on(MyLibrary data, Project context, ReactionScope scope) {
                        System.out.println("library: " + context.getName());
                    }
                }

                @Override public void apply(Settings target) {}
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("application: app")
        outputContains("library: lib")
    }

    def "can configure project root name"() {
        given:
        xdclSettingsFile '''
            settings {
              rootProject {
                name "root"
              }
            }
        '''

        when:
        succeeds("projects")

        then:
        outputContains("Root project 'root'")
    }

    def "xdcl build logic can share schemas"() {
        given: 'a build definition'
        xdclSettingsFile '''
            settings {
                pluginManagement {
                  includedBuilds ["build-logic"]
                }
                plugins [
                  { id "project-templates" }
                ]
                include [
                  "app",
                  "lib",
                ]
            }
        '''
        xdclFile 'app/build.gradle.xdcl', '''
            application {
              description "My application."
            }
        '''
        xdclFile 'lib/build.gradle.xdcl', '''
            library {
              // Accept defaults
            }
        '''

        and: 'with build-logic split in 4 projects'
        xdclFile 'build-logic/settings.gradle.xdcl', '''
            settings {
              include [
                "base",
                "app",
                "lib",
                "plugin",
              ]
            }
        '''

        buildFile 'build-logic/base/build.gradle', '''
            plugins {
                id "java-library"
                id "xdcl-gradle-plugin"
            }
        '''
        xdslFile 'build-logic/base/src/main/xdcl/base.xdsl', '''
            package my.base.dsl

            trait MyComponent {
              description: String = "My component."
            }
        '''

        buildFile 'build-logic/app/build.gradle', '''
            plugins {
                id "java-library"
                id "xdcl-gradle-plugin"
            }

            dependencies {
                api(project(":base"))
            }
        '''
        xdslFile 'build-logic/app/src/main/xdcl/app.xdsl', '''
            package my.app.dsl

            import my.base.dsl

            template MyApplication with MyComponent  {
              application {}
            }
        '''

        buildFile 'build-logic/lib/build.gradle', '''
            plugins {
                id "java-library"
                id "xdcl-gradle-plugin"
            }

            dependencies {
                api(project(":base"))
            }
        '''
        xdslFile 'build-logic/lib/src/main/xdcl/lib.xdsl', '''
            package my.lib.dsl

            import my.base.dsl

            template MyLibrary with MyComponent  {
              library {}
            }
        '''

        buildFile 'build-logic/plugin/build.gradle', '''
            plugins {
              id "java-gradle-plugin"
            }
            gradlePlugin {
              plugins {
                projectTemplatesPlugin {
                  id = "project-templates"
                  implementationClass = "my.ProjectTemplatesPlugin"
                }
              }
            }
            dependencies {
                api(project(":lib"))
                api(project(":app"))
                api(project(":base"))
            }
        '''
        javaFile 'build-logic/plugin/src/main/java/my/ProjectTemplatesPlugin.java', """
            package my;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.initialization.Settings;
            import org.gradle.api.xdcl.*; // xdcl API is visible as part of the Gradle API
            import my.base.dsl.*;
            import my.app.dsl.*;
            import my.lib.dsl.*;

            @BindReaction(ProjectTemplatesPlugin.ComponentReaction.class)
            @BindReaction(ProjectTemplatesPlugin.ApplicationReaction.class)
            @BindReaction(ProjectTemplatesPlugin.LibraryReaction.class)
            public class ProjectTemplatesPlugin implements Plugin<Settings> {

                static class ComponentReaction implements Reaction<MyComponent, Project> {
                    @Override public void on(MyComponent data, Project context, ReactionScope scope) {
                        System.out.println("component " + data.description().get() + " in " + context.getName());
                    }
                }

                static class ApplicationReaction implements Reaction<MyApplication, Project> {
                    @Override public void on(MyApplication data, Project context, ReactionScope scope) {
                        System.out.println("application: " + context.getName());
                    }
                }

                static class LibraryReaction implements Reaction<MyLibrary, Project> {
                    @Override public void on(MyLibrary data, Project context, ReactionScope scope) {
                        System.out.println("library: " + context.getName());
                    }
                }

                @Override public void apply(Settings target) {}
            }
        """

        when:
        succeeds("help")

        then:
        outputContains("component 'My application.' in app")
        outputContains("application: app")
        outputContains("component 'My component.' in lib")
        outputContains("library: lib")
    }

    def "can configure project root name"() {
        given:
        xdclSettingsFile '''
            settings {
              rootProject {
                name "root"
              }
            }
        '''

        when:
        succeeds("projects")

        then:
        outputContains("Root project 'root'")
    }

    TestFile xdclSettingsFile(String script) {
        file('settings.gradle.xdcl') << script
    }

    TestFile xdclFile(String path, String script) {
        file(path) << script
    }

    TestFile xdslFile(String path, String script) {
        file(path) << script
    }
}
