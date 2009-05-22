package org.gradle.api.changedetection.digest;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.security.MessageDigest;

/**
 * @author Tom Eyckmans
 */
public class ShaDigesterFactoryTest {
    private ShaDigesterFactory digesterFactory;

    @Before
    public void setUp() {
        digesterFactory = new ShaDigesterFactory();
    }

    @Test
    public void createDigester() {

        final MessageDigest digester = digesterFactory.createDigester();

        assertNotNull(digester);
        assertEquals("SHA-1", digester.getAlgorithm());
    }
}
