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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.scala.internal.reflect.ScalaListBuffer;
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.scala.internal.reflect.ScalaObject;
import org.gradle.scala.internal.reflect.ScalaReflectionUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;

public class RoutesCompilerAdapterV24X extends DefaultVersionedRoutesCompilerAdapter {
    private static final Logger LOGGER = Logging.getLogger(RoutesCompilerAdapterV24X.class);

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

    @Override
    public Boolean interpretResult(Object result) throws ClassNotFoundException {
        // result is a scala.util.Either
        // right is a Seq of files that were generated
        // left is routes compilation errors
        // TODO: It might be nice to pass along these errors in some way
        JavaMethod<Object, Boolean> isRight = JavaMethod.of(result, Boolean.class, "isRight");
        Boolean successful = Cast.cast(Boolean.class, isRight.invoke(result));
        if (successful) {
            // extract the files that were generated
            /*
                val rightResult = result.right()
                val generatedFiles = right.get()
                val empty = generatedFiles.isEmpty()
                empty.booleanValue()
             */
            JavaMethod<Object, Object> right = JavaMethod.of(result, Object.class, "right");
            Object rightResult = right.invoke(result);
            JavaMethod<Object, Object> get = JavaMethod.of(rightResult, Object.class, "get");
            Object generatedFiles = get.invoke(rightResult);
            JavaMethod<Object, Object> isEmpty = JavaMethod.of(generatedFiles, Object.class, "isEmpty");
            Object empty = isEmpty.invoke(generatedFiles);
            JavaMethod<Object, Boolean> booleanValue = JavaMethod.of(empty, Boolean.class, "booleanValue");
            return booleanValue.invoke(empty);
        } else {
            // extract exceptions
            /*
                val leftResult = result.left()
                val errorSeq = left.get()

                // convert errorSeq -> Java types
             */
            JavaMethod<Object, Object> left = JavaMethod.of(result, Object.class, "left");
            Object leftResult = left.invoke(result);
            JavaMethod<Object, Object> get = JavaMethod.of(leftResult, Object.class, "get");
            Object errorSeq = get.invoke(leftResult);

            // Convert Scala Seq[RoutesCompilationError] -> Java List<RoutesCompilationError>
            ClassLoader resultCl = result.getClass().getClassLoader();
            ScalaMethod seqAsJavaList = ScalaReflectionUtil.scalaMethod(resultCl, "scala.collection.JavaConversions", "seqAsJavaList", resultCl.loadClass("scala.collection.Seq"));
            List<Object> errors = Cast.uncheckedCast(seqAsJavaList.invoke(errorSeq));
            
            RoutesCompilationErrorAdapter errorAdapter = new RoutesCompilationErrorAdapter(
                    resultCl.loadClass("play.routes.compiler.RoutesCompilationError"),
                    resultCl.loadClass("scala.Option"));

            for (Object error : errors) {
                RoutesCompilationError adaptedError = errorAdapter.adapt(error);
                String message = adaptedError.toString();
                LOGGER.error(message);
            }
            throw new RuntimeException("route compilation failed with errors");
        }
    }

    private static class RoutesCompilationErrorAdapter {
        private final JavaMethod<Object, File> sourceMethod;
        private final JavaMethod<Object, String> messageMethod;
        private final JavaMethod<Object, Object> lineMethod;
        private final JavaMethod<Object, Object> columnMethod;
        private final JavaMethod<Object, Object> getMethod;

        private RoutesCompilationErrorAdapter(Class<?> routesCompilationError, Class<?> option) {
            this.sourceMethod = Cast.uncheckedCast(JavaMethod.of(routesCompilationError, File.class, "source"));
            this.messageMethod = Cast.uncheckedCast(JavaMethod.of(routesCompilationError, String.class, "message"));
            this.lineMethod = Cast.uncheckedCast(JavaMethod.of(routesCompilationError, Object.class, "line"));
            this.columnMethod = Cast.uncheckedCast(JavaMethod.of(routesCompilationError, Object.class, "column"));
            this.getMethod = Cast.uncheckedCast(JavaMethod.of(option, Object.class, "get"));
        }

        RoutesCompilationError adapt(Object error) {
            return new RoutesCompilationError(
                    sourceMethod.invoke(error),
                    messageMethod.invoke(error),
                    toInt(lineMethod.invoke(error)),
                    toInt(columnMethod.invoke(error)));
        }

        Integer toInt(Object optionInt) {
            try {
                return Cast.uncheckedCast(getMethod.invoke(optionInt));
            } catch (Exception e) {
                return 0;
            }
        }
    }

    private static class RoutesCompilationError {
        private final File source;
        private final String message;
        private final int line;
        private final int col;

        private RoutesCompilationError(File source, String message, int line, int col) {
            this.source = source;
            this.message = message;
            this.line = line;
            this.col = col;
        }

        @Override
        public String toString() {
            if (line > 0 && col > 0) {
                return source.getAbsolutePath() + ":" + line + ":" + col + " " + message;
            } else {
                return source.getAbsolutePath() + " " + message;
            }
        }
    }
}
