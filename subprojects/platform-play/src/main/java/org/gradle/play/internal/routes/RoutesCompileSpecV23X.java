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

import com.google.common.collect.Lists;
import org.gradle.api.tasks.compile.BaseForkOptions;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.scala.internal.reflect.ScalaListBuffer;
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.scala.internal.reflect.ScalaReflectionUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class RoutesCompileSpecV23X extends DefaultVersionedRoutesCompileSpec {
    private final boolean generateRefReverseRouter;

    public RoutesCompileSpecV23X(Iterable<File> sources, File destinationDir, List<String> additionalImports, boolean generateRefReverseRouter, BaseForkOptions forkOptions, boolean javaProject, PlayPlatform playPlatform) {
        super(sources, destinationDir, additionalImports, forkOptions, javaProject, playPlatform);
        this.generateRefReverseRouter = generateRefReverseRouter;
    }

    public boolean getGenerateRefReverseRouter() {
        return generateRefReverseRouter;
    }

    protected List<String> defaultScalaImports() {
        return Lists.newArrayList("controllers.Assets.Asset");
    }

    protected List<String> defaultJavaImports() {
        List<String> javaImports = defaultScalaImports();
        javaImports.add("play.libs.F");
        return javaImports;
    }

    public ScalaMethod getCompileMethod(ClassLoader cl) throws ClassNotFoundException {
        return ScalaReflectionUtil.scalaMethod(
                cl,
                "play.router.RoutesCompiler",
                "compile",
                File.class,
                File.class,
                cl.loadClass("scala.collection.Seq"),
                boolean.class,
                boolean.class,
                boolean.class
        );
    }

    public Object[] createCompileParameters(ClassLoader cl, File file) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return new Object[] {
                file,
                getDestinationDir(),
                ScalaListBuffer.fromList(cl, getAdditionalImports()),
                getGenerateReverseRoute(),
                getGenerateRefReverseRouter(),
                getNamespaceReverseRouter()
        };
    }

}
