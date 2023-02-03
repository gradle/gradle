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

package org.gradle.java.compile.incremental

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.util.internal.TextUtil

class JavaClassChangeCliIncrementalCompilationIntegrationTest extends BaseJavaClassChangeIncrementalCompilationIntegrationTest {

    def setup() {
        buildFile << """
            tasks.withType(JavaCompile) {
                options.fork = true
                options.forkOptions.executable = '${TextUtil.escapeString(AvailableJavaHomes.getJdk(JavaVersion.current()).javacExecutable)}'
            }
        """
    }

    def "changing an unused non-private constant incurs full rebuild"() {
        source "class A { int foo() { return 2; } }", "class B { final static int x = 1;}"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class B { /* change */ }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'B', 'A'
    }

    /**
     * Test scenario from {@link BaseIncrementalCompilationAfterFailureIntegrationTest#detects deletion of a source base class that leads to compilation failure but keeps old files()}
     * but for the CLI compiler where incremental compilation after failure is not supported.
     */
    def "detects deletion of a source base class that leads to compilation failure"() {
        def a = source "class A {}"
        source "class B extends A {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        assert a.delete()
        then:
        fails language.compileTaskName
        outputs.noneRecompiled()
        outputs.deletedClasses 'A', 'B'
    }

    /**
     * Test scenario from {@link BaseIncrementalCompilationAfterFailureIntegrationTest#incremental compilation works after a compile failure()}
     * but for the CLI compiler where incremental compilation after failure is not supported.
     */
    def "does full recompilation after a failure even if incrementalAfterFailure is set to true"() {
        given:
        buildFile << """
        tasks.withType(JavaCompile) {
            options.incremental = true
            options.incrementalAfterFailure = true
        }
        """
        source "class A {}", "class B extends A {}", "class C {}"

        when: "First compilation is always full compilation"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "C")

        when: "Compilation after failure is full recompilation when optimization is disabled"
        outputs.snapshot { source("class A { garbage }") }
        runAndFail language.compileTaskName
        outputs.snapshot { source("class A {}") }
        run language.compileTaskName, "--info"

        then:
        outputs.recompiledClasses("A", "B", "C")
        outputContains("Full recompilation is required")
    }
}
