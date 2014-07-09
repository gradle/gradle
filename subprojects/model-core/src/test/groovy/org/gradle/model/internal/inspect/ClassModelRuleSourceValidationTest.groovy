/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.inspect

import org.gradle.model.InvalidModelRuleDeclarationException
import spock.lang.Specification
import spock.lang.Unroll

class ClassModelRuleSourceValidationTest extends Specification {

    abstract static class AbstractClass {}

    static interface AnInterface {}

    class InnerInstanceClass {}

    private class PrivateInnerStaticClass {}

    static class HasSuperclass extends InnerPublicStaticClass {}

    static class HasTwoConstructors {
        HasTwoConstructors() {
        }

        HasTwoConstructors(String arg) {
        }
    }

    static class HasInstanceVar {
        String foo
    }

    static class HasFinalInstanceVar {
        final String foo = null
    }

    static class HasNonFinalStaticVar {
        static String foo = null
    }

    @Unroll
    def "invalid #type - #reason"() {
        when:
        new ModelRuleInspector().validate(type)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        def message = e.message
        def actualReason = message.split(":", 2)[1].trim()
        actualReason == reason

        where:
        type                       | reason
        AbstractClass              | "class cannot be abstract"
        AnInterface                | "must be a class, not an interface"
        InnerInstanceClass         | "enclosed classes must be static and non private"
        new Object() {}.getClass() | "enclosed classes must be static and non private"
        HasSuperclass              | "cannot have superclass"
        HasTwoConstructors         | "cannot have more than one constructor"
        HasInstanceVar             | "field foo is not static final"
        HasFinalInstanceVar        | "field foo is not static final"
        HasNonFinalStaticVar       | "field foo is not static final"
    }

    static class InnerPublicStaticClass {}

    static class HasExplicitDefaultConstructor {
        HasExplicitDefaultConstructor() {
        }
    }

    static class HasStaticFinalField {
        static final VALUE = null
    }

    @Unroll
    def "valid #type"() {
        when:
        new ModelRuleInspector().validate(type)

        then:
        noExceptionThrown()

        where:
        type << [
                InnerPublicStaticClass,
                HasExplicitDefaultConstructor,
                HasStaticFinalField
        ]
    }
}
