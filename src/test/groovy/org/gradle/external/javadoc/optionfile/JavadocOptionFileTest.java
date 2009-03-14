package org.gradle.external.javadoc.optionfile;

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author Tom Eyckmans
 */
public class JavadocOptionFileTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private JavadocOptionFileOption optionFileOptionMock;
    private final String optionName = "testOption";
    

    private JavadocOptionFile optionFile;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        optionFileOptionMock = context.mock(JavadocOptionFileOption.class);

        optionFile = new JavadocOptionFile();
    }

    @Test
    public void testDefaults() {
        assertNotNull(optionFile.getOptions());
        assertTrue(optionFile.getOptions().isEmpty());

        assertNotNull(optionFile.getPackageNames());
        assertNotNull(optionFile.getPackageNames().getValue());
        assertTrue(optionFile.getPackageNames().getValue().isEmpty());

        assertNotNull(optionFile.getSourceNames());
        assertNotNull(optionFile.getSourceNames().getValue());
        assertTrue(optionFile.getSourceNames().getValue().isEmpty());
    }

    @Test
    public void testAddOption() {
        context.checking(new Expectations() {{
            one(optionFileOptionMock).getOption();
            returnValue(optionName);
        }});

        optionFile.addOption(optionFileOptionMock);
    }
}
