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

class DefaultExtensionContainerSpec extends Specification {

    @Subject
    DefaultExtensionContainer extensionContainer

    Instantiator instantiator

    def setup() {
        instantiator = TestUtil.instantiatorFactory().decorateLenient()
        extensionContainer = new DefaultExtensionContainer(instantiator)
    }

    def "throws missing property exception for unknown property"() {
        when:
        extensionContainer.extensionsAsDynamicObject.prop

        then:
        thrown(MissingPropertyException)
    }

    def "throws missing method exception for unknown method"() {
        when:
        extensionContainer.extensionsAsDynamicObject.methUnknown()

        then:
        thrown(MissingMethodException)
    }


    def "adds property and configure method for each extension"() {
        when:
        extensionContainer = new DefaultExtensionContainer(instantiator)
        def ext = new FooExtension()
        extensionContainer.add("foo", ext)

        then:
        extensionContainer.extensionsAsDynamicObject.hasProperty("foo")
        extensionContainer.extensionsAsDynamicObject.hasMethod("foo", {})
        extensionContainer.extensionsAsDynamicObject.hasMethod("foo", {} as Action)
        extensionContainer.extensionsAsDynamicObject.properties.get("foo") == ext
    }

    def "can create extensions"() {
        given:
        extensionContainer = new DefaultExtensionContainer(instantiator)

        when:
        FooExtension extension = extensionContainer.create("foo", FooExtension)

        then:
        extension.is(extensionContainer.getByName("foo"))
    }

    def "honours has public type for added extension"() {
        when:
        extensionContainer.add("pet", new ExtensionWithPublicType())

        then:
        publicTypeOf("pet") == typeOf(PublicExtensionType)
    }

    def "honours has public type for created extension"() {
        when:
        extensionContainer.create("pet", ExtensionWithPublicType)

        then:
        publicTypeOf("pet") == typeOf(PublicExtensionType)
    }

    def "create will expose given type as the schema type even when instantiator returns decorated type"() {
        given:
        extensionContainer = new DefaultExtensionContainer(TestUtil.instantiatorFactory().decorateLenient())

        when:
        def result = extensionContainer.create("foo", FooExtension)

        then:
        result.class != FooExtension
        publicTypeOf("foo") == typeOf(FooExtension)
    }

    private TypeOf<?> publicTypeOf(String extension) {
        extensionContainer.extensionsSchema.find { it.name == extension }.publicType
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
