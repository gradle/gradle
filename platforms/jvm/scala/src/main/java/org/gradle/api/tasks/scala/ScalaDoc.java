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

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.scala.internal.GenerateScaladoc;
import org.gradle.api.tasks.scala.internal.ScalaRuntimeHelper;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.JavaForkOptions;
import org.gradle.util.internal.GUtil;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.List;

/**
 * Generates HTML API documentation for Scala source files.
 */
@CacheableTask
public abstract class ScalaDoc extends SourceTask {

    private File destinationDir;

    private FileCollection classpath;
    private FileCollection scalaClasspath;
    private ScalaDocOptions scalaDocOptions;
    private String title;
    private final Property<String> maxMemory;
    private final Property<JavaLauncher> javaLauncher;
    private final ConfigurableFileCollection compilationOutputs;

    public ScalaDoc() {
        ObjectFactory objectFactory = getObjectFactory();
        this.maxMemory = objectFactory.property(String.class);
        JavaToolchainService javaToolchainService = getJavaToolchainService();
        this.javaLauncher = objectFactory.property(JavaLauncher.class).convention(javaToolchainService.launcherFor(it -> {}));
        this.compilationOutputs = objectFactory.fileCollection();
        this.scalaDocOptions = objectFactory.newInstance(ScalaDocOptions.class);
    }

    /**
     * Returns the directory to generate the API documentation into.
     */
    @OutputDirectory
    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    /**
     * Returns the source for this task, after the include and exclude patterns have been applied. Ignores source files which do not exist.
     *
     * <p>
     * The {@link PathSensitivity} for the sources is configured to be {@link PathSensitivity#RELATIVE}.
     * </p>
     *
     * @return The source.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    @Override
    public FileTree getSource() {
        return super.getSource();
    }

    /**
     * Returns the compilation outputs needed by Scaladoc filtered to include <a href="https://docs.scala-lang.org/scala3/guides/tasty-overview.html">TASTy</a> files.
     * <p>
     * NOTE: This is only useful with Scala 3 or later. Scala 2 only processes source files.
     * </p>
     * @return the compilation outputs produced from the sources
     * @since 7.3
     */
    @InputFiles
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    protected FileTree getFilteredCompilationOutputs() {
        return getCompilationOutputs().getAsFileTree().matching(getPatternSet()).matching(pattern -> pattern.include("**/*.tasty"));
    }

    /**
     * Returns the compilation outputs produced by the sources that are generating Scaladoc.
     *
     * @return the compilation outputs produced from the sources
     * @since 7.3
     */
    @Internal
    public ConfigurableFileCollection getCompilationOutputs() {
        return compilationOutputs;
    }

    /**
     * <p>Returns the classpath to use to locate classes referenced by the documented source.</p>
     *
     * @return The classpath.
     */
    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    /**
     * Returns the classpath to use to load the ScalaDoc tool.
     */
    @Classpath
    public FileCollection getScalaClasspath() {
        return scalaClasspath;
    }

    public void setScalaClasspath(FileCollection scalaClasspath) {
        this.scalaClasspath = scalaClasspath;
    }

    /**
     * Returns the ScalaDoc generation options.
     */
    @Nested
    public ScalaDocOptions getScalaDocOptions() {
        return scalaDocOptions;
    }

    public void setScalaDocOptions(ScalaDocOptions scalaDocOptions) {
        this.scalaDocOptions = scalaDocOptions;
    }

    /**
     * Returns the documentation title.
     */
    @Nullable
    @Optional
    @Input
    public String getTitle() {
        return title;
    }

    public void setTitle(@Nullable String title) {
        this.title = title;
    }

    /**
     * Returns the amount of memory allocated to this task.
     * Ex. 512m, 1G
     *
     * @since 6.5
     */
    @Internal
    public Property<String> getMaxMemory() {
        return maxMemory;
    }

    /**
     * A JavaLauncher used to run the Scaladoc tool.
     * @since 7.2
     */
    @Nested
    public Property<JavaLauncher> getJavaLauncher() {
        return javaLauncher;
    }

    @TaskAction
    protected void generate() {
        ScalaDocOptions options = getScalaDocOptions();
        if (!GUtil.isTrue(options.getDocTitle())) {
            options.setDocTitle(getTitle());
        }

        WorkQueue queue = getWorkerExecutor().processIsolation(worker -> {
            worker.getClasspath().from(getScalaClasspath());
            JavaForkOptions forkOptions = worker.getForkOptions();
            if (getMaxMemory().isPresent()) {
                forkOptions.setMaxHeapSize(getMaxMemory().get());
            }

            forkOptions.setExecutable(javaLauncher.get().getExecutablePath().getAsFile().getAbsolutePath());
        });
        queue.submit(GenerateScaladoc.class, parameters -> {
            @Nullable
            File optionsFile = createOptionsFile();
            parameters.getOptionsFile().set(optionsFile);
            parameters.getClasspath().from(getClasspath());
            parameters.getOutputDirectory().set(getDestinationDir());
            boolean isScala3 = ScalaRuntimeHelper.findScalaJar(getScalaClasspath(), "library_3") != null;
            parameters.getIsScala3().set(isScala3);
            if (isScala3) {
                parameters.getSources().from(getFilteredCompilationOutputs());
            } else {
                parameters.getSources().from(getSource());

                if (options.isDeprecation()) {
                    parameters.getOptions().add("-deprecation");
                }

                if (options.isUnchecked()) {
                    parameters.getOptions().add("-unchecked");
                }
            }

            String footer = options.getFooter();
            if (footer != null) {
                parameters.getOptions().add("-doc-footer");
                parameters.getOptions().add(footer);
            }

            String docTitle = options.getDocTitle();
            if (docTitle != null) {
                parameters.getOptions().add("-doc-title");
                parameters.getOptions().add(docTitle);
            }

            // None of these options work for Scala >=2.8
            // options.getBottom();;
            // options.getTop();
            // options.getHeader();
            // options.getWindowTitle();

            List<String> additionalParameters = options.getAdditionalParameters();
            if (additionalParameters != null) {
                parameters.getOptions().addAll(additionalParameters);
            }
        });
    }

    /**
     * Creates the file to hold the options for the scaladoc process.
     *
     * @implNote This file will be cleaned up by {@link GenerateScaladoc#execute()}.
     */
    @Nullable
    private File createOptionsFile() {
        return new File(getTemporaryDir(), "scaladoc.options");
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    protected abstract IsolatedAntBuilder getAntBuilder();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @Inject
    protected abstract JavaToolchainService getJavaToolchainService();
}
