/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.project

import org.apache.tools.ant.Project
import org.apache.tools.ant.taskdefs.ConditionTask
import org.gradle.api.GradleException
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.DefaultClassPathProvider
import org.gradle.api.internal.DefaultClassPathRegistry
import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.api.internal.project.antbuilder.DefaultIsolatedAntBuilder
import org.gradle.api.logging.LogLevel
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.classloader.DefaultClassLoaderFactory
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.logging.TestOutputEventListener
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.not
import static org.hamcrest.CoreMatchers.sameInstance
import static org.junit.Assert.assertThat
import static org.junit.Assert.fail

class DefaultIsolatedAntBuilderTest {
    private final ModuleRegistry moduleRegistry = new DefaultModuleRegistry(CurrentGradleInstallation.get())
    private final ClassPathRegistry registry = new DefaultClassPathRegistry(new DefaultClassPathProvider(moduleRegistry))
    private final DefaultIsolatedAntBuilder builder = new DefaultIsolatedAntBuilder(registry, new DefaultClassLoaderFactory(), moduleRegistry)
    private final TestOutputEventListener outputEventListener = new TestOutputEventListener()
    @Rule
    public final ConfigureLogging logging = new ConfigureLogging(outputEventListener, LogLevel.INFO)
    private Collection<File> classpath

    @Before
    void attachAppender() {
        classpath = moduleRegistry.getExternalModule("groovy-all").getClasspath().asFiles
    }

    @After
    void cleanup() {
        builder.stop()
    }

    @Test
    void executesNestedClosures() {
        String propertyValue = null
        Object task = null
        builder.execute {
            property(name: 'message', value: 'a message')
            task = condition(property: 'prop', value: 'a message') {
                isset(property: 'message')
            }
            task = task.proxy
            propertyValue = project.properties.prop
        }

        assertThat(propertyValue, equalTo('a message'))
        assertThat(task.class.name, equalTo(ConditionTask.class.name))
    }

    @Test
    void canAccessAntBuilderFromWithinClosures() {
        builder.execute {
            assertThat(ant, sameInstance(delegate))

            ant.property(name: 'prop', value: 'a message')
            assertThat(project.properties.prop, equalTo('a message'))
        }
    }

    @Test
    void attachesLogger() {
        builder.execute {
            property(name: 'message', value: 'a message')
            echo('${message}')
        }

        assertThat(outputEventListener.toString(), equalTo('[[WARN] [org.gradle.api.internal.project.ant.AntLoggingAdapter] [ant:echo] a message]'))
    }

    @Test
    void bridgesLogging() {
        def classpath = ClasspathUtil.getClasspathForClass(TestAntTask)

        builder.withClasspath([classpath]).execute {
            taskdef(name: 'loggingTask', classname: TestAntTask.name)
            loggingTask()
        }

        assertThat(outputEventListener.toString(), containsString('[[INFO] [ant-test] a jcl log message]'))
        assertThat(outputEventListener.toString(), containsString('[[INFO] [ant-test] an slf4j log message]'))
        assertThat(outputEventListener.toString(), containsString('[[INFO] [ant-test] a log4j log message]'))
    }

    @Test
    void addsToolsJarToClasspath() {
        builder.execute {
            delegate.builder.class.classLoader.loadClass('com.sun.tools.javac.Main')
        }
    }

    @Test
    void reusesAntGroovyClassloader() {
        ClassLoader antClassLoader = null
        builder.withClasspath([new File("no-existo.jar")]).execute {
            antClassLoader = project.class.classLoader
        }
        ClassLoader antClassLoader2 = null
        builder.withClasspath([new File("unknown.jar")]).execute {
            antClassLoader2 = project.class.classLoader
        }

        assertThat(antClassLoader, sameInstance(antClassLoader2))
    }

    @Test
    void reusesClassloaderForImplementation() {
        ClassLoader loader1 = null
        ClassLoader loader2 = null
        def classpath = [new File("no-existo.jar")]
        builder.withClasspath(classpath).execute {
            loader1 = delegate.antlibClassLoader
            owner.builder.withClasspath(classpath).execute {
                loader2 = delegate.antlibClassLoader
            }
        }


        assertThat(loader1, sameInstance(loader2))


        ClassLoader loader3 = null
        builder.withClasspath(classpath + [new File("unknown.jar")]).execute {
            loader3 = delegate.antlibClassLoader
        }

        assertThat(loader1, not(sameInstance(loader3)))
    }

    @Test
    void setsContextClassLoader() {
        ClassLoader originalLoader = Thread.currentThread().contextClassLoader
        ClassLoader contextLoader = null
        Object antProject = null

        builder.execute {
            antProject = delegate.antProject
            contextLoader = Thread.currentThread().contextClassLoader
        }

        assertThat(contextLoader.loadClass(Project.name), sameInstance(antProject.class))
        assertThat(Thread.currentThread().contextClassLoader, sameInstance(originalLoader))
    }

    @Test
    void gradleClassesAreNotVisibleToAnt() {
        ClassLoader loader = null
        builder.execute {
            loader = antProject.getClass().classLoader
        }

        try {
            loader.loadClass(GradleException.name)
            fail()
        } catch (ClassNotFoundException e) {
            // expected
        }
    }
}
