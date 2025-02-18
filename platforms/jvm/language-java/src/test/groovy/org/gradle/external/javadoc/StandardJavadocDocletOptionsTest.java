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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.TestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import spock.lang.Issue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.gradle.util.Matchers.containsNormalizedString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StandardJavadocDocletOptionsTest {
    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass());

    private StandardJavadocDocletOptions options;
    private final FileResolver fileResolver = TestFiles.resolver(temporaryFolder.getTestDirectory());

    @Before
    public void setUp() {
        options = TestUtil.objectFactory(temporaryFolder.getTestDirectory()).newInstance(StandardJavadocDocletOptions.class);
    }

    @Test
    public void testDefaults() {
        // core javadoc options
        assertNull(options.getOverview().getOrNull());
        assertNull(options.getMemberLevel().getOrNull());
        assertNull(options.getDoclet().getOrNull());
        assertEmpty(options.getDocletpath().getFiles());
        assertNull(options.getSource().getOrNull());
        assertEmpty(options.getClasspath().getFiles());
        assertEmpty(options.getBootClasspath().getFiles());
        assertEmpty(options.getExtDirs().getFiles());
        assertEquals(JavadocOutputLevel.QUIET, options.getOutputLevel().get());
        assertFalse(options.getBreakIterator().get());
        assertNull(options.getLocale().getOrNull());
        assertNull(options.getEncoding().getOrNull());
        assertEmpty(options.getJFlags().get());
        assertEmpty(options.getSourceNames().get());
        assertEmpty(options.getOptionFiles().getFiles());
        // standard doclet options
        assertNull(options.getDestinationDirectory().getOrNull());
        assertFalse(options.getUse().get());
        assertFalse(options.getVersion().get());
        assertFalse(options.getAuthor().get());
        assertFalse(options.getSplitIndex().get());
        assertNull(options.getWindowTitle().getOrNull());
        assertNull(options.getDocTitle().getOrNull());
        assertNull(options.getFooter().getOrNull());
        assertNull(options.getBottom().getOrNull());
        assertEmpty(options.getLinks().get());
        assertEmpty(options.getLinksOffline().get());
        assertFalse(options.getLinkSource().get());
        assertEmpty(options.getGroups().get());
        assertFalse(options.getNoDeprecated().get());
        assertFalse(options.getNoDeprecatedList().get());
        assertFalse(options.getNoSince().get());
        assertFalse(options.getNoTree().get());
        assertFalse(options.getNoIndex().get());
        assertFalse(options.getNoHelp().get());
        assertFalse(options.getNoNavBar().get());
        assertNull(options.getHelpFile().getOrNull());
        assertNull(options.getStylesheetFile().getOrNull());
        assertFalse(options.getSerialWarn().get());
        assertNull(options.getCharSet().getOrNull());
        assertNull(options.getDocEncoding().getOrNull());
        assertFalse(options.getKeyWords().get());
        assertEmpty(options.getTags().get());
        assertEmpty(options.getTaglets().get());
        assertEmpty(options.getTagletPath().getFiles());
        assertFalse(options.getDocFilesSubDirs().get());
        assertEmpty(options.getExcludeDocFilesSubDir().get());
        assertEmpty(options.getNoQualifiers().get());
        assertTrue(options.getNoTimestamp().get());
        assertFalse(options.getNoComment().get());
    }

    @Test
    public void testFluentOverview() {
        final String overviewValue = "overview";
        assertEquals(options, options.overview(overviewValue));
        assertEquals(overviewValue, options.getOverview().get());
    }

    @Test
    public void testShowAll() {
        assertEquals(options, options.showAll());
        assertEquals(JavadocMemberLevel.PRIVATE, options.getMemberLevel().get());
    }

    @Test
    public void testShowFromPublic() {
        assertEquals(options, options.showFromPublic());
        assertEquals(JavadocMemberLevel.PUBLIC, options.getMemberLevel().get());
    }

    @Test
    public void testShowFromPackage() {
        assertEquals(options, options.showFromPackage());
        assertEquals(JavadocMemberLevel.PACKAGE, options.getMemberLevel().get());
    }

    @Test
    public void testShowFromProtected() {
        assertEquals(options, options.showFromProtected());
        assertEquals(JavadocMemberLevel.PROTECTED, options.getMemberLevel().get());
    }

    @Test
    public void testShowFromPrivate() {
        assertEquals(options, options.showFromPrivate());
        assertEquals(JavadocMemberLevel.PRIVATE, options.getMemberLevel().get());
    }

    @Test
    public void testFluentDocletClass() {
        final String docletValue = "org.gradle.CustomDocletClass";
        assertEquals(options, options.doclet(docletValue));
        assertEquals(docletValue, options.getDoclet().get());
    }

    @Test
    public void testFluentDocletClasspath() {
        final File[] docletClasspathValue = new File[]{new File("doclet.jar"), new File("doclet-dep.jar")};
        assertEquals(options, options.docletpath(docletClasspathValue));
        assertArrayEquals(Arrays.stream(docletClasspathValue).map(fileResolver::resolve).toArray(), options.getDocletpath().getFiles().toArray());
    }

    @Test
    public void testFluentSource() {
        final String sourceValue = "1.5";
        assertEquals(options, options.source(sourceValue));
        assertEquals(sourceValue, options.getSource().get());
    }

    @Test
    public void testFluentClasspath() {
        final File[] classpathValue = new File[]{new File("classpath.jar"), new File("classpath-dir")};
        assertEquals(options, options.classpath(classpathValue));
        assertArrayEquals(Arrays.stream(classpathValue).map(fileResolver::resolve).toArray(), options.getClasspath().getFiles().toArray());
    }

    @Test
    public void testFluentBootclasspath() {
        final File[] bootClasspathValue = new File[]{new File("bootclasspath.jar"), new File("bootclasspath2.jar")};
        assertEquals(options, options.bootClasspath(bootClasspathValue));
        assertArrayEquals(Arrays.stream(bootClasspathValue).map(fileResolver::resolve).toArray(), options.getBootClasspath().getFiles().toArray());
    }

    @Test
    public void testFluentExtDirs() {
        final File[] extDirsValue = new File[]{new File("extDirOne"), new File("extDirTwo")};
        assertEquals(options, options.extDirs(extDirsValue));
        assertArrayEquals(Arrays.stream(extDirsValue).map(fileResolver::resolve).toArray(), options.getExtDirs().getFiles().toArray());
    }

    @Test
    public void testQuietOutputLevel() {
        assertEquals(options, options.quiet());
        assertEquals(JavadocOutputLevel.QUIET, options.getOutputLevel().get());
    }

    @Test
    public void testVerboseOutputLevel() {
        assertEquals(options, options.verbose());
        assertEquals(JavadocOutputLevel.VERBOSE, options.getOutputLevel().get());
        assertTrue(options.getVerbose().get());
    }

    @Test
    public void testFluentBreakIterator() {
        assertEquals(options, options.breakIterator());
        assertTrue(options.getBreakIterator().get());
    }

    @Test
    public void testFluentLocale() {
        final String localeValue = "nl";
        assertEquals(options, options.locale(localeValue));
        assertEquals(localeValue, options.getLocale().get());
    }

    @Test
    public void testFluentEncoding() {
        final String encodingValue = "UTF-8";
        assertEquals(options, options.encoding(encodingValue));
        assertEquals(encodingValue, options.getEncoding().get());
    }

    @Test
    public void testFluentDirectory() {
        final File directoryValue = new File("testOutput");
        assertEquals(options, options.destinationDirectory(directoryValue));
        assertEquals(fileResolver.resolve(directoryValue), options.getDestinationDirectory().getAsFile().get());
    }

    @Test
    public void testFluentUse() {
        assertEquals(options, options.use());
        assertTrue(options.getUse().get());
    }

    @Test
    public void testFluentVersion() {
        assertEquals(options, options.version());
        assertTrue(options.getVersion().get());
    }

    @Test
    public void testFluentAuthor() {
        assertEquals(options, options.author());
        assertTrue(options.getAuthor().get());
    }

    @Test
    public void testFluentSplitIndex() {
        assertEquals(options, options.splitIndex());
        assertTrue(options.getSplitIndex().get());
    }

    @Test
    public void testFluentWindowTitle() {
        final String windowTitleValue = "windowTitleValue";
        assertEquals(options, options.windowTitle(windowTitleValue));
        assertEquals(windowTitleValue, options.getWindowTitle().get());
    }

    @Test
    public void testFluentDocTitle() {
        final String docTitleValue = "docTitleValue";
        assertEquals(options, options.docTitle(docTitleValue));
        assertEquals(docTitleValue, options.getDocTitle().get());
    }

    @Test
    public void testFluentFooter() {
        final String footerValue = "footerValue";
        assertEquals(options, options.footer(footerValue));
        assertEquals(footerValue, options.getFooter().get());
    }

    @Test
    public void testFluentBottom() {
        final String bottomValue = "bottomValue";
        assertEquals(options, options.bottom(bottomValue));
        assertEquals(bottomValue, options.getBottom().get());
    }

    @Test
    public void testFluentLink() {
        final String[] linkValue = new String[]{"http://otherdomain.org/javadoc"};
        assertEquals(options, options.links(linkValue));
        assertArrayEquals(linkValue, options.getLinks().get().toArray());
    }

    @Test
    public void testFluentLinkOffline() {
        final String extDocUrl = "http://otherdomain.org/javadoc";
        final String packageListLoc = "/home/someuser/used-lib-local-javadoc-list";
        assertEquals(options, options.linksOffline(extDocUrl, packageListLoc));
        assertEquals(extDocUrl, options.getLinksOffline().get().get(0).getExtDocUrl());
        assertEquals(packageListLoc, options.getLinksOffline().get().get(0).getPackagelistLoc());
    }

    @Test
    public void testFluentLinkSource() {
        assertEquals(options, options.linkSource());
        assertTrue(options.getLinkSource().get());
    }

    @Test
    public void testFluentGroup() {
        final String groupOneName = "groupOneName";
        final String[] groupOnePackages = new String[]{"java.lang", "java.io"};

        final String groupTwoName = "gradle";
        final String[] groupTwoPackages = new String[]{"org.gradle"};

        assertEquals(options, options.group(groupOneName, groupOnePackages));
        assertEquals(options, options.group(groupTwoName, groupTwoPackages));
        assertEquals(2, options.getGroups().get().size());
        assertArrayEquals(groupOnePackages, options.getGroups().get().get(groupOneName).toArray());
        assertArrayEquals(groupTwoPackages, options.getGroups().get().get(groupTwoName).toArray());
    }

    @Test
    public void testFluentNoDeprecated() {
        assertEquals(options, options.noDeprecated());
        assertTrue(options.getNoDeprecated().get());
    }

    @Test
    public void testFluentNoDeprecatedList() {
        assertEquals(options, options.noDeprecatedList());
        assertTrue(options.getNoDeprecatedList().get());
    }

    @Test
    public void testFluentNoSince() {
        assertEquals(options, options.noSince());
        assertTrue(options.getNoSince().get());
    }

    @Test
    public void testFluentNoTree() {
        assertEquals(options, options.noTree());
        assertTrue(options.getNoTree().get());
    }

    @Test
    public void testFluentNoIndex() {
        assertEquals(options, options.noIndex());
        assertTrue(options.getNoIndex().get());
    }

    @Test
    public void testFluentNoNavBar() {
        assertEquals(options, options.noNavBar());
        assertTrue(options.getNoNavBar().get());
    }

    @Test
    public void testFluentHelpFile() {
        final File helpFileValue = new File("help-file.txt");
        assertEquals(options, options.helpFile(helpFileValue));
        assertEquals(fileResolver.resolve(helpFileValue), options.getHelpFile().getAsFile().get());
    }

    @Test
    public void testFluentStylesheetFile() {
        final File stylesheetFileValue = new File("stylesheet.css");
        assertEquals(options, options.stylesheetFile(stylesheetFileValue));
        assertEquals(fileResolver.resolve(stylesheetFileValue), options.getStylesheetFile().getAsFile().get());
    }

    @Test
    public void testFluentSerialWarn() {
        assertEquals(options, options.serialWarn());
        assertTrue(options.getSerialWarn().get());
    }

    @Test
    public void testFluentCharset() {
        final String charsetValue = "dummy-charset";
        assertEquals(options, options.charSet(charsetValue));
        assertEquals(charsetValue, options.getCharSet().get());
    }

    @Test
    public void testFluentDocEncoding() {
        final String docEncodingValue = "UTF-16";
        assertEquals(options, options.docEncoding(docEncodingValue));
        assertEquals(docEncodingValue, options.getDocEncoding().get());
    }

    @Test
    public void testFluentKeywords() {
        assertEquals(options, options.keyWords());
        assertTrue(options.getKeyWords().get());
    }

    @Test
    public void testFluentTags() {
        final String[] tagsValue = new String[]{"param", "return", "todo:a:\"To Do:\""};

        final List<String> tempList = new ArrayList<String>(Arrays.asList(tagsValue));

        final Object[] totalTagsValue = tempList.toArray();
        assertEquals(options, options.tags(tagsValue));
        assertArrayEquals(totalTagsValue, options.getTags().get().toArray());
    }

    @Test
    public void testFluentTaglets() {
        final String[] tagletsValue = new String[]{"com.sun.tools.doclets.ToDoTaglet"};

        final List<String> tempList = new ArrayList<String>(Arrays.asList(tagletsValue));

        final Object[] totalTagletsValue = tempList.toArray();
        assertEquals(options, options.taglets(tagletsValue));
        assertArrayEquals(totalTagletsValue, options.getTaglets().get().toArray());
    }

    @Test
    public void testFluentTagletPath() {
        final File[] tagletPathValue = new File[]{new File("tagletOne.jar"), new File("tagletTwo.jar")};
        assertEquals(options, options.tagletPath(tagletPathValue));
        assertArrayEquals(Arrays.stream(tagletPathValue).map(fileResolver::resolve).toArray(), options.getTagletPath().getFiles().toArray());
    }

    @Test
    public void testFluentDocFilesSubDirs() {
        assertEquals(options, options.docFilesSubDirs());
        assertTrue(options.getDocFilesSubDirs().get());
    }

    @Test
    public void testFluentExcludeDocFilesSubDir() {
        final String[] excludeDocFilesSubDirValue = new String[]{".hg", ".svn", ".bzr", ".git"};
        assertEquals(options, options.excludeDocFilesSubDir(excludeDocFilesSubDirValue));
        assertArrayEquals(excludeDocFilesSubDirValue, options.getExcludeDocFilesSubDir().get().toArray());
    }

    @Test
    public void testFluentNoQualifier() {
        String[] noQualifierValue = new String[]{"java.lang", "java.io"};
        assertEquals(options, options.noQualifiers(noQualifierValue));
        assertArrayEquals(noQualifierValue, options.getNoQualifiers().get().toArray());
    }

    @Test
    public void testFluentNoTimestamp() {
        assertEquals(options, options.noTimestamp());
        assertTrue(options.getNoTimestamp().get());
    }

    @Test
    public void testFluentNoComment() {
        assertEquals(options, options.noComment());
        assertTrue(options.getNoComment().get());
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/1484")
    public void emitsVariousMultiValuedOptionsCorrectly() throws IOException {
        options.addMultilineStringsOption("addMultilineStringsOption").setValue(Arrays.asList("a", "b", "c"));
        options.addStringsOption("addStringsOption", " ").setValue(Arrays.asList("a", "b", "c"));
        options.addMultilineMultiValueOption("addMultilineMultiValueOption").setValue(Arrays.asList(Collections.singletonList("a"), Arrays.asList("b", "c")));

        TestFile optionsFile = temporaryFolder.file("javadoc.options");
        options.write(optionsFile);

        optionsFile.assertContents(containsNormalizedString("-addMultilineStringsOption 'a'\n" +
            "-addMultilineStringsOption 'b'\n" +
            "-addMultilineStringsOption 'c'"));

        optionsFile.assertContents(containsNormalizedString("-addStringsOption 'a b c'"));

        optionsFile.assertContents(containsNormalizedString("-addMultilineMultiValueOption \n" +
            "'a' \n" +
            "-addMultilineMultiValueOption \n" +
            "'b' 'c' "));
    }

    @After
    public void tearDown() {
        options = null;
    }

    public static void assertEmpty(Collection<?> shouldBeEmptyCollection) {
        assertNotNull(shouldBeEmptyCollection);
        assertTrue(shouldBeEmptyCollection.isEmpty());
    }

    public static void assertEmpty(Map<?, ?> shouldBeEmptyMap) {
        assertNotNull(shouldBeEmptyMap);
        assertTrue(shouldBeEmptyMap.isEmpty());
    }
}
