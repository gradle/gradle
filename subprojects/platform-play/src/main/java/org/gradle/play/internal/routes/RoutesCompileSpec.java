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

import org.gradle.play.internal.PlayCompileSpec;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class RoutesCompileSpec implements PlayCompileSpec, Serializable {
    private final Iterable<File> sources;
    private final File destinationDir;
    private final List<String> additionalImports = new ArrayList<String>();
    private final boolean generateReverseRoute;
    private final boolean generateRefReverseRouter;
    private final boolean namespaceReverseRouter;

    public RoutesCompileSpec(Iterable<File> sources, File destinationDir, List<String> additionalImports) {
        this.sources = sources;
        this.destinationDir = destinationDir;
        this.additionalImports.addAll(additionalImports);
        generateReverseRoute = true;
        generateRefReverseRouter = true;
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

    public Boolean getGenerateReverseRoute() {
        return generateReverseRoute;
    }

    public boolean isGenerateRefReverseRouter() {
        return generateRefReverseRouter;
    }

    public boolean isNamespaceReverseRouter() {
        return namespaceReverseRouter;
    }
}
