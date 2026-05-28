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
 */
public interface MinimalJavadocOptions {
    @Input
    @Optional
    @ReplacesEagerProperty
    Property<String> getOverview();

    void setOverview(@Nullable String overview);

    MinimalJavadocOptions overview(String overview);

    @Input
    @Optional
    @ReplacesEagerProperty
    Property<JavadocMemberLevel> getMemberLevel();

    void setMemberLevel(@Nullable JavadocMemberLevel memberLevel);

    MinimalJavadocOptions showFromPublic();

    MinimalJavadocOptions showFromProtected();

    MinimalJavadocOptions showFromPackage();

    MinimalJavadocOptions showFromPrivate();

    MinimalJavadocOptions showAll();

    @Input
    @Optional
    @ReplacesEagerProperty
    Property<String> getDoclet();

    void setDoclet(@Nullable String docletClass);

    MinimalJavadocOptions doclet(String docletClass);

    @Classpath
    @ReplacesEagerProperty(adapter = MinimalJavadocOptionsAdapters.DocletpathAdapter.class)
    ConfigurableFileCollection getDocletpath();

    void setDocletpath(List<File> docletpath);

    MinimalJavadocOptions docletpath(File... docletpath);

    @Input
    @Optional
    @ReplacesEagerProperty
    Property<String> getSource();

    void setSource(@Nullable String source);

    MinimalJavadocOptions source(String source);

    @Internal
    @ReplacesEagerProperty(adapter = MinimalJavadocOptionsAdapters.ClasspathAdapter.class)
    ConfigurableFileCollection getClasspath();

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

    MinimalJavadocOptions classpath(List<File> classpath);

    MinimalJavadocOptions classpath(File... classpath);

    @Classpath
    @ReplacesEagerProperty(adapter = MinimalJavadocOptionsAdapters.BootclasspathAdapter.class)
    ConfigurableFileCollection getBootClasspath();

    void setBootClasspath(List<File> bootClasspath);

    MinimalJavadocOptions bootClasspath(File... bootClasspath);

    @InputFiles
    @Optional
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    @ReplacesEagerProperty(adapter = MinimalJavadocOptionsAdapters.ExtDirsAdapter.class)
    ConfigurableFileCollection getExtDirs();

    void setExtDirs(@Nullable List<File> extDirs);

    MinimalJavadocOptions extDirs(File... extDirs);

    @Console
    @ReplacesEagerProperty
    Property<JavadocOutputLevel> getOutputLevel();

    void setOutputLevel(JavadocOutputLevel outputLevel);

    MinimalJavadocOptions verbose();

    @Internal
    @ReplacesEagerProperty(originalType = boolean.class)
    Provider<Boolean> getVerbose();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    Provider<Boolean> getIsVerbose();

    MinimalJavadocOptions quiet();

    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    Property<Boolean> getBreakIterator();

    void setBreakIterator(boolean breakIterator);

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    Property<Boolean> getIsBreakIterator();

    MinimalJavadocOptions breakIterator(boolean breakIterator);

    MinimalJavadocOptions breakIterator();

    @Input
    @Optional
    @ReplacesEagerProperty
    Property<String> getLocale();

    void setLocale(@Nullable String locale);

    MinimalJavadocOptions locale(String locale);

    @Input
    @Optional
    @ReplacesEagerProperty
    Property<String> getEncoding();

    void setEncoding(@Nullable String encoding);

    MinimalJavadocOptions encoding(String encoding);

    @Input
    @Optional
    @ReplacesEagerProperty
    ListProperty<String> getJFlags();

    void setJFlags(@Nullable List<String> jFlags);

    MinimalJavadocOptions jFlags(String... jFlags);

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    @ReplacesEagerProperty(adapter = MinimalJavadocOptionsAdapters.OptionFilesAdapter.class)
    ConfigurableFileCollection getOptionFiles();

    void setOptionFiles(@Nullable List<File> optionFiles);

    MinimalJavadocOptions optionFiles(File... argumentFiles);

    @Internal
    @ReplacesEagerProperty
    DirectoryProperty getDestinationDirectory();

    void setDestinationDirectory(@Nullable File destinationDirectory);

    MinimalJavadocOptions destinationDirectory(File directory);

    @Input
    @Optional
    @ReplacesEagerProperty
    Property<String> getWindowTitle();

    void setWindowTitle(@Nullable String windowTitle);

    StandardJavadocDocletOptions windowTitle(String windowTitle);

    @Input
    @Optional
    @ReplacesEagerProperty
    Property<String> getHeader();

    void setHeader(@Nullable String header);

    StandardJavadocDocletOptions header(String header);

    void write(File outputFile) throws IOException;

    @Internal
    @ReplacesEagerProperty
    ListProperty<String> getSourceNames();

    void setSourceNames(@Nullable List<String> sourceNames);

    MinimalJavadocOptions sourceNames(String... sourceNames);

    void contributeCommandLineOptions(ExecSpec execHandleBuilder);
}
