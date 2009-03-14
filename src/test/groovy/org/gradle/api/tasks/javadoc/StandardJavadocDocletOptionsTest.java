package org.gradle.api.tasks.javadoc;

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
    private BufferedWriter optionWriterMock;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        options = new StandardJavadocDocletOptions();
        optionWriterMock = context.mock(BufferedWriter.class);
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
