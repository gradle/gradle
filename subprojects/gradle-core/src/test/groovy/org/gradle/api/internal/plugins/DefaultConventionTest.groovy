/*
 * Copyright 2007-2008 the original author or authors.
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

package org.gradle.api.internal.plugins

import org.gradle.api.plugins.Convention
import org.gradle.api.plugins.TestPluginConvention1
import org.gradle.api.plugins.TestPluginConvention2
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*

/**
 * @author Hans Dockter
 */
class DefaultConventionTest {
    Convention convention

    TestPluginConvention1 convention1
    TestPluginConvention2 convention2

    @Before public void setUp() {
        convention = new DefaultConvention()
        convention1 = new TestPluginConvention1()
        convention2 = new TestPluginConvention2()
        convention.plugins.plugin1 = convention1
        convention.plugins.plugin2 = convention2
    }

    @Test public void testGetProperty() {
        assertEquals(convention1.a, convention.plugins.plugin1.a)
        assertEquals(convention1.a, convention.a)
    }

    @Test public void testGetPropertiesWithAmbiguity() {
        assertEquals(convention1.a, convention.plugins.plugin1.a)
        assertEquals(convention2.a, convention.plugins.plugin2.a)
        assertEquals(convention1.a, convention.a)
    }

    @Test public void testGetAllProperties() {
        assertEquals(convention1.a, convention.properties.a)
        assertEquals(convention1.b, convention.properties.b)
        assertEquals(convention1.c, convention.properties.c)
    }

    @Test public void testSetProperties() {
        convention.b = 'newvalue'
        assertEquals('newvalue', convention.plugins.plugin1.b)
    }

    @Test public void testSetPropertiesWithAmbiguity() {
        convention.a = 'newvalue'
        assertEquals('newvalue', convention1.a)
    }

    @Test (expected = MissingPropertyException) public void testMissingPropertiesWithGet() {
        convention.prop
    }

    @Test(expected = MissingPropertyException) public void testMissingPropertiesWithSet() {
        convention.prop = 'newvalue'
    }

    @Test public void testMethods() {
        assertEquals(convention1.meth('somearg'), convention.plugins.plugin1.meth('somearg'))
        assertEquals(convention1.meth('somearg'), convention.meth('somearg'))
    }

    @Test public void testMethodsWithAmbiguity() {
        assertEquals(convention1.meth(), convention.plugins.plugin1.meth())
        assertEquals(convention2.meth(), convention.plugins.plugin2.meth())
        assertEquals(convention.meth(), convention1.meth())
    }

    @Test (expected = MissingMethodException) public void testMissingMethod() {
        convention.methUnknown()
    }

    @Test public void testCanLocateConventionObjectByType() {
        assertSame(convention1, convention.getPlugin(TestPluginConvention1))
        assertSame(convention2, convention.getPlugin(TestPluginConvention2))
        assertSame(convention1, convention.findPlugin(TestPluginConvention1))
        assertSame(convention2, convention.findPlugin(TestPluginConvention2))
    }
    
    @Test public void testGetPluginFailsWhenMultipleConventionObjectsWithCompatibleType() {
        try {
            convention.getPlugin(Object)
            fail()
        } catch (java.lang.IllegalStateException e) {
            assertThat(e.message, equalTo('Found multiple convention objects of type Object.'))
        }
    }

    @Test public void testFindPluginFailsWhenMultipleConventionObjectsWithCompatibleType() {
        try {
            convention.getPlugin(Object)
            fail()
        } catch (java.lang.IllegalStateException e) {
            assertThat(e.message, equalTo('Found multiple convention objects of type Object.'))
        }
    }

    @Test public void testGetPluginFailsWhenNoConventionObjectsWithCompatibleType() {
        try {
            convention.getPlugin(String)
            fail()
        } catch (java.lang.IllegalStateException e) {
            assertThat(e.message, equalTo('Could not find any convention object of type String.'))
        }
    }
    
    @Test public void testFindPluginReturnsNullWhenNoConventionObjectsWithCompatibleType() {
        assertNull(convention.findPlugin(String))
    }
}
