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

import org.gradle.api.problems.LineInFileLocation
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

    def "prefers settings.gradle.xdcl over a Groovy settings file in the same directory"() {
        given:
        enableProblemsApiCheck()
        xdclSettingsFile '''
            settings {
              rootProject {
                name "from-xdcl"
              }
            }
        '''
        file("settings.gradle") << '''
            rootProject.name = "from-groovy"
        '''

        when:
        succeeds("projects")

        then: 'the xdcl settings script is the one evaluated, not the shadowed Groovy file'
        outputContains("Root project 'from-xdcl'")

        and: 'the ignored Groovy settings file is reported through the Problems API'
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:multiple-scripts'
            details.contains("Selected 'settings.gradle.xdcl'")
            details.contains("ignoring 'settings.gradle'")
        }
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
                        System.out.println("component '" + data.description().get() + "' in " + context.getName());
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

    def "can declare defaults in settings"() {
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
                defaults {
                  library { name "my lib" }
                  application { name "my app" }
                  for MyComponent { version "0.1.0" }
                }
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

            trait MyComponent {
              name: String?
              version: String?
            }

            template MyApplication with MyComponent {
              application {}
            }

            template MyLibrary with MyComponent {
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
                        System.out.println("app " + dump(data));
                    }
                }

                static class LibraryReaction implements Reaction<MyLibrary, Project> {
                    @Override public void on(MyLibrary data, Project context, ReactionScope scope) {
                        System.out.println("lib " + dump(data));
                    }
                }

                static String dump(MyComponent data) {
                    return "{ name " + quoted(data.name().get()) + ", version " + quoted(data.version().get()) + "}";
                }

                static String quoted(Object value) {
                    return "\\"" + value + "\\"";
                }

                @Override public void apply(Settings target) {}
            }
        """

        when:
        succeeds("help")

        then:
        outputContains 'app { name "my app", version "0.1.0"}'
        outputContains 'lib { name "my lib", version "0.1.0"}'
    }

    def "a poisoned default warns at assembly and hard-errors on the project that reaches it"() {
        given:
        enableProblemsApiCheck()
        componentsSchemaPlugin()
        xdclSettingsFile '''
            settings {
                pluginManagement { includedBuilds ["build-logic"] }
                plugins [ { id "components" } ]
                include [ "app" ]
                defaults {
                  for MyComponent { version "1.0" }
                  for MyComponent { version "2.0" }
                }
            }
        '''
        // The project omits `version`, so it REACHES the poisoned cell — a hard, located error (D9:
        // optionality does not dissolve a poison). Written at column 1 so the location is stable.
        xdclFile 'app/build.gradle.xdcl', 'application {}\n'

        when:
        fails("help")

        then: 'the build fails configuring the project that reached the poisoned cell'
        failure.assertHasDescription("A problem occurred configuring project ':app'.")

        and: 'both the unconditional W-class warning (detect-once) and the per-project error (report-per-instance) surface as known problems'
        // Problems sort by fqid: xdcl-conflicting-defaults (warning) < xdcl-evaluation-error (error).
        verifyAll(receivedProblem(0)) {
            definition.id.fqid == 'scripts:xdcl:xdcl-conflicting-defaults'
            contextualLabel.contains("conflicting defaults for property 'version'")
        }
        verifyAll(receivedProblem(1)) {
            definition.id.fqid == 'scripts:xdcl:xdcl-evaluation-error'
            contextualLabel.contains("conflicting defaults for property 'version'")
            contextualLabel.contains("set it explicitly to resolve the conflict")
            // located on the project that reached it, not the settings file where it was declared
            oneLocation(LineInFileLocation).path.endsWith("app/build.gradle.xdcl")
            oneLocation(LineInFileLocation).line == 1
        }
    }

    def "a poisoned default that no project reaches is warned at assembly but never errors"() {
        given:
        enableProblemsApiCheck()
        componentsSchemaPlugin()
        xdclSettingsFile '''
            settings {
                pluginManagement { includedBuilds ["build-logic"] }
                plugins [ { id "components" } ]
                include [ "app" ]
                defaults {
                  for MyComponent { version "1.0" }
                  for MyComponent { version "2.0" }
                }
            }
        '''
        // The project SUPPLIES the conflicted property, so the per-project error dissolves — but the
        // unconditional assembly warning still fires (a conflict no instance reaches is still latent).
        xdclFile 'app/build.gradle.xdcl', 'application {\n  version "explicit"\n}\n'

        when:
        succeeds("help")

        then: 'the explicit value wins (config beats every default)'
        outputContains("app-version=explicit")

        and: 'the unconditional poison warning still surfaces as a known problem'
        verifyAll(receivedProblem) {
            definition.id.fqid == 'scripts:xdcl:xdcl-conflicting-defaults'
            contextualLabel.contains("conflicting defaults for property 'version'")
            contextualLabel.contains("defined at the same specificity")
        }
    }

    def "all four default sources compose with precedence (config over project over settings over plugin)"() {
        given:
        // The plugin SHIPS its defaults AND binds its reactions from its single src/main/xdcl/<id>.xdcl
        // (xdcl-gradle-plugin generates the Plugin<Settings> carrier) — no hand-written plugin, no manual
        // gradlePlugin registration. The filename is the canonical plugin id ("components"). The
        // templates are `with DefaultsContributor`, so a project's build.gradle.xdcl can host its own
        // project-stratum `defaults { }` block.
        xdclFile 'build-logic/settings.gradle.xdcl', 'settings {}'
        buildFile 'build-logic/build.gradle', '''
            plugins {
              id "java-gradle-plugin"
              id "xdcl-gradle-plugin"
            }
        '''
        xdslFile 'build-logic/src/main/xdcl/my.xdsl', '''
            package my.dsl

            import xdcl.gradle.bootstrap

            trait MyComponent {
              name: String?
              version: String?
            }

            template MyApplication with MyComponent & DefaultsContributor {
              application {}
            }

            template MyLibrary with MyComponent & DefaultsContributor {
              library {}
            }
        '''
        xdclFile 'build-logic/src/main/xdcl/components.xdcl', '''
            plugin {
              reactions [:my.ApplicationReaction, :my.LibraryReaction]
              defaults {
                for MyComponent {
                  name "plugin-name"
                  version "plugin-ver"
                }
              }
            }
        '''
        javaFile 'build-logic/src/main/java/my/ApplicationReaction.java', """
            package my;

            import org.gradle.api.Project;
            import org.gradle.api.xdcl.*;
            import my.dsl.*;

            public class ApplicationReaction implements Reaction<MyApplication, Project> {
                @Override public void on(MyApplication data, Project context, ReactionScope scope) {
                    System.out.println("app name=" + data.name().get() + " version=" + data.version().get());
                }
            }
        """
        javaFile 'build-logic/src/main/java/my/LibraryReaction.java', """
            package my;

            import org.gradle.api.Project;
            import org.gradle.api.xdcl.*;
            import my.dsl.*;

            public class LibraryReaction implements Reaction<MyLibrary, Project> {
                @Override public void on(MyLibrary data, Project context, ReactionScope scope) {
                    System.out.println("lib name=" + data.name().get() + " version=" + data.version().get());
                }
            }
        """
        xdclSettingsFile '''
            settings {
                pluginManagement { includedBuilds ["build-logic"] }
                plugins [ { id "components" } ]
                include [ "app", "lib" ]
                defaults {
                  for MyComponent { version "settings-ver" }
                }
            }
        '''
        // app: a PROJECT-stratum default sets `name`; `version` falls through to the settings stratum.
        xdclFile 'app/build.gradle.xdcl', '''
            application {
              defaults { for MyComponent { name "app-name" } }
            }
        '''
        // lib: no project default, but sets `version` explicitly (config). `name` falls through to the plugin.
        xdclFile 'lib/build.gradle.xdcl', '''
            library {
              version "explicit-lib"
            }
        '''

        when:
        succeeds("help")

        then: 'each cell is won by a different source: project (app name) and settings (app version) shadow the plugin; explicit config (lib version) beats all; the plugin default survives where nothing higher sets it (lib name)'
        outputContains("app name=app-name version=settings-ver")
        outputContains("lib name=plugin-name version=explicit-lib")
    }

    /**
     * An included build-logic plugin (id {@code components}) that contributes ONLY a one-trait schema
     * ({@code MyComponent} carried by a single template, so a defaults conflict yields exactly one
     * warning) plus a reaction that prints the resolved {@code version}. Used by the poison tests; the
     * conflict itself is declared in the consuming {@code settings.gradle.xdcl}.
     */
    private void componentsSchemaPlugin() {
        xdclFile 'build-logic/settings.gradle.xdcl', 'settings {}'
        buildFile 'build-logic/build.gradle', '''
            plugins {
              id "java-gradle-plugin"
              id "xdcl-gradle-plugin"
            }
            gradlePlugin {
              plugins {
                componentsPlugin {
                  id = "components"
                  implementationClass = "my.ComponentsPlugin"
                }
              }
            }
        '''
        xdslFile 'build-logic/src/main/xdcl/my.xdsl', '''
            package my.dsl

            trait MyComponent {
              name: String?
              version: String?
            }

            template MyApplication with MyComponent {
              application {}
            }
        '''
        javaFile 'build-logic/src/main/java/my/ComponentsPlugin.java', """
            package my;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.initialization.Settings;
            import org.gradle.api.xdcl.*;
            import my.dsl.*;

            @BindReaction(ComponentsPlugin.ApplicationReaction.class)
            public class ComponentsPlugin implements Plugin<Settings> {
                static class ApplicationReaction implements Reaction<MyApplication, Project> {
                    @Override public void on(MyApplication data, Project context, ReactionScope scope) {
                        System.out.println("app-version=" + data.version().get());
                    }
                }
                @Override public void apply(Settings target) {}
            }
        """
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
