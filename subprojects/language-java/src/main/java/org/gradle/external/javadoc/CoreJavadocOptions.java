/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.external.javadoc.internal.JavadocOptionFile;
import org.gradle.process.ExecSpec;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Provides the core Javadoc Options. That is, provides the options which are not doclet specific.
 */
public abstract class CoreJavadocOptions implements MinimalJavadocOptions {
    private final JavadocOptionFile optionFile;

    public CoreJavadocOptions() {
        this(new JavadocOptionFile());
    }

    protected CoreJavadocOptions(JavadocOptionFile optionFile) {
        this.optionFile = optionFile;

        overview = addStringOption("overview");
        memberLevel = addEnumOption("memberLevel");
        doclet = addStringOption("doclet");
        docletpath = addPathOption("docletpath");
        source = addStringOption("source");
        classpath = addPathOption("classpath");
        bootClasspath = addPathOption("bootclasspath");
        extDirs = addPathOption("extdirs");
        outputLevel = addEnumOption("outputLevel", JavadocOutputLevel.QUIET);
        breakIterator = addBooleanOption("breakiterator");
        locale = addStringOption("locale");
        encoding = addStringOption("encoding");

        sourceNames = optionFile.getSourceNames();
    }

    /**
     * -overview  path\filename
     * Specifies that javadoc should retrieve the text for the overview documentation from
     * the "source" file specified by path/filename and place it on the Overview page (overview-summary.html).
     * The path/filename is relative to the -sourcepath.
     *
     * While you can use any name you want for filename and place it anywhere you want for path,
     * a typical thing to do is to name it overview.html and place it in the source tree at the directory that contains the topmost package directories.
     * In this location, no path is needed when documenting packages, since -sourcepath will point to this file.
     * For example, if the source tree for the java.lang package is C:\src\classes\java\lang\,
     * then you could place the overview file at C:\src\classes\overview.html. See Real World Example.
     *
     * For information about the file specified by path/filename, see overview comment file.
     *
     * Note that the overview page is created only if you pass into javadoc two or more package names.
     * For further explanation, see HTML Frames.)
     *
     * The title on the overview page is set by -doctitle.
     */
    private final JavadocOptionFileOption<String> overview;

    @Override
    public String getOverview() {
        return overview.getValue();
    }

    @Override
    public void setOverview(String overview) {
        this.overview.setValue(overview);
    }

    /**
     * Fluent setter for the overview option.
     * @param overview The new overview.
     * @return The <code>MinimalJavadocOptions</code> object.
     */
    @Override
    public MinimalJavadocOptions overview(String overview) {
        setOverview(overview);
        return this;
    }

    /**
     * Switch to set the members that should be included in the Javadoc. (-public, -protected, -package, -private)
     */
    private final JavadocOptionFileOption<JavadocMemberLevel> memberLevel;

    @Override
    public JavadocMemberLevel getMemberLevel() {
        return memberLevel.getValue();
    }

    @Override
    public void setMemberLevel(JavadocMemberLevel memberLevel) {
        this.memberLevel.setValue(memberLevel);
    }

    @Override
    public MinimalJavadocOptions showFromPublic() {
        setMemberLevel(JavadocMemberLevel.PUBLIC);
        return this;
    }

    @Override
    public MinimalJavadocOptions showFromProtected() {
        setMemberLevel(JavadocMemberLevel.PROTECTED);
        return this;
    }

    @Override
    public MinimalJavadocOptions showFromPackage() {
        setMemberLevel(JavadocMemberLevel.PACKAGE);
        return this;
    }

    @Override
    public void contributeCommandLineOptions(ExecSpec execHandleBuilder) {
        execHandleBuilder
            .args(GUtil.prefix("-J", jFlags)) // J flags can not be set in the option file
            .args(GUtil.prefix("@", GFileUtils.toPaths(optionFiles))); // add additional option files
    }

    @Override
    public MinimalJavadocOptions showFromPrivate() {
        setMemberLevel(JavadocMemberLevel.PRIVATE);
        return this;
    }

    @Override
    public MinimalJavadocOptions showAll() {
        return showFromPrivate();
    }

    /**
     * -doclet  class
     * Specifies the class file that starts the doclet used in generating the documentation. Use the fully-qualified name.
     * This doclet defines the content and formats the output. If the -doclet option is not used,
     * javadoc uses the standard doclet for generating the default HTML format.
     * This class must contain the start(Root) method.
     * The path to this starting class is defined by the -docletpath option.
     *
     * For example, to call the MIF doclet, use:
     *
     *     -doclet com.sun.tools.doclets.mif.MIFDoclet
     *
     * For full, working examples of running a particular doclet, see Running the MIF Doclet.
     */
    private final JavadocOptionFileOption<String> doclet;

    @Override
    public String getDoclet() {
        return doclet.getValue();
    }

    @Override
    public void setDoclet(String doclet) {
        this.doclet.setValue(doclet);
    }

    @Override
    public MinimalJavadocOptions doclet(String doclet) {
        setDoclet(doclet);
        return this;
    }

    /**
     * -docletpath  classpathlist
     * Specifies the path to the doclet starting class file (specified with the -doclet option) and any jar files it depends on.
     * If the starting class file is in a jar file, then this specifies the path to that jar file, as shown in the example below.
     * You can specify an absolute path or a path relative to the current directory. If classpathlist contains multiple paths or jar files,
     * they should be separated with a colon (:) on Solaris and a semi-colon (;) on Windows.
     * This option is not necessary if the doclet starting class is already in the search path.
     *
     * Example of path to jar file that contains the starting doclet class file. Notice the jar filename is included.
     *
     *    -docletpath C:/user/mifdoclet/lib/mifdoclet.jar
     *
     * Example of path to starting doclet class file. Notice the class filename is omitted.
     *
     *    -docletpath C:/user/mifdoclet/classes/com/sun/tools/doclets/mif/
     *
     * For full, working examples of running a particular doclet, see Running the MIF Doclet.
     */
    private final JavadocOptionFileOption<List<File>> docletpath;

    @Override
    public List<File> getDocletpath() {
        return docletpath.getValue();
    }

    @Override
    public void setDocletpath(List<File> docletpath) {
        this.docletpath.setValue(docletpath);
    }

    @Override
    public MinimalJavadocOptions docletpath(File ... docletpath) {
        this.docletpath.getValue().addAll(Arrays.asList(docletpath));
        return this;
    }

    /**
     * -source release
     * Specifies the version of source code accepted. The following values for release are allowed:
     * 1.5  javadoc accepts code containing generics and other language features introduced in JDK 1.5.
     * The compiler defaults to the 1.5 behavior if the -source flag is not used.
     * 1.4  javadoc accepts code containing assertions, which were introduced in JDK 1.4.
     * 1.3  javadoc does not support assertions, generics, or other language features introduced after JDK 1.3.
     *
     * Use the value of release corresponding to that used when compiling the code with javac.
     */
    private final JavadocOptionFileOption<String> source; // TODO bind with the sourceCompatibility property

    @Override
    public String getSource() {
        return source.getValue();
    }

    @Override
    public void setSource(String source) {
        this.source.setValue(source);
    }

    @Override
    public MinimalJavadocOptions source(String source) {
        setSource(source);
        return this;
    }

    /**
     * -classpath  classpathlist
     * Specifies the paths where javadoc will look for referenced classes (.class files)
     * -- these are the documented classes plus any classes referenced by those classes.
     * The classpathlist can contain multiple paths by separating them with a semicolon (;).
     * The Javadoc tool will search in all subdirectories of the specified paths.
     * Follow the instructions in class path documentation for specifying classpathlist.
     *
     * If -sourcepath is omitted, the Javadoc tool uses -classpath to find the source files as well as
     * class files (for backward compatibility). Therefore, if you want to search for source and class files in separate paths,
     * use both -sourcepath and -classpath.
     *
     * For example, if you want to document com.mypackage, whose source files reside in the directory C:/user/src/com/mypackage,
     * and if this package relies on a library in C:/user/lib, you would specify:
     *
     *   C:> javadoc -classpath /user/lib -sourcepath /user/src com.mypackage
     *
     * As with other tools, if you do not specify -classpath, the Javadoc tool uses the CLASSPATH environment variable,
     * if it is set. If both are not set, the Javadoc tool searches for classes from the current directory.
     *
     * For an in-depth description of how the Javadoc tool uses -classpath to find user classes as it relates to extension classes and
     * bootstrap classes, see How Classes Are Found.
     */
    private final JavadocOptionFileOption<List<File>> classpath; // TODO link to runtime configuration ?

    @Override
    public List<File> getClasspath() {
        return classpath.getValue();
    }

    @Override
    public void setClasspath(List<File> classpath) {
        this.classpath.setValue(classpath);
    }

    @Override
    public MinimalJavadocOptions classpath(List<File> classpath) {
        this.classpath.getValue().addAll(classpath);
        return this;
    }

    @Override
    public MinimalJavadocOptions classpath(File ... classpath) {
        this.classpath.getValue().addAll(Arrays.asList(classpath));
        return this;
    }

    /**
     * -bootclasspath  classpathlist
     * Specifies the paths where the boot classes reside. These are nominally the Java platform classes.
     * The bootclasspath is part of the search path the Javadoc tool will use to look up source and class files.
     * See How Classes Are Found. for more details. Separate directories in classpathlist with semicolons (;).
     */
    private final JavadocOptionFileOption<List<File>> bootClasspath;

    @Override
    public List<File> getBootClasspath() {
        return bootClasspath.getValue();
    }

    @Override
    public void setBootClasspath(List<File> bootClasspath) {
        this.bootClasspath.setValue(bootClasspath);
    }

    @Override
    public MinimalJavadocOptions bootClasspath(File ... bootClasspath) {
        this.bootClasspath.getValue().addAll(Arrays.asList(bootClasspath));
        return this;
    }

    /**
     * -extdirs  dirlist
     * Specifies the directories where extension classes reside.
     * These are any classes that use the Java Extension mechanism.
     * The extdirs is part of the search path the Javadoc tool will use to look up source and class files.
     * See -classpath (above) for more details. Separate directories in dirlist with semicolons (;).
     */
    private final JavadocOptionFileOption<List<File>> extDirs;

    @Override
    public List<File> getExtDirs() {
        return extDirs.getValue();
    }

    @Override
    public void setExtDirs(List<File> extDirs) {
        this.extDirs.setValue(extDirs);
    }

    @Override
    public MinimalJavadocOptions extDirs(File ... extDirs) {
        this.extDirs.getValue().addAll(Arrays.asList(extDirs));
        return this;
    }

    /**
     * Control the Javadoc output level (-verbose or -quiet).
     */
    private final JavadocOptionFileOption<JavadocOutputLevel> outputLevel;

    @Override
    public JavadocOutputLevel getOutputLevel() {
        return outputLevel.getValue();
    }

    @Override
    public void setOutputLevel(JavadocOutputLevel outputLevel) {
        this.outputLevel.setValue(outputLevel);
    }

    @Override
    public MinimalJavadocOptions verbose() {
        setOutputLevel(JavadocOutputLevel.VERBOSE);
        return this;
    }

    @Override
    public boolean isVerbose() {
        return outputLevel.getValue() == JavadocOutputLevel.VERBOSE;
    }

    @Override
    public MinimalJavadocOptions quiet() {
        setOutputLevel(JavadocOutputLevel.QUIET);
        return this;
    }

    /**
     * -breakiterator
     * Uses the internationalized sentence boundary of java.text.BreakIterator to determine the end of the first sentence
     * for English (all other locales already use BreakIterator), rather than an English language, locale-specific algorithm.
     * By first sentence, we mean the first sentence in the main description of a package, class or member.
     * This sentence is copied to the package, class or member summary, and to the alphabetic index.
     *
     * From JDK 1.2 forward, the BreakIterator class is already used to determine the end of sentence for all languages but English.
     * Therefore, the -breakiterator option has no effect except for English from 1.2 forward. English has its own default algorithm:
     *
     *     * English default sentence-break algorithm - Stops at a period followed by a space or a HTML block tag, such as <P>.
     *     * Breakiterator sentence-break algorithm - In general, stops at a period,
     *       question mark or exclamation mark followed by a space if the next word starts with a capital letter.
     *       This is meant to handle most abbreviations (such as "The serial no. is valid", but won't handle "Mr. Smith").
     *       Doesn't stop at HTML tags or sentences that begin with numbers or symbols.
     *       Stops at the last period in "../filename", even if embedded in an HTML tag.
     *
     *     NOTE: We have removed from 1.5.0 the breakiterator warning messages that were in 1.4.x and
     *           have left the default sentence-break algorithm unchanged. That is, the -breakiterator option is not the default in 1.5.0,
     *           nor do we expect it to become the default. This is a reversal from our former intention that
     *           the default would change in the "next major release" (1.5.0).
     *           This means if you have not modified your source code to eliminate the breakiterator warnings in 1.4.x,
     *           then you don't have to do anything, and the warnings go away starting with 1.5.0.
     *           The reason for this reversal is because any benefit to having breakiterator become the default
     *           would be outweighed by the incompatible source change it would require.
     *           We regret any extra work and confusion this has caused.
     */
    private final JavadocOptionFileOption<Boolean> breakIterator;

    @Override
    public boolean isBreakIterator() {
        return breakIterator.getValue();
    }

    @Override
    public void setBreakIterator(boolean breakIterator) {
        this.breakIterator.setValue(breakIterator);
    }

    @Override
    public MinimalJavadocOptions breakIterator(boolean breakIterator) {
        setBreakIterator(breakIterator);
        return this;
    }

    @Override
    public MinimalJavadocOptions breakIterator() {
        setBreakIterator(true);
        return this;
    }

    /**
     * -locale  language_country_variant
     *     Important - The -locale option must be placed ahead (to the left) of any options provided by the standard doclet or
     *                 any other doclet. Otherwise, the navigation bars will appear in English.
     *                 This is the only command-line option that is order-dependent.
     *
     * Specifies the locale that javadoc uses when generating documentation.
     * The argument is the name of the locale, as described in java.util.Locale documentation, such as
     * en_US (English, United States) or en_US_WIN (Windows variant).
     *
     * Specifying a locale causes javadoc to choose the resource files of that locale for messages
     * (strings in the navigation bar, headings for lists and tables, help file contents,
     * comments in stylesheet.css, and so forth).
     * It also specifies the sorting order for lists sorted alphabetically,
     * and the sentence separator to determine the end of the first sentence.
     * It does not determine the locale of the doc comment text specified in the source files of the documented classes.
     */
    private final JavadocOptionFileOption<String> locale;

    @Override
    public String getLocale() {
        return locale.getValue();
    }

    @Override
    public void setLocale(String locale) {
        this.locale.setValue(locale);
    }

    @Override
    public MinimalJavadocOptions locale(String locale) {
        setLocale(locale);
        return this;
    }

    /**
     * -encoding  name
     * Specifies the encoding name of the source files, such as EUCJIS/SJIS. If this option is not specified, the platform default converter is used.
     *
     * Also see -docencoding and -charset.
     */
    private final JavadocOptionFileOption<String> encoding;

    @Override
    public String getEncoding() {
        return encoding.getValue();
    }

    @Override
    public void setEncoding(String encoding) {
        this.encoding.setValue(encoding);
    }

    @Override
    public MinimalJavadocOptions encoding(String encoding) {
        setEncoding(encoding);
        return this;
    }

    private final OptionLessJavadocOptionFileOption<List<String>> sourceNames;

    @Override
    public List<String> getSourceNames() {
        return sourceNames.getValue();
    }

    @Override
    public void setSourceNames(List<String> sourceNames) {
        this.sourceNames.setValue(sourceNames);
    }

    @Override
    public MinimalJavadocOptions sourceNames(String ... sourceNames) {
        this.sourceNames.getValue().addAll(Arrays.asList(sourceNames));
        return this;
    }

    /**
     * -Jflag
     * Passes flag directly to the runtime system java that runs javadoc.
     * Notice there must be no space between the J and the flag. For example,
     * if you need to ensure that the system sets aside 32 megabytes of memory in which to process the generated documentation,
     * then you would call the -Xmx option of java as follows (-Xms is optional, as it only sets the size of initial memory,
     * which is useful if you know the minimum amount of memory required):
     *
     *    C:> javadoc -J-Xmx32m -J-Xms32m com.mypackage
     *
     * To tell what version of javadoc you are using, call the "-version" option of java:
     *
     *    C:> javadoc -J-version
     *    java version "1.2"
     *    Classic VM (build JDK-1.2-V, green threads, sunwjit)
     *
     * (The version number of the standard doclet appears in its output stream.)
     */
    private List<String> jFlags = new ArrayList<String>();

    @Override
    public List<String> getJFlags() {
        return jFlags;
    }

    @Override
    public void setJFlags(List<String> jFlags) {
        this.jFlags = jFlags;
    }

    @Override
    public MinimalJavadocOptions jFlags(String ... jFlags) {
        this.jFlags.addAll(Arrays.asList(jFlags));
        return this;
    }

    private List<File> optionFiles = new ArrayList<File>();

    @Override
    public List<File> getOptionFiles() {
        return optionFiles;
    }

    @Override
    public void setOptionFiles(List<File> optionFiles) {
        this.optionFiles = optionFiles;
    }

    @Override
    public MinimalJavadocOptions optionFiles(File ... argumentFiles) {
        this.optionFiles.addAll(Arrays.asList(argumentFiles));
        return this;
    }

    @Override
    public final void write(File outputFile) throws IOException {
        optionFile.write(outputFile);
    }

    public <T> JavadocOptionFileOption<T> addOption(JavadocOptionFileOption<T> option) {
        return optionFile.addOption(option);
    }

    public JavadocOptionFileOption<String> addStringOption(String option) {
        return optionFile.addStringOption(option);
    }

    public JavadocOptionFileOption<String> addStringOption(String option, String value) {
        return optionFile.addStringOption(option, value);
    }

    public <T extends Enum<T>> JavadocOptionFileOption<T> addEnumOption(String option) {
        return optionFile.addEnumOption(option);
    }

    public <T extends Enum<T>> JavadocOptionFileOption<T> addEnumOption(String option, T value) {
        return optionFile.addEnumOption(option, value);
    }

    public JavadocOptionFileOption<List<File>> addPathOption(String option) {
        return optionFile.addPathOption(option);
    }

    public JavadocOptionFileOption<List<File>> addPathOption(String option, String joinBy) {
        return optionFile.addPathOption(option, joinBy);
    }

    public JavadocOptionFileOption<List<String>> addStringsOption(String option) {
        return optionFile.addStringsOption(option);
    }

    public JavadocOptionFileOption<List<String>> addStringsOption(String option, String joinBy) {
        return optionFile.addStringsOption(option, joinBy);
    }

   public JavadocOptionFileOption<List<String>> addMultilineStringsOption(String option) {
       return optionFile.addMultilineStringsOption(option);
   }

    public JavadocOptionFileOption<Boolean> addBooleanOption(String option) {
        return optionFile.addBooleanOption(option);
    }

    public JavadocOptionFileOption<Boolean> addBooleanOption(String option, boolean value) {
        return optionFile.addBooleanOption(option, value);
    }

    public JavadocOptionFileOption<File> addFileOption(String option) {
        return optionFile.addFileOption(option);
    }

    public JavadocOptionFileOption<File> addFileOption(String option, File value) {
        return optionFile.addFileOption(option, value);
    }
}
