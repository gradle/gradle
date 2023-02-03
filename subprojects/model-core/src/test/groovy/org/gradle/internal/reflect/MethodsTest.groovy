/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.reflect;

import spock.lang.Specification

import static Methods.DESCRIPTOR_EQUIVALENCE
import static Methods.SIGNATURE_EQUIVALENCE

class MethodsTest extends Specification  {

    interface BaseType {
        Object someMethodName(Object o)
    }

    interface ChildType extends BaseType {
        @Override String someMethodName(Object o)
        int someMethodName(int i)
    }

    def "distinguish overloaded overridden method signatures"() {
        def takeObjReturnObj = BaseType.declaredMethods[0]
        def takeObjReturnStr = ChildType.declaredMethods[0]
        def takeIntReturnInt = ChildType.declaredMethods[1]

        expect:
        !SIGNATURE_EQUIVALENCE.equivalent(takeObjReturnStr, takeIntReturnInt)
        SIGNATURE_EQUIVALENCE.hash(takeObjReturnStr) != SIGNATURE_EQUIVALENCE.hash(takeIntReturnInt)

        SIGNATURE_EQUIVALENCE.equivalent(takeObjReturnObj, takeObjReturnStr)
        SIGNATURE_EQUIVALENCE.hash(takeObjReturnObj) == SIGNATURE_EQUIVALENCE.hash(takeObjReturnStr)

        SIGNATURE_EQUIVALENCE.equivalent(takeObjReturnObj, takeObjReturnObj)
        SIGNATURE_EQUIVALENCE.hash(takeObjReturnObj) == SIGNATURE_EQUIVALENCE.hash(takeObjReturnStr)
    }

    def "distinguish overloaded overridden method descriptors"() {
        def takeObjReturnObj = BaseType.declaredMethods[0]
        def takeObjReturnStr = ChildType.declaredMethods[0]
        def takeIntReturnInt = ChildType.declaredMethods[1]

        expect:
        !DESCRIPTOR_EQUIVALENCE.equivalent(takeObjReturnStr, takeIntReturnInt)
        DESCRIPTOR_EQUIVALENCE.hash(takeObjReturnStr) != DESCRIPTOR_EQUIVALENCE.hash(takeIntReturnInt)

        !DESCRIPTOR_EQUIVALENCE.equivalent(takeObjReturnObj, takeObjReturnStr)
        DESCRIPTOR_EQUIVALENCE.hash(takeObjReturnObj) != DESCRIPTOR_EQUIVALENCE.hash(takeObjReturnStr)

        DESCRIPTOR_EQUIVALENCE.equivalent(takeObjReturnObj, takeObjReturnObj)
        DESCRIPTOR_EQUIVALENCE.hash(takeObjReturnObj) == DESCRIPTOR_EQUIVALENCE.hash(takeObjReturnObj)
    }
}
