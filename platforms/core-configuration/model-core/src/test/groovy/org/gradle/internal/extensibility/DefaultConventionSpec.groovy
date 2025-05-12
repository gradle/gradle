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
import org.gradle.api.reflect.HasPublicType
import org.gradle.api.reflect.TypeOf
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.reflect.TypeOf.typeOf

class DefaultConventionSpec extends Specification {

    @Subject
    DefaultExtensionContainer convention

    TestPluginConvention1 convention1
    TestPluginConvention2 convention2

    Instantiator instantiator

    def setup() {
        instantiator = TestUtil.instantiatorFactory().decorateLenient()
        convention = new DefaultExtensionContainer(instantiator)
//        convention1 = new TestPluginConvention1()
//        convention2 = new TestPluginConvention2()
//        convention.plugins.plugin1 = convention1
//        convention.plugins.plugin2 = convention2
    }

//    def "mixes in each property of convention object"() {
//        expect:
//        convention1.b == convention.extensionsAsDynamicObject.b
//    }

//    def "convention objects properties have precedence according to order added"() {
//        expect:
//        convention1.a == convention.extensionsAsDynamicObject.a
//    }
//
//    def "can set convention object properties"() {
//        when:
//        convention.extensionsAsDynamicObject.b = 'newvalue'
//
//        then:
//        convention1.b == 'newvalue'
//    }

//    def "can set properties with ambiguity"() {
//        when:
//        convention.extensionsAsDynamicObject.a = 'newvalue'
//
//        then:
//        convention1.a == 'newvalue'
//    }

    def "throws missing property exception for unknown property"() {
        when:
        convention.extensionsAsDynamicObject.prop

        then:
        thrown(MissingPropertyException)
    }

//    def "mixes in each method of convention object"() {
//        expect:
//        convention1.meth('somearg') == convention.extensionsAsDynamicObject.meth('somearg')
//    }
//
//    def "convention objects methods have precedence according to order added"() {
//        expect:
//        convention1.meth() == convention.extensionsAsDynamicObject.meth()
//    }

    def "throws missing method exception for unknown method"() {
        when:
        convention.extensionsAsDynamicObject.methUnknown()

        then:
        thrown(MissingMethodException)
    }

//    def "can locate convention object by type"() {
//        expect:
//        convention.getPlugin(TestPluginConvention1).is(convention1)
//        convention.getPlugin(TestPluginConvention2).is(convention2)
//        convention.findPlugin(TestPluginConvention1).is(convention1)
//        convention.findPlugin(TestPluginConvention2).is(convention2)
//    }

//    def "get plugin fails when multiple convention objects with compatible type"() {
//        when:
//        convention.getPlugin(Object)
//
//        then:
//        def e = thrown(IllegalStateException)
//        e.message == 'Found multiple convention objects of type Object.'
//    }
//
//    def "find plugin fails when multiple convention objects with compatible type"() {
//        when:
//        convention.getPlugin(Object)
//
//        then:
//        def e = thrown(IllegalStateException)
//        e.message == 'Found multiple convention objects of type Object.'
//    }

//    def "get plugin fails when no convention objects with compatible type"() {
//        when:
//        convention.getPlugin(String)
//
//        then:
//        def e = thrown(IllegalStateException)
//        e.message == 'Could not find any convention object of type String.'
//    }
//
//    def "find plugin returns null when no convention objects with compatible type"() {
//        expect:
//        convention.findPlugin(String) == null
//    }

    def "adds property and configure method for each extension"() {
        when:
        convention = new DefaultExtensionContainer(instantiator)
        def ext = new FooExtension()
        convention.add("foo", ext)

        then:
        convention.extensionsAsDynamicObject.hasProperty("foo")
        convention.extensionsAsDynamicObject.hasMethod("foo", {})
        convention.extensionsAsDynamicObject.hasMethod("foo", {} as Action)
        convention.extensionsAsDynamicObject.properties.get("foo") == ext
    }

//    def "extensions take precedence over plugin conventions"() {
//        given:
//        convention = new DefaultExtensionContainer(instantiator)
//        convention.plugins.foo = new FooPluginExtension()
//
//        when:
//        convention.add("foo", new FooExtension())
//
//        then:
//        convention.extensionsAsDynamicObject.properties.get("foo") instanceof FooExtension
//        convention.extensionsAsDynamicObject.foo instanceof FooExtension
//
//        and:
//        convention.extensionsAsDynamicObject.foo {
//            assert message == "Hello world!"
//        }
//    }

    def "can create extensions"() {
        given:
        convention = new DefaultExtensionContainer(instantiator)

        when:
        FooExtension extension = convention.create("foo", FooExtension)

        then:
        extension.is(convention.getByName("foo"))
    }

    def "honours has public type for added extension"() {
        when:
        convention.add("pet", new ExtensionWithPublicType())

        then:
        publicTypeOf("pet") == typeOf(PublicExtensionType)
    }

    def "honours has public type for created extension"() {
        when:
        convention.create("pet", ExtensionWithPublicType)

        then:
        publicTypeOf("pet") == typeOf(PublicExtensionType)
    }

    def "create will expose given type as the schema type even when instantiator returns decorated type"() {
        given:
        convention = new DefaultExtensionContainer(TestUtil.instantiatorFactory().decorateLenient())

        when:
        def result = convention.create("foo", FooExtension)

        then:
        result.class != FooExtension
        publicTypeOf("foo") == typeOf(FooExtension)
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
        String message = "Hello world!"
    }

    static class FooPluginExtension {
        String foo = "foo"

        void foo(Closure closure) {
            assert false : "should not be called"
        }
    }
}
