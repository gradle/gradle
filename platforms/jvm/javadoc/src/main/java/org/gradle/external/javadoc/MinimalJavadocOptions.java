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

package org.gradle.external.javadoc;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.process.ExecSpec;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;


/**
 * Provides the core Javadoc options.
 * @since 0.7
 */
public interface MinimalJavadocOptions {
    /**
     * Returns the overview.
     *
     * @since 0.7
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    Property<String> getOverview();

    /**
     * Sets the overview.
     *
     * @since 0.7
     */
    void setOverview(@Nullable String overview);

    /**
     * Overview.
     *
     * @since 0.7
     */
    MinimalJavadocOptions overview(String overview);

    /**
     * Returns the member level.
     *
     * @since 0.7
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    Property<JavadocMemberLevel> getMemberLevel();

    /**
     * Sets the member level.
     *
     * @since 0.7
     */
    void setMemberLevel(@Nullable JavadocMemberLevel memberLevel);

    /**
     * Show from public.
     *
     * @since 0.7
     */
    MinimalJavadocOptions showFromPublic();

    /**
     * Show from protected.
     *
     * @since 0.7
     */
    MinimalJavadocOptions showFromProtected();

    /**
     * Show from package.
     *
     * @since 0.7
     */
    MinimalJavadocOptions showFromPackage();

    /**
     * Show from private.
     *
     * @since 0.7
     */
    MinimalJavadocOptions showFromPrivate();

    /**
     * Show all.
     *
     * @since 0.7
     */
    MinimalJavadocOptions showAll();

    /**
     * Returns the doclet.
     *
     * @since 0.7
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    Property<String> getDoclet();

    /**
     * Sets the doclet.
     *
     * @since 0.7
     */
    void setDoclet(@Nullable String docletClass);

    /**
     * Doclet.
     *
     * @since 0.7
     */
    MinimalJavadocOptions doclet(String docletClass);

    /**
     * Returns the docletpath.
     *
     * @since 0.9
     */
    @Classpath
    @ReplacesEagerProperty(adapter = MinimalJavadocOptionsAdapters.DocletpathAdapter.class)
    ConfigurableFileCollection getDocletpath();

    /**
     * Sets the docletpath.
     *
     * @since 0.9
     */
    void setDocletpath(List<File> docletpath);

    /**
     * Docletpath.
     *
     * @since 0.9
     */
    MinimalJavadocOptions docletpath(File... docletpath);

    /**
     * Returns the source.
     *
     * @since 0.7
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    Property<String> getSource();

    /**
     * Sets the source.
     *
     * @since 0.7
     */
    void setSource(@Nullable String source);

    /**
     * Source.
     *
     * @since 0.7
     */
    MinimalJavadocOptions source(String source);

    /**
     * Returns the classpath.
     *
     * @since 0.7
     */
    @Internal
    @ReplacesEagerProperty(adapter = MinimalJavadocOptionsAdapters.ClasspathAdapter.class)
    ConfigurableFileCollection getClasspath();

    /**
     * Sets the classpath.
     *
     * @since 0.7
     */
    void setClasspath(List<File> classpath);

    /**
     * The --module-path.
     *
     * @since 6.4
     */
    @Internal
    @ReplacesEagerProperty(adapter = MinimalJavadocOptionsAdapters.ModulePath.class)
    ConfigurableFileCollection getModulePath();

    /**
     * The --module-path.
     *
     * @since 6.4
     */
    void setModulePath(List<File> modulePath);

    /**
     * The --module-path.
     *
     * @since 6.4
     */
    MinimalJavadocOptions modulePath(List<File> classpath);

    /**
     * Classpath.
     *
     * @since 0.7
     */
    MinimalJavadocOptions classpath(List<File> classpath);

    /**
     * Classpath.
     *
     * @since 0.7
     */
    MinimalJavadocOptions classpath(File... classpath);

    /**
     * Returns the boot classpath.
     *
     * @since 0.7
     */
    @Classpath
    @ReplacesEagerProperty(adapter = MinimalJavadocOptionsAdapters.BootclasspathAdapter.class)
    ConfigurableFileCollection getBootClasspath();

    /**
     * Sets the boot classpath.
     *
     * @since 0.7
     */
    void setBootClasspath(List<File> bootClasspath);

    /**
     * Boot classpath.
     *
     * @since 0.7
     */
    MinimalJavadocOptions bootClasspath(File... bootClasspath);

    /**
     * Returns the ext dirs.
     *
     * @since 0.7
     */
    @InputFiles
    @Optional
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    @ReplacesEagerProperty(adapter = MinimalJavadocOptionsAdapters.ExtDirsAdapter.class)
    ConfigurableFileCollection getExtDirs();

    /**
     * Sets the ext dirs.
     *
     * @since 0.7
     */
    void setExtDirs(@Nullable List<File> extDirs);

    /**
     * Ext dirs.
     *
     * @since 0.7
     */
    MinimalJavadocOptions extDirs(File... extDirs);

    /**
     * Returns the output level.
     *
     * @since 0.7
     */
    @Console
    @ReplacesEagerProperty
    Property<JavadocOutputLevel> getOutputLevel();

    /**
     * Sets the output level.
     *
     * @since 0.7
     */
    void setOutputLevel(JavadocOutputLevel outputLevel);

    /**
     * Verbose.
     *
     * @since 0.7
     */
    MinimalJavadocOptions verbose();

    @Internal
    @ReplacesEagerProperty(originalType = boolean.class)
    Provider<Boolean> getVerbose();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    Provider<Boolean> getIsVerbose();

    /**
     * Quiet.
     *
     * @since 0.7
     */
    MinimalJavadocOptions quiet();

    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    Property<Boolean> getBreakIterator();

    /**
     * Sets the break iterator.
     *
     * @since 0.7
     */
    void setBreakIterator(boolean breakIterator);

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    Property<Boolean> getIsBreakIterator();

    /**
     * Break iterator.
     *
     * @since 0.7
     */
    MinimalJavadocOptions breakIterator(boolean breakIterator);

    /**
     * Break iterator.
     *
     * @since 0.7
     */
    MinimalJavadocOptions breakIterator();

    /**
     * Returns the locale.
     *
     * @since 0.7
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    Property<String> getLocale();

    /**
     * Sets the locale.
     *
     * @since 0.7
     */
    void setLocale(@Nullable String locale);

    /**
     * Locale.
     *
     * @since 0.7
     */
    MinimalJavadocOptions locale(String locale);

    /**
     * Returns the encoding.
     *
     * @since 0.7
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    Property<String> getEncoding();

    /**
     * Sets the encoding.
     *
     * @since 0.7
     */
    void setEncoding(@Nullable String encoding);

    /**
     * Encoding.
     *
     * @since 0.7
     */
    MinimalJavadocOptions encoding(String encoding);

    /**
     * Returns the j flags.
     *
     * @since 0.7
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    ListProperty<String> getJFlags();

    /**
     * Sets the j flags.
     *
     * @since 0.7
     */
    void setJFlags(@Nullable List<String> jFlags);

    /**
     * J flags.
     *
     * @since 0.7
     */
    MinimalJavadocOptions jFlags(String... jFlags);

    /**
     * Returns the option files.
     *
     * @since 0.7
     */
    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    @ReplacesEagerProperty(adapter = MinimalJavadocOptionsAdapters.OptionFilesAdapter.class)
    ConfigurableFileCollection getOptionFiles();

    /**
     * Sets the option files.
     *
     * @since 0.7
     */
    void setOptionFiles(@Nullable List<File> optionFiles);

    /**
     * Option files.
     *
     * @since 0.7
     */
    MinimalJavadocOptions optionFiles(File... argumentFiles);

    /**
     * Returns the destination directory.
     *
     * @since 0.7
     */
    @Internal
    @ReplacesEagerProperty
    DirectoryProperty getDestinationDirectory();

    /**
     * Sets the destination directory.
     *
     * @since 0.7
     */
    void setDestinationDirectory(@Nullable File destinationDirectory);

    /**
     * Destination directory.
     *
     * @since 0.7
     */
    MinimalJavadocOptions destinationDirectory(File directory);

    /**
     * Returns the window title.
     *
     * @since 0.7
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    Property<String> getWindowTitle();

    /**
     * Sets the window title.
     *
     * @since 0.7
     */
    void setWindowTitle(@Nullable String windowTitle);

    /**
     * Window title.
     *
     * @since 0.7
     */
    StandardJavadocDocletOptions windowTitle(String windowTitle);

    /**
     * Returns the header.
     *
     * @since 0.9
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    Property<String> getHeader();

    /**
     * Sets the header.
     *
     * @since 0.9
     */
    void setHeader(@Nullable String header);

    /**
     * Header.
     *
     * @since 0.9
     */
    StandardJavadocDocletOptions header(String header);

    /**
     * Write.
     *
     * @since 0.7
     */
    void write(File outputFile) throws IOException;

    /**
     * Returns the source names.
     *
     * @since 0.7
     */
    @Internal
    @ReplacesEagerProperty
    ListProperty<String> getSourceNames();

    /**
     * Sets the source names.
     *
     * @since 0.7
     */
    void setSourceNames(@Nullable List<String> sourceNames);

    /**
     * Source names.
     *
     * @since 0.7
     */
    MinimalJavadocOptions sourceNames(String... sourceNames);

    /**
     * Contribute command line options.
     *
     * @since 0.9
     */
    void contributeCommandLineOptions(ExecSpec execHandleBuilder);
}
