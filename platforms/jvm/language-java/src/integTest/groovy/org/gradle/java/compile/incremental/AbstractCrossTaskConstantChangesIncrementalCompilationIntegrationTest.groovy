/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.java.compile.incremental

import groovy.test.NotYetImplemented

abstract class AbstractCrossTaskConstantChangesIncrementalCompilationIntegrationTest extends AbstractCrossTaskIncrementalCompilationSupport {

    @NotYetImplemented
    // Currently not implemented, since it's expensive to track constants and their values
    def "ignores irrelevant changes to constant values"() {
        source api: ["class A {}", "class B { final static int x = 3; final static int y = -2; }"],
            impl: ["class X { int foo() { return B.x; }}", "class Y {int foo() { return B.y; }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static int x = 3 ; final static int y = -3;  void blah() { /*  change irrelevant to constant value x */ } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('Y')
    }

    def "change in an upstream transitive class with non-private constant does not cause full rebuild"() {
        source api: ["class A { final static int x = 1; }", "class B extends A {}"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* change */ }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('ImplB')
    }

    def "private constant in upstream project does not trigger full rebuild"() {
        source api: ["class A {}", "class B { private final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* change */ }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    def "detects that changed class still has the same constants so no recompile is necessary"() {
        source api: ["class A { public static final int FOO = 123;}"],
            impl: ["class B { void foo() { int x = 123; }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { public static final int FOO = 123; void addSomeRandomMethod() {} }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }
}
