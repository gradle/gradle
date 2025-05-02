/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.service

import org.gradle.internal.service.internal.ModifierStubs
import spock.lang.Specification

class AccessModifierDefaultServiceRegistryTest extends Specification {

    def "can add type to registry that has package private constructor"() {
        when:
        def registry = new DefaultServiceRegistry()
        registry.register {
            it.add(String, "Potato")
            it.add(ModifierStubs.PackagePrivateConstructor)
        }
        then:
        noExceptionThrown()
        registry.get(ModifierStubs.PackagePrivateConstructor) instanceof ModifierStubs.PackagePrivateConstructor
    }

    def "fails to register that has a package private constructor and a private constructor"() {
        when:
        def registry = new DefaultServiceRegistry()
        registry.register {
            it.add(ModifierStubs.PackagePrivateConstructorWithPrivateConstructor)
        }
        then:
        def exception = thrown(ServiceValidationException)
        exception.message.contains("Expected a single non-private constructor, or one constructor annotated with @Inject for")
    }

    def "can add a type to registry that has an annotated package private constructor and a private constructor"() {
        when:
        def registry = new DefaultServiceRegistry()
        registry.register {
            it.add(ModifierStubs.AnnotatedPackagePrivateConstructorWithPrivateConstructor)
        }
        then:
        noExceptionThrown()
        registry.get(ModifierStubs.AnnotatedPackagePrivateConstructorWithPrivateConstructor) instanceof ModifierStubs.AnnotatedPackagePrivateConstructorWithPrivateConstructor
    }

    def "fails to register that has a public constructor and a private constructor"() {
        when:
        def registry = new DefaultServiceRegistry()
        registry.register {
            it.add(ModifierStubs.PublicConstructorWithPrivateConstructor)
        }
        then:
        def exception = thrown(ServiceValidationException)
        exception.message.contains("Expected a single non-private constructor, or one constructor annotated with @Inject for")
    }

    def "can add a type to registry that has an annotated public constructor and a private constructor"() {
        when:
        def registry = new DefaultServiceRegistry()
        registry.register {
            it.add(ModifierStubs.AnnotatedPublicConstructorWithPrivateConstructor)
        }
        then:
        noExceptionThrown()
        registry.get(ModifierStubs.AnnotatedPublicConstructorWithPrivateConstructor) instanceof ModifierStubs.AnnotatedPublicConstructorWithPrivateConstructor
    }

    def "fails to register class that is non-static inner class"() {
        when:
        def registry = new DefaultServiceRegistry()
        registry.register {
            it.add(ModifierStubs.NonStaticInnerClass)
        }
        then:
        def exception = thrown(ServiceValidationException)
        exception.message.contains("Unable to select constructor for non-static inner class")
    }

    def "can add a type to registry that has just the default constructor without one being explicitly declared"() {
        when:
        def registry = new DefaultServiceRegistry()
        registry.register {
            it.add(ModifierStubs.DefaultConstructor)
        }
        then:
        noExceptionThrown()
        registry.get(ModifierStubs.DefaultConstructor) instanceof ModifierStubs.DefaultConstructor
    }

}
