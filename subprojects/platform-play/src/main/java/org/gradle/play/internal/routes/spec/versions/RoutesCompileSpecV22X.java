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

package org.gradle.play.internal.routes.spec.versions;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.gradle.play.internal.ScalaUtil;
import org.gradle.play.internal.routes.spec.DefaultRoutesCompileSpec;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class RoutesCompileSpecV22X extends DefaultRoutesCompileSpec {

    protected List<String> defaultScalaImports() {
        return Lists.newArrayList();
    }

    protected List<String> defaultJavaImports() {
        List<String> javaImports = defaultScalaImports();
        javaImports.add("play.libs.F");
        return javaImports;
    }

    public RoutesCompileSpecV22X(Iterable<File> sources, File destinationDir, List<String> additionalImports, boolean isJavaProject) {
        super(sources, destinationDir, additionalImports, isJavaProject);
    }

    public Function<Object[], Object> getCompilerMethod(ClassLoader cl) throws ClassNotFoundException {
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
                });
    }

    public Object[] createCompileParameters(ClassLoader cl, File file) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return new Object[]{
                file,
                getDestinationDir(),
                getAdditiontalImportsAsScalaSeq(cl, this),
                getGenerateReverseRoute(),
                getNamespaceReverseRouter()
        };
    }
}