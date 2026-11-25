/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * The lazy properties behind the eager getters and setters of {@link StandardJavadocDocletOptions}.
 *
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface StandardJavadocDocletOptionsInputs extends CoreJavadocOptionsInputs {

    /**
     * The lazy value of the {@code -d} output directory.
     *
     * @see StandardJavadocDocletOptions#getDestinationDirectory()
     * @since 10.0.0
     */
    DirectoryProperty getDestinationDirectory();

    /**
     * The lazy value of whether {@code -use} is set.
     *
     * @see StandardJavadocDocletOptions#isUse()
     * @since 10.0.0
     */
    Property<Boolean> getUse();

    /**
     * The lazy value of whether {@code -version} is set.
     *
     * @see StandardJavadocDocletOptions#isVersion()
     * @since 10.0.0
     */
    Property<Boolean> getVersion();

    /**
     * The lazy value of whether {@code -author} is set.
     *
     * @see StandardJavadocDocletOptions#isAuthor()
     * @since 10.0.0
     */
    Property<Boolean> getAuthor();

    /**
     * The lazy value of whether {@code -splitindex} is set.
     *
     * @see StandardJavadocDocletOptions#isSplitIndex()
     * @since 10.0.0
     */
    Property<Boolean> getSplitIndex();

    /**
     * The lazy value of the {@code -windowtitle}.
     *
     * @see StandardJavadocDocletOptions#getWindowTitle()
     * @since 10.0.0
     */
    Property<String> getWindowTitle();

    /**
     * The lazy value of the {@code -header}.
     *
     * @see StandardJavadocDocletOptions#getHeader()
     * @since 10.0.0
     */
    Property<String> getHeader();

    /**
     * The lazy value of the {@code -doctitle}.
     *
     * @see StandardJavadocDocletOptions#getDocTitle()
     * @since 10.0.0
     */
    Property<String> getDocTitle();

    /**
     * The lazy value of the {@code -footer}.
     *
     * @see StandardJavadocDocletOptions#getFooter()
     * @since 10.0.0
     */
    Property<String> getFooter();

    /**
     * The lazy value of the {@code -bottom}.
     *
     * @see StandardJavadocDocletOptions#getBottom()
     * @since 10.0.0
     */
    Property<String> getBottom();

    /**
     * The lazy value of the {@code -link} URLs.
     *
     * @see StandardJavadocDocletOptions#getLinks()
     * @since 10.0.0
     */
    ListProperty<String> getLinks();

    /**
     * The lazy value of the {@code -linkoffline} links.
     *
     * @see StandardJavadocDocletOptions#getLinksOffline()
     * @since 10.0.0
     */
    ListProperty<JavadocOfflineLink> getLinksOffline();

    /**
     * The lazy value of whether {@code -linksource} is set.
     *
     * @see StandardJavadocDocletOptions#isLinkSource()
     * @since 10.0.0
     */
    Property<Boolean> getLinkSource();

    /**
     * The lazy value of the {@code -group} package groups.
     *
     * @see StandardJavadocDocletOptions#getGroups()
     * @since 10.0.0
     */
    MapProperty<String, List<String>> getGroups();

    /**
     * The lazy value of whether {@code -nodeprecated} is set.
     *
     * @see StandardJavadocDocletOptions#isNoDeprecated()
     * @since 10.0.0
     */
    Property<Boolean> getNoDeprecated();

    /**
     * The lazy value of whether {@code -nodeprecatedlist} is set.
     *
     * @see StandardJavadocDocletOptions#isNoDeprecatedList()
     * @since 10.0.0
     */
    Property<Boolean> getNoDeprecatedList();

    /**
     * The lazy value of whether {@code -nosince} is set.
     *
     * @see StandardJavadocDocletOptions#isNoSince()
     * @since 10.0.0
     */
    Property<Boolean> getNoSince();

    /**
     * The lazy value of whether {@code -notree} is set.
     *
     * @see StandardJavadocDocletOptions#isNoTree()
     * @since 10.0.0
     */
    Property<Boolean> getNoTree();

    /**
     * The lazy value of whether {@code -noindex} is set.
     *
     * @see StandardJavadocDocletOptions#isNoIndex()
     * @since 10.0.0
     */
    Property<Boolean> getNoIndex();

    /**
     * The lazy value of whether {@code -nohelp} is set.
     *
     * @see StandardJavadocDocletOptions#isNoHelp()
     * @since 10.0.0
     */
    Property<Boolean> getNoHelp();

    /**
     * The lazy value of whether {@code -nonavbar} is set.
     *
     * @see StandardJavadocDocletOptions#isNoNavBar()
     * @since 10.0.0
     */
    Property<Boolean> getNoNavBar();

    /**
     * The lazy value of the {@code -helpfile}.
     *
     * @see StandardJavadocDocletOptions#getHelpFile()
     * @since 10.0.0
     */
    RegularFileProperty getHelpFile();

    /**
     * The lazy value of the {@code -stylesheetfile}.
     *
     * @see StandardJavadocDocletOptions#getStylesheetFile()
     * @since 10.0.0
     */
    RegularFileProperty getStylesheetFile();

    /**
     * The lazy value of whether {@code -serialwarn} is set.
     *
     * @see StandardJavadocDocletOptions#isSerialWarn()
     * @since 10.0.0
     */
    Property<Boolean> getSerialWarn();

    /**
     * The lazy value of the {@code -charset}.
     *
     * @see StandardJavadocDocletOptions#getCharSet()
     * @since 10.0.0
     */
    Property<String> getCharSet();

    /**
     * The lazy value of the {@code -docencoding}.
     *
     * @see StandardJavadocDocletOptions#getDocEncoding()
     * @since 10.0.0
     */
    Property<String> getDocEncoding();

    /**
     * The lazy value of whether {@code -keywords} is set.
     *
     * @see StandardJavadocDocletOptions#isKeyWords()
     * @since 10.0.0
     */
    Property<Boolean> getKeyWords();

    /**
     * The lazy value of the {@code -tag} definitions.
     *
     * @see StandardJavadocDocletOptions#getTags()
     * @since 10.0.0
     */
    ListProperty<String> getTags();

    /**
     * The lazy value of the {@code -taglet} classes.
     *
     * @see StandardJavadocDocletOptions#getTaglets()
     * @since 10.0.0
     */
    ListProperty<String> getTaglets();

    /**
     * The lazy value of the {@code -tagletpath}.
     *
     * @see StandardJavadocDocletOptions#getTagletPath()
     * @since 10.0.0
     */
    ConfigurableFileCollection getTagletPath();

    /**
     * The lazy value of whether {@code -docfilessubdirs} is set.
     *
     * @see StandardJavadocDocletOptions#isDocFilesSubDirs()
     * @since 10.0.0
     */
    Property<Boolean> getDocFilesSubDirs();

    /**
     * The lazy value of the {@code -excludedocfilessubdir} names.
     *
     * @see StandardJavadocDocletOptions#getExcludeDocFilesSubDir()
     * @since 10.0.0
     */
    ListProperty<String> getExcludeDocFilesSubDir();

    /**
     * The lazy value of the {@code -noqualifier} packages.
     *
     * @see StandardJavadocDocletOptions#getNoQualifiers()
     * @since 10.0.0
     */
    ListProperty<String> getNoQualifiers();

    /**
     * The lazy value of whether {@code -notimestamp} is set.
     *
     * @see StandardJavadocDocletOptions#isNoTimestamp()
     * @since 10.0.0
     */
    Property<Boolean> getNoTimestamp();

    /**
     * The lazy value of whether {@code -nocomment} is set.
     *
     * @see StandardJavadocDocletOptions#isNoComment()
     * @since 10.0.0
     */
    Property<Boolean> getNoComment();
}
