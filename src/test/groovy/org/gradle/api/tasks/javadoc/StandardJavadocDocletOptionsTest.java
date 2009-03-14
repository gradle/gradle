package org.gradle.api.tasks.javadoc;

import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.*;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;

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
    public void testWriteNullOverview() throws IOException {
        expectNoWrite();

        options.writeOverview(optionWriterMock);
    }

    @Test
    public void testWriteNotNullOverview() throws IOException {
        final String overviewValue = "overview";
        options.setOverview(overviewValue);
        assertEquals(overviewValue, options.getOverview());

        expectWriteValueOption(CoreJavadocOptions.OVERVIEW, overviewValue);

        options.writeOverview(optionWriterMock);
    }

    @Test
    public void testShowFromPublic() throws IOException {
        options.showFromPublic();
        assertEquals(JavadocMemberLevel.PUBLIC, options.getMemberLevel());

        expectWriteOption(JavadocMemberLevel.PUBLIC.toString().toLowerCase());

        options.writeMemberLevel(optionWriterMock);
    }

    @Test
    public void testShowFromProtected() throws IOException {
        options.showFromProtected();
        assertEquals(JavadocMemberLevel.PROTECTED, options.getMemberLevel());

        expectWriteOption(JavadocMemberLevel.PROTECTED.toString().toLowerCase());

        options.writeMemberLevel(optionWriterMock);
    }

    @Test
    public void testShowFromPakage() throws IOException {
        options.showFromPackage();
        assertEquals(JavadocMemberLevel.PACKAGE, options.getMemberLevel());

        expectWriteOption(JavadocMemberLevel.PACKAGE.toString().toLowerCase());

        options.writeMemberLevel(optionWriterMock);
    }

    @Test
    public void testShowFromPrivate() throws IOException {
        options.showFromPrivate();
        assertEquals(JavadocMemberLevel.PRIVATE, options.getMemberLevel());

        expectWriteOption(JavadocMemberLevel.PRIVATE.toString().toLowerCase());

        options.writeMemberLevel(optionWriterMock);
    }

    @Test
    public void testShowAll() throws IOException {
        options.showAll();
        assertEquals(JavadocMemberLevel.PRIVATE, options.getMemberLevel());

        expectWriteOption(JavadocMemberLevel.PRIVATE.toString().toLowerCase());

        options.writeMemberLevel(optionWriterMock);
    }

    @Test
    public void testFluentDocletClass() {
        final String docletValue = "org.gradle.CustomDocletClass";
        assertEquals(options, options.doclet(docletValue));
        assertEquals(docletValue, options.getDoclet());
    }

    @Test
    public void testWriteNullDocletClass() throws IOException {
        expectNoWrite();

        options.writeDoclet(optionWriterMock);
    }

    @Test
    public void testWriteNotNullDocletClass() throws IOException {
        final String docletValue = "org.gradle.CustomDocletClass";
        options.setDoclet(docletValue);
        assertEquals(docletValue, options.getDoclet());

        expectWriteValueOption(CoreJavadocOptions.DOCLET, docletValue);
    }

    @Test
    public void testFluentDocletClasspath() {
        final File[] docletClasspathValue = new File[]{new File("doclet.jar"), new File("doclet-dep.jar")};
        assertEquals(options, options.docletClasspath(docletClasspathValue));
        assertArrayEquals(docletClasspathValue, options.getDocletClasspath().toArray());
    }

    @Test
    public void testWriteEmptyDocletClasspath() throws IOException {
        expectNoWrite();

        options.writeDocletClasspath(optionWriterMock);
    }

    @Test
    public void testWriteNotEmptyDocletClasspath() throws IOException {
        final List<File> docletClasspathValue = Arrays.asList(new File("doclet.jar"), new File("doclet-dep.jar"));
        options.setDocletClasspath(docletClasspathValue);
        assertArrayEquals(docletClasspathValue.toArray(), options.getDocletClasspath().toArray());

        expectWritePathOption(CoreJavadocOptions.DOCLETPATH, System.getProperty("path.separator"), docletClasspathValue);

        options.writeDocletClasspath(optionWriterMock);
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

    private void expectNoWrite() {
        context.checking(new Expectations() {{}});
    }

    private void expectWriteOptionHeader(final String optionName) throws IOException {
        context.checking(new Expectations() {{
            one(optionWriterMock).write("-");
            one(optionWriterMock).write(optionName);
            one(optionWriterMock).write(" ");
        }});
    }

    private void expectWriteOption(final String optionName) throws IOException {
        expectWriteOptionHeader(optionName);
        context.checking(new Expectations() {{
            one(optionWriterMock).newLine();
        }});
    }

    private void expectWriteValueOption(final String optionName, final String value) throws IOException {
        expectWriteOptionHeader(optionName);
        context.checking(new Expectations() {{
            one(optionWriterMock).write(value);
            one(optionWriterMock).newLine();
        }});
    }

    private void expectWritePathOption(final String optionName, final String joiner, final List<File> files) throws IOException {
        expectWriteOptionHeader(optionName);
        final Iterator<File> filesIt = files.iterator();

        while ( filesIt.hasNext() ) {
            final File file = filesIt.next();

            context.checking(new Expectations() {{
                one(optionWriterMock).write(file.getAbsolutePath());
                if ( filesIt.hasNext () ) {
                    one(optionWriterMock).write(joiner);
                }
            }});
        }

        context.checking(new Expectations() {{
            one(optionWriterMock).newLine();
        }});
    }
}
