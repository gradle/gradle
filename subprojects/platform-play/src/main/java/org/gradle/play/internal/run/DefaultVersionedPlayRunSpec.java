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

import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.scala.internal.reflect.ScalaReflectionUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.jar.JarFile;

public abstract class DefaultVersionedPlayRunSpec extends DefaultPlayRunSpec implements VersionedPlayRunSpec {
    private final Iterable<File> classfpath; //Using Iterable<File> to be Serializable
    private final String scalaVersion;
    private final String playVersion;

    public DefaultVersionedPlayRunSpec(Iterable<File> classpath, File projectPath, int httpPort, PlayPlatform playPlatform) {
        super(classpath, projectPath, httpPort);
        this.scalaVersion = playPlatform.getScalaVersion();
        this.playVersion = playPlatform.getPlayVersion();
        this.classfpath = classpath;
    }

    protected abstract Class<?> getBuildLinkClass(ClassLoader classLoader) throws ClassNotFoundException;

    protected abstract Class<?> getDocHandlerFactoryClass(ClassLoader classLoader) throws ClassNotFoundException;

    protected abstract Class<?> getBuildDocHandlerClass(ClassLoader docsClassLoader) throws ClassNotFoundException;

    public Object getBuildLink(ClassLoader classLoader) throws ClassNotFoundException {
        return Proxy.newProxyInstance(classLoader, new Class<?>[]{getBuildLinkClass(classLoader)}, new InvocationHandler() {
            private volatile boolean shouldReloadNextTime = true;

            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("projectPath")) {
                    return getProjectPath();
                } else if (method.getName().equals("reload")) {
                    if (shouldReloadNextTime) {
                        shouldReloadNextTime = false; //reload only once for now
                        DefaultClassPath projectClasspath = new DefaultClassPath(getClasspath());
                        URLClassLoader classLoader = new URLClassLoader(projectClasspath.getAsURLs().toArray(new URL[]{}), Thread.currentThread().getContextClassLoader()); //we have to use this classloader because plugins assumes that the classes are in this thread. Still a bit uncertain whether it is a 100%
                        return classLoader;
                    } else {
                        return null;
                    }
                } else if (method.getName().equals("settings")) {
                    return new HashMap<String, String>();
                }
                //TODO: all methods
                return null;
            }
        });
    }

    protected Method getDocHandlerFactoryMethod(ClassLoader classLoader) throws ClassNotFoundException, NoSuchMethodException {
        return getDocHandlerFactoryClass(classLoader).getMethod("fromJar", JarFile.class, String.class);
    }

    public Object getBuildDocHandler(ClassLoader docsClassLoader, JarFile docJar) throws NoSuchMethodException, ClassNotFoundException, IOException, InvocationTargetException, IllegalAccessException {
        return getDocHandlerFactoryMethod(docsClassLoader).invoke(null, docJar, "play/docs/content");
    }

    public ScalaMethod getNettyServerDevHttpMethod(ClassLoader classLoader, ClassLoader docsClassLoader) throws ClassNotFoundException {
        return ScalaReflectionUtil.scalaMethod(classLoader, "play.core.server.NettyServer", "mainDevHttpMode", getBuildLinkClass(classLoader), getBuildDocHandlerClass(docsClassLoader), int.class);
    }

    public Object getDocsDependencyNotation() {
        return String.format("com.typesafe.play:play-docs_%s:%s", scalaVersion, playVersion);
    }

    public Iterable<String> getSharedPackages() {
        return Arrays.asList("org.gradle.play.internal.run", "play.core", "play.core.server", "play.docs", "scala");
    }
}
