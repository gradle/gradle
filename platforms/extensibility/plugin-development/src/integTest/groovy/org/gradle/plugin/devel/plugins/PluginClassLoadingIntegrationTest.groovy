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

package org.gradle.plugin.devel.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class PluginClassLoadingIntegrationTest extends AbstractIntegrationSpec {

    def "plugin can be applied when its jar contains classes referencing missing classes"() {
        given:
        pluginJarWithMissingClasses("""System.out.println("It works!");""")

        when:
        succeeds("help")

        then:
        outputContains("It works!")
    }

    def "reports NoClassDefFoundError from plugin apply when using missing class"() {
        given:
        pluginJarWithMissingClasses("new BrokenInner();")

        when:
        executer.withArgument("--stacktrace")
        fails("help")

        then:
        failure.assertHasDescription("A problem occurred evaluating root project")
        failure.assertHasErrorOutput("Caused by: java.lang.NoClassDefFoundError: example/Base")
        failure.assertHasErrorOutput("at example.MyPlugin.apply(MyPlugin.java:")
    }

    private String pluginJarWithMissingClasses(String applyCode) {
        // a plugin jar with a broken inner class
        file("plugin-build/settings.gradle") << "include 'base', 'plugin'"
        file("plugin-build/base/build.gradle") << """
            plugins { id 'java' }
        """
        file("plugin-build/base/src/main/java/example/Base.java") << """
            package example;
            public class Base {}
        """
        file("plugin-build/plugin/build.gradle") << """
            plugins { id 'java' }
            dependencies {
                compileOnly gradleApi()
                compileOnly project(':base')
            }
        """
        file("plugin-build/plugin/src/main/java/example/MyPlugin.java") << """
            package example;
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            public class MyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    $applyCode
                }
                public static class BrokenInner extends Base {}
            }
        """
        executer.inDirectory(file("plugin-build")).withTasks("jar").run()

        // plugin jar on the buildscript classpath without base library
        buildFile << """
            buildscript {
                dependencies {
                    classpath files("plugin-build/plugin/build/libs/plugin.jar")
                }
            }
            apply plugin: example.MyPlugin
        """
    }
}
