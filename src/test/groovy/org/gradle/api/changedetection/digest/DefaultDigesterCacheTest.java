package org.gradle.api.changedetection.digest;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;

import java.security.MessageDigest;

/**
 * @author Tom Eyckmans
 */
public class DefaultDigesterCacheTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    private final String okDigesterKey = "okKey";

    private DigesterFactory digesterFactoryMock;
    private MessageDigest digesterMock;

    private DefaultDigesterCache digesterCache;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        digesterFactoryMock = context.mock(DigesterFactory.class);
        digesterMock = context.mock(MessageDigest.class);

        digesterCache = new DefaultDigesterCache(digesterFactoryMock);
    }

    @Test ( expected = IllegalArgumentException.class )
    public void nullDigesterFactoryCreationTest() {
        digesterCache = new DefaultDigesterCache(null);
    }

    @Test
    public void testGetDigesterFactory() {
        assertEquals(digesterFactoryMock, digesterCache.getDigesterFactory());
    }

    @Test ( expected = IllegalArgumentException.class )
    public void testGetDigesterForNullKey() {
        digesterCache.getDigester(null);
    }

    @Test ( expected = IllegalArgumentException.class )
    public void testGetDigesterForEmptyKey() {
        digesterCache.getDigester("");
    }

    @Test
    public void testGetDigesterForNewKey() {
        context.checking(new Expectations(){{
            one(digesterFactoryMock).createDigester();will(returnValue(digesterMock));
        }});

        final MessageDigest digester = digesterCache.getDigester(okDigesterKey);

        assertNotNull(digester);
        assertEquals(digester, digesterMock);
    }

    @Test
    public void testGetDigesterForExistingKey() {
        // prepare so the key exists
        context.checking(new Expectations(){{
            one(digesterFactoryMock).createDigester();will(returnValue(digesterMock));
        }});

        MessageDigest digester = digesterCache.getDigester(okDigesterKey);

        assertNotNull(digester);
        assertEquals(digester, digesterMock);

        // get the existing key
        context.checking(new Expectations(){{
            one(digesterMock).reset();
        }});
        digester = digesterCache.getDigester(okDigesterKey);

        assertNotNull(digester);
        assertEquals(digester, digesterMock);
    }
}
