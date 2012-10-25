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
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.compile.*;
import org.gradle.api.internal.tasks.compile.Compiler;
import org.gradle.api.internal.tasks.scala.*;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.internal.Factory;
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
    private static final Logger LOGGER = Logging.getLogger(ScalaCompile.class);

    private FileCollection scalaClasspath;
    private FileCollection zincClasspath;
    private Compiler<ScalaJavaJointCompileSpec> compiler;
    private final CompileOptions compileOptions = new CompileOptions();
    private final ScalaCompileOptions scalaCompileOptions = new ScalaCompileOptions();

    @Inject
    public ScalaCompile(Instantiator instantiator) { // TODO: don't need instantiator ATM
        ProjectInternal projectInternal = (ProjectInternal) getProject();
        IsolatedAntBuilder antBuilder = getServices().get(IsolatedAntBuilder.class);
        Factory<AntBuilder> antBuilderFactory = getServices().getFactory(AntBuilder.class);
        JavaCompilerFactory inProcessCompilerFactory = new InProcessJavaCompilerFactory();
        TemporaryFileProvider tempFileProvider = projectInternal.getServices().get(TemporaryFileProvider.class);
        DefaultJavaCompilerFactory javaCompilerFactory = new DefaultJavaCompilerFactory(projectInternal, tempFileProvider, antBuilderFactory, inProcessCompilerFactory);
        ScalaCompilerFactory scalaCompilerFactory = new ScalaCompilerFactory(projectInternal, antBuilder, antBuilderFactory, javaCompilerFactory);
        Compiler<ScalaJavaJointCompileSpec> delegatingCompiler = new DelegatingScalaCompiler(scalaCompilerFactory);
        compiler = new IncrementalScalaCompiler(delegatingCompiler, getOutputs());
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
     * Returns the classpath to use to load the Zinc incremental compiler.
     * This compiler in turn loads the Scala compiler.
     */
    @InputFiles
    public FileCollection getZincClasspath() {
        return zincClasspath;
    }

    public void setZincClasspath(FileCollection zincClasspath) {
        this.zincClasspath = zincClasspath;
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
        DefaultScalaJavaJointCompileSpec spec = new DefaultScalaJavaJointCompileSpec();
        spec.setSource(getSource());
        spec.setDestinationDir(getDestinationDir());
        spec.setClasspath(getClasspath());
        spec.setScalaClasspath(getScalaClasspath());
        spec.setZincClasspath(getZincClasspath());
        spec.setSourceCompatibility(getSourceCompatibility());
        spec.setTargetCompatibility(getTargetCompatibility());
        spec.setCompileOptions(compileOptions);
        spec.setScalaCompileOptions(scalaCompileOptions);
        if (!scalaCompileOptions.isUseAnt()) {
            spec.setCompilerCacheMap(createCompilerCacheMap());

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Compiler cache file: {}", scalaCompileOptions.getCompilerCacheFile());
                LOGGER.debug("Compiler cache map: {}", spec.getCompilerCacheMap());
            }
        }
        compiler.execute(spec);
    }

    private Map<File, File> createCompilerCacheMap() {
        Map<File, File> cacheMap = new HashMap<File, File>();
        for (Project project : getProject().getRootProject().getAllprojects()) {
            Collection<ScalaCompile> compileTasks = project.getTasks().withType(ScalaCompile.class);
            for (ScalaCompile task : compileTasks) {
                if (!task.getScalaCompileOptions().isUseAnt()) {
                    // TODO: is this the correct key, or does it have to be the Jar that
                    // eventually lands on another ScalaCompile task's compile class path?
                    //cacheMap.put(project.getTasks().withType(Jar.class).findByName("jar").getArchivePath(), task.getScalaCompileOptions().getCompilerCacheFile());
                    cacheMap.put(task.getDestinationDir(), task.getScalaCompileOptions().getCompilerCacheFile());
                }
            }
        }
        return cacheMap;
    }
}
