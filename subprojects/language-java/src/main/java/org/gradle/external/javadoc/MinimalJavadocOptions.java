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

import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.process.ExecSpec;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Provides the core Javadoc options.
 */
public interface MinimalJavadocOptions {
    @Input @Optional
    String getOverview();

    void setOverview(String overview);

    MinimalJavadocOptions overview(String overview);

    @Input @Optional
    JavadocMemberLevel getMemberLevel();

    void setMemberLevel(JavadocMemberLevel memberLevel);

    MinimalJavadocOptions showFromPublic();

    MinimalJavadocOptions showFromProtected();

    MinimalJavadocOptions showFromPackage();

    MinimalJavadocOptions showFromPrivate();

    MinimalJavadocOptions showAll();

    @Input @Optional
    String getDoclet();

    void setDoclet(String docletClass);

    MinimalJavadocOptions doclet(String docletClass);

    @Classpath
    List<File> getDocletpath();

    void setDocletpath(List<File> docletpath);

    MinimalJavadocOptions docletpath(File ... docletpath);

    @Input @Optional
    String getSource();

    void setSource(String source);

    MinimalJavadocOptions source(String source);

    @Internal
    List<File> getClasspath();

    void setClasspath(List<File> classpath);

    MinimalJavadocOptions classpath(List<File> classpath);

    MinimalJavadocOptions classpath(File ... classpath);

    @Classpath
    List<File> getBootClasspath();

    void setBootClasspath(List<File> bootClasspath);

    MinimalJavadocOptions bootClasspath(File ... bootClasspath);

    @Optional @InputFiles
    List<File> getExtDirs();

    void setExtDirs(List<File> extDirs);

    MinimalJavadocOptions extDirs(File ... extDirs);

    @Console
    JavadocOutputLevel getOutputLevel();

    void setOutputLevel(JavadocOutputLevel outputLevel);

    MinimalJavadocOptions verbose();

    @Internal
    boolean isVerbose();

    MinimalJavadocOptions quiet();

    @Input
    boolean isBreakIterator();

    void setBreakIterator(boolean breakIterator);

    MinimalJavadocOptions breakIterator(boolean breakIterator);

    MinimalJavadocOptions breakIterator();

    @Input @Optional
    String getLocale();

    void setLocale(String locale);

    MinimalJavadocOptions locale(String locale);

    @Input @Optional
    String getEncoding();

    void setEncoding(String encoding);

    MinimalJavadocOptions encoding(String encoding);

    @Optional @Input
    List<String> getJFlags();

    void setJFlags(List<String> jFlags);

    MinimalJavadocOptions jFlags(String ... jFlags);

    @Optional @InputFiles
    List<File> getOptionFiles();

    void setOptionFiles(List<File> optionFiles);

    MinimalJavadocOptions optionFiles(File ... argumentFiles);

    @Internal
    File getDestinationDirectory();

    void setDestinationDirectory(File directory);

    MinimalJavadocOptions destinationDirectory(File directory);

    @Input @Optional
    String getWindowTitle();

    void setWindowTitle(String windowTitle);

    StandardJavadocDocletOptions windowTitle(String windowTitle);

    @Input @Optional
    String getHeader();

    void setHeader(String header);

    StandardJavadocDocletOptions header(String header);

    void write(File outputFile) throws IOException;

    @Internal
    List<String> getSourceNames();

    void setSourceNames(List<String> sourceNames);

    MinimalJavadocOptions sourceNames(String ... sourceNames);

    void contributeCommandLineOptions(ExecSpec execHandleBuilder);
}
