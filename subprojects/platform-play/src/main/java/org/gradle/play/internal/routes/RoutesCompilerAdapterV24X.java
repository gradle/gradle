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

package org.gradle.play.internal.routes;

import com.google.common.collect.Lists;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.scala.internal.reflect.ScalaListBuffer;
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.scala.internal.reflect.ScalaObject;
import org.gradle.scala.internal.reflect.ScalaReflectionUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;

public class RoutesCompilerAdapterV24X extends DefaultVersionedRoutesCompilerAdapter {
    private static final String PLAY_ROUTES_COMPILER_STATIC_ROUTES_GENERATOR = "play.routes.compiler.StaticRoutesGenerator";
    private static final String PLAY_ROUTES_COMPILER_INJECTED_ROUTES_GENERATOR = "play.routes.compiler.InjectedRoutesGenerator";

    private final List<String> defaultScalaImports = Lists.newArrayList("controllers.Assets.Asset");
    private final List<String> defaultJavaImports = Lists.newArrayList("controllers.Assets.Asset", "play.libs.F");

    public RoutesCompilerAdapterV24X(String playVersion, String scalaVersion) {
        // No 2.11 version of routes compiler published
        super(playVersion, scalaVersion);
    }

    @Override
    public ScalaMethod getCompileMethod(ClassLoader cl) throws ClassNotFoundException {
        return ScalaReflectionUtil.scalaMethod(
            cl,
            "play.routes.compiler.RoutesCompiler",
            "compile",
            cl.loadClass("play.routes.compiler.RoutesCompiler$RoutesCompilerTask"),
            cl.loadClass("play.routes.compiler.RoutesGenerator"),
            File.class
        );
    }

    @Override
    public Object[] createCompileParameters(ClassLoader cl, File file, File destinationDir, boolean javaProject, boolean namespaceReverseRouter, boolean generateReverseRoutes, boolean injectedRoutesGenerator, Collection<String> additionalImports) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        List<String> defaultImports = javaProject ? defaultJavaImports : defaultScalaImports;
        defaultImports.addAll(additionalImports);

        Object routesCompilerTask = DirectInstantiator.instantiate(cl.loadClass("play.routes.compiler.RoutesCompiler$RoutesCompilerTask"),
            file,
            ScalaListBuffer.fromList(cl, defaultImports),
            isGenerateForwardsRouter(),
            generateReverseRoutes,
            namespaceReverseRouter
        );

        String routeGenerator;
        if (injectedRoutesGenerator) {
            routeGenerator = PLAY_ROUTES_COMPILER_INJECTED_ROUTES_GENERATOR;
        } else {
            routeGenerator = PLAY_ROUTES_COMPILER_STATIC_ROUTES_GENERATOR;
        }
        return new Object[]{
            routesCompilerTask,
            new ScalaObject(cl, routeGenerator).getInstance(),
            destinationDir
        };
    }

    protected boolean isGenerateForwardsRouter() {
        return true;
    }
}
