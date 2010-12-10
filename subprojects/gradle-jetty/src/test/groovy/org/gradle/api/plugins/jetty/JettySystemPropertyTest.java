/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.plugins.jetty;

import org.gradle.api.InvalidUserDataException;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Test case for TomcatSystemPropertyTest.
 *
 * @author Benjamin Muschko
 */
public class JettySystemPropertyTest
{
    @After
    public void tearDown() {
        System.clearProperty(JettySystemProperty.HTTP_PORT_SYSPROPERTY);
        System.clearProperty(JettySystemProperty.STOP_PORT_SYSPROPERTY);
        System.clearProperty(JettySystemProperty.STOP_KEY_SYSPROPERTY);
    }

    @Test
    public void testGetHttpPortForNonExistingProperty() {
        assertNull(JettySystemProperty.getHttpPort());
    }

    @Test(expected = InvalidUserDataException.class)
    public void testGetHttpPortForInvalidExistingProperty() {
        System.setProperty(JettySystemProperty.HTTP_PORT_SYSPROPERTY, "xxx");
        JettySystemProperty.getHttpPort();
    }

    @Test
    public void testGetHttpPortForValidExistingProperty() {
        System.setProperty(JettySystemProperty.HTTP_PORT_SYSPROPERTY, "1234");
        assertEquals(new Integer(1234), JettySystemProperty.getHttpPort());
    }

    @Test
    public void testGetStopPortForNonExistingProperty() {
        assertNull(JettySystemProperty.getStopPort());
    }

    @Test(expected = InvalidUserDataException.class)
    public void testGetStopPortForInvalidExistingProperty() {
        System.setProperty(JettySystemProperty.STOP_PORT_SYSPROPERTY, "xxx");
        JettySystemProperty.getStopPort();
    }

    @Test
    public void testGetStopPortForValidExistingProperty() {
        System.setProperty(JettySystemProperty.STOP_PORT_SYSPROPERTY, "1234");
        assertEquals(new Integer(1234), JettySystemProperty.getStopPort());
    }

    @Test
    public void testGetStopKeyForNonExistingProperty() {
        assertNull(JettySystemProperty.getStopKey());
    }

    @Test
    public void testGetStopKeyForValidExistingProperty() {
        System.setProperty(JettySystemProperty.STOP_KEY_SYSPROPERTY, "X");
        assertEquals("X", JettySystemProperty.getStopKey());
    }
}
