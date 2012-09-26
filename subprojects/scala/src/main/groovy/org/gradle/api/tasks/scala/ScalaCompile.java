/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.scala;

import org.gradle.api.AntBuilder;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.tasks.compile.AntJavaCompiler;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.internal.tasks.scala.*;
import org.gradle.api.internal.tasks.scala.incremental.SbtScalaCompiler;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.internal.reflect.Instantiator;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Compiles Scala source files, and optionally, Java source files.
 */
public class ScalaCompile extends AbstractCompile {
    private FileCollection scalaClasspath;
    private Compiler<ScalaJavaJointCompileSpec> compiler;
    private final CompileOptions compileOptions = new CompileOptions();
    private final ScalaCompileOptions scalaCompileOptions;

    /**
     * For testing only.
     */
    public ScalaCompile() {
        scalaCompileOptions = new ScalaCompileOptions();
    }

    @Inject
    public ScalaCompile(Instantiator instantiator) {
        scalaCompileOptions = instantiator.newInstance(ScalaCompileOptions.class);
    }

    /**
     * Returns the classpath to use to load the Scala compiler.
     */
    @InputFiles
    public FileCollection getScalaClasspath() {
        return scalaClasspath;
    }

    public void setScalaClasspath(FileCollection scalaClasspath) {
        this.scalaClasspath = scalaClasspath;
    }

    /**
     * Returns the Scala compilation options.
     */
    @Nested
    public ScalaCompileOptions getScalaCompileOptions() {
        return scalaCompileOptions;
    }

    /**
     * Returns the Java compilation options.
     */
    @Nested
    public CompileOptions getOptions() {
        return compileOptions;
    }

    /**
     * For testing only.
     */
    void setCompiler(Compiler<ScalaJavaJointCompileSpec> compiler) {
        this.compiler = compiler;
    }

    @Override
    protected void compile() {
        Compiler<ScalaJavaJointCompileSpec> compiler = createCompiler();
        DefaultScalaJavaJointCompileSpec spec = new DefaultScalaJavaJointCompileSpec();
        spec.setSource(getSource());
        spec.setDestinationDir(getDestinationDir());
        spec.setClasspath(getClasspath());
        spec.setScalaClasspath(getScalaClasspath());
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setTargetCompatibility(getTargetCompatibility());
        spec.setCompileOptions(compileOptions);
        spec.setScalaCompileOptions(scalaCompileOptions);
        if (scalaCompileOptions.isIncremental()) {
            spec.setIncrementalCacheMap(createIncrementalCacheMap());
        }
        compiler.execute(spec);
    }

    private Compiler<ScalaJavaJointCompileSpec> createCompiler() {
        if (this.compiler != null) {
            return this.compiler;
        }

        Compiler<JavaCompileSpec> javaCompiler = new AntJavaCompiler(getServices().getFactory(AntBuilder.class));

        if (scalaCompileOptions.isIncremental()) {
            return new DefaultScalaJavaJointCompiler(new SbtScalaCompiler(), javaCompiler);
        }

        Compiler<ScalaCompileSpec> scalaCompiler = new AntScalaCompiler(getServices().get(IsolatedAntBuilder.class));
        Compiler<ScalaJavaJointCompileSpec> jointCompiler = new DefaultScalaJavaJointCompiler(scalaCompiler, javaCompiler);
        return new IncrementalScalaCompiler(jointCompiler, getOutputs());
    }

    private Map<File, File> createIncrementalCacheMap() {
        Map<File, File> cacheMap = new HashMap<File, File>();
        for (Project project : getProject().getRootProject().getAllprojects()) {
            Collection<ScalaCompile> compileTasks = project.getTasks().withType(ScalaCompile.class);
            for (ScalaCompile task : compileTasks) {
                if (task.getScalaCompileOptions().isIncremental()) {
                    // TODO: is this the correct key, or does it have to be the Jar that
                    // eventually lands on another ScalaCompile task's compile class path?
                    cacheMap.put(task.getDestinationDir(), task.getScalaCompileOptions().getIncrementalCacheFile());
                }
            }
        }
        return cacheMap;
    }
}
