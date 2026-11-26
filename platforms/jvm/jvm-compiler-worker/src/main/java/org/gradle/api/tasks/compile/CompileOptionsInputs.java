/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.tasks.compile;

import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.internal.instrumentation.api.annotations.LazyProperties;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of {@link CompileOptions}.
 *
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface CompileOptionsInputs {

    /**
     * Whether to fail the build when compilation fails.
     *
     * @see CompileOptions#isFailOnError()
     * @since 10.0.0
     */
    Property<Boolean> getFailOnError();

    /**
     * Whether to produce verbose output.
     *
     * @see CompileOptions#isVerbose()
     * @since 10.0.0
     */
    Property<Boolean> getVerbose();

    /**
     * Whether to log the files to be compiled.
     *
     * @see CompileOptions#isListFiles()
     * @since 10.0.0
     */
    Property<Boolean> getListFiles();

    /**
     * Whether to log details of usage of deprecated members or classes.
     *
     * @see CompileOptions#isDeprecation()
     * @since 10.0.0
     */
    Property<Boolean> getDeprecation();

    /**
     * Whether to log warning messages.
     *
     * @see CompileOptions#isWarnings()
     * @since 10.0.0
     */
    Property<Boolean> getWarnings();

    /**
     * The character encoding to be used when reading source files.
     *
     * @see CompileOptions#getEncoding()
     * @since 10.0.0
     */
    Property<String> getEncoding();

    /**
     * Whether to include debugging information in the generated class files.
     *
     * @see CompileOptions#isDebug()
     * @since 10.0.0
     */
    Property<Boolean> getDebug();

    /**
     * Whether to run the compiler in its own process.
     *
     * @see CompileOptions#isFork()
     * @since 10.0.0
     */
    Property<Boolean> getFork();

    /**
     * The bootstrap classpath to use for compilation.
     *
     * @see CompileOptions#getBootstrapClasspath()
     * @since 10.0.0
     */
    ConfigurableFileCollection getBootstrapClasspath();

    /**
     * The extension dirs to be used for compilation.
     *
     * @see CompileOptions#getExtensionDirs()
     * @since 10.0.0
     */
    Property<String> getExtensionDirs();

    /**
     * Any additional arguments to be passed to the compiler.
     *
     * @see CompileOptions#getCompilerArgs()
     * @since 10.0.0
     */
    ListProperty<String> getCompilerArgs();

    /**
     * Whether to use incremental compilation.
     *
     * @see CompileOptions#isIncremental()
     * @since 10.0.0
     */
    Property<Boolean> getIncremental();

    /**
     * The source path to use for the compilation.
     *
     * @see CompileOptions#getSourcepath()
     * @since 10.0.0
     */
    ConfigurableFileCollection getSourcepath();

    /**
     * The classpath to use to load annotation processors.
     *
     * @see CompileOptions#getAnnotationProcessorPath()
     * @since 10.0.0
     */
    ConfigurableFileCollection getAnnotationProcessorPath();

    /**
     * The lazy properties behind the {@link CompileOptions#getForkOptions() fork options}.
     *
     * @since 10.0.0
     */
    @LazyProperties
    ForkOptionsInputs getForkOptions();

    /**
     * The lazy properties behind the {@link CompileOptions#getDebugOptions() debug options}.
     *
     * @since 10.0.0
     */
    @LazyProperties
    DebugOptionsInputs getDebugOptions();
}
