package org.gradle.api.tasks.javadoc;

import java.io.File;
import java.util.*;

/**
 * @author Tom Eyckmans
 */
public class StandardJavadocDocletOptions extends CoreJavadocOptions  implements MinimalJavadocOptions {
    /**
     * -d  directory
     * Specifies the destination directory where javadoc saves the generated HTML files. (The "d" means "destination.")
     * Omitting this option causes the files to be saved to the current directory.
     * The value directory can be absolute, or relative to the current working directory.
     * As of 1.4, the destination directory is automatically created when javadoc is run.
     * For example, the following generates the documentation for the package com.mypackage and
     * saves the results in the C:/user/doc/ directory:
     *
     *   C:> javadoc -d /user/doc com.mypackage
     */
    private File directory;

    public File getDirectory() {
        return directory;
    }

    public void setDirectory(File directory) {
        this.directory = directory;
    }

    public StandardJavadocDocletOptions directory(File directory) {
        setDirectory(directory);
        return this;
    }

    /**
     * -use
     * Includes one "Use" page for each documented class and package. The page describes what packages, classes, methods,
     * constructors and fields use any API of the given class or package. Given class C,
     * things that use class C would include subclasses of C, fields declared as C, methods that return C,
     * and methods and constructors with parameters of type C.
     * For example, let's look at what might appear on the "Use" page for String.
     * The getName() method in the java.awt.Font class returns type String. Therefore, getName() uses String,
     * and you will find that method on the "Use" page for String.
     *
     * Note that this documents only uses of the API, not the implementation.
     * If a method uses String in its implementation but does not take a string as an argument or return a string,
     * that is not considered a "use" of String.
     *
     * You can access the generated "Use" page by first going to the class or package,
     * then clicking on the "Use" link in the navigation bar.
     */
    private boolean use;

    public boolean isUse() {
        return use;
    }

    public void setUse(boolean use) {
        this.use = use;
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
     * Includes the @version text in the generated docs. This text is omitted by default.
     * To tell what version of the Javadoc tool you are using, use the -J-version option.
     */
    private boolean version;

    public boolean isVersion() {
        return version;
    }

    public void setVersion(boolean version) {
        this.version = version;
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
     * Includes the @author text in the generated docs.
     */
    private boolean author;

    public boolean isAuthor() {
        return author;
    }

    public void setAuthor(boolean author) {
        this.author = author;
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
     * Splits the index file into multiple files, alphabetically, one file per letter,
     * plus a file for any index entries that start with non-alphabetical characters.
     */
    private boolean splitIndex;

    public boolean isSplitIndex() {
        return splitIndex;
    }

    public void setSplitIndex(boolean splitIndex) {
        this.splitIndex = splitIndex;
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
     * Specifies the title to be placed in the HTML <title> tag.
     * This appears in the window title and in any browser bookmarks (favorite places) that someone creates for this page.
     * This title should not contain any HTML tags, as the browser will not properly interpret them.
     * Any internal quotation marks within title may have to be escaped. If -windowtitle is omitted,
     * the Javadoc tool uses the value of -doctitle for this option.
     *   C:> javadoc -windowtitle "Java 2 Platform" com.mypackage
     */
    private String windowTitle = null;

    public String getWindowTitle() {
        return windowTitle;
    }

    public void setWindowTitle(String windowTitle) {
        this.windowTitle = windowTitle;
    }

    public StandardJavadocDocletOptions windowTitle(String windowTitle) {
        setWindowTitle(windowTitle);
        return this;
    }

    /**
     * -doctitle  title
     * Specifies the title to be placed near the top of the overview summary file. The title will be placed as a centered,
     * level-one heading directly beneath the upper navigation bar. The title may contain html tags and white space,
     * though if it does, it must be enclosed in quotes. Any internal quotation marks within title may have to be escaped.
     *   C:> javadoc -doctitle "Java<sup><font size=\"-2\">TM</font></sup>" com.mypackage
     */
    private String docTitle = null;

    public String getDocTitle() {
        return docTitle;
    }

    public void setDocTitle(String docTitle) {
        this.docTitle = docTitle;
    }

    public StandardJavadocDocletOptions docTitle(String docTitle) {
        setDocTitle(docTitle);
        return this;
    }

    /**
     * -footer  footer
     * Specifies the footer text to be placed at the bottom of each output file.
     * The footer will be placed to the right of the lower navigation bar. footer may contain html tags and white space,
     * though if it does, it must be enclosed in quotes. Any internal quotation marks within footer may have to be escaped.
     */
    private String footer = null;

    public String getFooter() {
        return footer;
    }

    public void setFooter(String footer) {
        this.footer = footer;
    }

    public StandardJavadocDocletOptions footer(String footer) {
        setFooter(footer);
        return this;
    }

    public StandardJavadocDocletOptions footerFile(File footerFile) {
        return (StandardJavadocDocletOptions) singleOptionFile("footer", footerFile);
    }

    /**
     * -bottom  text
     * Specifies the text to be placed at the bottom of each output file.
     * The text will be placed at the bottom of the page, below the lower navigation bar.
     * The text may contain HTML tags and white space, though if it does, it must be enclosed in quotes.
     * Any internal quotation marks within text may have to be escaped.
     */
    private String bottom = null;

    public String getBottom() {
        return bottom;
    }

    public void setBottom(String bottom) {
        this.bottom = bottom;
    }

    public StandardJavadocDocletOptions bottom(String bottom) {
        setBottom(bottom);
        return this;
    }

    public StandardJavadocDocletOptions bottomFile(File bottomFile) {
        return (StandardJavadocDocletOptions)singleOptionFile("bottom", bottomFile);
    }

    /**
     * -link  extdocURL
     * Creates links to existing javadoc-generated documentation of external referenced classes. It takes one argument:
     *
     * extdocURL is the absolute or relative URL of the directory containing the external javadoc-generated documentation
     * you want to link to. Examples are shown below.
     * The package-list file must be found in this directory (otherwise, use -linkoffline).
     * The Javadoc tool reads the package names from the package-list file and then links to those packages at that URL.
     * When the Javadoc tool is run, the extdocURL value is copied literally into the <A HREF> links that are created.
     * Therefore, extdocURL must be the URL to the directory, not to a file.
     * You can use an absolute link for extdocURL to enable your docs to link to a document on any website,
     * or can use a relative link to link only to a relative location. If relative,
     * the value you pass in should be the relative path from the destination directory (specified with -d) to the directory containing the packages being linked to.
     *
     * When specifying an absolute link you normally use an http: link. However,
     * if you want to link to a file system that has no web server, you can use a file: link -- however,
     * do this only if everyone wanting to access the generated documentation shares the same file system.
     */
    private List<String> links = new ArrayList<String>();

    public List<String> getLinks() {
        return links;
    }

    public void setLinks(List<String> links) {
        this.links = links;
    }

    public StandardJavadocDocletOptions link(String ... links) {
        this.links.addAll(Arrays.asList(links));
        return this;
    }

    public StandardJavadocDocletOptions linkFile(File linkFile) {
        return (StandardJavadocDocletOptions) optionFiles(linkFile);
    }

    /**
     * -linkoffline  extdocURL  packagelistLoc
     * This option is a variation of -link; they both create links to javadoc-generated documentation
     * for external referenced classes. Use the -linkoffline option when linking to a document on the web
     * when the Javadoc tool itself is "offline" -- that is, it cannot access the document through a web connection.
     * More specifically, use -linkoffline if the external document's package-list file is not accessible or
     * does not exist at the extdocURL location but does exist at a different location,
     * which can be specified by packageListLoc (typically local). Thus, if extdocURL is accessible only on the World Wide Web,
     * -linkoffline removes the constraint that the Javadoc tool have a web connection when generating the documentation.
     *
     * Another use is as a "hack" to update docs: After you have run javadoc on a full set of packages,
     * then you can run javadoc again on onlya smaller set of changed packages,
     * so that the updated files can be inserted back into the original set. Examples are given below.
     *
     * The -linkoffline option takes two arguments -- the first for the string to be embedded in the <a href> links,
     * the second telling it where to find package-list:
     *
     * extdocURL is the absolute or relative URL of the directory containing the external javadoc-generated documentation you want to link to.
     * If relative, the value should be the relative path from the destination directory (specified with -d) to the root of the packages being linked to.
     * For more details, see extdocURL in the -link option.
     * packagelistLoc is the path or URL to the directory containing the package-list file for the external documentation.
     * This can be a URL (http: or file:) or file path, and can be absolute or relative. If relative,
     * make it relative to the current directory from where javadoc was run. Do not include the package-list filename.
     */
    private List<JavadocOfflineLink> linksOffline = new ArrayList<JavadocOfflineLink>();

    public List<JavadocOfflineLink> getLinksOffline() {
        return linksOffline;
    }

    public void setLinksOffline(List<JavadocOfflineLink> linksOffline) {
        this.linksOffline = linksOffline;
    }

    public StandardJavadocDocletOptions linksOffline(String extDocUrl, String packageListLoc) {
        this.linksOffline.add(new JavadocOfflineLink(extDocUrl, packageListLoc));
        return this;
    }

    public StandardJavadocDocletOptions linksOfflineFile(File linksOfflineFile) {
        return (StandardJavadocDocletOptions) optionFiles(linksOfflineFile);
    }

    /**
     * -linksource
     * Creates an HTML version of each source file (with line numbers) and adds links to them from the standard HTML documentation. Links are created for classes, interfaces, constructors, methods and fields whose declarations are in a source file. Otherwise, links are not created, such as for default constructors and generated classes.
     * This option exposes all private implementation details in the included source files, including private classes, private fields, and the bodies of private methods, regardless of the -public, -package, -protected and -private options. Unless you also use the -private option, not all private classes or interfaces will necessarily be accessible via links.
     *
     * Each link appears on the name of the identifier in its declaration. For example, the link to the source code of the Button class would be on the word "Button":
     *
     *     public class Button
     *     extends Component
     *     implements Accessible
     * and the link to the source code of the getLabel() method in the Button class would be on the word "getLabel":
     *     public String getLabel()
     */
    private boolean linkSource;

    public boolean isLinkSource() {
        return linkSource;
    }

    public void setLinkSource(boolean linkSource) {
        this.linkSource = linkSource;
    }

    public StandardJavadocDocletOptions linkSource(boolean linkSource) {
        setLinkSource(linkSource);
        return this;
    }

    public StandardJavadocDocletOptions linkSource() {
        return linkSource(true);
    }

    /**
     * -group  groupheading  packagepattern:packagepattern:...
     * Separates packages on the overview page into whatever groups you specify, one group per table.
     * You specify each group with a different -group option.
     * The groups appear on the page in the order specified on the command line; packages are alphabetized within a group.
     * For a given -group option, the packages matching the list of packagepattern expressions appear in a table
     * with the heading groupheading.
     * groupheading can be any text, and can include white space. This text is placed in the table heading for the group.
     * packagepattern can be any package name, or can be the start of any package name followed by an asterisk (*).
     * The asterisk is a wildcard meaning "match any characters". This is the only wildcard allowed.
     * Multiple patterns can be included in a group by separating them with colons (:).
     * NOTE: If using an asterisk in a pattern or pattern list, the pattern list must be inside quotes,
     * such as "java.lang*:java.util"
     * If you do not supply any -group option, all packages are placed in one group with the heading "Packages".
     * If the all groups do not include all documented packages,
     * any leftover packages appear in a separate group with the heading "Other Packages".
     *
     * For example, the following option separates the four documented packages into core,
     * extension and other packages. Notice the trailing "dot" does not appear in "java.lang*" -- including the dot,
     * such as "java.lang.*" would omit the java.lang package.
     *
     *   C:> javadoc -group "Core Packages" "java.lang*:java.util"
     *             -group "Extension Packages" "javax.*"
     *             java.lang java.lang.reflect java.util javax.servlet java.new
     * This results in the groupings:
     * Core Packages
     * java.lang
     * java.lang.reflect
     * java.util
     * Extension Packages
     * javax.servlet
     * Other Packages
     * java.new
     */
    private Map<String, List<String>> groups = new HashMap<String, List<String>>();

    public Map<String, List<String>> getGroups() {
        return groups;
    }

    public void setGroups(Map<String, List<String>> groups) {
        this.groups = groups;
    }

    public StandardJavadocDocletOptions group(Map<String, List<String>> groups) {
        setGroups(groups);
        return this;
    }

    public StandardJavadocDocletOptions group(String groupName, List<String> packagePatterns) {
        this.groups.put(groupName, packagePatterns);
        return this;
    }

    public StandardJavadocDocletOptions group(String groupName, String ... packagePatterns) {
        return group(groupName, Arrays.asList(packagePatterns));
    }

    public StandardJavadocDocletOptions groupsFile(File groupsFile) {
        return (StandardJavadocDocletOptions) optionFiles(groupsFile);
    }

    /**
     * -nodeprecated
     * Prevents the generation of any deprecated API at all in the documentation.
     * This does what -nodeprecatedlist does, plus it does not generate any deprecated API throughout the rest of the documentation.
     * This is useful when writing code and you don't want to be distracted by the deprecated code.
     */
    private boolean noDeprecated;

    public boolean isNoDeprecated() {
        return noDeprecated;
    }

    public void setNoDeprecated(boolean noDeprecated) {
        this.noDeprecated = noDeprecated;
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
     * Prevents the generation of the file containing the list of deprecated APIs (deprecated-list.html) and
     * the link in the navigation bar to that page.
     * (However, javadoc continues to generate the deprecated API throughout the rest of the document.)
     * This is useful if your source code contains no deprecated API, and you want to make the navigation bar cleaner.
     */
    private boolean noDeprecatedList;

    public boolean isNoDeprecatedList() {
        return noDeprecatedList;
    }

    public void setNoDeprecatedList(boolean noDeprecatedList) {
        this.noDeprecatedList = noDeprecatedList;
    }

    public StandardJavadocDocletOptions noDeprecatedList(boolean noDeprecatedList) {
        setNoDeprecatedList(noDeprecatedList);
        return this;
    }

    /**
     * -nosince
     * Omits from the generated docs the "Since" sections associated with the @since tags.
     */
    private boolean noSince = false;

    public boolean isNoSince() {
        return noSince;
    }

    public void setNoSince(boolean noSince) {
        this.noSince = noSince;
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
     * Omits the class/interface hierarchy pages from the generated docs.
     * These are the pages you reach using the "Tree" button in the navigation bar.
     * The hierarchy is produced by default.
     */
    private boolean noTree = false;

    public boolean isNoTree() {
        return noTree;
    }

    public void setNoTree(boolean noTree) {
        this.noTree = noTree;
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
     * Omits the index from the generated docs. The index is produced by default.
     */
    private boolean noIndex = false;

    public boolean isNoIndex() {
        return noIndex;
    }

    public void setNoIndex(boolean noIndex) {
        this.noIndex = noIndex;
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
     * Omits the HELP link in the navigation bars at the top and bottom of each page of output.
     */
    private boolean noHelp = false;

    public boolean isNoHelp() {
        return noHelp;
    }

    public void setNoHelp(boolean noHelp) {
        this.noHelp = noHelp;
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
     * Prevents the generation of the navigation bar, header and footer,
     * otherwise found at the top and bottom of the generated pages. Has no affect on the "bottom" option.
     * The -nonavbar option is useful when you are interested only in the content and have no need for navigation,
     * such as converting the files to PostScript or PDF for print only.
     */
    private boolean noNavBar = false;

    public boolean isNoNavBar() {
        return noNavBar;
    }

    public void setNoNavBar(boolean noNavBar) {
        this.noNavBar = noNavBar;
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
     * Specifies the path of an alternate help file path\filename that the HELP link in the top and bottom navigation bars link to. Without this option, the Javadoc tool automatically creates a help file help-doc.html that is hard-coded in the Javadoc tool. This option enables you to override this default. The filename can be any name and is not restricted to help-doc.html -- the Javadoc tool will adjust the links in the navigation bar accordingly. For example:
     *
     *   C:> javadoc -helpfile C:/user/myhelp.html java.awt
     *
     */
    private File helpFile;

    public File getHelpFile() {
        return helpFile;
    }

    public void setHelpFile(File helpFile) {
        this.helpFile = helpFile;
    }

    public StandardJavadocDocletOptions helpFile(File helpFile) {
        setHelpFile(helpFile);
        return this;
    }

    /**
     * -stylesheetfile  path\filename
     * Specifies the path of an alternate HTML stylesheet file. Without this option, the Javadoc tool automatically creates a stylesheet file stylesheet.css that is hard-coded in the Javadoc tool. This option enables you to override this default. The filename can be any name and is not restricted to stylesheet.css. For example:
     *
     *   C:> javadoc -stylesheetfile C:/user/mystylesheet.css com.mypackage
     *
     */
    private File stylesheetFile;

    public File getStylesheetFile() {
        return stylesheetFile;
    }

    public void setStylesheetFile(File stylesheetFile) {
        this.stylesheetFile = stylesheetFile;
    }

    public StandardJavadocDocletOptions stylesheetFile(File stylesheetFile) {
        setStylesheetFile(stylesheetFile);
        return this;
    }

    /**
     * -serialwarn
     * Generates compile-time warnings for missing @serial tags.
     * By default, Javadoc 1.2.2 (and later versions) generates no serial warnings.
     * (This is a reversal from earlier versions.) Use this option to display the serial warnings,
     * which helps to properly document default serializable fields and writeExternal methods.
     */
    private boolean serialWarn = true;

    public boolean isSerialWarn() {
        return serialWarn;
    }

    public void setSerialWarn(boolean serialWarn) {
        this.serialWarn = serialWarn;
    }

    public StandardJavadocDocletOptions serialWarn(boolean serialWarn) {
        setSerialWarn(serialWarn);
        return this;
    }

    public StandardJavadocDocletOptions noSerialWarn() {
        return serialWarn(false);
    }

    /**
     * -charset  name
     * Specifies the HTML character set for this document. The name should be a preferred MIME name as given in the IANA Registry. For example:
     *
     *   C:> javadoc -charset "iso-8859-1" mypackage
     *
     * would insert the following line in the head of every generated page:
     *
     *    <META http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
     *
     * This META tag is described in the HTML standard. (4197265 and 4137321)
     *
     * Also see -encoding and -docencoding.
     */
    private String charSet;

    public String getCharSet() {
        return charSet;
    }

    public void setCharSet(String charSet) {
        this.charSet = charSet;
    }

    public StandardJavadocDocletOptions charSet(String charSet) {
        setCharSet(charSet);
        return this;
    }

    /**
     * -docencoding  name
     * Specifies the encoding of the generated HTML files. The name should be a preferred MIME name as given in the IANA Registry. If you omit this option but use -encoding, then the encoding of the generated HTML files is determined by -encoding. Example:
     *
     *   % javadoc -docencoding "ISO-8859-1" mypackage
     *
     * Also see -encoding and -charset.
     */
    private String docEncoding;

    public String getDocEncoding() {
        return docEncoding;
    }

    public void setDocEncoding(String docEncoding) {
        this.docEncoding = docEncoding;
    }

    public StandardJavadocDocletOptions docEncoding(String docEncoding) {
        setDocEncoding(docEncoding);
        return this;
    }

    /**
     * -keywords
     */
    private boolean keyWords;

    public boolean isKeyWords() {
        return keyWords;
    }

    public void setKeyWords(boolean keyWords) {
        this.keyWords = keyWords;
    }

    public StandardJavadocDocletOptions keyWords(boolean keyWords) {
        setKeyWords(keyWords);
        return this;
    }

    public StandardJavadocDocletOptions keyWords() {
        return keyWords(true);
    }

    /**
     * -tag  tagname:Xaoptcmf:"taghead"
     * -taglet  class
     */
    private List<String> tags = new ArrayList<String>();

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public StandardJavadocDocletOptions tags(List<String> tags) {
        this.tags.addAll(tags);
        return this;
    }

    public StandardJavadocDocletOptions tags(String ... tags) {
        this.tags.addAll(Arrays.asList(tags));
        return this;
    }

    public StandardJavadocDocletOptions taglets(String ... taglets) {
        return tags(Arrays.asList(taglets));
    }

    public StandardJavadocDocletOptions tagsFile(File tagsFile) {
        return (StandardJavadocDocletOptions) optionFiles(tagsFile);
    }

    /**
     * -tagletpath  tagletpathlist
     */
    private List<File> tagletPath = new ArrayList<File>();

    public List<File> getTagletPath() {
        return tagletPath;
    }

    public void setTagletPath(List<File> tagletPath) {
        this.tagletPath = tagletPath;
    }

    public StandardJavadocDocletOptions tagletPath(List<File> tagletPath) {
        this.tagletPath.addAll(tagletPath);
        return this;
    }

    public StandardJavadocDocletOptions tagletPath(File ... tagletPath) {
        return tagletPath(Arrays.asList(tagletPath));
    }

    /**
     * -docfilessubdirs
     */
    private boolean docFilesSubDirs;

    public boolean isDocFilesSubDirs() {
        return docFilesSubDirs;
    }

    public void setDocFilesSubDirs(boolean docFilesSubDirs) {
        this.docFilesSubDirs = docFilesSubDirs;
    }

    public StandardJavadocDocletOptions docFilesSubDirs(boolean docFilesSubDirs) {
        setDocFilesSubDirs(docFilesSubDirs);
        return this;
    }

    public StandardJavadocDocletOptions docFilesSubDirs() {
        return docFilesSubDirs(true);
    }

    /**
     * -excludedocfilessubdir  name1:name2...
     */
    private List<String> excludeDocFilesSubDir = new ArrayList<String>();

    public List<String> getExcludeDocFilesSubDir() {
        return excludeDocFilesSubDir;
    }

    public void setExcludeDocFilesSubDir(List<String> excludeDocFilesSubDir) {
        this.excludeDocFilesSubDir = excludeDocFilesSubDir;
    }

    public StandardJavadocDocletOptions excludeDocFilesSubDir(List<String> excludeDocFilesSubDir) {
        this.excludeDocFilesSubDir.addAll(excludeDocFilesSubDir);
        return this;
    }

    public StandardJavadocDocletOptions excludeDocFilesSubDir(String ... excludeDocFilesSubDir) {
        return excludeDocFilesSubDir(Arrays.asList(excludeDocFilesSubDir));
    }

    /**
     * -noqualifier  all  |  packagename1:packagename2:...
     */
    private List<String> noQualifiers = new ArrayList<String>();

    public List<String> getNoQualifiers() {
        return noQualifiers;
    }

    public void setNoQualifiers(List<String> noQualifiers) {
        this.noQualifiers = noQualifiers;
    }

    public StandardJavadocDocletOptions noQualifier(List<String> noQualifiers) {
        this.noQualifiers.addAll(noQualifiers);
        return this;
    }

    public StandardJavadocDocletOptions noQualifier(String ... noQualifiers) {
        return noQualifier(Arrays.asList(noQualifiers));
    }

    /**
     * -notimestamp
     */
    public boolean noTimestamp = true;

    public boolean isNoTimestamp() {
        return noTimestamp;
    }

    public void setNoTimestamp(boolean noTimestamp) {
        this.noTimestamp = noTimestamp;
    }

    public StandardJavadocDocletOptions noTimestamp(boolean noTimestamp) {
        setNoTimestamp(noTimestamp);
        return this;
    }

    public StandardJavadocDocletOptions timestamp() {
        return noTimestamp(false);
    }

    /**
     * -nocomment
     */
    private boolean noComment;

    public boolean isNoComment() {
        return noComment;
    }

    public void setNoComment(boolean noComment) {
        this.noComment = noComment;
    }

    public StandardJavadocDocletOptions noComment(boolean noComment) {
        setNoComment(noComment);
        return this;
    }

    public StandardJavadocDocletOptions noComment() {
        return noComment(true);
    }
}
