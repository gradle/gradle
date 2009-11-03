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

import ch.qos.logback.classic.Level
import ch.qos.logback.core.AppenderBase
import org.apache.tools.ant.BuildException
import org.apache.tools.ant.Project
import org.apache.tools.ant.taskdefs.ConditionTask
import org.gradle.api.GradleException
import org.gradle.api.internal.project.ant.BasicAntBuilder
import org.gradle.util.BootstrapUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

class DefaultIsolatedAntBuilderTest {
    private final DefaultIsolatedAntBuilder builder = new DefaultIsolatedAntBuilder()
    private TestAppender appender
    private ch.qos.logback.classic.Logger delegateLogger

    @Before
    public void attachAppender() {
        appender = new TestAppender()
        delegateLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger('ROOT')
        delegateLogger.addAppender(appender)
        appender.context = LoggerFactory.getILoggerFactory()
        delegateLogger.setLevel(Level.INFO);
    }

    @After
    public void detachAppender() {
        delegateLogger.detachAppender(appender);
    }

    @Test
    public void executesClosureAgainstDifferentVersionOfAntAndGroovy() {
        Object antBuilder = null
        Object antProject = null
        builder.execute(BootstrapUtil.antJarFiles + BootstrapUtil.groovyFiles) {
            antBuilder = delegate.builder
            antProject = delegate.antProject
        }
        assertThat(antBuilder, notNullValue())
        assertThat(antProject, notNullValue())

        ClassLoader loader = antBuilder.class.classLoader
        assertThat(loader, not(sameInstance(AntBuilder.classLoader)))

        assertThat(antBuilder, not(instanceOf(BasicAntBuilder)))
        assertThat(antBuilder, instanceOf(loader.loadClass(BasicAntBuilder.name)))

        assertThat(antBuilder, not(instanceOf(AntBuilder)))
        assertThat(antBuilder, instanceOf(loader.loadClass(AntBuilder.name)))

        assertThat(antProject, not(instanceOf(Project)))
        assertThat(antProject, instanceOf(loader.loadClass(Project.name)))

        assertThat(loader.loadClass(AntBuilder.name).classLoader, sameInstance(loader.loadClass(Project.name).classLoader))

        assertThat(loader.loadClass(BuildException.name), not(sameInstance(BuildException)))
        assertThat(loader.loadClass(Closure.name), not(sameInstance(Closure)))
    }

    @Test
    public void executesNestedClosures() {
        String propertyValue = null
        Object task = null
        builder.execute(BootstrapUtil.antJarFiles + BootstrapUtil.groovyFiles) {
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
    public void attachesLogger() {
        builder.execute(BootstrapUtil.antJarFiles + BootstrapUtil.groovyFiles) {
            property(name: 'message', value: 'a message')
            echo('${message}')
        }

        assertThat(appender.writer.toString(), equalTo('[ant:echo] a message'))
    }

    @Test
    public void addsToolsJarToClasspath() {
        builder.execute(BootstrapUtil.antJarFiles + BootstrapUtil.groovyFiles) {
            delegate.builder.class.classLoader.loadClass('com.sun.tools.javac.Main')
        }
    }

    @Test
    public void cachesClassloaderForGivenClassPath() {
        Object antBuilder1 = null
        builder.execute(BootstrapUtil.antJarFiles + BootstrapUtil.groovyFiles) {
            antBuilder1 = delegate.builder
        }
        Object antBuilder2 = null
        builder.execute(BootstrapUtil.antJarFiles + BootstrapUtil.groovyFiles) {
            antBuilder2 = delegate.builder
        }

        assertThat(antBuilder1.class, sameInstance(antBuilder2.class))
    }

    @Test
    public void setsContextClassLoader() {
        ClassLoader originalLoader = Thread.currentThread().contextClassLoader
        ClassLoader contextLoader = null
        Object antProject = null

        builder.execute(BootstrapUtil.antJarFiles + BootstrapUtil.groovyFiles) {
            antProject = delegate.antProject
            contextLoader = Thread.currentThread().contextClassLoader
        }

        assertThat(contextLoader, sameInstance(antProject.class.classLoader))
        assertThat(Thread.currentThread().contextClassLoader, sameInstance(originalLoader))
    }

    @Test
    public void gradleClassesAreNotVisibleToAnt() {
        ClassLoader loader = null
        builder.execute(BootstrapUtil.antJarFiles + BootstrapUtil.groovyFiles) {
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

class TestAppender<LoggingEvent> extends AppenderBase<LoggingEvent> {
    StringWriter writer = new StringWriter()

    synchronized void doAppend(LoggingEvent e) {
        append(e)
    }

    protected void append(LoggingEvent e) {
        writer.write(e.formattedMessage)
    }
}
