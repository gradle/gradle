/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.project.antbuilder;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.jvm.Jvm;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

public class DefaultIsolatedAntBuilder implements IsolatedAntBuilder {

    private final static String CORE_GRADLE_LOADER = "Gradle Core";
    private final static String ANT_GRADLE_LOADER = "Ant loader";

    private final static Set<Finalizer> FINALIZERS = Sets.newConcurrentHashSet();
    private final static ReferenceQueue<DefaultIsolatedAntBuilder> FINALIZER_REFQUEUE = new ReferenceQueue<DefaultIsolatedAntBuilder>();

    static {
        // this thread is responsible for polling phantom references from the finalizer queue
        // where we will be able to trigger cleanup of resources when the root ant builder is
        // about to be garbage collected
        Thread finalizerThread = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    try {
                        Finalizer builder = (Finalizer) FINALIZER_REFQUEUE.remove();
                        FINALIZERS.remove(builder);
                        builder.cleanup();
                        builder.clear();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        };
        finalizerThread.setDaemon(true);
        finalizerThread.start();
    }

    private final ClassLoader baseAntLoader;
    private final ClassPath libClasspath;
    private final ClassLoader gradleLoader;
    private final Map<WeakReference<ClassPath>, ClassPathToClassLoader> classLoaderCache;
    private final ClassPathRegistry classPathRegistry;
    private final ClassLoaderFactory classLoaderFactory;
    private final FinalizerThread finalizerThread;
    private final MemoryLeakPrevention gradleToIsolatedLeakPrevention;
    private final MemoryLeakPrevention antToGradleLeakPrevention;

    public DefaultIsolatedAntBuilder(ClassPathRegistry classPathRegistry, ClassLoaderFactory classLoaderFactory) {
        this.classPathRegistry = classPathRegistry;
        this.classLoaderFactory = classLoaderFactory;
        this.classLoaderCache = Maps.newConcurrentMap();
        this.libClasspath = new DefaultClassPath();

        List<File> antClasspath = Lists.newArrayList(classPathRegistry.getClassPath("ANT").getAsFiles());
        // Need tools.jar for compile tasks
        File toolsJar = Jvm.current().getToolsJar();
        if (toolsJar!=null) {
            antClasspath.add(toolsJar);
        }


        ClassLoader antLoader = classLoaderFactory.createIsolatedClassLoader(new DefaultClassPath(antClasspath));
        FilteringClassLoader loggingLoader = new FilteringClassLoader(getClass().getClassLoader());
        loggingLoader.allowPackage("org.slf4j");
        loggingLoader.allowPackage("org.apache.commons.logging");
        loggingLoader.allowPackage("org.apache.log4j");
        loggingLoader.allowClass(Logger.class);
        loggingLoader.allowClass(LogLevel.class);

        this.baseAntLoader = new CachingClassLoader(new MultiParentClassLoader(antLoader, loggingLoader));

        // Need gradle core to pick up ant logging adapter, AntBuilder and such
        ClassPath gradleCoreUrls = classPathRegistry.getClassPath("GRADLE_CORE");
        gradleCoreUrls = gradleCoreUrls.plus(classPathRegistry.getClassPath("GROOVY"));

        // Need Transformer (part of AntBuilder API) from base services
        gradleCoreUrls = gradleCoreUrls.plus(classPathRegistry.getClassPath("GRADLE_BASE_SERVICES"));
        this.gradleLoader = new URLClassLoader(gradleCoreUrls.getAsURLArray(), baseAntLoader);
        finalizerThread = new FinalizerThread(classLoaderCache);
        finalizerThread.start();

        this.gradleToIsolatedLeakPrevention = new MemoryLeakPrevention(CORE_GRADLE_LOADER, this.getClass().getClassLoader(), null);
        this.antToGradleLeakPrevention = new MemoryLeakPrevention(ANT_GRADLE_LOADER, gradleLoader, gradleCoreUrls);
        this.gradleToIsolatedLeakPrevention.prepare();
        this.antToGradleLeakPrevention.prepare();

        // register finalizer for the root builder only!
        FINALIZERS.add(new Finalizer(this, FINALIZER_REFQUEUE));
    }

    protected DefaultIsolatedAntBuilder(DefaultIsolatedAntBuilder copy, Iterable<File> libClasspath) {
        this.classPathRegistry = copy.classPathRegistry;
        this.classLoaderFactory = copy.classLoaderFactory;
        this.classLoaderCache = copy.classLoaderCache;
        this.baseAntLoader = copy.baseAntLoader;
        this.gradleLoader = copy.gradleLoader;
        this.finalizerThread = copy.finalizerThread;
        this.libClasspath = new DefaultClassPath(libClasspath);
        this.gradleToIsolatedLeakPrevention = copy.gradleToIsolatedLeakPrevention;
        this.antToGradleLeakPrevention = copy.antToGradleLeakPrevention;
    }

    public IsolatedAntBuilder withClasspath(Iterable<File> classpath) {
        return new DefaultIsolatedAntBuilder(this, classpath);
    }

    public void execute(Closure antClosure) {
        ClassPathToClassLoader cached = getCachedLoader(libClasspath);
        ClassLoader classLoader = cached == null ? null : cached.getClassLoader();
        boolean cacheLoader = classLoader == null;
        if (cacheLoader) {
            classLoader = new URLClassLoader(libClasspath.getAsURLArray(), baseAntLoader);
            cached = new ClassPathToClassLoader(libClasspath, classLoader, gradleToIsolatedLeakPrevention, antToGradleLeakPrevention);
        }


        Object antBuilder = newInstanceOf("org.gradle.api.internal.project.ant.BasicAntBuilder");
        Object antLogger = newInstanceOf("org.gradle.api.internal.project.ant.AntLoggingAdapter");

        // This looks ugly, very ugly, but that is apparently what Ant does itself
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);

        try {
            configureAntBuilder(antBuilder, antLogger);

            // Ideally, we'd delegate directly to the AntBuilder, but it's Closure class is different to our caller's
            // Closure class, so the AntBuilder's methodMissing() doesn't work. It just converts our Closures to String
            // because they are not an instanceof it's Closure class.
            Object delegate =  new AntBuilderDelegate(antBuilder, classLoader);
            ConfigureUtil.configure(antClosure, delegate);

        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
            disposeBuilder(antBuilder, antLogger);
            cached.getClassLoaderLeakPrevention().afterUse();

            if (cacheLoader) {
                classLoaderCache.put(finalizerThread.referenceOf(libClasspath), cached);
            }
        }

    }

    private ClassPathToClassLoader getCachedLoader(final ClassPath key) {
        for (Map.Entry<WeakReference<ClassPath>, ClassPathToClassLoader> entry : classLoaderCache.entrySet()) {
            if (key.equals(entry.getKey().get())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Object newInstanceOf(String className) {
        // we must use a String literal here, otherwise using things like Foo.class.name will trigger unnecessary
        // loading of classes in the classloader of the DefaultIsolatedAntBuilder, which is not what we want.
        try {
            return gradleLoader.loadClass(className).newInstance();
        } catch (Exception e) {
            // should never happen
            throw new RuntimeException(e);
        }
    }

    // We *absolutely* need to avoid polluting the project with ClassInfo from *our* classloader
    // So this class must NOT call any dynamic Groovy code. This means we must do what follows using
    // good old java reflection!

    private Object getProject(Object antBuilder) throws Exception {
        return antBuilder.getClass().getMethod("getProject").invoke(antBuilder);
    }

    protected void configureAntBuilder(Object antBuilder, Object antLogger) {
        try {
            Object project = getProject(antBuilder);
            Class<?> projectClass = project.getClass();
            ClassLoader cl = projectClass.getClassLoader();
            Class<?> buildListenerClass = cl.loadClass("org.apache.tools.ant.BuildListener");
            Method addBuildListener = projectClass.getDeclaredMethod("addBuildListener", buildListenerClass);
            Method removeBuildListener = projectClass.getDeclaredMethod("removeBuildListener", buildListenerClass);
            Method getBuildListeners = projectClass.getDeclaredMethod("getBuildListeners");
            Vector listeners = (Vector) getBuildListeners.invoke(project);
            removeBuildListener.invoke(project, listeners.get(0));
            addBuildListener.invoke(project, antLogger);
        } catch (Exception ex) {
            // when we did this in Groovy, we weren't concerned by non existent throwables ;)
        }
    }

    protected void disposeBuilder(Object antBuilder, Object antLogger) {
        try {
            Object project = getProject(antBuilder);
            Class<?> projectClass = project.getClass();
            ClassLoader cl = projectClass.getClassLoader();
            // remove build listener
            Class<?> buildListenerClass = cl.loadClass("org.apache.tools.ant.BuildListener");
            Method removeBuildListener = projectClass.getDeclaredMethod("removeBuildListener", buildListenerClass);
            removeBuildListener.invoke(project, antLogger);
            antBuilder.getClass().getDeclaredMethod("close").invoke(antBuilder);
        } catch (Exception ex) {
            // when we did this in Groovy, we weren't concerned by non existent throwables ;)
        }

    }


    private static class Finalizer extends PhantomReference<DefaultIsolatedAntBuilder> {

        private final FinalizerThread finalizerThread;
        private final MemoryLeakPrevention gradleToIsolatedLeakPrevention;
        private final MemoryLeakPrevention antToGradleLeakPrevention;
        private final Map<WeakReference<ClassPath>, ClassPathToClassLoader> classLoaderCache;
        private final ClassLoader gradleLoader;
        private final ClassLoader baseAntLoader;

        public Finalizer(DefaultIsolatedAntBuilder referent, ReferenceQueue<? super DefaultIsolatedAntBuilder> q) {
            super(referent, q);
            finalizerThread = referent.finalizerThread;
            gradleToIsolatedLeakPrevention = referent.gradleToIsolatedLeakPrevention;
            antToGradleLeakPrevention = referent.antToGradleLeakPrevention;
            classLoaderCache = referent.classLoaderCache;
            gradleLoader = referent.gradleLoader;
            baseAntLoader = referent.baseAntLoader;
        }

        private void emptyCache() {
            for (ClassPathToClassLoader cached : classLoaderCache.values()) {
                cached.cleanup();
            }

            classLoaderCache.clear();
        }

        public void cleanup() {
            finalizerThread.exit();
            emptyCache();

            // clean classes from Gradle Core that leaked into the Ant classloader
            gradleToIsolatedLeakPrevention.dispose(gradleLoader, baseAntLoader);

            // clean classes from the Ant classloader that leaked into the various loader
            antToGradleLeakPrevention.dispose(gradleLoader,  baseAntLoader, this.getClass().getClassLoader());
        }
    }
}
