/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.reflect

import org.gradle.internal.UncheckedException
import org.gradle.testing.internal.util.Specification

import static org.gradle.internal.reflect.JavaMethod.method
import static org.gradle.internal.reflect.JavaMethod.staticMethod

class JavaMethodTest extends Specification {

    JavaTestSubject myProperties = new JavaTestSubject()

    def "call methods successfully reflectively"() {
        expect:
        method(myProperties.class, String, "getMyProperty").invoke(myProperties) == myProperties.myProp
        method(myProperties.class, String, "doSomeStuff", int.class, Integer.class).invoke(myProperties, 1, 2) == "1.2"

        when:
        method(myProperties.class, Void, "setMyProperty", String).invoke(myProperties, "foo")

        then:
        method(myProperties.class, String, "getMyProperty").invoke(myProperties) == "foo"
    }

    def "call static methods successfully reflectively" () {
        when:
        staticMethod(myProperties.class, Void, "setStaticProperty", String.class).invokeStatic("foo")

        then:
        staticMethod(myProperties.class, String, "getStaticProperty").invokeStatic() == "foo"
    }

    def "static methods are identifiable" () {
        expect:
        staticMethod(myProperties.class, Void, "setStaticProperty", String.class).isStatic()
        staticMethod(myProperties.class, String, "getStaticProperty").isStatic()
        !method(myProperties.class, String, "getMyProperty").isStatic()
    }

    def "call failing methods reflectively"() {
        when:
        method(myProperties.class, Void, "throwsException").invoke(myProperties)

        then:
        IllegalStateException e = thrown()
        e == myProperties.failure

        when:
        method(myProperties.class, Void, "throwsCheckedException").invoke(myProperties)

        then:
        UncheckedException checkedFailure = thrown()
        checkedFailure.cause instanceof JavaTestSubject.TestCheckedException
        checkedFailure.cause.cause == myProperties.failure
    }

    def "call declared method that may not be public"() {
        expect:
        method(JavaTestSubjectSubclass, String, "protectedMethod").invoke(new JavaTestSubjectSubclass()) == "parent"
        method(JavaTestSubjectSubclass, String, "overridden").invoke(new JavaTestSubjectSubclass()) == "subclass"
    }

    def "cannot call unknown method"() {
        when:
        method(JavaTestSubjectSubclass, String, "unknown")

        then:
        NoSuchMethodException e = thrown()
        e.message == /Could not find method unknown() on JavaTestSubjectSubclass./
    }

}
