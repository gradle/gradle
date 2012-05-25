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

class GroovyStackTraceSpecTest extends Specification {
    def "removes Groovy internals from stack trace"() {
        def spec = new GroovyStackTraceSpec()
        def filter = new StackTraceFilter(spec)
        def trace = createTrace()

        when:
        def filtered = filter.filter(trace)

        then:
        filtered.size() == 7
        filtered[0] == new StackTraceElement("org.gradle.api.internal.tasks.testing.logging.GroovyStackTraceSpecTest\$Baz", "getBoom", "GroovyStackTraceSpecTest.groovy", 74)
        filtered[1] == new StackTraceElement("org.gradle.api.internal.tasks.testing.logging.GroovyStackTraceSpecTest\$SuperBaz", "setBaz", "GroovyStackTraceSpecTest.groovy", 62)
        filtered[2] == new StackTraceElement("org.gradle.api.internal.tasks.testing.logging.GroovyStackTraceSpecTest\$Baz", "setBaz", "GroovyStackTraceSpecTest.groovy", 70)
        filtered[3] == new StackTraceElement("org.gradle.api.internal.tasks.testing.logging.GroovyStackTraceSpecTest\$Bar", "bar", "GroovyStackTraceSpecTest.groovy", 56)
        filtered[4] == new StackTraceElement("org.gradle.api.internal.tasks.testing.logging.GroovyStackTraceSpecTest", "foo", "GroovyStackTraceSpecTest.groovy", 51)
        filtered[5] == new StackTraceElement("org.gradle.api.internal.tasks.testing.logging.GroovyStackTraceSpecTest", "createTrace", "GroovyStackTraceSpecTest.groovy", 42)
    }

    private List<StackTraceElement> createTrace() {
        try {
            foo()
            throw new AssertionError()
        } catch (Exception e) {
            def filter = new StackTraceFilter(new TruncatedStackTraceSpec(new ClassMethodNameStackTraceSpec(getClass().getName(), null)))
            return filter.filter(e)
        }
    }

    private void foo() {
        Bar.bar()
    }

    static class Bar {
        static void bar() {
            new Baz().baz = 42
        }
    }

    static abstract class SuperBaz {
        void setBaz(int baz) {
            boom
        }

        abstract String getBoom()
    }

    static class Baz extends SuperBaz {
        void setBaz(int baz) {
            super.setBaz(baz)
        }

        String getBoom() {
            throw new Exception("boom")
        }
    }
}
