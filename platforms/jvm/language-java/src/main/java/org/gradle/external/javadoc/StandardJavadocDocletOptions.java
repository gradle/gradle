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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.provider.ProviderApiDeprecationLogger;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.external.javadoc.internal.GroupsJavadocOptionFileOption;
import org.gradle.external.javadoc.internal.JavadocOptionFile;
import org.gradle.external.javadoc.internal.LinksOfflineJavadocOptionFileOption;
import org.gradle.external.javadoc.internal.MultilineStringsJavadocOptionFileOption;
import org.gradle.external.javadoc.internal.StringsJavadocOptionFileOption;
import org.gradle.external.javadoc.internal.options.ConfigurableFileCollectionKnownOption;
import org.gradle.external.javadoc.internal.options.HasMultipleValuesKnownOption;
import org.gradle.external.javadoc.internal.options.KnownOption;
import org.gradle.external.javadoc.internal.options.MapPropertyKnownOption;
import org.gradle.external.javadoc.internal.options.PropertyKnownOption;
import org.gradle.internal.Cast;
import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.tasks.PathSensitivity.NAME_ONLY;

/**
 * Provides the options for the standard Javadoc doclet.
 */
public abstract class StandardJavadocDocletOptions extends CoreJavadocOptions implements MinimalJavadocOptions {

    private static final List<KnownOption<StandardJavadocDocletOptions>> KNOWN_OPTIONS = ImmutableList.<KnownOption<StandardJavadocDocletOptions>>builder()
        .add(new PropertyKnownOption<>("d", StandardJavadocDocletOptions::getDestinationDirectory))
        .add(new PropertyKnownOption<>("use", StandardJavadocDocletOptions::getUse))
        .add(new PropertyKnownOption<>("version", StandardJavadocDocletOptions::getVersion))
        .add(new PropertyKnownOption<>("author", StandardJavadocDocletOptions::getAuthor))
        .add(new PropertyKnownOption<>("splitindex", StandardJavadocDocletOptions::getSplitIndex))
        .add(new PropertyKnownOption<>("header", StandardJavadocDocletOptions::getHeader))
        .add(new PropertyKnownOption<>("windowtitle", StandardJavadocDocletOptions::getWindowTitle))
        .add(new PropertyKnownOption<>("doctitle", StandardJavadocDocletOptions::getDocTitle))
        .add(new PropertyKnownOption<>("footer", StandardJavadocDocletOptions::getFooter))
        .add(new PropertyKnownOption<>("bottom", StandardJavadocDocletOptions::getBottom))
        .add(new PropertyKnownOption<>("linksource", StandardJavadocDocletOptions::getLinkSource))
        .add(new PropertyKnownOption<>("nodeprecated", StandardJavadocDocletOptions::getNoDeprecated))
        .add(new PropertyKnownOption<>("nodeprecatedlist", StandardJavadocDocletOptions::getNoDeprecatedList))
        .add(new PropertyKnownOption<>("nosince", StandardJavadocDocletOptions::getNoSince))
        .add(new PropertyKnownOption<>("notree", StandardJavadocDocletOptions::getNoTree))
        .add(new PropertyKnownOption<>("noindex", StandardJavadocDocletOptions::getNoIndex))
        .add(new PropertyKnownOption<>("nohelp", StandardJavadocDocletOptions::getNoHelp))
        .add(new PropertyKnownOption<>("nonavbar", StandardJavadocDocletOptions::getNoNavBar))
        .add(new PropertyKnownOption<>("helpfile", StandardJavadocDocletOptions::getHelpFile))
        .add(new PropertyKnownOption<>("stylesheetfile", StandardJavadocDocletOptions::getStylesheetFile))
        .add(new PropertyKnownOption<>("serialwarn", StandardJavadocDocletOptions::getSerialWarn))
        .add(new PropertyKnownOption<>("charset", StandardJavadocDocletOptions::getCharSet))
        .add(new PropertyKnownOption<>("docencoding", StandardJavadocDocletOptions::getDocEncoding))
        .add(new PropertyKnownOption<>("keywords", StandardJavadocDocletOptions::getKeyWords))
        .add(new PropertyKnownOption<>("docfilessubdirs", StandardJavadocDocletOptions::getDocFilesSubDirs))
        .add(new PropertyKnownOption<>("notimestamp", StandardJavadocDocletOptions::getNoTimestamp))
        .add(new PropertyKnownOption<>("nocomment", StandardJavadocDocletOptions::getNoComment))
        .add(new ConfigurableFileCollectionKnownOption<>("tagletpath", StandardJavadocDocletOptions::getTagletPath))
        .add(new HasMultipleValuesKnownOption<>("linkoffline", StandardJavadocDocletOptions::getLinksOffline,
            (option, value) -> new LinksOfflineJavadocOptionFileOption(option, Cast.uncheckedCast(value))))
        .add(new HasMultipleValuesKnownOption<>("link", StandardJavadocDocletOptions::getLinks,
            (option, value) -> new MultilineStringsJavadocOptionFileOption(option, Cast.uncheckedCast(value))))
        .add(new HasMultipleValuesKnownOption<>("tag", StandardJavadocDocletOptions::getTags,
            (option, value) -> new MultilineStringsJavadocOptionFileOption(option, Cast.uncheckedCast(value))))
        .add(new HasMultipleValuesKnownOption<>("taglet", StandardJavadocDocletOptions::getTaglets,
            (option, value) -> new MultilineStringsJavadocOptionFileOption(option, Cast.uncheckedCast(value))))
        .add(new HasMultipleValuesKnownOption<>("excludedocfilessubdir", StandardJavadocDocletOptions::getExcludeDocFilesSubDir,
            (option, value) -> new StringsJavadocOptionFileOption(option, Cast.uncheckedCast(value), ":")))
        .add(new HasMultipleValuesKnownOption<>("noqualifier", StandardJavadocDocletOptions::getNoQualifiers,
            (option, value) -> new StringsJavadocOptionFileOption(option, Cast.uncheckedCast(value), ":")))
        .add(new MapPropertyKnownOption<>("group", StandardJavadocDocletOptions::getGroups,
            (option, value) -> new GroupsJavadocOptionFileOption(option, Cast.uncheckedCast(value))))
        .build();

    private static final Set<String> KNOWN_OPTION_NAMES = KNOWN_OPTIONS.stream()
        .map(KnownOption::getOption)
        .collect(ImmutableSet.toImmutableSet());

    @Inject
    public StandardJavadocDocletOptions() {
        super(new JavadocOptionFile());
        addKnownOptionsToOptionFile();
        getUse().convention(false);
        getVersion().convention(false);
        getAuthor().convention(false);
        getSplitIndex().convention(false);
        getLinkSource().convention(false);
        getNoDeprecated().convention(false);
        getNoDeprecatedList().convention(false);
        getNoSince().convention(false);
        getNoTree().convention(false);
        getNoIndex().convention(false);
        getNoHelp().convention(false);
        getNoNavBar().convention(false);
        getSerialWarn().convention(false);
        getKeyWords().convention(false);
        getDocFilesSubDirs().convention(false);
        getNoTimestamp().convention(true);
        getNoComment().convention(false);
    }

    /**
     * Gets a set of all the options that are known to this class and its super class and have separate properties.
     *
     * @return set of property names
     * @since 7.5
     */
    @Incubating
    @Override
    public Set<String> knownOptionNames() {
        return Sets.union(super.knownOptionNames(), KNOWN_OPTION_NAMES);
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
    public abstract DirectoryProperty getDestinationDirectory();

    @Override
    public StandardJavadocDocletOptions destinationDirectory(File destinationDirectory) {
        getDestinationDirectory().set(destinationDirectory);
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
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getUse();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsUse() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsUse()", "getUse()");
        return getUse();
    }

    public StandardJavadocDocletOptions use(boolean use) {
        getUse().set(use);
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
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getVersion();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsVersion() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsVersion()", "getVersion()");
        return getVersion();
    }

    public StandardJavadocDocletOptions version(boolean version) {
        getVersion().set(version);
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
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getAuthor();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsAuthor() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsAuthor()", "getAuthor()");
        return getAuthor();
    }

    public StandardJavadocDocletOptions author(boolean author) {
        getAuthor().set(author);
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
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getSplitIndex();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsSplitIndex() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsSplitIndex()", "getSplitIndex()");
        return getSplitIndex();
    }

    public StandardJavadocDocletOptions splitIndex(boolean splitIndex) {
        getSplitIndex().set(splitIndex);
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
    public abstract Property<String> getWindowTitle();

    @Override
    public StandardJavadocDocletOptions windowTitle(String windowTitle) {
        getWindowTitle().set(windowTitle);
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
    public abstract Property<String> getHeader();

    @Override
    public StandardJavadocDocletOptions header(String header) {
        getHeader().set(header);
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
    @Input
    @Optional
    @ReplacesEagerProperty
    public abstract Property<String> getDocTitle();

    public StandardJavadocDocletOptions docTitle(String docTitle) {
        getDocTitle().set(docTitle);
        return this;
    }

    /**
     * -footer footer
     * <p>
     * Specifies the footer text to be placed at the bottom of each output file.
     * The footer will be placed to the right of the lower navigation bar. footer may contain HTML tags and white space,
     * though if it does, it must be enclosed in quotes. Any internal quotation marks within footer may have to be escaped.
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    public abstract Property<String> getFooter();

    public StandardJavadocDocletOptions footer(String footer) {
        getFooter().set(footer);
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
    @Input
    @Optional
    @ReplacesEagerProperty
    public abstract Property<String> getBottom();

    public StandardJavadocDocletOptions bottom(String bottom) {
        getBottom().set(bottom);
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
    @Input
    @Optional
    @ReplacesEagerProperty
    public abstract ListProperty<String> getLinks();

    public StandardJavadocDocletOptions links(String... links) {
        getLinks().addAll(Arrays.asList(links));
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
    @Input
    @Optional
    @ReplacesEagerProperty
    public abstract ListProperty<JavadocOfflineLink> getLinksOffline();

    public StandardJavadocDocletOptions linksOffline(String extDocUrl, String packageListLoc) {
        getLinksOffline().add(new JavadocOfflineLink(extDocUrl, packageListLoc));
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
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getLinkSource();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsLinkSource() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsLinkSource()", "getLinkSource()");
        return getLinkSource();
    }

    public StandardJavadocDocletOptions linkSource(boolean linkSource) {
        getLinkSource().set(linkSource);
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
    @Input
    @Optional
    @ReplacesEagerProperty
    public abstract MapProperty<String, List<String>> getGroups();

    public StandardJavadocDocletOptions group(Map<String, List<String>> groups) {
        getGroups().putAll(groups);
        return this;
    }

    public StandardJavadocDocletOptions group(String groupName, List<String> packagePatterns) {
        getGroups().put(groupName, packagePatterns);
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
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getNoDeprecated();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsNoDeprecated() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsNoDeprecated()", "getNoDeprecated()");
        return getNoDeprecated();
    }

    public StandardJavadocDocletOptions noDeprecated(boolean nodeprecated) {
        getNoDeprecated().set(nodeprecated);
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
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getNoDeprecatedList();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsNoDeprecatedList() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsNoDeprecatedList()", "getNoDeprecatedList()");
        return getNoDeprecatedList();
    }

    public StandardJavadocDocletOptions noDeprecatedList(boolean noDeprecatedList) {
        getNoDeprecatedList().set(noDeprecatedList);
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
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getNoSince();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsNoSince() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsNoSince()", "getNoSince()");
        return getNoSince();
    }

    public StandardJavadocDocletOptions noSince(boolean noSince) {
        getNoSince().set(noSince);
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
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getNoTree();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsNoTree() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsNoTree()", "getNoTree()");
        return getNoTree();
    }

    public StandardJavadocDocletOptions noTree(boolean noTree) {
        getNoTree().set(noTree);
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
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getNoIndex();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsNoIndex() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsNoIndex()", "getNoIndex()");
        return getNoIndex();
    }

    public StandardJavadocDocletOptions noIndex(boolean noIndex) {
        getNoIndex().set(noIndex);
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
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getNoHelp();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsNoHelp() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsNoHelp()", "getNoHelp()");
        return getNoHelp();
    }

    public StandardJavadocDocletOptions noHelp(boolean noHelp) {
        getNoHelp().set(noHelp);
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
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getNoNavBar();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsNoNavBar() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsNoNavBar()", "getNoNavBar()");
        return getNoNavBar();
    }

    public StandardJavadocDocletOptions noNavBar(boolean noNavBar) {
        getNoNavBar().set(noNavBar);
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
    @InputFile
    @PathSensitive(NAME_ONLY)
    @Optional
    @ReplacesEagerProperty
    public abstract RegularFileProperty getHelpFile();

    public StandardJavadocDocletOptions helpFile(File helpFile) {
        getHelpFile().set(helpFile);
        return this;
    }

    /**
     * -stylesheetfile  path\filename
     * <p>
     * Specifies the path of an alternate HTML stylesheet file. Without this option, the Javadoc tool automatically creates a stylesheet file stylesheet.css that is hard-coded in the Javadoc tool. This option enables you to override this default. The filename can be any name and is not restricted to stylesheet.css. For example:
     * <p>
     * javadoc -stylesheetfile C:/user/mystylesheet.css com.mypackage
     */
    @InputFile
    @PathSensitive(NAME_ONLY)
    @Optional
    @ReplacesEagerProperty
    public abstract RegularFileProperty getStylesheetFile();

    public StandardJavadocDocletOptions stylesheetFile(File stylesheetFile) {
        getStylesheetFile().set(stylesheetFile);
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
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getSerialWarn();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsSerialWarn() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsSerialWarn()", "getSerialWarn()");
        return getSerialWarn();
    }

    public StandardJavadocDocletOptions serialWarn(boolean serialWarn) {
        getSerialWarn().set(serialWarn);
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
    @Input
    @Optional
    @ReplacesEagerProperty
    public abstract Property<String> getCharSet();

    public StandardJavadocDocletOptions charSet(String charSet) {
        getCharSet().set(charSet);
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
    @Input
    @Optional
    @ReplacesEagerProperty
    public abstract Property<String> getDocEncoding();

    public StandardJavadocDocletOptions docEncoding(String docEncoding) {
        getDocEncoding().set(docEncoding);
        return this;
    }

    /**
     * -keywords.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getKeyWords();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsKeyWords() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsKeyWords()", "getKeyWords()");
        return getKeyWords();
    }

    public StandardJavadocDocletOptions keyWords(boolean keyWords) {
        getKeyWords().set(keyWords);
        return this;
    }

    public StandardJavadocDocletOptions keyWords() {
        return keyWords(true);
    }

    /**
     * -tag tagname:Xaoptcmf:"taghead".
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    public abstract ListProperty<String> getTags();

    public StandardJavadocDocletOptions tags(List<String> tags) {
        getTags().addAll(tags);
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
    @Input
    @Optional
    @ReplacesEagerProperty
    public abstract ListProperty<String> getTaglets();

    public StandardJavadocDocletOptions taglets(List<String> taglets) {
        getTaglets().addAll(taglets);
        return this;
    }

    public StandardJavadocDocletOptions taglets(String... taglets) {
        return taglets(Arrays.asList(taglets));
    }

    /**
     * -tagletpath tagletpathlist.
     */
    @Optional
    @Classpath
    @ReplacesEagerProperty(adapter = StandardJavadocDocletOptions.TagletPathAdapter.class)
    public abstract ConfigurableFileCollection getTagletPath();

    public StandardJavadocDocletOptions tagletPath(List<File> tagletPath) {
        getTagletPath().from(tagletPath);
        return this;
    }

    public StandardJavadocDocletOptions tagletPath(File... tagletPath) {
        return tagletPath(Arrays.asList(tagletPath));
    }

    /**
     * -docfilessubdirs.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getDocFilesSubDirs();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsDocFilesSubDirs() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsDocFilesSubDirs()", "getDocFilesSubDirs()");
        return getDocFilesSubDirs();
    }

    public StandardJavadocDocletOptions docFilesSubDirs(boolean docFilesSubDirs) {
        getDocFilesSubDirs().set(docFilesSubDirs);
        return this;
    }

    public StandardJavadocDocletOptions docFilesSubDirs() {
        return docFilesSubDirs(true);
    }

    /**
     * -excludedocfilessubdir name1:name2...
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    public abstract ListProperty<String> getExcludeDocFilesSubDir();

    public StandardJavadocDocletOptions excludeDocFilesSubDir(List<String> excludeDocFilesSubDir) {
        getExcludeDocFilesSubDir().addAll(excludeDocFilesSubDir);
        return this;
    }

    public StandardJavadocDocletOptions excludeDocFilesSubDir(String... excludeDocFilesSubDir) {
        return excludeDocFilesSubDir(Arrays.asList(excludeDocFilesSubDir));
    }

    /**
     * -noqualifier all | packagename1:packagename2:...
     */
    @Input
    @Optional
    @ReplacesEagerProperty
    public abstract ListProperty<String> getNoQualifiers();

    public StandardJavadocDocletOptions noQualifier(List<String> noQualifiers) {
        getNoQualifiers().addAll(noQualifiers);
        return this;
    }

    public StandardJavadocDocletOptions noQualifiers(String... noQualifiers) {
        return noQualifier(Arrays.asList(noQualifiers));
    }

    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getNoTimestamp();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsNoTimestamp() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsNoTimestamp()", "getNoTimestamp()");
        return getNoTimestamp();
    }

    public StandardJavadocDocletOptions noTimestamp(boolean noTimestamp) {
        getNoTimestamp().set(noTimestamp);
        return this;
    }

    public StandardJavadocDocletOptions noTimestamp() {
        return noTimestamp(true);
    }

    /**
     * -nocomment.
     */
    @Input
    @ReplacesEagerProperty(originalType = boolean.class)
    public abstract Property<Boolean> getNoComment();

    /**
     * This method exists only for Kotlin source backward compatibility.
     */
    @Internal
    @Deprecated
    public Property<Boolean> getIsNoComment() {
        ProviderApiDeprecationLogger.logDeprecation(StandardJavadocDocletOptions.class, "getIsNoComment()", "getNoComment()");
        return getNoComment();
    }

    public StandardJavadocDocletOptions noComment(boolean noComment) {
        getNoComment().set(noComment);
        return this;
    }

    public StandardJavadocDocletOptions noComment() {
        return noComment(true);
    }

    /**
     * Copies the values of the given {@code StandardJavadocDocletOptions} to this instance.
     * @since 9.0
     */
    @Incubating
    public StandardJavadocDocletOptions copy(StandardJavadocDocletOptions original) {
        super.copy(original);
        copyKnownOptionValues();
        addKnownOptionsToOptionFile();
        return this;
    }

    private void copyKnownOptionValues() {
        for (KnownOption<StandardJavadocDocletOptions> knownOption : KNOWN_OPTIONS) {
            knownOption.copyValueFromOptionFile(this, optionFile);
        }
    }

    private void addKnownOptionsToOptionFile() {
        for (KnownOption<StandardJavadocDocletOptions> knownOption : KNOWN_OPTIONS) {
            knownOption.addToOptionFile(this, optionFile);
        }
    }

    /**
     * Adapter for {@link StandardJavadocDocletOptions#getTagletPath()}.
     */
    static class TagletPathAdapter {
        @BytecodeUpgrade
        static List<File> getTagletPath(StandardJavadocDocletOptions self) {
            return new ArrayList<>(self.getTagletPath().getFiles());
        }

        @BytecodeUpgrade
        static void setTagletPath(StandardJavadocDocletOptions self, List<File> tagletPath) {
            self.getTagletPath().setFrom(tagletPath);
        }
    }
}
