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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class PlayExecuter {
    public PlayRunResult run(PlayRunSpec spec) {
        ClassLoader cl = getClass().getClassLoader();
        try {
            Class<?> mainClass = cl.loadClass("play.core.server.NettyServer");
            Class<?> buildLinkClass = cl.loadClass("play.core.SBTLink"); //cl.loadClass("play.core.BuildLink");
            Class<?> buildDocHandlerClass = cl.loadClass("play.core.SBTDocHandler"); //cl.loadClass("play.core.BuildDocHandler");

            ClassLoader docsClassLoader = cl; //TODO: split into seperate classpaths!
            Class<?> docHandlerFactoryClass = docsClassLoader.loadClass("play.docs.BuildDocHandlerFactory");
            Method factoryMethod = docHandlerFactoryClass.getMethod("empty");
            Object buildDocHandler = factoryMethod.invoke(null);

            Method runMethod = mainClass.getMethod("mainDevHttpMode", buildLinkClass, buildDocHandlerClass, Integer.class);
            Object buildLink = Proxy.newProxyInstance(cl, new java.lang.Class[]{buildLinkClass}, new InvocationHandler() {

                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    return null;
                }
            });
            //runMethod.invoke(null, )

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return new PlayRunResult();
    }
}
