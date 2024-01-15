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

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading

class PluginUnderTest {

    public static final String PLUGIN_ID = "com.company.helloworld"

    private final TestDirectoryProvider testDirectoryProvider = new TestDirectoryProvider() {
        @Override
        TestFile getTestDirectory() {
            return projectDir
        }

        @Override
        void suppressCleanup() {

        }

        @Override
        void suppressCleanupErrors() {

        }
    }

    private final int num
    private final TestFile projectDir
    private List<File> implClasspath = []

    PluginUnderTest(TestFile projectDir) {
        this(0, projectDir)
    }

    PluginUnderTest(int num, TestFile projectDir) {
        this.num = num
        this.projectDir = projectDir
        this.implClasspath.addAll(getImplClasspath())
    }

    TestFile file(String path) {
        projectDir.file(path)
    }

    PluginUnderTest implClasspath(File... files) {
        implClasspath.clear()
        implClasspath.addAll(files)
        this
    }

    PluginUnderTest noImplClasspath() {
        implClasspath = null
        this
    }

    List<File> getImplClasspath() {
        // TODO: This should come from a common place
        [projectDir.file("build/classes/java/main"),
         projectDir.file("build/classes/groovy/main"),
         projectDir.file('build/resources/main')]
    }

    PluginUnderTest build() {
        writeSourceFiles()
        writeBuildScript()
        def executer = new GradleContextualExecuter(new UnderDevelopmentGradleDistribution(), testDirectoryProvider, IntegrationTestBuildContext.INSTANCE)
        try {
            executer
                .usingProjectDirectory(projectDir)
                .withArguments('classes')
                .withWarningMode(null)
                .run()
        } finally {
            executer.stop()
        }
        this
    }

    public <T> T exposeMetadata(Closure<T> closure) {
        def originalClassLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = new URLClassLoader(DefaultClassPath.of(generateMetadataFile().parentFile).asURLArray, originalClassLoader)
        try {
            closure.call()
        } finally {
            Thread.currentThread().contextClassLoader = originalClassLoader
        }
    }

    PluginUnderTest writeSourceFiles() {
        pluginClassSourceFile() << """
            package org.gradle.test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class HelloWorldPlugin$suffix implements Plugin<Project> {
                void apply(Project project) {
                    project.task('helloWorld$suffix', type: HelloWorld$suffix)
                }
            }
        """

        projectDir.file("src/main/groovy/org/gradle/test/HelloWorld${suffix}.groovy") << """
            package org.gradle.test

            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.TaskAction

            class HelloWorld$suffix extends DefaultTask {
                @TaskAction
                void doSomething() {
                    println 'Hello world!$suffix'
                    project.file("out.txt").text = "Hello world!$suffix"
                }
            }
        """

        projectDir.file("src/main/resources/META-INF/gradle-plugins/${id}.properties") << """
            implementation-class=org.gradle.test.HelloWorldPlugin$suffix
        """

        this
    }

    TestFile pluginClassSourceFile() {
        projectDir.file("src/main/groovy/org/gradle/test/HelloWorldPlugin${suffix}.groovy")
    }

    PluginUnderTest writeBuildScript() {
        projectDir.file("build.gradle") << """
            apply plugin: 'groovy'
            dependencies {
                implementation gradleApi()
                implementation localGroovy()
            }
        """

        this
    }

    private File generateMetadataFile() {
        def file = metadataFile.touch()
        def properties = new Properties()

        if (implClasspath != null) {
            def content = implClasspath.collect { it.absolutePath.replaceAll('\\\\', '/') }.join(File.pathSeparator)
            properties.setProperty(PluginUnderTestMetadataReading.IMPLEMENTATION_CLASSPATH_PROP_KEY, content)
        }

        file.withOutputStream { properties.store(it, null) }
        file
    }

    TestFile getMetadataFile() {
        projectDir.file("build/pluginUnderTestMetadata/${PluginUnderTestMetadataReading.PLUGIN_METADATA_FILE_NAME}")
    }

    String getSuffix() {
        num > 0 ? "$num" : ""
    }

    String getUseDeclaration() {
        """
            plugins {
                id "$id"
            }
        """
    }

    String getId() {
        "$PLUGIN_ID$suffix"
    }

    String getTaskClassName() {
        "org.gradle.test.HelloWorld${suffix}"
    }

    String echoClassNameTask() {
        """
            task echo$suffix {
                doLast {
                    println "class name: " + ${taskClassName}.name
                }
            }
        """
    }

    String echoClassNameTaskRuntime() {
        """
            def loader = getClass().classLoader
            task echo$suffix {
                doLast {
                    try {
                      println "class name: " + loader.loadClass("$taskClassName").name
                    } catch (ClassNotFoundException e) {
                      throw new RuntimeException("failed to load class $taskClassName")
                    }
                }
            }
        """
    }

}
