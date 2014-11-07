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

package org.gradle.play.internal.routes.spec;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public abstract class DefaultRoutesCompileSpec implements RoutesCompileSpec {
    private final Iterable<File> sources;
    private final File destinationDir;
    private final List<String> additionalImports = new ArrayList<String>();
    private final boolean generateReverseRoute;
    private final boolean namespaceReverseRouter;


    protected abstract List<String> defaultScalaImports();

    protected abstract List<String> defaultJavaImports();

    protected Object getAdditiontalImportsAsScalaSeq(ClassLoader cl, RoutesCompileSpec spec) throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
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

    public DefaultRoutesCompileSpec(Iterable<File> sources, File destinationDir, List<String> additionalImports, boolean isJavaProject) {
        this.sources = sources;
        this.destinationDir = destinationDir;
        if (additionalImports.isEmpty()) {
            if (isJavaProject) {
                this.additionalImports.addAll(defaultJavaImports());
            } else {
                this.additionalImports.addAll(defaultScalaImports());
            }
        } else {
            this.additionalImports.addAll(additionalImports);
        }
        generateReverseRoute = true;
        namespaceReverseRouter = false;
    }

    public Iterable<File> getSources() {
        return sources;
    }

    public File getDestinationDir() {
        return destinationDir;
    }

    public List<String> getAdditionalImports() {
        return additionalImports;
    }

    public boolean getGenerateReverseRoute() {
        return generateReverseRoute;
    }

    public boolean getNamespaceReverseRouter() {
        return namespaceReverseRouter;
    }
}
