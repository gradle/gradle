package org.gradle.external.javadoc;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.*;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.external.javadoc.JavadocOutputLevel;
import org.gradle.external.javadoc.JavadocMemberLevel;
import org.gradle.external.javadoc.optionfile.JavadocOptionFile;
import org.gradle.external.javadoc.optionfile.LinksOfflineJavadocOptionFileOption;
import org.gradle.external.javadoc.optionfile.GroupsJavadocOptionFileOption;

import java.util.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.File;

/**
 * @author Tom Eyckmans
 */
public class StandardJavadocDocletOptionsTest {

    private final JUnit4Mockery context = new JUnit4Mockery();
    private StandardJavadocDocletOptions options;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        options = new StandardJavadocDocletOptions();
    }

    @Test
    public void testDefaults() {
        // core javadoc options
        assertNull(options.getOverview());
        assertNull(options.getMemberLevel());
        assertNull(options.getDoclet());
        assertEmpty(options.getDocletClasspath());
        assertNull(options.getSource());
        assertEmpty(options.getSourcepath());
        assertEmpty(options.getClasspath());
        assertEmpty(options.getSubPackages());
        assertEmpty(options.getExclude());
        assertEmpty(options.getBootClasspath());
        assertEmpty(options.getExtDirs());
        assertEquals(options.getOutputLevel(), JavadocOutputLevel.QUIET);
        assertFalse(options.isBreakIterator());
        assertNull(options.getLocale());
        assertNull(options.getEncoding());
        assertEmpty(options.getJFlags());
        assertEmpty(options.getPackageNames());
        assertEmpty(options.getSourceNames());
        assertEmpty(options.getOptionFiles());
        // standard doclet options
        assertNull(options.getDirectory());
        assertFalse(options.isUse());
        assertFalse(options.isVersion());
        assertFalse(options.isAuthor());
        assertFalse(options.isSplitIndex());
        assertNull(options.getWindowTitle());
        assertNull(options.getDocTitle());
        assertNull(options.getFooter());
        assertNull(options.getBottom());
        assertEmpty(options.getLinks());
        assertEmpty(options.getLinksOffline());
        assertFalse(options.isLinkSource());
        assertEmpty(options.getGroups());
        assertFalse(options.isNoDeprecated());
        assertFalse(options.isNoDeprecatedList());
        assertFalse(options.isNoSince());
        assertFalse(options.isNoTree());
        assertFalse(options.isNoIndex());
        assertFalse(options.isNoHelp());
        assertFalse(options.isNoNavBar());
        assertNull(options.getHelpFile());
        assertNull(options.getStylesheetFile());
        assertFalse(options.isSerialWarn());
        assertNull(options.getCharSet());
        assertNull(options.getDocEncoding());
        assertFalse(options.isKeyWords());
        assertEmpty(options.getTags());
        assertEmpty(options.getTagletPath());
        assertFalse(options.isDocFilesSubDirs());
        assertEmpty(options.getExcludeDocFilesSubDir());
        assertEmpty(options.getNoQualifiers());
        assertFalse(options.isNoTimestamp());
        assertFalse(options.isNoComment());
    }

    @Test
    public void testConstructor() {
        final JavadocOptionFile optionFileMock = context.mock(JavadocOptionFile.class);

        context.checking(new Expectations(){{
            // core options
            one(optionFileMock).addStringOption("overview");
            one(optionFileMock).addEnumOption("memberLevel");
            one(optionFileMock).addStringOption("doclet");
            one(optionFileMock).addPathOption("docletclasspath");
            one(optionFileMock).addStringOption("source");
            one(optionFileMock).addPathOption("sourcepath");
            one(optionFileMock).addPathOption("classpath");
            one(optionFileMock).addStringsOption("subpackages", ";");
            one(optionFileMock).addStringsOption("exclude", ":");
            one(optionFileMock).addPathOption("bootclasspath");
            one(optionFileMock).addPathOption("extdirs");
            one(optionFileMock).addEnumOption("outputLevel", JavadocOutputLevel.QUIET);
            one(optionFileMock).addBooleanOption("breakiterator");
            one(optionFileMock).addStringOption("locale");
            one(optionFileMock).addStringOption("encoding");
            // standard doclet options
            one(optionFileMock).addFileOption("d");
            one(optionFileMock).addBooleanOption("use");
            one(optionFileMock).addBooleanOption("version");
            one(optionFileMock).addBooleanOption("author");
            one(optionFileMock).addBooleanOption("splitindex");
            one(optionFileMock).addStringOption("windowtitle");
            one(optionFileMock).addStringOption("doctitle");
            one(optionFileMock).addStringOption("footer");
            one(optionFileMock).addStringOption("bottom");
            one(optionFileMock).addStringOption("link");
            allowing(optionFileMock).addOption(new LinksOfflineJavadocOptionFileOption("linkoffline"));
            one(optionFileMock).addBooleanOption("linksource");
            one(optionFileMock).addOption(new GroupsJavadocOptionFileOption("group"));
            one(optionFileMock).addBooleanOption("nodeprecated");
            one(optionFileMock).addBooleanOption("nodeprecatedlist");
            one(optionFileMock).addBooleanOption("nosince");
            one(optionFileMock).addBooleanOption("notree");
            one(optionFileMock).addBooleanOption("noindex");
            one(optionFileMock).addBooleanOption("nohelp");
            one(optionFileMock).addBooleanOption("nonavbar");
            one(optionFileMock).addFileOption("helpfile");
            one(optionFileMock).addFileOption("stylesheetfile");
            one(optionFileMock).addBooleanOption("serialwarn");
            one(optionFileMock).addStringOption("charset");
            one(optionFileMock).addStringOption("docencoding");
            one(optionFileMock).addBooleanOption("keywords");
            one(optionFileMock).addStringOption("tags");
            one(optionFileMock).addPathOption("tagletpath");
            one(optionFileMock).addBooleanOption("docfilessubdirs");
            one(optionFileMock).addStringsOption("excludedocfilessubdir", ":");
            one(optionFileMock).addStringsOption("noqualifier", ":");
            one(optionFileMock).addBooleanOption("notimestamp");
            one(optionFileMock).addBooleanOption("nocomment");
        }});

        options = new StandardJavadocDocletOptions();
    }

    @Test
    public void testFluentOverview() {
        final String overviewValue = "overview";
        assertEquals(options, options.overview(overviewValue));
        assertEquals(overviewValue, options.getOverview());
    }

    @Test
    public void testFluentDocletClass() {
        final String docletValue = "org.gradle.CustomDocletClass";
        assertEquals(options, options.doclet(docletValue));
        assertEquals(docletValue, options.getDoclet());
    }

    @Test
    public void testFluentDocletClasspath() {
        final File[] docletClasspathValue = new File[]{new File("doclet.jar"), new File("doclet-dep.jar")};
        assertEquals(options, options.docletClasspath(docletClasspathValue));
        assertArrayEquals(docletClasspathValue, options.getDocletClasspath().toArray());
    }

    @After
    public void tearDown() {
        options = null;
    }

    public static void assertEmpty(Collection shouldBeEmptyCollection) {
        assertNotNull(shouldBeEmptyCollection);
        assertTrue(shouldBeEmptyCollection.isEmpty());
    }

    public static void assertEmpty(Map shouldBeEmptyMap) {
        assertNotNull(shouldBeEmptyMap);
        assertTrue(shouldBeEmptyMap.isEmpty());
    }
}
