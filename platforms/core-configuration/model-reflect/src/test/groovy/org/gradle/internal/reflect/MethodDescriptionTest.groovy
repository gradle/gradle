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

package org.gradle.internal.reflect

import spock.lang.Specification

class MethodDescriptionTest extends Specification {

    def "string"() {
        expect:
        MethodDescription.name("a").toString() == "a"
        MethodDescription.name("a").returns(String).toString() == "java.lang.String a"
        MethodDescription.name("a").returns(String).takes().toString() == "java.lang.String a()"
        MethodDescription.name("a").returns(String).owner(String).takes().toString() == "java.lang.String java.lang.String#a()"
        MethodDescription.name("a").returns(String).owner(String).takes(String).toString() == "java.lang.String java.lang.String#a(java.lang.String)"
        MethodDescription.name("a").returns(String).owner(String).takes(String, String).toString() == "java.lang.String java.lang.String#a(java.lang.String, java.lang.String)"
        MethodDescription.name("a").owner(String).takes(String, String).toString() == "java.lang.String#a(java.lang.String, java.lang.String)"
    }

    def "inner class name format"() {
        expect:
        MethodDescription.name('a').owner(Outer.Inner).toString() == 'org.gradle.internal.reflect.MethodDescriptionTest$Outer$Inner#a'
    }

    class Outer {
        static class Inner {

        }
    }
}
