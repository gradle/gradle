/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.tasks;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.internal.classpath.ClassPath;

import javax.annotation.Nullable;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scala library Jar file, its version, associated Scala compiler and compiler bridge.
 */
public abstract class ScalaLibraryJar {
    public final File file;

    public final String version;

    public ScalaLibraryJar(final File file, final String version) {
        this.file = file;
        this.version = version;
    }

    public final DefaultExternalModuleDependency compilerJar() {
        return new DefaultExternalModuleDependency("org.scala-lang", compilerArtifactId(), version);
    }

    protected abstract String compilerArtifactId();

    public final DefaultExternalModuleDependency compilerBridgeJar(final String zincVersion) {
        final DefaultExternalModuleDependency result = getCompilerBridgeJar(zincVersion);

        result.setTransitive(false);

        if (isCompilerBridgeDistributedInSourceForm()) {
            result.artifact(artifact -> {
                artifact.setClassifier("sources");
                artifact.setType("jar");
                artifact.setExtension("jar");
                artifact.setName(result.getName());
            });
        }

        return result;
    }

    public final File findCompilerBridgeJar(final ClassPath classpath) {
        for (final File f : classpath.getAsFiles()) {
            if (f.getName().startsWith(compilerBridgeArtifactIdPrefix())) return f;
        }

        throw new IllegalStateException(String.format("Cannot find any files starting with %s in %s", compilerBridgeArtifactIdPrefix(), classpath.getAsFiles()));
    }

    protected abstract DefaultExternalModuleDependency getCompilerBridgeJar(final String zincVersion);

    protected abstract String compilerBridgeArtifactIdPrefix();

    public abstract boolean isCompilerBridgeDistributedInSourceForm();


    /**
     * Scala 2 library and associated dependencies.
     */
    public static final class Scala2 extends ScalaLibraryJar {
        public Scala2(final File file, final String version) {
            super(file, version);
        }

        @Override
        protected String compilerArtifactId() {
            return "scala-compiler";
        }

        @Override
        protected DefaultExternalModuleDependency getCompilerBridgeJar(final String zincVersion) {
            String scalaMajorMinorVersion = Joiner.on('.').join(Splitter.on('.').splitToList(version).subList(0, 2));
            return new DefaultExternalModuleDependency(
                "org.scala-sbt",
                compilerBridgeArtifactIdPrefix() + "_" + scalaMajorMinorVersion,
                zincVersion
            );
        }

        @Override
        protected String compilerBridgeArtifactIdPrefix() {
            return "compiler-bridge";
        }

        @Override
        public boolean isCompilerBridgeDistributedInSourceForm() {
            return true;
        }
    }

    /**
     * Scala 3 library and associated dependencies.
     */
    public static final class Scala3 extends ScalaLibraryJar {
        public Scala3(final File file, final String version) {
            super(file, version);
        }

        @Override
        protected String compilerArtifactId() {
            return "scala3-compiler_3";
        }

        @Override
        protected DefaultExternalModuleDependency getCompilerBridgeJar(final String zincVersion) {
            return new DefaultExternalModuleDependency(
                "org.scala-lang",
                compilerBridgeArtifactIdPrefix(),
                version
            );
        }

        protected String compilerBridgeArtifactIdPrefix() {
            return "scala3-sbt-bridge";
        }

        @Override
        public boolean isCompilerBridgeDistributedInSourceForm() {
            // Scala 3 compiler bridge is published in binary form,
            // so there is no need to compile it - which is great,
            // since it is written in Java, and Zinc's AnalyzingCompiler.compileSources()
            // does not compile Java.
            return false;
        }
    }

    private static final Pattern Scala2Pattern = Pattern.compile("scala-library-(\\d.*).jar");

    private static final Pattern Scala3Pattern = Pattern.compile("scala3-library_3-(\\d.*).jar");

    /**
     * Searches the specified class path for a Scala library Jar file and determines its version;
     * if no such file is found, {@code GradleException} is thrown.
     *
     * @param classpath the class path to search
     * @param project in which the Scala library search is being performed
     * @return {@code ScalaLibraryJar} instance containing the found Scala library Jar {@code file} and its {@code version}
     * @throws GradleException if the Scala library is not found
     */
    public static ScalaLibraryJar find(final Iterable<File> classpath, @Nullable final Project project) {
        // Scala 3 library brings Scala 2 library as a dependency, so look for the Scala 3 library first:
        for (final File file : classpath) {
            final Matcher matcher = Scala3Pattern.matcher(file.getName());
            if (matcher.matches()) return new Scala3(file, matcher.group(1));
        }

        for (final File file : classpath) {
            final Matcher matcher = Scala2Pattern.matcher(file.getName());
            if (matcher.matches()) return new Scala2(file, matcher.group(1));
        }

        throw new GradleException(String.format("Cannot infer Scala class path because no Scala library Jar was found. "
            + "Does %s declare dependency to scala-library? Searched classpath: %s.", project, classpath));
    }
}
