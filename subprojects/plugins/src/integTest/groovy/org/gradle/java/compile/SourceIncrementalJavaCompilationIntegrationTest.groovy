/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.java.compile;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.CompilationOutputsFixture;

public class SourceIncrementalJavaCompilationIntegrationTest extends AbstractIntegrationSpec {

    CompilationOutputsFixture outputs

    def setup() {
        outputs = new CompilationOutputsFixture(file("build/classes"))

        buildFile << """
            apply plugin: 'java'
            compileJava.options.incremental = true
        """
    }

    private File java(String ... classBodies) {
        File out
        for (String body : classBodies) {
            def className = (body =~ /(?s).*?class (\w+) .*/)[0][1]
            assert className: "unable to find class name"
            def f = file("src/main/java/${className}.java")
            f.createFile()
            f.text = body
            out = f
        }
        out
    }

    def "detects deletion of an isolated source class with an inner class"() {
        def a = java """class A {
            class InnerA {}
        }"""
        java "class B {}"

        outputs.snapshot { run "compileJava" }

        when:
        assert a.delete()
        run "compileJava"

        then:
        outputs.noneRecompiled() //B is not recompiled
        outputs.deletedClasses 'A', 'A$InnerA' //inner class is also deleted
    }

    def "detects deletion of a source base class that leads to compilation failure"() {
        def a = java "class A {}"
        java "class B extends A {}"

        outputs.snapshot { run "compileJava" }

        when: assert a.delete()
        then:
        fails "compileJava"
        outputs.noneRecompiled()
        outputs.deletedClasses 'A', 'B'
    }

    def "detects change of an isolated source class with an inner class"() {
        java """class A {
            class InnerA {}
        }"""
        java "class B {}"

        outputs.snapshot { run "compileJava" }

        when:
        java """class A {
            class InnerA { /* change */ }
        }"""
        run "compileJava"

        then:
        outputs.recompiledClasses 'A', 'A$InnerA'
    }

    def "detects change of an isolated class"() {
        java "class A {}", "class B {}"

        outputs.snapshot { run "compileJava" }

        when:
        java "class A { /* change */ }"
        run "compileJava"

        then:
        outputs.recompiledClasses 'A'
    }

    def "detects deletion of an inner class"() {
        java """class A {
            class InnerA {}
        }"""
        java "class B {}"

        outputs.snapshot { run "compileJava" }

        when:
        java "class A {}"
        run "compileJava"

        then:
        outputs.recompiledClasses 'A'
        outputs.deletedClasses 'A$InnerA'
    }

    def "detects rename of an inner class"() {
        java """class A {
            class InnerA {}
        }"""
        java "class B {}"

        outputs.snapshot { run "compileJava" }

        when:
        java """class A {
            class InnerA2 {}
        }"""
        run "compileJava"

        then:
        outputs.recompiledClasses 'A', 'A$InnerA2'
        outputs.deletedClasses 'A$InnerA'
    }

    def "detects addition af a new class with an inner class"() {
        java "class B {}"

        outputs.snapshot { run "compileJava" }

        when:
        java """class A {
            class InnerA {}
        }"""
        run "compileJava"

        then:
        outputs.recompiledClasses 'A', 'A$InnerA'
    }

    def "detects transitive dependencies"() {
        java "class A {}", "class B extends A {}", "class C extends B {}", "class D {}"
        outputs.snapshot { run "compileJava" }

        when:
        java "class A { /* change */ }"
        run "compileJava"

        then: outputs.recompiledClasses 'A', 'B', 'C'

        when:
        outputs.snapshot()
        java "class B { /* change */ }"
        run "compileJava"

        then: outputs.recompiledClasses 'B', 'C'
    }

    def "detects transitive dependencies with inner classes"() {
        java "class A {}", "class B extends A {}", "class D {}"
        java """class C extends B {
            class InnerC {}
        }
        """
        outputs.snapshot { run "compileJava" }

        when:
        java "class A { /* change */ }"
        run "compileJava"

        then: outputs.recompiledClasses 'A', 'B', 'C', 'C$InnerC'
    }

    def "handles cycles in class dependencies"() {
        java "class A {}", "class D {}"
        java "class B extends A { C c; }", "class C extends B {}" //cycle
        outputs.snapshot { run "compileJava" }

        when:
        java "class A { /* change */ }"
        run "compileJava"

        then: outputs.recompiledClasses 'A', 'B', 'C'
    }

    def "change to an annotation class triggers full rebuild"() {
        def annotationClass = file("src/main/java/SourceAnnotation.java") << """import java.lang.annotation.*;
            @Retention(RetentionPolicy.SOURCE) public @interface SourceAnnotation {}
        """
        java "class A {}", "class B {}"
        outputs.snapshot { run "compileJava" }

        when:
        annotationClass.text += "/* change */"
        run "compileJava"

        then: outputs.recompiledClasses 'A', 'B', 'SourceAnnotation'
    }

    def "changed class with private constant does not incur full rebuild"() {
        java "class A {}", "class B { private final static int x = 1;}"
        outputs.snapshot { run "compileJava" }

        when:
        java "class B { /* change */ }"
        run "compileJava"

        then: outputs.recompiledClasses 'B'
    }

    def "changed class with non-private constant incurs full rebuild"() {
        java "class A {}", "class B { final static int x = 1;}"
        outputs.snapshot { run "compileJava" }

        when:
        java "class B { /* change */ }"
        run "compileJava"

        then: outputs.recompiledClasses 'B', 'A'
    }

    def "dependent class with non-private constant does not incur full rebuild"() {
        java "class A {}", "class B extends A { final static int x = 1;}", "class C {}"
        outputs.snapshot { run "compileJava" }

        when:
        java "class A { /* change */ }"
        run "compileJava"

        then: outputs.recompiledClasses 'B', 'A'
    }

    def "detects class changes in subsequent runs ensuring the class dependency data is refreshed"() {
        java "class A {}", "class B {}", "class C {}"
        outputs.snapshot { run "compileJava" }

        when:
        java "class B extends A {}"
        run "compileJava"

        then: outputs.recompiledClasses('B')

        when:
        outputs.snapshot()
        java "class A { /* change */ }"
        run "compileJava"

        then: outputs.recompiledClasses('A', 'B')
    }

    def "handles multiple compile tasks within a single project"() {
        java "class A {}", "class B extends A {}"
        file("src/integTest/java/X.java") << "class X {}"
        file("src/integTest/java/Y.java") << "class Y extends X {}"

        //new separate compile task (integTestCompile)
        file("build.gradle") << """
            sourceSets { integTest.java.srcDir 'src/integTest/java' }
        """

        outputs.snapshot { run "compileIntegTestJava", "compileJava" }

        when: //when A class is changed
        java "class A { String change; }"
        run "compileIntegTestJava", "compileJava", "-i"

        then: //only B and A are recompiled
        outputs.recompiledClasses("A", "B")

        when: //when X class is changed
        outputs.snapshot()
        file("src/integTest/java/X.java").text = "class X { String change;}"
        run "compileIntegTestJava", "compileJava", "-i"

        then: //only X and Y are recompiled
        outputs.recompiledClasses("X", "Y")
    }

    def "recompiles classes from extra source directories"() {
        buildFile << "sourceSets.main.java.srcDir 'java'"

        java("class B {}")
        file("java/A.java") << "class A extends B {}"
        file("java/C.java") << "class C {}"

        outputs.snapshot { run "compileJava" }

        when:
        java("class B { String change; } ")
        run "compileJava"

        then:
        outputs.recompiledClasses("B", "A")
    }

    def "detects changes to source in extra source directories"() {
        buildFile << "sourceSets.main.java.srcDir 'java'"

        java("class A extends B {}")
        file("java/B.java") << "class B {}"
        file("java/C.java") << "class C {}"

        outputs.snapshot { run "compileJava" }

        when:
        file("java/B.java").text = "class B { String change; }"
        run "compileJava"

        then:
        outputs.recompiledClasses("B", "A")
    }

    def "handles duplicate class across source directories"() {
        //compiler does not allow this scenario, documenting it here
        buildFile << "sourceSets.main.java.srcDir 'java'"

        java("class A {}")
        file("java/A.java") << "class A {}"

        when: fails "compileJava"
        then: failure.assertHasCause("Compilation failed")
    }
}