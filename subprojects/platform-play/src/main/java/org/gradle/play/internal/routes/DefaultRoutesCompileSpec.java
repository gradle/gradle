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

import org.gradle.api.tasks.compile.BaseForkOptions;

import java.io.File;
import java.util.List;

public class DefaultRoutesCompileSpec implements RoutesCompileSpec {
    private final Iterable<File> sourceFiles;
    private final File outputDirectory;
    private final List<String> additionalImports;
    private final boolean namespaceReverseRouter;
    private final BaseForkOptions forkOptions;
    private final boolean javaProject;

    public DefaultRoutesCompileSpec(Iterable<File> sourceFiles, File outputDirectory, List<String> additionalImports, boolean namespaceReverseRouter, BaseForkOptions forkOptions, boolean javaProject) {
        this.sourceFiles = sourceFiles;
        this.outputDirectory = outputDirectory;
        this.additionalImports = additionalImports;
        this.namespaceReverseRouter = namespaceReverseRouter;
        this.forkOptions = forkOptions;
        this.javaProject = javaProject;
    }

    public Iterable<File> getSources() {
        return sourceFiles;
    }

    public BaseForkOptions getForkOptions() {
        return forkOptions;
    }

    public boolean isJavaProject() {
        return javaProject;
    }

    public File getDestinationDir() {
        return outputDirectory;
    }

    public List<String> getAdditionalImports() {
        return additionalImports;
    }

    public boolean isNamespaceReverseRouter() {
        return namespaceReverseRouter;
    }
}
