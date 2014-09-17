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

import com.google.common.collect.Lists
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.project.ant.AntLoggingAdapter
import org.gradle.api.internal.project.ant.BasicAntBuilder
import org.gradle.internal.classloader.ClassLoaderFactory
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classloader.MultiParentClassLoader
import org.gradle.internal.classloader.MutableURLClassLoader
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.jvm.Jvm
import org.gradle.util.ConfigureUtil

// TODO: should be threadsafe; is stateful and of build scope

class DefaultIsolatedAntBuilder implements IsolatedAntBuilder {

    private final ClassLoader antClassloader
    private final Map<ClassPath, ClassLoaderSet> classloaders
    private final ClassPathRegistry classPathRegistry
    private final ClassLoaderFactory classLoaderFactory
    private final ClassPath libClasspath

    def DefaultIsolatedAntBuilder(ClassPathRegistry classPathRegistry, ClassLoaderFactory classLoaderFactory) {
        this.classPathRegistry = classPathRegistry
        this.classLoaderFactory = classLoaderFactory
        this.classloaders = [:]
        this.libClasspath = new DefaultClassPath()

        List<File> antClasspath = Lists.newArrayList(classPathRegistry.getClassPath("ANT").asFiles)
        // Need tools.jar for compile tasks
        File toolsJar = Jvm.current().toolsJar
        if (toolsJar) {
            antClasspath += toolsJar
        }
        this.antClassloader = classLoaderFactory.createIsolatedClassLoader(new DefaultClassPath(antClasspath))
    }

    private DefaultIsolatedAntBuilder(DefaultIsolatedAntBuilder copy, Iterable<File> libClasspath) {
        this.classPathRegistry = copy.classPathRegistry
        this.classLoaderFactory = copy.classLoaderFactory
        this.classloaders = copy.classloaders
        this.antClassloader = copy.antClassloader
        this.libClasspath = new DefaultClassPath(libClasspath)
    }

    IsolatedAntBuilder withClasspath(Iterable<File> classpath) {
        return new DefaultIsolatedAntBuilder(this, classpath)
    }

    void execute(Closure antClosure) {
        def classLoadersForImpl = classloaders[libClasspath]

        if (!classLoadersForImpl) {
            // Need gradle core to pick up ant logging adapter, AntBuilder and such
            def gradleCoreUrls = classPathRegistry.getClassPath("GRADLE_CORE")
            gradleCoreUrls += classPathRegistry.getClassPath("GROOVY")

            // Need Transformer (part of AntBuilder API) from base services
            gradleCoreUrls += classPathRegistry.getClassPath("GRADLE_BASE_SERVICES")

            FilteringClassLoader loggingLoader = new FilteringClassLoader(getClass().classLoader)
            loggingLoader.allowPackage('org.slf4j')
            loggingLoader.allowPackage('org.apache.commons.logging')
            loggingLoader.allowPackage('org.apache.log4j')
            ClassLoader parent = new MultiParentClassLoader(antClassloader, loggingLoader)

            def antLoader = new URLClassLoader(libClasspath.asURLArray, parent)
            def gradleLoader = new MutableURLClassLoader(parent, gradleCoreUrls)

            classLoadersForImpl = new ClassLoaderSet(gradleLoader, antLoader)
            classloaders[libClasspath] = classLoadersForImpl
        }

        ClassLoader originalLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = classLoadersForImpl.ant
        try {
            Object antBuilder = classLoadersForImpl.gradle.loadClass(BasicAntBuilder.class.name).newInstance()

            Object antLogger = classLoadersForImpl.gradle.loadClass(AntLoggingAdapter.class.name).newInstance()
            antBuilder.project.removeBuildListener(antBuilder.project.getBuildListeners()[0])
            antBuilder.project.addBuildListener(antLogger)

            // Ideally, we'd delegate directly to the AntBuilder, but it's Closure class is different to our caller's
            // Closure class, so the AntBuilder's methodMissing() doesn't work. It just converts our Closures to String
            // because they are not an instanceof it's Closure class
            Object delegate = new AntBuilderDelegate(antBuilder, classLoadersForImpl.ant)
            ConfigureUtil.configure(antClosure, delegate)
        } finally {
            Thread.currentThread().contextClassLoader = originalLoader
        }
    }

    private static class ClassLoaderSet {
        final ClassLoader gradle
        final ClassLoader ant

        ClassLoaderSet(ClassLoader gradle, ClassLoader ant) {
            this.gradle = gradle
            this.ant = ant
        }
    }
}

class AntBuilderDelegate extends BuilderSupport {
    final Object builder
    final ClassLoader antlibClassLoader

    def AntBuilderDelegate(builder, antlibClassLoader) {
        this.builder = builder;
        this.antlibClassLoader = antlibClassLoader
    }

    def getAnt() {
        return this
    }

    def taskdef(Map<String, ?> args) {
        if (args.keySet() == ['name', 'classname'] as Set) {
            builder.project.addTaskDefinition(args.name, antlibClassLoader.loadClass(args.classname))
        } else if (args.keySet() == ['resource'] as Set) {
            antlibClassLoader.getResource(args.resource).withInputStream { instr ->
                def xml = new XmlParser().parse(instr)
                xml.taskdef.each {
                    builder.project.addTaskDefinition(it.@name, antlibClassLoader.loadClass(it.@classname))
                }
            }
        } else {
            throw new RuntimeException("Unsupported parameters for taskdef().")
        }
    }

    def propertyMissing(String name) {
        builder."$name"
    }

    protected Object createNode(Object name) {
        builder.createNode(name)
    }

    protected Object createNode(Object name, Map attributes) {
        if (name == "taskdef") {
            taskdef(attributes)
        } else {
            builder.createNode(name, attributes)
        }
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
        if (parent == null && node == null) { // happens when dispatching to taskdef via createNode()
            return
        }

        builder.nodeCompleted(parent, node)
    }

    protected Object postNodeCompletion(Object parent, Object node) {
        builder.postNodeCompletion(parent, node)
    }
}
