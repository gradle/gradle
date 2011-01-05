/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.internal.project.ant.BasicAntBuilder
import org.gradle.api.internal.project.ant.AntLoggingAdapter
import org.gradle.util.*
import org.gradle.api.internal.ClassPathRegistry

class DefaultIsolatedAntBuilder implements IsolatedAntBuilder {
    private final Map<List<File>, Map<String, Object>> classloaders
    private final ClassPathRegistry classPathRegistry
    private final Iterable<File> groovyClasspath
    private final Iterable<File> libClasspath

    def DefaultIsolatedAntBuilder(ClassPathRegistry classPathRegistry) {
        this.classPathRegistry = classPathRegistry
        classloaders = [:]
        groovyClasspath = classPathRegistry.getClassPathFiles("LOCAL_GROOVY")
        libClasspath = []
    }

    private DefaultIsolatedAntBuilder(DefaultIsolatedAntBuilder copy, Iterable<File> groovyClasspath, Iterable<File> libClasspath) {
        this.classPathRegistry = copy.classPathRegistry
        this.classloaders = copy.classloaders
        this.groovyClasspath = groovyClasspath
        this.libClasspath = libClasspath
    }

    IsolatedAntBuilder withGroovy(Iterable<File> classpath) {
        return new DefaultIsolatedAntBuilder(this, classpath, libClasspath);
    }

    IsolatedAntBuilder withClasspath(Iterable<File> classpath) {
        return new DefaultIsolatedAntBuilder(this, groovyClasspath, classpath);
    }

    void execute(Closure antClosure) {
        List<File> normalisedClasspath = []
        normalisedClasspath.addAll(classPathRegistry.getClassPathFiles("ANT"))
        normalisedClasspath.addAll(groovyClasspath as List)
        normalisedClasspath.addAll(libClasspath as List)

        Map<String, Object> classloadersForPath = classloaders[normalisedClasspath]
        ClassLoader antLoader
        ClassLoader gradleLoader
        if (classloadersForPath) {
            antLoader = classloadersForPath.antLoader
            gradleLoader = classloadersForPath.gradleLoader
        } else {
            // Need tools.jar for compile tasks
            List<File> fullClasspath = normalisedClasspath
            File toolsJar = Jvm.current().toolsJar
            if (toolsJar) {
                fullClasspath += toolsJar
            }

            Closure converter = {File file -> file.toURI().toURL() }
            URL[] classpathUrls = fullClasspath.collect(converter)
            // Need gradle core to pick up ant logging adapter
            URL[] gradleCoreUrls = classPathRegistry.getClassPathUrls("GRADLE_CORE")

            FilteringClassLoader loggingLoader = new FilteringClassLoader(getClass().classLoader)
            loggingLoader.allowPackage('org.slf4j')

            antLoader = new URLClassLoader(classpathUrls, ClassLoader.systemClassLoader.parent)
            gradleLoader = new URLClassLoader(gradleCoreUrls, new MultiParentClassLoader(antLoader, loggingLoader))

            classloaders[normalisedClasspath] = [antLoader: antLoader, gradleLoader: gradleLoader]
        }

        ClassLoader originalLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = antLoader
        try {
            Object antBuilder = gradleLoader.loadClass(BasicAntBuilder.class.name).newInstance()

            Object antLogger = gradleLoader.loadClass(AntLoggingAdapter.class.name).newInstance()
            antBuilder.project.removeBuildListener(antBuilder.project.getBuildListeners()[0])
            antBuilder.project.addBuildListener(antLogger)

            // Ideally, we'd delegate directly to the AntBuilder, but it's Closure class is different to our caller's
            // Closure class, so the AntBuilder's methodMissing() doesn't work. It just converts our Closures to String
            // because they are not an instanceof it's Closure class
            Object delegate = new AntBuilderDelegate(antBuilder)
            ConfigureUtil.configure(antClosure, delegate)
        } finally {
            Thread.currentThread().contextClassLoader = originalLoader
        }
    }
}

class AntBuilderDelegate extends BuilderSupport {
    def Object builder

    def AntBuilderDelegate(builder) {
        this.builder = builder;
    }

    def propertyMissing(String name) {
        builder."$name"
    }

    protected Object createNode(Object name) {
        builder.createNode(name)
    }

    protected Object createNode(Object name, Map attributes) {
        builder.createNode(name, attributes)
    }

    protected Object createNode(Object name, Map attributes, Object value) {
        builder.createNode(name, attributes, value)
    }

    protected Object createNode(Object name, Object value) {
        builder.createNode(name, value)
    }

    protected void setParent(Object parent, Object child) {
        builder.setParent(parent, child)
    }

    protected void nodeCompleted(Object parent, Object node) {
        builder.nodeCompleted(parent, node)
    }

    protected Object postNodeCompletion(Object parent, Object node) {
        builder.postNodeCompletion(parent, node)
    }
}
