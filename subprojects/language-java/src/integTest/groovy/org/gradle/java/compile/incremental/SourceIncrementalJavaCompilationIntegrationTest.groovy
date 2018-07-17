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

package org.gradle.java.compile.incremental

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.CompilationOutputsFixture
import spock.lang.Issue
import spock.lang.Unroll

class SourceIncrementalJavaCompilationIntegrationTest extends AbstractIntegrationSpec {

    CompilationOutputsFixture outputs

    def setup() {
        outputs = new CompilationOutputsFixture(file("build/classes"))

        buildFile << """
            apply plugin: 'java'
        """
    }

    private File java(String... classBodies) {
        File out
        for (String body : classBodies) {
            def className = (body =~ /(?s).*?(?:class|interface|enum) (\w+) .*/)[0][1]
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

        when:
        assert a.delete()
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

        then:
        outputs.recompiledClasses 'A', 'B', 'C'

        when:
        outputs.snapshot()
        java "class B { /* change */ }"
        run "compileJava"

        then:
        outputs.recompiledClasses 'B', 'C'
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

        then:
        outputs.recompiledClasses 'A', 'B', 'C', 'C$InnerC'
    }

    def "handles cycles in class dependencies"() {
        java "class A {}", "class D {}"
        java "class B extends A { C c; }", "class C extends B {}" //cycle
        outputs.snapshot { run "compileJava" }

        when:
        java "class A { /* change */ }"
        run "compileJava"

        then:
        outputs.recompiledClasses 'A', 'B', 'C'
    }

    @Unroll
    def "change to #retention retention annotation class recompiles #desc"() {
        def annotationClass = file("src/main/java/SomeAnnotation.java") << """
            import java.lang.annotation.*;

            @Retention(RetentionPolicy.$retention) 
            public @interface SomeAnnotation {}
        """
        java "@SomeAnnotation class A {}", "class B {}"
        outputs.snapshot { run "compileJava" }

        when:
        annotationClass.text += "/* change */"
        run "compileJava"

        then:
        outputs.recompiledClasses(expected as String[])

        where:
        desc              | retention | expected
        'all'             | 'SOURCE'  | ['A', 'B', 'SomeAnnotation']
        'annotated types' | 'CLASS'   | ['SomeAnnotation', 'A']
        'annotated types' | 'RUNTIME' | ['SomeAnnotation', 'A']
    }

    def "change to class referenced by an annotation recompiles annotated types"() {
        java """
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.CLASS) 
            public @interface B {
                Class<?> value();
            }
        """
        def a = java "class A {}"
        java "@B(A.class) class OnClass {}",
            "class OnMethod { @B(A.class) void foo() {} }",
            "class OnField { @B(A.class) String foo; }",
            "class OnParameter { void foo(@B(A.class) int x) {} }"
        outputs.snapshot { run "compileJava" }

        when:
        a.text += "/* change */"
        run "compileJava"

        then:
        outputs.recompiledClasses("A", "OnClass", "OnMethod", "OnParameter", "OnField")
    }

    def "change to class referenced by an array value in an annotation recompiles annotated types"() {
        java """
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.CLASS) 
            public @interface B {
                Class<?>[] value();
            }
        """
        def a = java "class A {}"
        java "@B(A.class) class OnClass {}",
            "class OnMethod { @B(A.class) void foo() {} }",
            "class OnField { @B(A.class) String foo; }",
            "class OnParameter { void foo(@B(A.class) int x) {} }"
        outputs.snapshot { run "compileJava" }

        when:
        a.text += "/* change */"
        run "compileJava"

        then:
        outputs.recompiledClasses("A", "OnClass", "OnMethod", "OnParameter", "OnField")
    }

    def "change to enum referenced by an annotation recompiles annotated types"() {
        java """
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.CLASS) 
            public @interface B {
                A value();
            }
        """
        def a = java "enum A { FOO }"
        java "@B(A.FOO) class OnClass {}",
            "class OnMethod { @B(A.FOO) void foo() {} }",
            "class OnField { @B(A.FOO) String foo; }",
            "class OnParameter { void foo(@B(A.FOO) int x) {} }"
        outputs.snapshot { run "compileJava" }

        when:
        a.text += "/* change */"
        run "compileJava"

        then:
        outputs.recompiledClasses("A", "B", "OnClass", "OnMethod", "OnParameter", "OnField")
    }

    def "change to value in nested annotation recompiles annotated types"() {
        java """
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.CLASS) 
            public @interface B {
                A value();
            }
        """
        java "public @interface A { Class<?> value(); }"
        def c = java "class C {}"
        java "@B(@A(C.class)) class OnClass {}",
            "class OnMethod { @B(@A(C.class)) void foo() {} }",
            "class OnField { @B(@A(C.class)) String foo; }",
            "class OnParameter { void foo(@B(@A(C.class)) int x) {} }"
        outputs.snapshot { run "compileJava" }

        when:
        c.text += "/* change */"
        run "compileJava"

        then:
        outputs.recompiledClasses("C", "OnClass", "OnMethod", "OnParameter", "OnField")
    }

    def "changed class with private constant does not incur full rebuild"() {
        java "class A {}", "class B { private final static int x = 1;}"
        outputs.snapshot { run "compileJava" }

        when:
        java "class B { /* change */ }"
        run "compileJava"

        then:
        outputs.recompiledClasses 'B'
    }

    def "changed class with used non-private constant incurs full rebuild"() {
        java "class A { int foo() { return 1; } }", "class B { final static int x = 1;}"
        outputs.snapshot { run "compileJava" }

        when:
        java "class B { /* change */ }"
        run "compileJava"

        then:
        outputs.recompiledClasses 'B', 'A'
    }

    @NotYetImplemented
    //  Can re-enable with compiler plugins. See gradle/gradle#1474
    def "changing an unused non-private constant incurs partial rebuild"() {
        java "class A { int foo() { return 2; } }", "class B { final static int x = 1;}"
        outputs.snapshot { run "compileJava" }

        when:
        java "class B { /* change */ }"
        run "compileJava"

        then:
        outputs.recompiledClasses 'B'
    }

    def "dependent class with non-private constant does not incur full rebuild"() {
        java "class A {}", "class B extends A { final static int x = 1;}", "class C {}"
        outputs.snapshot { run "compileJava" }

        when:
        java "class A { /* change */ }"
        run "compileJava"

        then:
        outputs.recompiledClasses 'B', 'A'
    }

    def "detects class changes in subsequent runs ensuring the class dependency data is refreshed"() {
        java "class A {}", "class B {}", "class C {}"
        outputs.snapshot { run "compileJava" }

        when:
        java "class B extends A {}"
        run "compileJava"

        then:
        outputs.recompiledClasses('B')

        when:
        outputs.snapshot()
        java "class A { /* change */ }"
        run "compileJava"

        then:
        outputs.recompiledClasses('A', 'B')
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
        buildFile << "sourceSets.main.java.srcDir 'extra-java'"

        java("class B {}")
        file("extra-java/A.java") << "class A extends B {}"
        file("extra-java/C.java") << "class C {}"

        outputs.snapshot { run "compileJava" }

        when:
        java("class B { String change; } ")
        run "compileJava"

        then:
        outputs.recompiledClasses("B", "A")
    }

    def "recompilation considers changes from dependent sourceSet"() {
        buildFile << """
sourceSets {
    other {}
    main { compileClasspath += sourceSets.other.output }
}
"""

        java("class Main extends com.foo.Other {}")
        file("src/other/java/com/foo/Other.java") << "package com.foo; public class Other {}"

        outputs.snapshot { run "compileJava" }

        when:
        file("src/other/java/com/foo/Other.java").text = "package com.foo; public class Other { String change; }"
        run "compileJava"

        then:
        outputs.recompiledClasses("Other", "Main")
    }

    def "recompilation does not process removed classes from dependent sourceSet"() {
        def unusedClass = java("public class Unused {}")
        // Need another class or :compileJava will always be considered UP-TO-DATE
        java("public class Other {}")

        file("src/test/java/BazTest.java") << "public class BazTest {}"

        outputs.snapshot { run "compileTestJava" }

        when:
        file("src/test/java/BazTest.java").text = "public class BazTest { String change; }"
        unusedClass.delete()

        run "compileTestJava"

        then:
        outputs.recompiledClasses("BazTest")
        outputs.deletedClasses("Unused")
    }

    def "detects changes to source in extra source directories"() {
        buildFile << "sourceSets.main.java.srcDir 'extra-java'"

        java("class A extends B {}")
        file("extra-java/B.java") << "class B {}"
        file("extra-java/C.java") << "class C {}"

        outputs.snapshot { run "compileJava" }

        when:
        file("extra-java/B.java").text = "class B { String change; }"
        run "compileJava"

        then:
        outputs.recompiledClasses("B", "A")
    }

    def "recompiles classes from extra source directory provided as #type"() {
        given:
        buildFile << "compileJava.source $method('extra-java')"

        java("class B {}")
        file("extra-java/A.java") << "class A extends B {}"
        file("extra-java/C.java") << "class C {}"

        outputs.snapshot { run "compileJava" }

        when:
        java("class B { String change; } ")
        run "compileJava"

        then:
        outputs.recompiledClasses("B", "A")

        where:
        type            | method
        "File"          | "file"
        "DirectoryTree" | "fileTree"
    }

    def "detects changes to source in extra source directory provided as #type"() {
        buildFile << "compileJava.source $method('extra-java')"

        java("class A extends B {}")
        file("extra-java/B.java") << "class B {}"
        file("extra-java/C.java") << "class C {}"

        outputs.snapshot { run "compileJava" }

        when:
        file("extra-java/B.java").text = "class B { String change; }"
        run "compileJava"

        then:
        outputs.recompiledClasses("B", "A")

        where:
        type            | method
        "File"          | "file"
        "DirectoryTree" | "fileTree"
    }

    def "reports source type that does not support detection of source root"() {
        buildFile << "compileJava.source([file('extra-java'), file('other'), file('text-file.txt')])"

        java("class A extends B {}")
        file("extra-java/B.java") << "class B {}"
        file("extra-java/C.java") << "class C {}"
        def textFile = file('text-file.txt')
        textFile.text = "text file as root"

        outputs.snapshot { run "compileJava" }

        when:
        file("extra-java/B.java").text = "class B { String change; }"
        executer.withArgument "--info"
        run "compileJava"

        then:
        outputs.recompiledClasses("A", "B", "C")
        output.contains("Cannot infer source root(s) for source `file '${textFile.absolutePath}'`. Supported types are `File` (directories only), `DirectoryTree` and `SourceDirectorySet`.")
        output.contains("Full recompilation is required because the source roots could not be inferred.")
    }

    def "handles duplicate class across source directories"() {
        //compiler does not allow this scenario, documenting it here
        buildFile << "sourceSets.main.java.srcDir 'java'"

        java("class A {}")
        file("java/A.java") << "class A {}"

        when:
        fails "compileJava"
        then:
        failure.assertHasCause("Compilation failed")
    }

    @Issue("GRADLE-3426")
    def "supports Java 1.2 dependencies"() {
        java "class A {}"

        buildFile << """
        ${jcenterRepository()}
dependencies { compile 'com.ibm.icu:icu4j:2.6.1' }
"""
        expect:
        succeeds "compileJava"
    }

    @Issue("GRADLE-3426")
    def "fully recompiles when a non-analyzable jar is changed"() {
        def a =  java """
            import com.ibm.icu.util.Calendar;
            class A {
                Calendar cal;
            }
        """

        buildFile << """
            ${jcenterRepository()}
            if (hasProperty("withIcu")) {
                dependencies { compile 'com.ibm.icu:icu4j:2.6.1' }
            }

        """
        succeeds "compileJava", "-PwithIcu"

        when:
        a.text = "class A {}"

        then:
        succeeds "compileJava", "--info"
        outputContains("Full recompilation is required because class file LocaleElements_zh__PINYIN.class could not be analyzed.")
    }

    @Issue("GRADLE-3495")
    def "supports Java 1.1 dependencies"() {
        java "class A {}"

        buildFile << """
${jcenterRepository()}
dependencies { compile 'net.sf.ehcache:ehcache:2.10.2' }
"""
        expect:
        run "compileJava"
    }

    @Unroll("detects changes to class referenced through a #modifier field")
    def "detects changes to class referenced through a field"() {
        given:
        java """class A {
    $modifier B b;
    void doSomething() {
        Runnable r = b;
        r.run();
    }
}"""
        java '''abstract class B implements Runnable { }'''

        outputs.snapshot { run "compileJava" }

        when:
        file("src/main/java/B.java").text = "class B { }"
        fails "compileJava"

        then:
        failure.assertHasErrorOutput 'Runnable r = b;'

        where:
        modifier << ['', 'static']
    }

    @Unroll("detects changes to class referenced through a #modifier array field")
    def "detects changes to class referenced through an array field"() {
        given:
        java """class A {
    $modifier B[] b;
    void doSomething() {
        Runnable r = b[0];
        r.run();
    }
}"""
        java '''abstract class B implements Runnable { }'''

        outputs.snapshot { run "compileJava" }

        when:
        file("src/main/java/B.java").text = "class B { }"
        fails "compileJava"

        then:
        failure.assertHasErrorOutput 'Runnable r = b[0];'

        where:
        modifier << ['', 'static']
    }

    @Unroll("detects changes to class referenced through a #modifier multi-dimensional array field")
    def "detects changes to class referenced through an multi-dimensional array field"() {
        given:
        java """class A {
    $modifier B[][] b;
    void doSomething() {
        Runnable r = b[0][0];
        r.run();
    }
}"""
        java '''abstract class B implements Runnable { }'''

        outputs.snapshot { run "compileJava" }

        when:
        file("src/main/java/B.java").text = "class B { }"
        fails "compileJava"

        then:
        failure.assertHasErrorOutput 'Runnable r = b[0][0];'

        where:
        modifier << ['', 'static']
    }

    def "detects changes to class referenced in method body"() {
        given:
        java '''class A {
    void doSomething(Object b) {
        Runnable r = (B) b;
        r.run();
    }
}'''
        java '''abstract class B implements Runnable { }'''

        outputs.snapshot { run "compileJava" }

        when:
        file("src/main/java/B.java").text = "class B { }"
        fails "compileJava"

        then:
        failure.assertHasErrorOutput 'Runnable r = (B) b;'
    }

    def "detects changes to class referenced through return type"() {
        given:
        java '''class A {
    B b() { return null; }
    
    void doSomething() {
        Runnable r = b();
        r.run();
    }
}'''
        java '''abstract class B implements Runnable { }'''

        outputs.snapshot { run "compileJava" }

        when:
        file("src/main/java/B.java").text = "class B { }"
        fails "compileJava"

        then:
        failure.assertHasErrorOutput 'Runnable r = b();'
    }

    def "detects changes to class referenced through method signature"() {
        given:
        java '''class A {
    Runnable go(B b) {
        Runnable r = b;
        r.run();
        return b;
    }
}'''
        java '''abstract class B implements Runnable { }'''

        outputs.snapshot { run "compileJava" }

        when:
        file("src/main/java/B.java").text = "class B { }"
        fails "compileJava"

        then:
        failure.assertHasErrorOutput 'Runnable r = b;'
    }

    def "detects changes to class referenced through type argument in field"() {
        given:
        java '''class A {
    java.util.List<B> bs;
    void doSomething() {
        for (B b: bs) {
           Runnable r = b;
           r.run();
        }
    }
}'''
        java '''abstract class B implements Runnable { }'''

        outputs.snapshot { run "compileJava" }

        when:
        file("src/main/java/B.java").text = "class B { }"
        fails "compileJava"

        then:
        failure.assertHasErrorOutput 'Runnable r = b;'
    }

    def "detects changes to class referenced through type argument in return type"() {
        given:
        java '''class A {
    java.util.List<B> bs() { return null; }
    
    void doSomething() {
        for (B b: bs()) {
           Runnable r = b;
           r.run();
        }
    }
}'''
        java '''abstract class B implements Runnable { }'''

        outputs.snapshot { run "compileJava" }

        when:
        file("src/main/java/B.java").text = "class B { }"
        fails "compileJava"

        then:
        failure.assertHasErrorOutput 'Runnable r = b;'
    }

    def "detects changes to class referenced through type argument in parameter"() {
        given:
        java '''class A {
    
    void doSomething(java.util.List<B> bs) {
        for (B b: bs) {
           Runnable r = b;
           r.run();
        }
    }
}'''
        java '''abstract class B implements Runnable { }'''

        outputs.snapshot { run "compileJava" }

        when:
        file("src/main/java/B.java").text = "class B { }"
        fails "compileJava"

        then:
        failure.assertHasErrorOutput 'Runnable r = b;'
    }

    def "deletes empty packages dirs"() {
        given:
        def a = file('src/main/java/com/foo/internal/A.java') << """
            package com.foo.internal;
            public class A {}
        """
        file('src/main/java/com/bar/B.java') << """
            package com.bar;
            public class B {}
        """

        succeeds "compileJava"
        a.delete()

        when:
        succeeds "compileJava"

        then:
        ! file("build/classes/java/main/com/foo").exists()
    }

    def "recompiles types whose names look like inne classes even if they aren't"() {
        given:
        file('src/main/java/Test.java') << 'public class Test{}'
        file('src/main/java/Test$$InnerClass.java') << 'public class Test$$InnerClass{}'
        buildFile << '''
            apply plugin: 'java'
        '''.stripIndent()

        when:
        succeeds ':compileJava'

        then:
        executedAndNotSkipped ':compileJava'
        file('build/classes/java/main/Test.class').assertExists()
        file('build/classes/java/main/Test$$InnerClass.class').assertExists()

        when:
        file('src/main/java/Test.java').text = 'public class Test{ void foo() {} }'
        succeeds ':compileJava'

        then:
        executedAndNotSkipped ':compileJava'
        file('build/classes/java/main/Test.class').assertExists()
        file('build/classes/java/main/Test$$InnerClass.class').assertExists()
    }

    def "incremental java compilation ignores empty packages"() {
        given:
        file('src/main/java/org/gradle/test/MyTest.java').text = """
            package org.gradle.test;
            
            class MyTest {}
        """

        when:
        run 'compileJava'
        then:
        executedAndNotSkipped(':compileJava')

        when:
        file('src/main/java/org/gradle/different').createDir()
        run('compileJava')

        then:
        skipped(':compileJava')
    }
}
