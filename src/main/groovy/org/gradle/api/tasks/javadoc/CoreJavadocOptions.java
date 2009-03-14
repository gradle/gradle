package org.gradle.api.tasks.javadoc;

import java.io.File;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

/**
 * 
 *
 *
 * @author Tom Eyckmans
 */
public abstract class CoreJavadocOptions implements MinimalJavadocOptions
{
    public static final String OVERVIEW = "overview";
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
    private String overview = null;

    public String getOverview() {
        return overview;
    }

    public void setOverview(String overview) {
        this.overview = overview;
    }

    public MinimalJavadocOptions overview(String overview) {
        setOverview(overview);
        return this;
    }

    void writeOverview(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( overview != null ) {
            writeValueOption(OVERVIEW, overview, javadocOptionFileWriter);
        }
    }

    /**
     * Switch to set the members that should be included in the Javadoc. (-public, -protected, -package, -private)
     */
    private JavadocMemberLevel memberLevel = null;

    public JavadocMemberLevel getMemberLevel() {
        return memberLevel;
    }

    public void setMemberLevel(JavadocMemberLevel memberLevel) {
        this.memberLevel = memberLevel;
    }

    public MinimalJavadocOptions showFromPublic() {
        setMemberLevel(JavadocMemberLevel.PUBLIC);
        return this;
    }

    public MinimalJavadocOptions showFromProtected() {
        setMemberLevel(JavadocMemberLevel.PROTECTED);
        return this;
    }

    public MinimalJavadocOptions showFromPackage() {
        setMemberLevel(JavadocMemberLevel.PACKAGE);
        return this;
    }

    public MinimalJavadocOptions showFromPackaged() {
        return showFromPackage();
    }

    public MinimalJavadocOptions showFromPrivate() {
        setMemberLevel(JavadocMemberLevel.PRIVATE);
        return this;
    }

    public MinimalJavadocOptions showAll() {
        return showFromPrivate();
    }

    void writeMemberLevel(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( memberLevel != null ) {
            writeOption(memberLevel.toString().toLowerCase(), javadocOptionFileWriter);
        }
    }

    public static final String DOCLET = "doclet";
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
    private String doclet = null;

    public String getDoclet() {
        return doclet;
    }

    public void setDoclet(String doclet) {
        this.doclet = doclet;
    }

    public MinimalJavadocOptions doclet(String doclet) {
        setDoclet(doclet);
        return this;
    }

    void writeDoclet(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( doclet != null ) {
            writeValueOption(DOCLET, doclet, javadocOptionFileWriter);
        }
    }

    public static final String DOCLETPATH = "docletpath";
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
    private List<File> docletClasspath = new ArrayList<File>();

    public List<File> getDocletClasspath() {
        return docletClasspath;
    }

    public void setDocletClasspath(List<File> docletClasspath) {
        this.docletClasspath = docletClasspath;
    }

    public MinimalJavadocOptions docletClasspath(File ... docletClasspath) {
        this.docletClasspath.addAll(Arrays.asList(docletClasspath));
        return this;
    }

    void writeDocletClasspath(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( docletClasspath != null && !docletClasspath.isEmpty() ) {
            writePathOption(DOCLETPATH, docletClasspath, System.getProperty("path.separator"), javadocOptionFileWriter);
        }
    }

    public static final String SOURCE = "source";
    /**
     * -source release
     * Specifies the version of source code accepted. The following values for release are allowed:
     * 1.5 	javadoc accepts code containing generics and other language features introduced in JDK 1.5.
     * The compiler defaults to the 1.5 behavior if the -source flag is not used.
     * 1.4 	javadoc accepts code containing assertions, which were introduced in JDK 1.4.
     * 1.3 	javadoc does not support assertions, generics, or other language features introduced after JDK 1.3.
     *
     * Use the value of release corresponding to that used when compiling the code with javac.
     */
    private String source = null;// TODO bind with the sourceCompatibility property

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public MinimalJavadocOptions source(String source) {
        setSource(source);
        return this;
    }

    void writeSource(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( source != null ) {
            writeValueOption(SOURCE, source, javadocOptionFileWriter);
        }
    }

    public static final String SOURCEPATH = "sourcepath";
    /**
     * -sourcepath  sourcepathlist
     *     Specifies the search paths for finding source files (.java) when passing package names or -subpackages into the javadoc command.
     * The sourcepathlist can contain multiple paths by separating them with a semicolon (;).
     * The Javadoc tool will search in all subdirectories of the specified paths.
     * Note that this option is not only used to locate the source files being documented,
     * but also to find source files that are not being documented but whose comments are inherited by the source files being documented.
     *
     *     Note that you can use the -sourcepath option only when passing package names into the javadoc command
     * -- it will not locate .java files passed into the javadoc command.
     * (To locate .java files, cd to that directory or include the path ahead of each file,
     * as shown at Documenting One or More Classes.) If -sourcepath is omitted,
     * javadoc uses the class path to find the source files (see -classpath).
     * Therefore, the default -sourcepath is the value of class path.
     * If -classpath is omitted and you are passing package names into javadoc,
     * it looks in the current directory (and subdirectories) for the source files.
     *
     *     Set sourcepathlist to the root directory of the source tree for the package you are documenting.
     * For example, suppose you want to document a package called com.mypackage whose source files are located at:
     *
     *     C:/user/src/com/mypackage/*.java
     *
     *     In this case you would specify the sourcepath to C:/user/src, the directory that contains com\mypackage,
     * and then supply the package name com.mypackage:
     *
     *       C:> javadoc -sourcepath C:/user/src com.mypackage
     *
     *     This is easy to remember by noticing that if you concatenate the value of sourcepath and
     * the package name together and change the dot to a backslash "\", you end up with the full path to the package: C:/user/src/com/mypackage.
     *
     *     To point to two source paths:
     *
     *       C:> javadoc -sourcepath C:/user1/src;C:/user2/src com.mypackage
     *
     */
    private List<File> sourcepath = new ArrayList<File>();// TODO bind with the srcDirs

    public List<File> getSourcepath() {
        return sourcepath;
    }

    public void setSourcepath(List<File> sourcepath) {
        this.sourcepath = sourcepath;
    }

    public MinimalJavadocOptions sourcepath(List<File> sourcepath) {
        setSourcepath(sourcepath);
        return this;
    }

    public MinimalJavadocOptions sourcepath(File ... sourcepath) {
        this.sourcepath.addAll(Arrays.asList(sourcepath));
        return this;
    }

    void writeSourcepath(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( sourcepath != null && !sourcepath.isEmpty() ) {
            writePathOption(SOURCEPATH, sourcepath, System.getProperty("path.separator"), javadocOptionFileWriter);
        }
    }

    public static final String CLASSPATH = "classpath";
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
    private List<File> classpath = new ArrayList<File>();// TODO link to runtime configuration ?

    public List<File> getClasspath() {
        return classpath;
    }

    public void setClasspath(List<File> classpath) {
        this.classpath = classpath;
    }

    public MinimalJavadocOptions classpath(List<File> classpath) {
        this.classpath.addAll(classpath);
        return this;
    }

    public MinimalJavadocOptions classpath(File ... classpath) {
        this.classpath.addAll(Arrays.asList(classpath));
        return this;
    }

    void writeClasspath(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( classpath != null && !classpath.isEmpty() ) {
            writePathOption(CLASSPATH, classpath, System.getProperty("path.separator"), javadocOptionFileWriter);
        }
    }

    public static final String SUBPACKAGES = "subpackages";
    /**
     * -subpackages  package1:package2:...
     * Generates documentation from source files in the specified packages and recursively in their subpackages.
     * This option is useful when adding new subpackages to the source code, as they are automatically included.
     * Each package argument is any top-level subpackage (such as java) or fully qualified package (such as javax.swing)
     * that does not need to contain source files. Arguments are separated by colons (on all operating systmes).
     * Wildcards are not needed or allowed.
     * Use -sourcepath to specify where to find the packages.
     * This option is smart about not processing source files that are in the source tree but do not belong to the packages,
     * as described at processing of source files.
     *
     * For example:
     *
     *   C:> javadoc -d docs -sourcepath C:/user/src -subpackages java:javax.swing
     *
     * This command generates documentation for packages named "java" and "javax.swing" and all their subpackages.
     *
     * You can use -subpackages in conjunction with -exclude to exclude specific packages.
     */
    private List<String> subPackages = new ArrayList<String>();

    public List<String> getSubPackages() {
        return subPackages;
    }

    public void setSubPackages(List<String> subPackages) {
        this.subPackages = subPackages;
    }

    public MinimalJavadocOptions subPackages(String ... subPackages) {
        this.subPackages.addAll(Arrays.asList(subPackages));
        return this;
    }

    void writeSubPackages(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( subPackages != null && !subPackages.isEmpty() ) {
            writeValuesOption(SUBPACKAGES, subPackages, ";", javadocOptionFileWriter);
        }
    }

    public static final String EXCLUDE = "exclude";
    /**
     * -exclude  packagename1:packagename2:...
     * Unconditionally excludes the specified packages and their subpackages from the list formed by -subpackages.
     * It excludes those packages even if they would otherwise be included by some previous or later -subpackages option.
     *
     * For example:
     *
     *   C:> javadoc -sourcepath C:/user/src -subpackages java -exclude java.net:java.lang
     *
     * would include java.io, java.util, and java.math (among others),
     * but would exclude packages rooted at java.net and java.lang.
     * Notice this excludes java.lang.ref, a subpackage of java.lang).
     */
    private List<String> exclude = new ArrayList<String>();

    public List<String> getExclude() {
        return exclude;
    }

    public void setExclude(List<String> exclude) {
        this.exclude = exclude;
    }

    public MinimalJavadocOptions exclude(String ... exclude) {
        this.exclude.addAll(Arrays.asList(exclude));
        return this;
    }

    void writeExclude(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( exclude != null && !exclude.isEmpty() ) {
            writeValuesOption(EXCLUDE, exclude, ":", javadocOptionFileWriter);
        }
    }

    public static final String BOOTCLASSPATH = "bootclasspath";
    /**
     * -bootclasspath  classpathlist
     * Specifies the paths where the boot classes reside. These are nominally the Java platform classes.
     * The bootclasspath is part of the search path the Javadoc tool will use to look up source and class files.
     * See How Classes Are Found. for more details. Separate directories in classpathlist with semicolons (;).
     */
    private List<File> bootClasspath = new ArrayList<File>();

    public List<File> getBootClasspath() {
        return bootClasspath;
    }

    public void setBootClasspath(List<File> bootClasspath) {
        this.bootClasspath = bootClasspath;
    }

    public MinimalJavadocOptions bootClasspath(File ... bootClasspath) {
        this.bootClasspath.addAll(Arrays.asList(bootClasspath));
        return this;
    }

    void writeBootClasspath(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( bootClasspath != null && !bootClasspath.isEmpty() ) {
            writePathOption(BOOTCLASSPATH, bootClasspath, System.getProperty("path.separator"), javadocOptionFileWriter);
        }
    }

    public static final String EXTDIRS = "extdirs";
    /**
     * -extdirs  dirlist
     * Specifies the directories where extension classes reside.
     * These are any classes that use the Java Extension mechanism.
     * The extdirs is part of the search path the Javadoc tool will use to look up source and class files.
     * See -classpath (above) for more details. Separate directories in dirlist with semicolons (;).
     */
    private List<File> extDirs = new ArrayList<File>();

    public List<File> getExtDirs() {
        return extDirs;
    }

    public void setExtDirs(List<File> extDirs) {
        this.extDirs = extDirs;
    }

    public MinimalJavadocOptions extDirs(File ... extDirs) {
        this.extDirs.addAll(Arrays.asList(extDirs));
        return this;
    }

    void writeExtDirs(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( extDirs != null && !extDirs.isEmpty() ) {
            writePathOption(EXTDIRS, extDirs, System.getProperty("path.separator"), javadocOptionFileWriter);
        }
    }

    /**
     * Control the Javadoc output level (-verbose or -quiet
     */
    private JavadocOutputLevel outputLevel = JavadocOutputLevel.QUIET;

    public JavadocOutputLevel getOutputLevel() {
        return outputLevel;
    }

    public void setOutputLevel(JavadocOutputLevel outputLevel) {
        this.outputLevel = outputLevel;
    }

    public MinimalJavadocOptions verbose() {
        setOutputLevel(JavadocOutputLevel.VERBOSE);
        return this;
    }

    public boolean isVerbose() {
        return outputLevel == JavadocOutputLevel.VERBOSE;
    }

    public MinimalJavadocOptions quiet() {
        setOutputLevel(JavadocOutputLevel.QUIET);
        return this;
    }

    void writeOutputLevel(BufferedWriter javadocOptionFileWriter) throws IOException {
        writeOption(outputLevel.toString().toLowerCase(), javadocOptionFileWriter);
    }

    public static final String BREAKITERATOR = "breakiterator";
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
    private boolean breakIterator = false;

    public boolean isBreakIterator() {
        return breakIterator;
    }

    public void setBreakIterator(boolean breakIterator) {
        this.breakIterator = breakIterator;
    }

    public MinimalJavadocOptions breakIterator(boolean breakIterator) {
        setBreakIterator(breakIterator);
        return this;
    }

    public MinimalJavadocOptions breakIterator() {
        setBreakIterator(true);
        return this;
    }

    void writeBreakIterator(BufferedWriter javadocOptionFileWriter) throws IOException {
        if (breakIterator) {
            writeOption(BREAKITERATOR, javadocOptionFileWriter);
        }
    }

    public static final String LOCALE = "locale";
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
    private String locale = null;

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public MinimalJavadocOptions locale(String locale) {
        setLocale(locale);
        return this;
    }

    void writeLocale(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( locale != null ) {
            writeValueOption(LOCALE, locale, javadocOptionFileWriter);
        }
    }

    public static final String ENCODING = "encoding";
    /**
     * -encoding  name
     * Specifies the encoding name of the source files, such as EUCJIS/SJIS. If this option is not specified, the platform default converter is used.
     *
     * Also see -docencoding and -charset.
     */
    private String encoding = null;

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public MinimalJavadocOptions encoding(String encoding) {
        setEncoding(encoding);
        return this;
    }

    void writeEncoding(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( encoding != null ) {
            writeValueOption(ENCODING, encoding, javadocOptionFileWriter);
        }
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

    public List<String> getJFlags() {
        return jFlags;
    }

    public void setJFlags(List<String> jFlags) {
        this.jFlags = jFlags;
    }

    public MinimalJavadocOptions jFlags(String ... jFlags) {
        this.jFlags.addAll(Arrays.asList(jFlags));
        return this;
    }

    private List<String> packageNames = new ArrayList<String>();

    public List<String> getPackageNames() {
        return packageNames;
    }

    public void setPackageNames(List<String> packageNames) {
        this.packageNames = packageNames;
    }

    public MinimalJavadocOptions packageNames(String ... packageNames) {
        this.packageNames.addAll(Arrays.asList(packageNames));
        return this;
    }

    void writePackageNames(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( packageNames != null && !packageNames.isEmpty() ) {
            for ( String packageName : packageNames ) {
                javadocOptionFileWriter.write(packageName);
                javadocOptionFileWriter.newLine();
            }
        }
    }

    private List<String> sourceNames = new ArrayList<String>();

    public List<String> getSourceNames() {
        return sourceNames;
    }

    public void setSourceNames(List<String> sourceNames) {
        this.sourceNames = sourceNames;
    }

    public MinimalJavadocOptions sourceNames(String ... sourceNames) {
        this.sourceNames.addAll(Arrays.asList(sourceNames));
        return this;
    }

    void writeSourceNames(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( sourceNames != null && !sourceNames.isEmpty() ) {
            for ( String packageName : sourceNames ) {
                javadocOptionFileWriter.write(packageName);
                javadocOptionFileWriter.newLine();
            }
        }
    }

    private List<File> optionFiles = new ArrayList<File>();

    public List<File> getOptionFiles() {
        return optionFiles;
    }

    public void setOptionFiles(List<File> optionFiles) {
        this.optionFiles = optionFiles;
    }

    public MinimalJavadocOptions optionFiles(File ... argumentFiles) {
        this.optionFiles.addAll(Arrays.asList(argumentFiles));
        return this;
    }

    void writeOptionFiles(BufferedWriter javadocOptionFileWriter) throws IOException {
        if ( optionFiles != null && !optionFiles.isEmpty() ) {
            for ( File optionFile : optionFiles ) {
                writeOptionFileLink(optionFile, javadocOptionFileWriter);
                javadocOptionFileWriter.newLine();
            }
        }
    }

    public void toOptionsFile(BufferedWriter javadocOptionFileWriter) throws IOException {
        writeOverview(javadocOptionFileWriter);
        writeMemberLevel(javadocOptionFileWriter);
        writeDoclet(javadocOptionFileWriter);
        writeDocletClasspath(javadocOptionFileWriter);
        writeSource(javadocOptionFileWriter);
        writeSourcepath(javadocOptionFileWriter);
        writeClasspath(javadocOptionFileWriter);
        writeSubPackages(javadocOptionFileWriter);
        writeExclude(javadocOptionFileWriter);
        writeBootClasspath(javadocOptionFileWriter);
        writeExtDirs(javadocOptionFileWriter);
        writeOutputLevel(javadocOptionFileWriter);
        writeBreakIterator(javadocOptionFileWriter);
        writeLocale(javadocOptionFileWriter);
        writeEncoding(javadocOptionFileWriter);
        writeOptionFiles(javadocOptionFileWriter);
        writePackageNames(javadocOptionFileWriter);
        writeSourceNames(javadocOptionFileWriter);
    }

    protected void writeOptionHeader(String option, BufferedWriter javadocOptionFileWriter) throws IOException {
        javadocOptionFileWriter.write("-");
        javadocOptionFileWriter.write(option);
        javadocOptionFileWriter.write(" ");
    }

    protected void writeOption(String option, BufferedWriter javadocOptionFileWriter) throws IOException {
        writeOptionHeader(option, javadocOptionFileWriter);
        javadocOptionFileWriter.newLine();
    }

    protected void writeValueOption(String option, String value, BufferedWriter javadocOptionFileWriter) throws IOException {
        writeOptionHeader(option, javadocOptionFileWriter);
        javadocOptionFileWriter.write(value);
        javadocOptionFileWriter.newLine();
    }

    protected void writeValueOption(String option, File optionFile, BufferedWriter javadocOptionFileWriter) throws IOException {
        writeOptionHeader(option, javadocOptionFileWriter);
        writeOptionFileLink(optionFile, javadocOptionFileWriter);
        javadocOptionFileWriter.newLine();
    }

    protected void writeValueOption(String option, Collection<String> values, BufferedWriter javadocOptionFileWriter) throws IOException {
        for ( final String value : values ) {
            writeValueOption(option, value, javadocOptionFileWriter);
        }
    }

    protected void writeValuesOption(String option, Collection<String> values, String joinValuesBy, BufferedWriter javadocOptionFileWriter) throws IOException {
        writeOptionHeader(option, javadocOptionFileWriter);
        final Iterator<String> valuesIt = values.iterator();
        while ( valuesIt.hasNext() ) {
            javadocOptionFileWriter.write(valuesIt.next());
            if ( valuesIt.hasNext() )
                javadocOptionFileWriter.write(joinValuesBy);
        }
        javadocOptionFileWriter.newLine();
    }

    protected void writePathOption(String option, Collection<File> files, String joinValuesBy, BufferedWriter javadocOptionFileWriter) throws IOException {
        writeOptionHeader(option, javadocOptionFileWriter);
        final Iterator<File> filesIt = files.iterator();
        while ( filesIt.hasNext() ) {
            javadocOptionFileWriter.write(filesIt.next().getAbsolutePath());
            if ( filesIt.hasNext() )
                javadocOptionFileWriter.write(joinValuesBy);
        }
        javadocOptionFileWriter.newLine();
    }

    protected void writeOptionFileLink(File optionFile, BufferedWriter javadocOptionFileWriter) throws IOException {
        javadocOptionFileWriter.write("@");
        javadocOptionFileWriter.write(optionFile.getAbsolutePath());
    }
}
