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

import org.gradle.internal.UncheckedException;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
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
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;

public abstract class DefaultVersionedPlayRunAdapter implements VersionedPlayRunAdapter, Serializable {
    private final AtomicReference<Object> reloadObject = new AtomicReference<Object>();
    private volatile SoftReference<URLClassLoader> previousClassLoaderReference;
    private volatile SoftReference<URLClassLoader> currentClassLoaderReference;

    protected abstract Class<?> getBuildLinkClass(ClassLoader classLoader) throws ClassNotFoundException;

    protected abstract Class<?> getDocHandlerFactoryClass(ClassLoader classLoader) throws ClassNotFoundException;

    protected abstract Class<?> getBuildDocHandlerClass(ClassLoader docsClassLoader) throws ClassNotFoundException;

    public Object getBuildLink(final ClassLoader classLoader, final File projectPath, final File applicationJar, final Iterable<File> changingClasspath, final File assetsJar, final Iterable<File> assetsDirs) throws ClassNotFoundException {
        final ClassLoader assetsClassLoader = createAssetsClassLoader(assetsJar, assetsDirs, classLoader);
        forceReloadNextTime();
        return Proxy.newProxyInstance(classLoader, new Class<?>[]{getBuildLinkClass(classLoader)}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("projectPath")) {
                    return projectPath;
                } else if (method.getName().equals("reload")) {
                    closePreviousClassLoader();
                    Object result = reloadObject.getAndSet(null);
                    if (result == null) {
                        return null;
                    } else if (result == Boolean.TRUE) {
                        ClassPath classpath = new DefaultClassPath(applicationJar).plus(new DefaultClassPath(changingClasspath));
                        URLClassLoader currentClassLoader = new URLClassLoader(classpath.getAsURLArray(), assetsClassLoader);
                        storeClassLoader(currentClassLoader);
                        return currentClassLoader;
                    } else {
                        throw new IllegalStateException();
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
        return new URLClassLoader(new DefaultClassPath(assetsJar).getAsURLArray(), classLoader);
    }

    private void storeClassLoader(URLClassLoader currentClassLoader) {
        URLClassLoader previousClassLoader = currentClassLoaderReference != null ? currentClassLoaderReference.get() : null;
        if (previousClassLoader != null) {
            previousClassLoaderReference = new SoftReference<URLClassLoader>(previousClassLoader);
        }
        currentClassLoaderReference = new SoftReference<URLClassLoader>(currentClassLoader);
    }

    private void closePreviousClassLoader() throws IOException {
        URLClassLoader previousClassLoader = previousClassLoaderReference != null ? previousClassLoaderReference.get() : null;
        if (previousClassLoader instanceof Closeable) {
            // use Closeable interface to find close method to prevent Java 1.7 specific method access
            ((Closeable) previousClassLoader).close();
        }
        previousClassLoaderReference = null;
    }

    @Override
    public void forceReloadNextTime() {
        reloadObject.set(Boolean.TRUE);
    }

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
        // TODO:DAZ Use the location of the DocHandlerFactoryClass instead.
        File docJarFile = null;
        for (File file : classpath) {
            if (file.getName().startsWith("play-docs")) {
                docJarFile = file;
                break;
            }
        }
        return new JarFile(docJarFile);
    }

    public void runDevHttpServer(ClassLoader classLoader, ClassLoader docsClassLoader, Object buildLink, Object buildDocHandler, int httpPort) throws ClassNotFoundException {
        ScalaMethod runMethod = ScalaReflectionUtil.scalaMethod(classLoader, "play.core.server.NettyServer", "mainDevHttpMode", getBuildLinkClass(classLoader), getBuildDocHandlerClass(docsClassLoader), int.class);
        runMethod.invoke(buildLink, buildDocHandler, httpPort);
    }

}
