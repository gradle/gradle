/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.logging

import spock.lang.Specification

class ClassMethodNameStackTraceSpecTest extends Specification {
    def "accepts elements whose class and method names match"() {
        def spec = new ClassMethodNameStackTraceSpec("ClassName", "methodName")

        expect:
        spec.isSatisfiedBy(new StackTraceElement("ClassName", "methodName", "SomeFile.java", 42))
        spec.isSatisfiedBy(new StackTraceElement("ClassName", "methodName", "OtherFile.java", 21))
        spec.isSatisfiedBy(new StackTraceElement('ClassName$1', "methodName", "ClassName.java", 21))
        spec.isSatisfiedBy(new StackTraceElement('ClassName$1$22', "methodName", "ClassName.kt", 21))
    }

    def "rejects elements whose class or method name does not match"() {
        def spec = new ClassMethodNameStackTraceSpec("ClassName", "methodName")

        expect:
        !spec.isSatisfiedBy(new StackTraceElement("ClassName", "otherMethodName", "SomeFile.java", 42))
        !spec.isSatisfiedBy(new StackTraceElement("OtherClassName", "methodName", "OtherFile.java", 21))
        !spec.isSatisfiedBy(new StackTraceElement('ClassName$Inner', "otherMethodName", "SomeFile.kt", 42))
    }

    def "allows to only match class name (by setting method name to null)"() {
        def spec = new ClassMethodNameStackTraceSpec("ClassName", null)

        expect:
        spec.isSatisfiedBy(new StackTraceElement("ClassName", "methodName", "SomeFile.java", 42))
        spec.isSatisfiedBy(new StackTraceElement('ClassName$1$1$1', "methodName", "SomeFile.kt", 42))
        spec.isSatisfiedBy(new StackTraceElement("ClassName", "otherMethodName", "SomeFile.java", 42))
        !spec.isSatisfiedBy(new StackTraceElement('ClassName1$1', "otherMethodName", "SomeFile.java", 42))
        !spec.isSatisfiedBy(new StackTraceElement("OtherClassName", "methodName", "OtherFile.java", 21))
        !spec.isSatisfiedBy(new StackTraceElement('ClassName$Inner$1', "methodName", "OtherFile.kt", 21))
    }
}
