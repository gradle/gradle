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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.external.javadoc.internal.GroupsJavadocOptionFileOption;
import org.gradle.external.javadoc.internal.JavadocOptionFile;
import org.gradle.external.javadoc.internal.LinksOfflineJavadocOptionFileOption;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.gradle.api.tasks.PathSensitivity.NAME_ONLY;

/**
 * Provides the options for the standard Javadoc doclet.
 */
public class StandardJavadocDocletOptions extends CoreJavadocOptions implements MinimalJavadocOptions {

    private final JavadocOptionFileOption<File> destinationDirectory;
    private final JavadocOptionFileOption<Boolean> use;
    private final JavadocOptionFileOption<Boolean> version;
    private final JavadocOptionFileOption<Boolean> author;
    private final JavadocOptionFileOption<Boolean> splitIndex;
    private final JavadocOptionFileOption<String> windowTitle;
    private final JavadocOptionFileOption<String> header;
    private final JavadocOptionFileOption<String> docTitle;
    private final JavadocOptionFileOption<String> footer;
    private final JavadocOptionFileOption<String> bottom;
    private final JavadocOptionFileOption<List<String>> links;
    private final JavadocOptionFileOption<List<JavadocOfflineLink>> linksOffline;
    private final JavadocOptionFileOption<Boolean> linkSource;
    private final JavadocOptionFileOption<Map<String, List<String>>> groups;
    private final JavadocOptionFileOption<Boolean> noDeprecated;
    private final JavadocOptionFileOption<Boolean> noDeprecatedList;
    private final JavadocOptionFileOption<Boolean> noSince;
    private final JavadocOptionFileOption<Boolean> noTree;
    private final JavadocOptionFileOption<Boolean> noIndex;
    private final JavadocOptionFileOption<Boolean> noHelp;
    private final JavadocOptionFileOption<Boolean> noNavBar;
    private final JavadocOptionFileOption<File> helpFile;
    private final JavadocOptionFileOption<File> stylesheetFile;
    private final JavadocOptionFileOption<Boolean> serialWarn;
    private final JavadocOptionFileOption<String> charSet;
    private final JavadocOptionFileOption<String> docEncoding;
    private final JavadocOptionFileOption<Boolean> keyWords;
    private final JavadocOptionFileOption<List<String>> tags;
    private final JavadocOptionFileOption<List<String>> taglets;
    private final JavadocOptionFileOption<List<File>> tagletPath;
    private final JavadocOptionFileOption<Boolean> docFilesSubDirs;
    private final JavadocOptionFileOption<List<String>> excludeDocFilesSubDir;
    private final JavadocOptionFileOption<List<String>> noQualifiers;
    public final JavadocOptionFileOption<Boolean> noTimestamp;
    private final JavadocOptionFileOption<Boolean> noComment;

    public StandardJavadocDocletOptions() {
        this(new JavadocOptionFile());
    }

    public StandardJavadocDocletOptions(JavadocOptionFile javadocOptionFile) {
        super(javadocOptionFile);

        destinationDirectory = addFileOption("d");
        use = addBooleanOption("use");
        version = addBooleanOption("version");
        author = addBooleanOption("author");
        splitIndex = addBooleanOption("splitindex");
        header = addStringOption("header");
        windowTitle = addStringOption("windowtitle");
        docTitle = addStringOption("doctitle");
        footer = addStringOption("footer");
        bottom = addStringOption("bottom");
        links = addMultilineStringsOption("link");
        linksOffline = addOption(new LinksOfflineJavadocOptionFileOption("linkoffline", Lists.<JavadocOfflineLink>newArrayList()));
        linkSource = addBooleanOption("linksource");
        groups = addOption(new GroupsJavadocOptionFileOption("group", Maps.<String, List<String>>newLinkedHashMap()));
        noDeprecated = addBooleanOption("nodeprecated");
        noDeprecatedList = addBooleanOption("nodeprecatedlist");
        noSince = addBooleanOption("nosince");
        noTree = addBooleanOption("notree");
        noIndex = addBooleanOption("noindex");
        noHelp = addBooleanOption("nohelp");
        noNavBar = addBooleanOption("nonavbar");
        helpFile = addFileOption("helpfile");
        stylesheetFile = addFileOption("stylesheetfile");
        serialWarn = addBooleanOption("serialwarn");
        charSet = addStringOption("charset");
        docEncoding = addStringOption("docencoding");
        keyWords = addBooleanOption("keywords");
        tags = addMultilineStringsOption("tag");
        taglets = addMultilineStringsOption("taglet");
        tagletPath = addPathOption("tagletpath");
        docFilesSubDirs = addBooleanOption("docfilessubdirs");
        excludeDocFilesSubDir = addStringsOption("excludedocfilessubdir", ":");
        noQualifiers = addStringsOption("noqualifier", ":");
        noTimestamp = addBooleanOption("notimestamp");
        noComment = addBooleanOption("nocomment");
    }

    public StandardJavadocDocletOptions(StandardJavadocDocletOptions original) {
        this(original, new JavadocOptionFile(original.optionFile));
    }

    public StandardJavadocDocletOptions(StandardJavadocDocletOptions original, JavadocOptionFile optionFile) {
        super(original, optionFile);

        destinationDirectory = optionFile.getOption("d");
        use = optionFile.getOption("use");
        version = optionFile.getOption("version");
        author = optionFile.getOption("author");
        splitIndex = optionFile.getOption("splitindex");
        header = optionFile.getOption("header");
        windowTitle = optionFile.getOption("windowtitle");
        docTitle = optionFile.getOption("doctitle");
        footer = optionFile.getOption("footer");
        bottom = optionFile.getOption("bottom");
        links = optionFile.getOption("link");
        linksOffline = optionFile.getOption("linkoffline");
        linkSource = optionFile.getOption("linksource");
        groups = optionFile.getOption("group");
        noDeprecated = optionFile.getOption("nodeprecated");
        noDeprecatedList = optionFile.getOption("nodeprecatedlist");
        noSince = optionFile.getOption("nosince");
        noTree = optionFile.getOption("notree");
        noIndex = optionFile.getOption("noindex");
        noHelp = optionFile.getOption("nohelp");
        noNavBar = optionFile.getOption("nonavbar");
        helpFile = optionFile.getOption("helpfile");
        stylesheetFile = optionFile.getOption("stylesheetfile");
        serialWarn = optionFile.getOption("serialwarn");
        charSet = optionFile.getOption("charset");
        docEncoding = optionFile.getOption("docencoding");
        keyWords = optionFile.getOption("keywords");
        tags = optionFile.getOption("tag");
        taglets = optionFile.getOption("taglet");
        tagletPath = optionFile.getOption("tagletpath");
        docFilesSubDirs = optionFile.getOption("docfilessubdirs");
        excludeDocFilesSubDir = optionFile.getOption("excludedocfilessubdir");
        noQualifiers = optionFile.getOption("noqualifier");
        noTimestamp = optionFile.getOption("notimestamp");
        noComment = optionFile.getOption("nocomment");
    }

    public StandardJavadocDocletOptions(MinimalJavadocOptions original) {
        this();

        setOverview(original.getOverview());
        setMemberLevel(original.getMemberLevel());
        setDoclet(original.getDoclet());
        setDocletpath(copyOrNull(original.getDocletpath()));
        setSource(original.getSource());
        setClasspath(copyOrNull(original.getClasspath()));
        setBootClasspath(copyOrNull(original.getBootClasspath()));
        setExtDirs(copyOrNull(original.getExtDirs()));
        setOutputLevel(original.getOutputLevel());
        setBreakIterator(original.isBreakIterator());
        setLocale(original.getLocale());
        setEncoding(original.getEncoding());
        setJFlags(copyOrNull(original.getJFlags()));
        setOptionFiles(copyOrNull(original.getOptionFiles()));
        setDestinationDirectory(original.getDestinationDirectory());
        setWindowTitle(original.getWindowTitle());
        setHeader(original.getHeader());
        setSourceNames(copyOrNull(original.getSourceNames()));
    }

    private static <T> List<T> copyOrNull(List<T> items) {
        if (items == null) {
            return null;
        } else {
            return Lists.newArrayList(items);
        }
    }

    /**
     * -d  directory
     * <p>
     * Specifies the destination directory where javadoc saves the generated HTML files. (The "d" means "destination.")
     * Omitting this option causes the files to be saved to the current directory.
     * The value directory can be absolute, or relative to the current working directory.
     * As of 1.4, the destination directory is automatically created when javadoc is run.
     * For example, the following generates the documentation for the package com.mypackage and
     * saves the results in the C:/user/doc/ directory:
     * <p>
     * javadoc -d /user/doc com.mypackage
     */
    @Override
    public File getDestinationDirectory() {
        return destinationDirectory.getValue();
    }

    @Override
    public void setDestinationDirectory(File directory) {
        this.destinationDirectory.setValue(directory);
    }

    @Override
    public StandardJavadocDocletOptions destinationDirectory(File destinationDirectory) {
        setDestinationDirectory(destinationDirectory);
        return this;
    }

    /**
     * -use
     * <p>
     * Includes one "Use" page for each documented class and package. The page describes what packages, classes, methods,
     * constructors and fields use any API of the given class or package. Given class C,
     * things that use class C would include subclasses of C, fields declared as C, methods that return C,
     * and methods and constructors with parameters of type C.
     * For example, let's look at what might appear on the "Use" page for String.
     * The getName() method in the java.awt.Font class returns type String. Therefore, getName() uses String,
     * and you will find that method on the "Use" page for String.
     * <p>
     * Note that this documents only uses of the API, not the implementation.
     * If a method uses String in its implementation but does not take a string as an argument or return a string,
     * that is not considered a "use" of String.
     * <p>
     * You can access the generated "Use" page by first going to the class or package,
     * then clicking on the "Use" link in the navigation bar.
     */
    @Input
    public boolean isUse() {
        return use.getValue();
    }

    public void setUse(boolean use) {
        this.use.setValue(use);
    }

    public StandardJavadocDocletOptions use(boolean use) {
        setUse(use);
        return this;
    }

    public StandardJavadocDocletOptions use() {
        return use(true);
    }

    /**
     * -version
     * <p>
     * Includes the @version text in the generated docs. This text is omitted by default.
     * To tell what version of the Javadoc tool you are using, use the -J-version option.
     */
    @Input
    public boolean isVersion() {
        return version.getValue();
    }

    public void setVersion(boolean version) {
        this.version.setValue(version);
    }

    public StandardJavadocDocletOptions version(boolean version) {
        setVersion(version);
        return this;
    }

    public StandardJavadocDocletOptions version() {
        return version(true);
    }

    /**
     * -author
     * <p>
     * Includes the @author text in the generated docs.
     */
    @Input
    public boolean isAuthor() {
        return author.getValue();
    }

    public void setAuthor(boolean author) {
        this.author.setValue(author);
    }

    public StandardJavadocDocletOptions author(boolean author) {
        setAuthor(author);
        return this;
    }

    public StandardJavadocDocletOptions author() {
        return author(true);
    }

    /**
     * -splitindex
     * <p>
     * Splits the index file into multiple files, alphabetically, one file per letter,
     * plus a file for any index entries that start with non-alphabetical characters.
     */
    @Input
    public boolean isSplitIndex() {
        return splitIndex.getValue();
    }

    public void setSplitIndex(boolean splitIndex) {
        this.splitIndex.setValue(splitIndex);
    }

    public StandardJavadocDocletOptions splitIndex(boolean splitIndex) {
        setSplitIndex(splitIndex);
        return this;
    }

    public StandardJavadocDocletOptions splitIndex() {
        return splitIndex(true);
    }

    /**
     * -windowtitle  title
     * <p>
     * Specifies the title to be placed in the HTML &lt;title&gt; tag.
     * This appears in the window title and in any browser bookmarks (favorite places) that someone creates for this page.
     * This title should not contain any HTML tags, as the browser will not properly interpret them.
     * Any internal quotation marks within title may have to be escaped. If -windowtitle is omitted,
     * the Javadoc tool uses the value of -doctitle for this option.
     * javadoc -windowtitle "Java 2 Platform" com.mypackage
     */
    @Override
    public String getWindowTitle() {
        return windowTitle.getValue();
    }

    @Override
    public void setWindowTitle(String windowTitle) {
        this.windowTitle.setValue(windowTitle);
    }

    @Override
    public StandardJavadocDocletOptions windowTitle(String windowTitle) {
        setWindowTitle(windowTitle);
        return this;
    }

    /**
     * -header header
     * <p>
     * Specifies the header text to be placed at the top of each output file. The header will be placed to the right of
     * the upper navigation bar. header may contain HTML tags and white space, though if it does, it must be enclosed
     * in quotes. Any internal quotation marks within header may have to be escaped.
     * javadoc -header "<b>Java 2 Platform </b><br>v1.4" com.mypackage
     */
    @Override
    public String getHeader() {
        return header.getValue();
    }

    @Override
    public void setHeader(String header) {
        this.header.setValue(header);
    }

    @Override
    public StandardJavadocDocletOptions header(String header) {
        setHeader(header);
        return this;
    }


    /**
     * -doctitle title
     * <p>
     * Specifies the title to be placed near the top of the overview summary file. The title will be placed as a centered,
     * level-one heading directly beneath the upper navigation bar. The title may contain HTML tags and white space,
     * though if it does, it must be enclosed in quotes. Any internal quotation marks within title may have to be escaped.
     * javadoc -doctitle "Java&lt;sup&gt;&lt;font size=\"-2\"&gt;TM&lt;/font&gt;&lt;/sup&gt;" com.mypackage
     */
    @Optional @Input
    public String getDocTitle() {
        return docTitle.getValue();
    }

    public void setDocTitle(String docTitle) {
        this.docTitle.setValue(docTitle);
    }

    public StandardJavadocDocletOptions docTitle(String docTitle) {
        setDocTitle(docTitle);
        return this;
    }

    /**
     * -footer footer
     * <p>
     * Specifies the footer text to be placed at the bottom of each output file.
     * The footer will be placed to the right of the lower navigation bar. footer may contain HTML tags and white space,
     * though if it does, it must be enclosed in quotes. Any internal quotation marks within footer may have to be escaped.
     */
    @Optional @Input
    public String getFooter() {
        return footer.getValue();
    }

    public void setFooter(String footer) {
        this.footer.setValue(footer);
    }

    public StandardJavadocDocletOptions footer(String footer) {
        setFooter(footer);
        return this;
    }

    /**
     * -bottom text
     * <p>
     * Specifies the text to be placed at the bottom of each output file.
     * The text will be placed at the bottom of the page, below the lower navigation bar.
     * The text may contain HTML tags and white space, though if it does, it must be enclosed in quotes.
     * Any internal quotation marks within text may have to be escaped.
     */
    @Optional @Input
    public String getBottom() {
        return bottom.getValue();
    }

    public void setBottom(String bottom) {
        this.bottom.setValue(bottom);
    }

    public StandardJavadocDocletOptions bottom(String bottom) {
        setBottom(bottom);
        return this;
    }

    /**
     * -link extdocURL
     * <p>
     * Creates links to existing javadoc-generated documentation of external referenced classes. It takes one argument:
     * <p>
     * extdocURL is the absolute or relative URL of the directory containing the external javadoc-generated documentation
     * you want to link to. Examples are shown below.
     * The package-list file must be found in this directory (otherwise, use -linkoffline).
     * The Javadoc tool reads the package names from the package-list file and then links to those packages at that URL.
     * When the Javadoc tool is run, the extdocURL value is copied literally into the &lt;A HREF&gt; links that are created.
     * Therefore, extdocURL must be the URL to the directory, not to a file.
     * You can use an absolute link for extdocURL to enable your docs to link to a document on any website,
     * or can use a relative link to link only to a relative location. If relative,
     * the value you pass in should be the relative path from the destination directory (specified with -d) to the directory containing the packages being linked to.
     * <p>
     * When specifying an absolute link you normally use an http: link. However,
     * if you want to link to a file system that has no web server, you can use a file: link -- however,
     * do this only if everyone wanting to access the generated documentation shares the same file system.
     */
    @Optional @Input
    public List<String> getLinks() {
        return links.getValue();
    }

    public void setLinks(List<String> links) {
        this.links.setValue(links);
    }

    public StandardJavadocDocletOptions links(String... links) {
        this.links.getValue().addAll(Arrays.asList(links));
        return this;
    }

    public StandardJavadocDocletOptions linksFile(File linksFile) {
        return (StandardJavadocDocletOptions) optionFiles(linksFile);
    }

    /**
     * -linkoffline extdocURL packagelistLoc
     * <p>
     * This option is a variation of -link; they both create links to javadoc-generated documentation
     * for external referenced classes. Use the -linkoffline option when linking to a document on the web
     * when the Javadoc tool itself is "offline" -- that is, it cannot access the document through a web connection.
     * More specifically, use -linkoffline if the external document's package-list file is not accessible or
     * does not exist at the extdocURL location but does exist at a different location,
     * which can be specified by packageListLoc (typically local). Thus, if extdocURL is accessible only on the World Wide Web,
     * -linkoffline removes the constraint that the Javadoc tool have a web connection when generating the documentation.
     * <p>
     * Another use is as a "hack" to update docs: After you have run javadoc on a full set of packages,
     * then you can run javadoc again on only a smaller set of changed packages,
     * so that the updated files can be inserted back into the original set. Examples are given below.
     * <p>
     * The -linkoffline option takes two arguments -- the first for the string to be embedded in the &lt;a href&gt; links,
     * the second telling it where to find package-list:
     * <p>
     * extdocURL is the absolute or relative URL of the directory containing the external javadoc-generated documentation you want to link to.
     * If relative, the value should be the relative path from the destination directory (specified with -d) to the root of the packages being linked to.
     * For more details, see extdocURL in the -link option.
     * packagelistLoc is the path or URL to the directory containing the package-list file for the external documentation.
     * This can be a URL (http: or file:) or file path, and can be absolute or relative. If relative,
     * make it relative to the current directory from where javadoc was run. Do not include the package-list filename.
     */
    @Optional @Input
    public List<JavadocOfflineLink> getLinksOffline() {
        return linksOffline.getValue();
    }

    public void setLinksOffline(List<JavadocOfflineLink> linksOffline) {
        this.linksOffline.setValue(linksOffline);
    }

    public StandardJavadocDocletOptions linksOffline(String extDocUrl, String packageListLoc) {
        this.linksOffline.getValue().add(new JavadocOfflineLink(extDocUrl, packageListLoc));
        return this;
    }

    public StandardJavadocDocletOptions linksOfflineFile(File linksOfflineFile) {
        return (StandardJavadocDocletOptions) optionFiles(linksOfflineFile);
    }

    /**
     * -linksource
     * <p>
     * Creates an HTML version of each source file (with line numbers) and adds links to them from the standard HTML documentation. Links are created for classes, interfaces, constructors, methods and fields whose declarations are in a source file. Otherwise, links are not created, such as for default constructors and generated classes.
     * This option exposes all private implementation details in the included source files, including private classes, private fields, and the bodies of private methods, regardless of the -public, -package, -protected and -private options. Unless you also use the -private option, not all private classes or interfaces will necessarily be accessible via links.
     * <p>
     * Each link appears on the name of the identifier in its declaration. For example, the link to the source code of the Button class would be on the word "Button":
     * <p>
     * public class Button
     * extends Component
     * implements Accessible
     * and the link to the source code of the getLabel() method in the Button class would be on the word "getLabel":
     * public String getLabel()
     */
    @Input
    public boolean isLinkSource() {
        return linkSource.getValue();
    }

    public void setLinkSource(boolean linkSource) {
        this.linkSource.setValue(linkSource);
    }

    public StandardJavadocDocletOptions linkSource(boolean linkSource) {
        setLinkSource(linkSource);
        return this;
    }

    public StandardJavadocDocletOptions linkSource() {
        return linkSource(true);
    }

    /**
     * -group groupheading packagepattern:packagepattern:...
     * <p>
     * Separates packages on the overview page into whatever groups you specify, one group per table.
     * You specify each group with a different -group option.
     * The groups appear on the page in the order specified on the command line; packages are alphabetized within a group.
     * For a given -group option, the packages matching the list of packagepattern expressions appear in a table
     * with the heading groupheading.
     * <p>
     * groupheading can be any text, and can include white space. This text is placed in the table heading for the group.
     * packagepattern can be any package name, or can be the start of any package name followed by an asterisk (*).
     * The asterisk is a wildcard meaning "match any characters". This is the only wildcard allowed.
     * Multiple patterns can be included in a group by separating them with colons (:).
     * <p>
     * NOTE: If using an asterisk in a pattern or pattern list, the pattern list must be inside quotes,
     * such as "java.lang*:java.util"
     * <p>
     * If you do not supply any -group option, all packages are placed in one group with the heading "Packages".
     * If the all groups do not include all documented packages,
     * any leftover packages appear in a separate group with the heading "Other Packages".
     * <p>
     * For example, the following option separates the four documented packages into core,
     * extension and other packages. Notice the trailing "dot" does not appear in "java.lang*" -- including the dot,
     * such as "java.lang.*" would omit the java.lang package.
     * <p>
     * javadoc -group "Core Packages" "java.lang*:java.util"
     * -group "Extension Packages" "javax.*"
     * java.lang java.lang.reflect java.util javax.servlet java.new
     * <p>
     * This results in the groupings:
     * <p>
     * Core Packages
     * <br>java.lang
     * <br>java.lang.reflect
     * <br>java.util
     * <p>
     * Extension Packages
     * <br>javax.servlet
     * <p>
     * Other Packages
     * <br>java.new
     */
    @Optional @Input
    public Map<String, List<String>> getGroups() {
        return groups.getValue();
    }

    public void setGroups(Map<String, List<String>> groups) {
        this.groups.setValue(groups);
    }

    public StandardJavadocDocletOptions group(Map<String, List<String>> groups) {
        setGroups(groups);
        return this;
    }

    public StandardJavadocDocletOptions group(String groupName, List<String> packagePatterns) {
        this.groups.getValue().put(groupName, packagePatterns);
        return this;
    }

    public StandardJavadocDocletOptions group(String groupName, String... packagePatterns) {
        return group(groupName, Arrays.asList(packagePatterns));
    }

    public StandardJavadocDocletOptions groupsFile(File groupsFile) {
        return (StandardJavadocDocletOptions) optionFiles(groupsFile);
    }

    /**
     * -nodeprecated
     * <p>
     * Prevents the generation of any deprecated API at all in the documentation.
     * This does what -nodeprecatedlist does, plus it does not generate any deprecated API throughout the rest of the documentation.
     * This is useful when writing code and you don't want to be distracted by the deprecated code.
     */
    @Input
    public boolean isNoDeprecated() {
        return noDeprecated.getValue();
    }

    public void setNoDeprecated(boolean noDeprecated) {
        this.noDeprecated.setValue(noDeprecated);
    }

    public StandardJavadocDocletOptions noDeprecated(boolean nodeprecated) {
        setNoDeprecated(nodeprecated);
        return this;
    }

    public StandardJavadocDocletOptions noDeprecated() {
        return noDeprecated(true);
    }

    /**
     * -nodeprecatedlist
     * <p>
     * Prevents the generation of the file containing the list of deprecated APIs (deprecated-list.html) and
     * the link in the navigation bar to that page.
     * (However, javadoc continues to generate the deprecated API throughout the rest of the document.)
     * This is useful if your source code contains no deprecated API, and you want to make the navigation bar cleaner.
     */
    @Input
    public boolean isNoDeprecatedList() {
        return noDeprecatedList.getValue();
    }

    public void setNoDeprecatedList(boolean noDeprecatedList) {
        this.noDeprecatedList.setValue(noDeprecatedList);
    }

    public StandardJavadocDocletOptions noDeprecatedList(boolean noDeprecatedList) {
        setNoDeprecatedList(noDeprecatedList);
        return this;
    }

    public StandardJavadocDocletOptions noDeprecatedList() {
        return noDeprecatedList(true);
    }

    /**
     * -nosince
     * <p>
     * Omits from the generated docs the "Since" sections associated with the @since tags.
     */
    @Input
    public boolean isNoSince() {
        return noSince.getValue();
    }

    public void setNoSince(boolean noSince) {
        this.noSince.setValue(noSince);
    }

    public StandardJavadocDocletOptions noSince(boolean noSince) {
        setNoSince(noSince);
        return this;
    }

    public StandardJavadocDocletOptions noSince() {
        return noSince(true);
    }

    /**
     * -notree
     * <p>
     * Omits the class/interface hierarchy pages from the generated docs.
     * These are the pages you reach using the "Tree" button in the navigation bar.
     * The hierarchy is produced by default.
     */
    @Input
    public boolean isNoTree() {
        return noTree.getValue();
    }

    public void setNoTree(boolean noTree) {
        this.noTree.setValue(noTree);
    }

    public StandardJavadocDocletOptions noTree(boolean noTree) {
        setNoTree(noTree);
        return this;
    }

    public StandardJavadocDocletOptions noTree() {
        return noTree(true);
    }

    /**
     * -noindex
     * <p>
     * Omits the index from the generated docs. The index is produced by default.
     */
    @Input
    public boolean isNoIndex() {
        return noIndex.getValue();
    }

    public void setNoIndex(boolean noIndex) {
        this.noIndex.setValue(noIndex);
    }

    public StandardJavadocDocletOptions noIndex(boolean noIndex) {
        setNoIndex(noIndex);
        return this;
    }

    public StandardJavadocDocletOptions noIndex() {
        return noIndex(true);
    }

    /**
     * -nohelp
     * <p>
     * Omits the HELP link in the navigation bars at the top and bottom of each page of output.
     */
    @Input
    public boolean isNoHelp() {
        return noHelp.getValue();
    }

    public void setNoHelp(boolean noHelp) {
        this.noHelp.setValue(noHelp);
    }

    public StandardJavadocDocletOptions noHelp(boolean noHelp) {
        setNoHelp(noHelp);
        return this;
    }

    public StandardJavadocDocletOptions noHelp() {
        return noHelp(true);
    }

    /**
     * -nonavbar
     * <p>
     * Prevents the generation of the navigation bar, header and footer,
     * otherwise found at the top and bottom of the generated pages. Has no affect on the "bottom" option.
     * The -nonavbar option is useful when you are interested only in the content and have no need for navigation,
     * such as converting the files to PostScript or PDF for print only.
     */
    @Input
    public boolean isNoNavBar() {
        return noNavBar.getValue();
    }

    public void setNoNavBar(boolean noNavBar) {
        this.noNavBar.setValue(noNavBar);
    }

    public StandardJavadocDocletOptions noNavBar(boolean noNavBar) {
        setNoNavBar(noNavBar);
        return this;
    }

    public StandardJavadocDocletOptions noNavBar() {
        return noNavBar(true);
    }

    /**
     * -helpfile  path/filename
     * <p>
     * Specifies the path of an alternate help file path\filename that the HELP link in the top and bottom navigation bars link to. Without this option, the Javadoc tool automatically creates a help file help-doc.html that is hard-coded in the Javadoc tool. This option enables you to override this default. The filename can be any name and is not restricted to help-doc.html -- the Javadoc tool will adjust the links in the navigation bar accordingly. For example:
     * <p>
     * javadoc -helpfile C:/user/myhelp.html java.awt
     */
    @Optional @PathSensitive(NAME_ONLY) @InputFile
    public File getHelpFile() {
        return helpFile.getValue();
    }

    public void setHelpFile(File helpFile) {
        this.helpFile.setValue(helpFile);
    }

    public StandardJavadocDocletOptions helpFile(File helpFile) {
        setHelpFile(helpFile);
        return this;
    }

    /**
     * -stylesheetfile  path\filename
     * <p>
     * Specifies the path of an alternate HTML stylesheet file. Without this option, the Javadoc tool automatically creates a stylesheet file stylesheet.css that is hard-coded in the Javadoc tool. This option enables you to override this default. The filename can be any name and is not restricted to stylesheet.css. For example:
     * <p>
     * javadoc -stylesheetfile C:/user/mystylesheet.css com.mypackage
     */
    @Optional @PathSensitive(NAME_ONLY) @InputFile
    public File getStylesheetFile() {
        return stylesheetFile.getValue();
    }

    public void setStylesheetFile(File stylesheetFile) {
        this.stylesheetFile.setValue(stylesheetFile);
    }

    public StandardJavadocDocletOptions stylesheetFile(File stylesheetFile) {
        setStylesheetFile(stylesheetFile);
        return this;
    }

    /**
     * -serialwarn
     * <p>
     * Generates compile-time warnings for missing @serial tags.
     * By default, Javadoc 1.2.2 (and later versions) generates no serial warnings.
     * (This is a reversal from earlier versions.) Use this option to display the serial warnings,
     * which helps to properly document default serializable fields and writeExternal methods.
     */
    @Input
    public boolean isSerialWarn() {
        return serialWarn.getValue();
    }

    public void setSerialWarn(boolean serialWarn) {
        this.serialWarn.setValue(serialWarn);
    }

    public StandardJavadocDocletOptions serialWarn(boolean serialWarn) {
        setSerialWarn(serialWarn);
        return this;
    }

    public StandardJavadocDocletOptions serialWarn() {
        return serialWarn(true);
    }

    /**
     * -charset  name
     * Specifies the HTML character set for this document. The name should be a preferred MIME name as given in the IANA Registry. For example:
     * <p>
     * javadoc -charset "iso-8859-1" mypackage
     * <p>
     * would insert the following line in the head of every generated page:
     * <p>
     * &lt;META http-equiv="Content-Type" content="text/html; charset=ISO-8859-1"&gt;
     * <p>
     * This META tag is described in the HTML standard. (4197265 and 4137321)
     * <p>
     * Also see -encoding and -docencoding.
     */
    @Optional @Input
    public String getCharSet() {
        return charSet.getValue();
    }

    public void setCharSet(String charSet) {
        this.charSet.setValue(charSet);
    }

    public StandardJavadocDocletOptions charSet(String charSet) {
        setCharSet(charSet);
        return this;
    }

    /**
     * -docencoding  name
     * <p>
     * Specifies the encoding of the generated HTML files. The name should be a preferred MIME name as given in the IANA Registry. If you omit this option but use -encoding, then the encoding of the generated HTML files is determined by -encoding. Example:
     * <p>
     * % javadoc -docencoding "ISO-8859-1" mypackage
     * <p>
     * Also see -encoding and -charset.
     */
    @Optional @Input
    public String getDocEncoding() {
        return docEncoding.getValue();
    }

    public void setDocEncoding(String docEncoding) {
        this.docEncoding.setValue(docEncoding);
    }

    public StandardJavadocDocletOptions docEncoding(String docEncoding) {
        setDocEncoding(docEncoding);
        return this;
    }

    /**
     * -keywords.
     */
    @Input
    public boolean isKeyWords() {
        return keyWords.getValue();
    }

    public void setKeyWords(boolean keyWords) {
        this.keyWords.setValue(keyWords);
    }

    public StandardJavadocDocletOptions keyWords(boolean keyWords) {
        setKeyWords(keyWords);
        return this;
    }

    public StandardJavadocDocletOptions keyWords() {
        return keyWords(true);
    }

    /**
     * -tag tagname:Xaoptcmf:"taghead".
     */
    @Optional @Input
    public List<String> getTags() {
        return tags.getValue();
    }

    public void setTags(List<String> tags) {
        this.tags.setValue(tags);
    }

    public StandardJavadocDocletOptions tags(List<String> tags) {
        this.tags.getValue().addAll(tags);
        return this;
    }

    public StandardJavadocDocletOptions tags(String... tags) {
        return tags(Arrays.asList(tags));
    }

    public StandardJavadocDocletOptions tagsFile(File tagsFile) {
        return (StandardJavadocDocletOptions) optionFiles(tagsFile);
    }

    /**
     * -taglet class.
     */
    @Optional @Input
    public List<String> getTaglets() {
        return taglets.getValue();
    }

    public void setTaglets(List<String> taglets) {
        this.taglets.setValue(taglets);
    }

    public StandardJavadocDocletOptions taglets(List<String> taglets) {
        this.taglets.getValue().addAll(taglets);
        return this;
    }

    public StandardJavadocDocletOptions taglets(String... taglets) {
        return taglets(Arrays.asList(taglets));
    }

    /**
     * -tagletpath tagletpathlist.
     */
    @Optional @Classpath
    public List<File> getTagletPath() {
        return tagletPath.getValue();
    }

    public void setTagletPath(List<File> tagletPath) {
        this.tagletPath.setValue(tagletPath);
    }

    public StandardJavadocDocletOptions tagletPath(List<File> tagletPath) {
        this.tagletPath.getValue().addAll(tagletPath);
        return this;
    }

    public StandardJavadocDocletOptions tagletPath(File... tagletPath) {
        return tagletPath(Arrays.asList(tagletPath));
    }

    /**
     * -docfilessubdirs.
     */
    @Input
    public boolean isDocFilesSubDirs() {
        return docFilesSubDirs.getValue();
    }

    public void setDocFilesSubDirs(boolean docFilesSubDirs) {
        this.docFilesSubDirs.setValue(docFilesSubDirs);
    }

    public StandardJavadocDocletOptions docFilesSubDirs(boolean docFilesSubDirs) {
        setDocFilesSubDirs(docFilesSubDirs);
        return this;
    }

    public StandardJavadocDocletOptions docFilesSubDirs() {
        return docFilesSubDirs(true);
    }

    /**
     * -excludedocfilessubdir name1:name2...
     */
    @Optional @Input
    public List<String> getExcludeDocFilesSubDir() {
        return excludeDocFilesSubDir.getValue();
    }

    public void setExcludeDocFilesSubDir(List<String> excludeDocFilesSubDir) {
        this.excludeDocFilesSubDir.setValue(excludeDocFilesSubDir);
    }

    public StandardJavadocDocletOptions excludeDocFilesSubDir(List<String> excludeDocFilesSubDir) {
        this.excludeDocFilesSubDir.getValue().addAll(excludeDocFilesSubDir);
        return this;
    }

    public StandardJavadocDocletOptions excludeDocFilesSubDir(String... excludeDocFilesSubDir) {
        return excludeDocFilesSubDir(Arrays.asList(excludeDocFilesSubDir));
    }

    /**
     * -noqualifier all | packagename1:packagename2:...
     */
    @Optional @Input
    public List<String> getNoQualifiers() {
        return noQualifiers.getValue();
    }

    public void setNoQualifiers(List<String> noQualifiers) {
        this.noQualifiers.setValue(noQualifiers);
    }

    public StandardJavadocDocletOptions noQualifier(List<String> noQualifiers) {
        this.noQualifiers.getValue().addAll(noQualifiers);
        return this;
    }

    public StandardJavadocDocletOptions noQualifiers(String... noQualifiers) {
        return noQualifier(Arrays.asList(noQualifiers));
    }

    @Input
    public boolean isNoTimestamp() {
        return noTimestamp.getValue();
    }

    public void setNoTimestamp(boolean noTimestamp) {
        this.noTimestamp.setValue(noTimestamp);
    }

    public StandardJavadocDocletOptions noTimestamp(boolean noTimestamp) {
        setNoTimestamp(noTimestamp);
        return this;
    }

    public StandardJavadocDocletOptions noTimestamp() {
        return noTimestamp(true);
    }

    /**
     * -nocomment.
     */
    @Input
    public boolean isNoComment() {
        return noComment.getValue();
    }

    public void setNoComment(boolean noComment) {
        this.noComment.setValue(noComment);
    }

    public StandardJavadocDocletOptions noComment(boolean noComment) {
        setNoComment(noComment);
        return this;
    }

    public StandardJavadocDocletOptions noComment() {
        return noComment(true);
    }
}
