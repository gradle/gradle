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

package org.gradle.testkit.runner.fixtures

class AutomaticClasspathInjectionFixture {

    void createPluginProjectSourceFiles(File projectDir) {
        projectDir.file("src/main/groovy/org/gradle/test/HelloWorldPlugin.groovy") << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class HelloWorldPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.task('helloWorld', type: HelloWorld)
                }
            }
        """

        projectDir.file("src/main/groovy/org/gradle/test/HelloWorld.groovy") << """
            package org.gradle.test

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            class HelloWorld extends DefaultTask {
                @TaskAction
                void doSomething() {
                    println 'Hello world!'
                }
            }
        """

        projectDir.file("src/main/resources/META-INF/gradle-plugins/com.company.helloworld.properties") << """
            implementation-class=org.gradle.test.HelloWorldPlugin
        """

        projectDir.file("build.gradle") << """
            apply plugin: 'groovy'

            dependencies {
                compile gradleApi()
                compile localGroovy()
            }
        """
    }

    List<File> getPluginClasspath(File projectDir) {
        [projectDir.file("build/classes/main"), projectDir.file('build/resources/main')]
    }

    File createPluginClasspathManifestFile(File projectDir, List<File> classpath) {
        String content = classpath.collect { it.absolutePath.replaceAll('\\\\', '/') }.join('\n')
        File pluginClasspathFile = projectDir.file("build/pluginClasspathManifest/plugin-classpath.txt")
        pluginClasspathFile << content
        pluginClasspathFile
    }

    def withClasspath(List<File> classpathFiles, Closure closure) {
        ClassLoader originalClassLoader = getClass().classLoader

        try {
            URLClassLoader classLoader = new URLClassLoader(classpathFiles.collect { file -> file.toURI().toURL() } as URL[], Thread.currentThread().contextClassLoader)
            Thread.currentThread().contextClassLoader = classLoader
            return closure()
        } finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }
    }
}
