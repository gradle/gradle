/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.extensibility

import org.gradle.api.Action
import org.gradle.api.plugins.Convention
import org.gradle.api.reflect.HasPublicType
import org.gradle.api.reflect.TypeOf
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.TestUtil
import org.junit.Before
import org.junit.Test

import static org.gradle.api.reflect.TypeOf.typeOf
import static org.hamcrest.CoreMatchers.equalTo
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertSame
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

class DefaultConventionTest {
    Convention convention

    TestPluginConvention1 convention1
    TestPluginConvention2 convention2

    Instantiator instantiator = TestUtil.instantiatorFactory().decorateLenient()

    @Before void setUp() {
        convention = new DefaultConvention(instantiator)
        convention1 = new TestPluginConvention1()
        convention2 = new TestPluginConvention2()
        convention.plugins.plugin1 = convention1
        convention.plugins.plugin2 = convention2
    }

    @Test void mixesInEachPropertyOfConventionObject() {
        assertEquals(convention1.b, convention.extensionsAsDynamicObject.b)
    }

    @Test void conventionObjectsPropertiesHavePrecedenceAccordingToOrderAdded() {
        assertEquals(convention1.a, convention.extensionsAsDynamicObject.a)
    }

    @Test void canSetConventionObjectProperties() {
        convention.extensionsAsDynamicObject.b = 'newvalue'
        assertEquals('newvalue', convention1.b)
    }

    @Test void canSetPropertiesWithAmbiguity() {
        convention.extensionsAsDynamicObject.a = 'newvalue'
        assertEquals('newvalue', convention1.a)
    }

    @Test(expected = MissingPropertyException) void throwsMissingPropertyExceptionForUnknownProperty() {
        convention.extensionsAsDynamicObject.prop
    }

    @Test void mixesInEachMethodOfConventionObject() {
        assertEquals(convention1.meth('somearg'), convention.extensionsAsDynamicObject.meth('somearg'))
    }

    @Test void conventionObjectsMethodsHavePrecedenceAccordingToOrderAdded() {
        assertEquals(convention1.meth(), convention.extensionsAsDynamicObject.meth())
    }

    @Test(expected = MissingMethodException) void testMissingMethod() {
        convention.extensionsAsDynamicObject.methUnknown()
    }

    @Test void testCanLocateConventionObjectByType() {
        assertSame(convention1, convention.getPlugin(TestPluginConvention1))
        assertSame(convention2, convention.getPlugin(TestPluginConvention2))
        assertSame(convention1, convention.findPlugin(TestPluginConvention1))
        assertSame(convention2, convention.findPlugin(TestPluginConvention2))
    }

    @Test void testGetPluginFailsWhenMultipleConventionObjectsWithCompatibleType() {
        try {
            convention.getPlugin(Object)
            fail()
        } catch (java.lang.IllegalStateException e) {
            assertThat(e.message, equalTo('Found multiple convention objects of type Object.'))
        }
    }

    @Test void testFindPluginFailsWhenMultipleConventionObjectsWithCompatibleType() {
        try {
            convention.getPlugin(Object)
            fail()
        } catch (java.lang.IllegalStateException e) {
            assertThat(e.message, equalTo('Found multiple convention objects of type Object.'))
        }
    }

    @Test void testGetPluginFailsWhenNoConventionObjectsWithCompatibleType() {
        try {
            convention.getPlugin(String)
            fail()
        } catch (java.lang.IllegalStateException e) {
            assertThat(e.message, equalTo('Could not find any convention object of type String.'))
        }
    }

    @Test void testFindPluginReturnsNullWhenNoConventionObjectsWithCompatibleType() {
        assertNull(convention.findPlugin(String))
    }

    @Test void addsPropertyAndConfigureMethodForEachExtension() {
        //when
        convention = new DefaultConvention(instantiator)
        def ext = new FooExtension()
        convention.add("foo", ext)

        //then
        assertTrue(convention.extensionsAsDynamicObject.hasProperty("foo"))
        assertTrue(convention.extensionsAsDynamicObject.hasMethod("foo", {}))
        assertTrue(convention.extensionsAsDynamicObject.hasMethod("foo", {} as Action))
        assertEquals(convention.extensionsAsDynamicObject.properties.get("foo"), ext)
    }

    @Test void extensionsTakePrecedenceOverPluginConventions() {
        convention = new DefaultConvention(instantiator)
        convention.plugins.foo = new FooPluginExtension()
        convention.add("foo", new FooExtension())

        assertTrue(convention.extensionsAsDynamicObject.properties.get("foo") instanceof FooExtension);
        assertTrue(convention.extensionsAsDynamicObject.foo instanceof FooExtension);
        convention.extensionsAsDynamicObject.foo {
            assertEquals("Hello world!", message);
        }
    }

    @Test void canCreateExtensions() {
        convention = new DefaultConvention(instantiator)
        FooExtension extension = convention.create("foo", FooExtension)
        assert extension.is(convention.getByName("foo"))
    }

    @Test void honoursHasPublicTypeForAddedExtension() {
        convention.add("pet", new ExtensionWithPublicType())
        assert publicTypeOf("pet") == typeOf(PublicExtensionType)
    }

    @Test void honoursHasPublicTypeForCreatedExtension() {
        convention.create("pet", ExtensionWithPublicType)
        assert publicTypeOf("pet") == typeOf(PublicExtensionType)
    }

    @Test void createWillExposeGivenTypeAsTheSchemaTypeEvenWhenInstantiatorReturnsDecoratedType() {
        convention = new DefaultConvention(TestUtil.instantiatorFactory().decorateLenient())
        assert convention.create("foo", FooExtension).class != FooExtension
        assert publicTypeOf("foo") == typeOf(FooExtension)
    }

    private TypeOf<?> publicTypeOf(String extension) {
        convention.extensionsSchema.find { it.name == extension }.publicType
    }

    interface PublicExtensionType {
    }

    static class ExtensionWithPublicType implements HasPublicType {
        @Override
        TypeOf<?> getPublicType() {
            typeOf(PublicExtensionType)
        }
    }

    static class FooExtension {
        String message = "Hello world!";
    }

    static class FooPluginExtension {
        String foo = "foo"

        void foo(Closure closure) {
            fail("should not be called");
        }
    }
}
