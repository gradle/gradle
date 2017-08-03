/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.run;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.scala.internal.reflect.ScalaReflectionUtil;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;

public abstract class DefaultVersionedPlayRunAdapter implements VersionedPlayRunAdapter, Serializable {

    private static final String PLAY_EXCEPTION_CLASSNAME = "play.api.PlayException";
    private static final Logger LOGGER = Logging.getLogger(DefaultVersionedPlayRunAdapter.class);

    private final AtomicBoolean blockReload = new AtomicBoolean();
    private final AtomicReference<ClassLoader> currentClassloader = new AtomicReference<ClassLoader>();
    private final Queue<SoftReference<Closeable>> loadersToClose = new ConcurrentLinkedQueue<SoftReference<Closeable>>();
    private volatile Throwable buildFailure;

    protected abstract Class<?> getBuildLinkClass(ClassLoader classLoader) throws ClassNotFoundException;

    protected abstract Class<?> getDocHandlerFactoryClass(ClassLoader classLoader) throws ClassNotFoundException;

    protected abstract Class<?> getBuildDocHandlerClass(ClassLoader docsClassLoader) throws ClassNotFoundException;

    @Override
    public Object getBuildLink(final ClassLoader classLoader, final ReloadListener reloadListener, final File projectPath, final File applicationJar, final Iterable<File> changingClasspath, final File assetsJar, final Iterable<File> assetsDirs) throws ClassNotFoundException {
        final ClassLoader assetsClassLoader = createAssetsClassLoader(assetsJar, assetsDirs, classLoader);
        final Class<? extends Throwable> playExceptionClass = Cast.uncheckedCast(classLoader.loadClass(PLAY_EXCEPTION_CLASSNAME));

        return Proxy.newProxyInstance(classLoader, new Class<?>[]{getBuildLinkClass(classLoader)}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("projectPath")) {
                    return projectPath;
                } else if (method.getName().equals("reload")) {

                    reloadListener.reloadRequested();

                    try {
                        synchronized (blockReload) {
                            LOGGER.debug("waiting for blockReload to clear {} ", blockReload.get());
                            while (blockReload.get()) {
                                blockReload.wait();
                            }
                            LOGGER.debug("blockReload cleared {} ", blockReload.get());
                        }

                        // We can't close replaced loaders immediately, because their classes may be used during shutdown,
                        // after the return of the reload() call that caused the loader to be swapped out.
                        // We have no way of knowing when the loader is actually done with, so we use the request after the request
                        // that triggered the reload as the trigger point to close the replaced loader.
                        closeOldLoaders();
                        if (buildFailure == null) {
                            ClassPath classpath = new DefaultClassPath(applicationJar).plus(new DefaultClassPath(changingClasspath));
                            URLClassLoader currentClassLoader = new URLClassLoader(classpath.getAsURLArray(), assetsClassLoader);
                            storeClassLoader(currentClassLoader);
                            return currentClassLoader;
                        } else {
                            Throwable failure = buildFailure;
                            if (failure == null) {
                                return null;
                            } else {
                                try {
                                    return DirectInstantiator.instantiate(playExceptionClass, "Gradle Build Failure", failure.getMessage(), failure);
                                } catch (Exception e) {
                                    LOGGER.warn("Could not translate " + failure + " to " + PLAY_EXCEPTION_CLASSNAME, e);
                                    return failure;
                                }
                            }
                        }
                    } finally {
                        reloadListener.reloadComplete();
                    }
                } else if (method.getName().equals("settings")) {
                    return new HashMap<String, String>();
                }
                //TODO: all methods
                return null;
            }
        });
    }

    protected ClassLoader createAssetsClassLoader(File assetsJar, Iterable<File> assetsDirs, ClassLoader classLoader) {
        try {
            return new URLClassLoader(new URL[]{assetsJar.toURI().toURL()}, classLoader);
        } catch (MalformedURLException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void storeClassLoader(ClassLoader classLoader) {
        final ClassLoader previous = currentClassloader.getAndSet(classLoader);
        if (previous != null && previous instanceof Closeable) {
            loadersToClose.add(new SoftReference<Closeable>(Cast.cast(Closeable.class, previous)));
        }
    }

    private void closeOldLoaders() throws IOException {
        SoftReference<Closeable> ref = loadersToClose.poll();
        while (ref != null) {
            Closeable closeable = ref.get();
            if (closeable != null) {
                closeable.close();
            }
            ref.clear();
            ref = loadersToClose.poll();
        }
    }


    @Override
    public void outOfDate() {
        synchronized (blockReload) {
            blockReload.set(true);
            blockReload.notifyAll();
            LOGGER.debug("notify outOfDate");
        }
    }

    @Override
    public void upToDate(Throwable throwable) {
        buildFailure = throwable;

        synchronized (blockReload) {
            blockReload.set(false);
            blockReload.notifyAll();
            LOGGER.debug("notify upToDate");
        }
    }

    @Override
    public Object getBuildDocHandler(ClassLoader docsClassLoader, Iterable<File> classpath) throws NoSuchMethodException, ClassNotFoundException, IOException, IllegalAccessException {
        Class<?> docHandlerFactoryClass = getDocHandlerFactoryClass(docsClassLoader);
        Method docHandlerFactoryMethod = docHandlerFactoryClass.getMethod("fromJar", JarFile.class, String.class);
        JarFile documentationJar = findDocumentationJar(classpath);
        try {
            return docHandlerFactoryMethod.invoke(null, documentationJar, "play/docs/content");
        } catch (InvocationTargetException e) {
            throw UncheckedException.unwrapAndRethrow(e);
        }
    }

    private JarFile findDocumentationJar(Iterable<File> classpath) throws IOException {
        File docJarFile = null;
        for (File file : classpath) {
            if (file.getName().startsWith("play-docs")) {
                docJarFile = file;
                break;
            }
        }
        return new JarFile(docJarFile);
    }

    @Override
    public InetSocketAddress runDevHttpServer(ClassLoader classLoader, ClassLoader docsClassLoader, Object buildLink, Object buildDocHandler, int httpPort) throws ClassNotFoundException {
        ScalaMethod runMethod = ScalaReflectionUtil.scalaMethod(classLoader, "play.core.server.NettyServer", "mainDevHttpMode", getBuildLinkClass(classLoader), getBuildDocHandlerClass(docsClassLoader), int.class);
        Object reloadableServer = runMethod.invoke(buildLink, buildDocHandler, httpPort);
        return JavaReflectionUtil.method(reloadableServer, InetSocketAddress.class, "mainAddress").invoke(reloadableServer);
    }

}
