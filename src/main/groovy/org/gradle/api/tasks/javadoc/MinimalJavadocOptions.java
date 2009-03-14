package org.gradle.api.tasks.javadoc;

import java.io.File;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public interface MinimalJavadocOptions {
    String getOverview();

    void setOverview(String overview);

    MinimalJavadocOptions overview(String overview);

    JavadocMemberLevel getShow();

    void setShow(JavadocMemberLevel memberLevel);

    MinimalJavadocOptions showFromPublic();

    MinimalJavadocOptions showFromProtected();

    MinimalJavadocOptions showFromPackage();

    MinimalJavadocOptions showFromPrivate();

    MinimalJavadocOptions showAll();

    String getDocletClass();

    void setDocletClass(String docletClass);

    MinimalJavadocOptions docletClass(String docletClass);

    List<File> getDocletClasspath();

    void setDocletClasspath(List<File> docletClasspath);

    MinimalJavadocOptions docletClasspath(File ... docletClasspath);

    String getSource();

    void setSource(String source);

    MinimalJavadocOptions source(String source);

    List<File> getSourcepath();

    void setSourcepath(List<File> sourcepath);

    MinimalJavadocOptions sourcepath(List<File> sourcepath);

    MinimalJavadocOptions sourcepath(File ... sourcepath);

    List<File> getClasspath();

    void setClasspath(List<File> classpath);

    MinimalJavadocOptions classpath(List<File> classpath);

    MinimalJavadocOptions classpath(File ... classpath);

    List<String> getSubPackages();

    void setSubPackages(List<String> subPackages);

    MinimalJavadocOptions subPackages(String ... subPackages);

    List<String> getExclude();

    void setExclude(List<String> exclude);

    MinimalJavadocOptions exclude(String ... exclude);

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

    File getDirectory();

    void setDirectory(File directory);

    MinimalJavadocOptions directory(File directory);

    String getWindowTitle();

    void setWindowTitle(String windowTitle);

    StandardJavadocDocletOptions windowTitle(String windowTitle);

    void toOptionsFile(BufferedWriter javadocCommandLineWriter) throws IOException;
}
