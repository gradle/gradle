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

package org.gradle.play.internal.routes;

import com.google.common.base.Function;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.play.internal.ScalaUtil;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class RoutesCompiler implements Compiler<RoutesCompileSpec>, Serializable {
    private final RoutesCompilerVersion routesCompilerVersion;

    public RoutesCompiler(String routesCompilerVersion) {
        this.routesCompilerVersion = RoutesCompilerVersion.parse(routesCompilerVersion);
    }

    private enum RoutesCompilerVersion {
        PLAY_ROUTES_VERSION_22,
        PLAY_ROUTES_VERSION_23;

        public static RoutesCompilerVersion parse(String version) {
            if (version == null) {
                throw new InvalidUserDataException("There is no version of the Play Routes Compiler detected");
            }

            if (version.matches("2\\.2\\..*?")) {
                return PLAY_ROUTES_VERSION_22;
            } else if (version.matches("2\\.3\\..*?")) {
                return PLAY_ROUTES_VERSION_23;
            }
            throw new InvalidUserDataException("Could not find a compatible Play version for Routes Compiler. This plugin is compatible with: 2.3.x, 2.2.x");
        }
    }

    private static Function<Object[], Object> createCompileFunction(ClassLoader cl, RoutesCompilerVersion routesCompilerVersion) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        switch (routesCompilerVersion) {
            case PLAY_ROUTES_VERSION_22:
                return ScalaUtil.scalaObjectFunction(
                        cl,
                        "play.router.RoutesCompiler",
                        "compile",
                        new Class<?>[]{
                                File.class, //input
                                File.class,
                                cl.loadClass("scala.collection.Seq"),
                                boolean.class,
                                boolean.class
                        }
                );
            case PLAY_ROUTES_VERSION_23:
                return ScalaUtil.scalaObjectFunction(
                        cl,
                        "play.router.RoutesCompiler",
                        "compile",
                        new Class<?>[]{
                                File.class, //input
                                File.class,
                                cl.loadClass("scala.collection.Seq"),
                                boolean.class,
                                boolean.class,
                                boolean.class
                        }
                );

            default:
                throw new InvalidUserDataException("Could not find a compatible Play version for Routes Compiler. This plugin is compatible with: 2.3.x, 2.2.x");
        }

    }

    private Object[] createCompileParameters(File sourceFile, RoutesCompilerVersion routesCompilerVersion, RoutesCompileSpec spec, ClassLoader cl) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        File generatedDirectory = spec.getDestinationDir();
        Boolean generateReverseRoute= spec.getGenerateReverseRoute();
        boolean generateRefReverseRouter = spec.isGenerateRefReverseRouter();
        boolean namespaceReverseRouter = spec.isNamespaceReverseRouter();

        switch (routesCompilerVersion) {
            case PLAY_ROUTES_VERSION_22:
                return  new Object[]{
                    sourceFile,
                    generatedDirectory,
                    getAdditiontalImportsAsScalaSeq(cl, spec),
                    generateReverseRoute,
                    namespaceReverseRouter
                };
            case PLAY_ROUTES_VERSION_23:
                return new Object[]{
                    sourceFile,
                    generatedDirectory,
                    getAdditiontalImportsAsScalaSeq(cl, spec),
                    generateReverseRoute,
                    generateRefReverseRouter,
                    namespaceReverseRouter
                };

            default:
                throw new RuntimeException("Could not find a compatible Play version for Routes Compiler. This plugin is compatible with: 2.3.x, 2.2.x");
        }
    }

    private Object getAdditiontalImportsAsScalaSeq(ClassLoader cl, RoutesCompileSpec spec) throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        List<String> additionalImports = new ArrayList<String>();
        additionalImports.addAll(spec.getAdditionalImports());

        Class<?> bufferClass = cl.loadClass("scala.collection.mutable.ListBuffer");
        Object buffer = bufferClass.newInstance();
        Method bufferPlusEq = bufferClass.getMethod("$plus$eq", Object.class);

        if (additionalImports != null) {
            for (String additionalImport : additionalImports) {
                bufferPlusEq.invoke(buffer, additionalImport);
            }
        }
        return buffer;
    }

    public WorkResult execute(RoutesCompileSpec spec) {
        boolean didWork = false;

        try {
            ClassLoader cl = getClass().getClassLoader();
            Iterable<File> sources = spec.getSources();
            Function<Object[], Object> compile = createCompileFunction(cl, routesCompilerVersion);
            for (File sourceFile : sources) {
                Object ret = compile.apply(createCompileParameters(sourceFile, routesCompilerVersion, spec, cl));
                if (ret != null && ret instanceof Boolean) {
                    didWork = (Boolean) ret || didWork;
                } else {
                    didWork = true; //assume we did some work
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile Play routes.", e);
        }

        return new SimpleWorkResult(didWork);
    }

}
