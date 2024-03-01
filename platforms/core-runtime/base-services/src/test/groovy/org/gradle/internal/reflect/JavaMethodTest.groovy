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
import spock.lang.Specification

class JavaMethodTest extends Specification {

    JavaMethodTestSubject myProperties = new JavaMethodTestSubject()

    def "call methods successfully reflectively"() {
        expect:
        JavaMethod.of(myProperties.class, String, "getMyProperty").invoke(myProperties) == myProperties.myProp
        JavaMethod.of(myProperties.class, String, "doSomeStuff", int.class, Integer.class).invoke(myProperties, 1, 2) == "1.2"

        when:
        JavaMethod.of(myProperties.class, Void, "setMyProperty", String).invoke(myProperties, "foo")

        then:
        JavaMethod.of(myProperties.class, String, "getMyProperty").invoke(myProperties) == "foo"
    }

    def "call static methods successfully reflectively" () {
        when:
        JavaMethod.ofStatic(myProperties.class, Void, "setStaticProperty", String.class).invokeStatic("foo")

        then:
        JavaMethod.ofStatic(myProperties.class, String, "getStaticProperty").invokeStatic() == "foo"
    }

    def "static methods are identifiable" () {
        expect:
        JavaMethod.ofStatic(myProperties.class, Void, "setStaticProperty", String.class).isStatic()
        JavaMethod.ofStatic(myProperties.class, String, "getStaticProperty").isStatic()
        !JavaMethod.of(myProperties.class, String, "getMyProperty").isStatic()
    }

    def "call failing methods reflectively"() {
        when:
        JavaMethod.of(myProperties.class, Void, "throwsException").invoke(myProperties)

        then:
        IllegalStateException e = thrown()
        e == myProperties.failure

        when:
        JavaMethod.of(myProperties.class, Void, "throwsCheckedException").invoke(myProperties)

        then:
        UncheckedException checkedFailure = thrown()
        checkedFailure.cause instanceof JavaMethodTestSubject.TestCheckedException
        checkedFailure.cause.cause == myProperties.failure
    }

    def "call declared method that may not be public"() {
        expect:
        JavaMethod.of(JavaMethodTestSubjectSubclass, String, "protectedMethod").invoke(new JavaMethodTestSubjectSubclass()) == "parent"
        JavaMethod.of(JavaMethodTestSubjectSubclass, String, "overridden").invoke(new JavaMethodTestSubjectSubclass()) == "subclass"
    }

    def "cannot call unknown method"() {
        when:
        JavaMethod.of(JavaMethodTestSubjectSubclass, String, "unknown")

        then:
        NoSuchMethodException e = thrown()
        e.message == /Could not find method unknown() on JavaMethodTestSubjectSubclass./
    }

    def "default methods are discovered"() {
        expect:
        JavaMethod.of(JavaMethodTestSubjectSubclass, String, "defaultMethod").invoke(new JavaMethodTestSubjectSubclass()) == "parent-interface"
    }
}
