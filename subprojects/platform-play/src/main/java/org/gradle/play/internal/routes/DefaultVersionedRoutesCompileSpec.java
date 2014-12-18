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
import org.gradle.play.platform.PlayPlatform;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class DefaultVersionedRoutesCompileSpec extends DefaultRoutesCompileSpec implements VersionedRoutesCompileSpec {
    private final Iterable<File> sources;
    private final File destinationDir;
    private final List<String> additionalImports = new ArrayList<String>();
    private final boolean generateReverseRoute;
    private final boolean namespaceReverseRouter;
    private final String scalaVersion;

    private final String playVersion;


    protected abstract List<String> defaultScalaImports();

    protected abstract List<String> defaultJavaImports();

    public DefaultVersionedRoutesCompileSpec(Iterable<File> sources, File destinationDir, List<String> additionalImports, BaseForkOptions forkOptions, boolean javaProject, PlayPlatform playPlatform) {
        super(sources, destinationDir, additionalImports, false, forkOptions, javaProject);
        this.scalaVersion = "2.10";
        this.playVersion = playPlatform.getPlayVersion();
        this.sources = sources;
        this.destinationDir = destinationDir;
        if (additionalImports == null) {
            if (javaProject) {
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

    public String getPlayVersion() {
        return playVersion;
    }

    public String getScalaVersion() {
        return scalaVersion;
    }

    public Object getDependencyNotation() {
        return String.format("com.typesafe.play:routes-compiler_%s:%s", getScalaVersion(), getPlayVersion());
    }

    public List<String> getClassLoaderPackages() {
        return Arrays.asList("play.router", "scala.collection", "scala.collection.mutable", "scala.util.matching");
    }
}
