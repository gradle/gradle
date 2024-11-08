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

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
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
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.process.JavaForkOptions;
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

    private ScalaDocOptions scalaDocOptions;
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
    @ReplacesEagerProperty
    public abstract DirectoryProperty getDestinationDir();

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
    @ToBeReplacedByLazyProperty
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
    @ReplacesEagerProperty
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * Returns the classpath to use to load the ScalaDoc tool.
     */
    @Classpath
    @ReplacesEagerProperty
    public abstract ConfigurableFileCollection getScalaClasspath();

    /**
     * Returns the ScalaDoc generation options.
     */
    @Nested
    public ScalaDocOptions getScalaDocOptions() {
        return scalaDocOptions;
    }

    /**
     * Sets the ScalaDoc generation options.
     *
     * @deprecated Setting a new instance of this property is unnecessary. This method will be removed in Gradle 9.0. Use {@link #scalaDocOptions(Action)} instead.
     */
    @Deprecated
    public void setScalaDocOptions(ScalaDocOptions scalaDocOptions) {
        DeprecationLogger.deprecateMethod(ScalaDoc.class, "setScalaDocOptions(ScalaDocOptions)")
            .replaceWith("scalaDocOptions(Action)")
            .withContext("Setting a new instance of scalaDocOptions is unnecessary.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_nested_properties_setters")
            .nagUser();
        this.scalaDocOptions = scalaDocOptions;
    }

    /**
     * Configures the ScalaDoc generation options.
     *
     * @since 8.11
     */
    public void scalaDocOptions(Action<? super ScalaDocOptions> action) {
        action.execute(getScalaDocOptions());
    }

    /**
     * Returns the documentation title.
     */
    @Optional
    @Input
    @ReplacesEagerProperty
    public abstract Property<String> getTitle();

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
        String docTitle = options.getDocTitle()
            .orElse(getTitle())
            .getOrNull();

        WorkQueue queue = getWorkerExecutor().processIsolation(worker -> {
            worker.getClasspath().from(getScalaClasspath());
            JavaForkOptions forkOptions = worker.getForkOptions();
            if (getMaxMemory().isPresent()) {
                forkOptions.getMaxHeapSize().set(getMaxMemory());
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

                if (options.getDeprecation().get()) {
                    parameters.getOptions().add("-deprecation");
                }

                if (options.getUnchecked().get()) {
                    parameters.getOptions().add("-unchecked");
                }
            }

            String footer = options.getFooter().getOrNull();
            if (footer != null) {
                parameters.getOptions().add("-doc-footer");
                parameters.getOptions().add(footer);
            }

            if (docTitle != null) {
                parameters.getOptions().add("-doc-title");
                parameters.getOptions().add(docTitle);
            }

            // None of these options work for Scala >=2.8
            // options.getBottom();;
            // options.getTop();
            // options.getHeader();
            // options.getWindowTitle();

            List<String> additionalParameters = options.getAdditionalParameters().get();
            parameters.getOptions().addAll(additionalParameters);
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
