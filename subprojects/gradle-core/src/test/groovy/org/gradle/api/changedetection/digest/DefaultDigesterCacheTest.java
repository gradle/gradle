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
