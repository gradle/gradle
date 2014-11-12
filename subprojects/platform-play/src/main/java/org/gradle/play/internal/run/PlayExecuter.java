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

import com.google.common.base.Function;

import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.play.internal.scala.reflection.util.ScalaUtil;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.jar.JarFile;

public class PlayExecuter {
    public void run(final PlayRunSpec spec) {
        ClassLoader cl = getClass().getClassLoader();
        try {
            Class<?> buildLinkClass = cl.loadClass("play.core.BuildLink"); //cl.loadClass("play.core.SBTLink");  //2.2.x
            Class<?> buildDocHandlerClass = cl.loadClass("play.core.BuildDocHandler"); //cl.loadClass("play.core.SBTDocHandler");  //2.2.x

            ClassLoader docsClassLoader = cl; //TODO: split into seperate classpaths!
            Class<?> docHandlerFactoryClass = docsClassLoader.loadClass("play.docs.BuildDocHandlerFactory"); // docsClassLoader.loadClass("play.docs.SBTDocHandlerFactory");

            Method factoryMethod = docHandlerFactoryClass.getMethod("fromJar", JarFile.class, String.class);

            File docJarFile = null;
            for (File file: spec.getClasspath()) {
                if (file.getName().startsWith("play-docs")) {
                    docJarFile = file;
                    break;
                }
            }

            JarFile docJar = new JarFile(docJarFile);
            Object buildDocHandler = factoryMethod.invoke(null, docJar, "play/docs/content");

            Function<Object[], Object> runMethod = ScalaUtil.scalaObjectFunction(cl, "play.core.server.NettyServer", "mainDevHttpMode", new Class<?>[]{
                    buildLinkClass, buildDocHandlerClass, int.class
            });
            Object buildLink = Proxy.newProxyInstance(cl, new java.lang.Class<?>[]{buildLinkClass}, new InvocationHandler() {

                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if (method.getName().equals("projectPath")) {
                        return spec.getProjectPath();
                    } else if (method.getName().equals("reload")) {
                        DefaultClassPath projectClasspath = new DefaultClassPath(spec.getProjectClasspath());
                        URLClassLoader classLoader = new URLClassLoader(projectClasspath.getAsURLs().toArray(new URL[]{}), Thread.currentThread().getContextClassLoader()); //we have to use this classloader because plugins assumes that the classes are in this thread. Still a bit uncertain whether it is a 100%
                        return classLoader;
                    } else if (method.getName().equals("settings")) {
                        return new HashMap<String, String>();
                    }
                    //TODO: all methods
                    return null;
                }
            });
            Integer port = 9000;
            Object server = runMethod.apply(new Object[]{
                    buildLink,
                    buildDocHandler,
                    port
            }); //TODO: add server close
            while (true) {
                Thread.sleep(100);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
