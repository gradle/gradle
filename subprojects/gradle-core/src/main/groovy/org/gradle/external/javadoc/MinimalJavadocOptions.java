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

import org.gradle.util.exec.ExecHandleBuilder;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public interface MinimalJavadocOptions {
    String getOverview();

    void setOverview(String overview);

    MinimalJavadocOptions overview(String overview);

    JavadocMemberLevel getMemberLevel();

    void setMemberLevel(JavadocMemberLevel memberLevel);

    MinimalJavadocOptions showFromPublic();

    MinimalJavadocOptions showFromProtected();

    MinimalJavadocOptions showFromPackage();

    MinimalJavadocOptions showFromPrivate();

    MinimalJavadocOptions showAll();

    String getDoclet();

    void setDoclet(String docletClass);

    MinimalJavadocOptions doclet(String docletClass);

    List<File> getDocletClasspath();

    void setDocletClasspath(List<File> docletClasspath);

    MinimalJavadocOptions docletClasspath(File ... docletClasspath);

    String getSource();

    void setSource(String source);

    MinimalJavadocOptions source(String source);

    List<File> getClasspath();

    void setClasspath(List<File> classpath);

    MinimalJavadocOptions classpath(List<File> classpath);

    MinimalJavadocOptions classpath(File ... classpath);

    List<File> getBootClasspath();

    void setBootClasspath(List<File> bootClasspath);

    MinimalJavadocOptions bootClasspath(File ... bootClasspath);

    List<File> getExtDirs();

    void setExtDirs(List<File> extDirs);

    MinimalJavadocOptions extDirs(File ... extDirs);

    JavadocOutputLevel getOutputLevel();

    void setOutputLevel(JavadocOutputLevel outputLevel);

    MinimalJavadocOptions verbose();

    boolean isVerbose();

    MinimalJavadocOptions quiet();

    boolean isBreakIterator();

    void setBreakIterator(boolean breakIterator);

    MinimalJavadocOptions breakIterator(boolean breakIterator);

    MinimalJavadocOptions breakIterator();

    String getLocale();

    void setLocale(String locale);

    MinimalJavadocOptions locale(String locale);

    String getEncoding();

    void setEncoding(String encoding);

    MinimalJavadocOptions encoding(String encoding);

    List<String> getJFlags();

    void setJFlags(List<String> jFlags);

    MinimalJavadocOptions jFlags(String ... jFlags);

    List<File> getOptionFiles();

    void setOptionFiles(List<File> optionFiles);

    MinimalJavadocOptions optionFiles(File ... argumentFiles);

    File getDestinationDirectory();

    void setDestinationDirectory(File directory);

    MinimalJavadocOptions destinationDirectory(File directory);

    String getWindowTitle();

    void setWindowTitle(String windowTitle);

    StandardJavadocDocletOptions windowTitle(String windowTitle);

    void write(File outputFile) throws IOException;

    List<String> getSourceNames();

    void setSourceNames(List<String> sourceNames);

    MinimalJavadocOptions sourceNames(String ... sourceNames);

    void contributeCommandLineOptions(ExecHandleBuilder execHandleBuilder);
}
