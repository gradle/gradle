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

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.Lists
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.internal.classloader.CachingClassLoader
import org.gradle.internal.classloader.ClassLoaderFactory
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classloader.MultiParentClassLoader
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.jvm.Jvm
import org.gradle.util.ConfigureUtil

// TODO: should be threadsafe; is stateful and of build scope

@CompileStatic
class DefaultIsolatedAntBuilder implements IsolatedAntBuilder {

    private final static Logger LOG = Logging.getLogger(DefaultIsolatedAntBuilder)
    private final static MemoryLeakPrevention LEAK_PREVENTION = new MemoryLeakPrevention()

    private final ClassLoader baseAntLoader
    private final ClassLoader gradleLoader
    private final Cache<String, ClassLoader> classloaders
    private final ClassPathRegistry classPathRegistry
    private final ClassLoaderFactory classLoaderFactory
    private final ClassPath libClasspath

    DefaultIsolatedAntBuilder(ClassPathRegistry classPathRegistry, ClassLoaderFactory classLoaderFactory) {
        this.classPathRegistry = classPathRegistry
        this.classLoaderFactory = classLoaderFactory
        this.classloaders = CacheBuilder.<String, ClassLoader> newBuilder().softValues().build()
        this.libClasspath = new DefaultClassPath()

        List<File> antClasspath = Lists.newArrayList(classPathRegistry.getClassPath("ANT").asFiles)
        // Need tools.jar for compile tasks
        File toolsJar = Jvm.current().toolsJar
        if (toolsJar) {
            antClasspath += toolsJar
        }

        def antLoader = classLoaderFactory.createIsolatedClassLoader(new DefaultClassPath(antClasspath))
        def loggingLoader = new FilteringClassLoader(getClass().classLoader)
        loggingLoader.allowPackage('org.slf4j')
        loggingLoader.allowPackage('org.apache.commons.logging')
        loggingLoader.allowPackage('org.apache.log4j')
        loggingLoader.allowClass(Logger)
        loggingLoader.allowClass(LogLevel)
        this.baseAntLoader = new CachingClassLoader(new MultiParentClassLoader(antLoader, loggingLoader))

        // Need gradle core to pick up ant logging adapter, AntBuilder and such
        def gradleCoreUrls = classPathRegistry.getClassPath("GRADLE_CORE")
        gradleCoreUrls += classPathRegistry.getClassPath("GROOVY")

        // Need Transformer (part of AntBuilder API) from base services
        gradleCoreUrls += classPathRegistry.getClassPath("GRADLE_BASE_SERVICES")
        this.gradleLoader = new URLClassLoader(gradleCoreUrls.asURLArray, baseAntLoader)
    }

    private DefaultIsolatedAntBuilder(DefaultIsolatedAntBuilder copy, Iterable<File> libClasspath) {
        this.classPathRegistry = copy.classPathRegistry
        this.classLoaderFactory = copy.classLoaderFactory
        this.classloaders = copy.classloaders
        this.baseAntLoader = copy.baseAntLoader
        this.gradleLoader = copy.gradleLoader
        this.libClasspath = new DefaultClassPath(libClasspath)
    }

    IsolatedAntBuilder withClasspath(Iterable<File> classpath) {
        return new DefaultIsolatedAntBuilder(this, classpath)
    }

    void execute(Closure antClosure) {
        // temporarily disable classloader caching to take this out of the debugging story
        //def classLoader = classloaders.get(libClasspath.asURIs.collect { it.path }.join(":")) {

        def classLoader = new URLClassLoader(libClasspath.asURLArray, baseAntLoader)
        //}

        Object antBuilder = newInstanceOf('org.gradle.api.internal.project.ant.BasicAntBuilder')
        Object antLogger = newInstanceOf('org.gradle.api.internal.project.ant.AntLoggingAdapter')

        // This looks ugly, very ugly, but that is apparently what Ant does itself
        ClassLoader originalLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = classLoader

        try {
            configureAntBuilder(antBuilder, antLogger)

            // Ideally, we'd delegate directly to the AntBuilder, but it's Closure class is different to our caller's
            // Closure class, so the AntBuilder's methodMissing() doesn't work. It just converts our Closures to String
            // because they are not an instanceof it's Closure class
            ConfigureUtil.configure(antClosure, new AntBuilderDelegate(antBuilder, classLoader))

        } finally {
            Thread.currentThread().contextClassLoader = originalLoader
            disposeBuilder(antBuilder, antLogger)
            if (LOG.isDebugEnabled()) {
                LOG.debug('Applying memory leak prevention strategies')
            }
            [classLoader, gradleLoader].each {
                try {
                    LEAK_PREVENTION.preventMemoryLeaks(libClasspath, it)
                } catch (Throwable e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Memory leak prevention strategies failed with an error", e)
                    }
                }
            }
        }
    }

    private Object newInstanceOf(String className) {
        // we must use a String literal here, otherwise using things like Foo.class.name will trigger unnecessary
        // loading of classes in the classloader of the DefaultIsolatedAntBuilder, which is not what we want.
        // also we use getDeclaredConstructor().newInstance() in order to avoid a slower invocation path
        // with DGM#newInstance which is preferred over Class#newInstance
        gradleLoader.loadClass(className).getDeclaredConstructor().newInstance()
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private void configureAntBuilder(def antBuilder, def antLogger) {
        antBuilder.project.removeBuildListener(antBuilder.project.getBuildListeners()[0])
        antBuilder.project.addBuildListener(antLogger)
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private void disposeBuilder(def antBuilder, def antLogger) {
        def project = antBuilder.project
        project.removeBuildListener(antLogger)
        project.dataTypeDefinitions.clear()
        project.references.clear()
        antBuilder.close()
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
