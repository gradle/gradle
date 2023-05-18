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

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

abstract class CrossTaskConstantChangesIncrementalJavaCompilationIntegrationTest extends AbstractCrossTaskConstantChangesIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    def "change in an upstream class with non-private constant causes rebuild if constant is used (#constantType)"() {
        source api: ["class A {}", "class B { final static $constantType x = $constantValue; }"], impl: ["class X { $constantType foo() { return B.x; }}", "class Y {int foo() { return -2; }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static $constantType x = $newValue; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X')

        where:
        constantType | constantValue   | newValue
        'boolean'    | 'false'         | 'true'
        'byte'       | '(byte) 125'    | '(byte) 126'
        'short'      | '(short) 666'   | '(short) 555'
        'int'        | '55542'         | '444'
        'long'       | '5L'            | '689L'
        'float'      | '6f'            | '6.5f'
        'double'     | '7d'            | '7.2d'
        'String'     | '"foo"'         | '"bar"'
        'String'     | '"foo" + "bar"' | '"bar"'
    }

    def "change in an upstream class with non-private constant causes rebuild correctly incrementally"() {
        source api: ["class A { final static int x = 1; }", "class B { final static int y = 1; }"],
            impl: ["class X { int foo() { return A.x; }}", "class Y {int foo() { return B.y; }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        // A is changed so we expect X to recompile
        source api: ["class A { final static int x = 2; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X')

        when:
        impl.snapshot()
        // B is changed so we expect Y to recompile
        source api: ["class B { final static int y = 2; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('Y')

        when:
        impl.snapshot()
        // A and B are changed so we expect X, Y to recompile
        source api: ["class A { final static int x = 3; }",
                     "class B { final static int y = 3; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses( 'X', 'Y')

        when:
        impl.snapshot()
        // No change, so we expect no recompilation
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    def "change in an upstream class with non-private constant causes rebuild if constant is referenced in method body (#constantType)"() {
        source api: ["class A {}", "class B { final static $constantType x = $constantValue; }"],
            impl: ["class ImplA { $constantType foo() { return B.x; }}", "class ImplB {int foo() { return 2; }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static $constantType x = $newValue; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('ImplA')

        where:
        constantType | constantValue   | newValue
        'boolean'    | 'false'         | 'true'
        'byte'       | '(byte) 125'    | '(byte) 126'
        'short'      | '(short) 666'   | '(short) 555'
        'int'        | '55542'         | '444'
        'long'       | '5L'            | '689L'
        'float'      | '6f'            | '6.5f'
        'double'     | '7d'            | '7.2d'
        'String'     | '"foo"'         | '"bar"'
        'String'     | '"foo" + "bar"' | '"bar"'
    }

    def "change in an upstream class with non-private constant causes rebuild if constant is referenced in field (#constantType)"() {
        source api: ["class A {}", "class B { final static $constantType x = $constantValue; }"], impl: ["class ImplA extends A { final $constantType foo = B.x; }", "class ImplB {int foo() { return 2; }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static $constantType x = $newValue; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('ImplA')

        where:
        constantType | constantValue   | newValue
        'int'        | '55542'         | '444'
        'String'     | '"foo" + "bar"' | '"bar"'
    }

    def "non-abi change to constant origin class does not cause recompilation but constant change does"() {
        source api: ["class A {}", "class B { final static int x = 1; int method() { return 1; } }"], impl: ["class X { int method() { return B.x; } }", "class Y {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        // non-abi-change
        source api: ["class B { final static int x = 1; int method() { return 2; } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()

        when:
        impl.snapshot()
        // Constant change
        source api: ["class B { final static int x = 2; int method() { return 2; } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X')

        when:
        // non-abi change
        impl.snapshot()
        source api: ["class B { final static int x = 2; int method() { return 3; } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    def "changing upstream constant causes compilation for downstream constants"() {
        source api: ["class A { final static int x = 1; }", "class B { final static int x = A.x; }"],
            impl: ["class X { final static int x = B.x; }",
                   "class Y { final static int x = X.x; }",
                   "class Z { final static int x = Y.x; }",
                   "class W {  }"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { final static int x = 2; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X', 'Y', 'Z')
    }

    def "changing upstream constant does not cause recompilation if not public dependent of constant for literal"() {
        source api: ["class A { final static int x = 1; }"],
            impl: ["class X { final static int x = A.x; }",
                   "class Y { final static int x = 1; int method() { return X.x; } }",
                   // Z is not recompiled, since Y has constant of X in method body
                   "class Z { final static int x = Y.x;  }",
                   "class W {  }"]
        impl.snapshot { run language.compileTaskName }
        impl.execute("""
            assert X.x == 1
            assert Y.x == 1
            assert new Y().method() == 1
            assert Z.x == 1
        """)

        when:
        source api: ["class A { final static int x = 2; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X', 'Y')

        and:
        impl.execute("""
            assert X.x == 2
            assert Y.x == 1
            assert new Y().method() == 2
            assert Z.x == 1
        """)
    }

    def "changing upstream constant causes compilation for downstream constants for binary expression"() {
        source api: ["class A { final static int x = 1; }", "class B { final static int x = A.x + 1; }"],
            impl: ["class X { final static int x = B.x + 1; }",
                   "class Y { final static int x = X.x + 1; }",
                   "class Z { final static int x = Y.x + 1; }",
                   "class W {  }"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { final static int x = 2; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X', 'Y', 'Z')
    }

    def "changing an unused non-private constant recompile child classes"() {
        println(buildFile.text)
        source api: ["class A {}", "class B { final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static int x = 2; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('ImplB')
    }

    def "removing an unused non-private constant recompile child classes"() {
        println(buildFile.text)
        source api: ["class A {}", "class B { final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* change */ }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('ImplB')
    }

    def "changing an unused non-private constant doesn't cause any compilation when no inheritance"() {
        source api: ["class A {}", "class B { final static int x = 1; }"], impl: ["class ImplA {}", "class ImplB {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static int x = 2; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    def "removing an unused non-private constant doesn't cause any compilation when no inheritance"() {
        source api: ["class A {}", "class B { final static int x = 1; }"], impl: ["class ImplA {}", "class ImplB {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* change */ }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    @NotYetImplemented
    // This would be possible but it's not yet implemented since we would have
    // to track every constants as (symbol, value) pair and this is expensive, so there is no solution yet
    def "ignores irrelevant changes to constant values in annotations"() {
        source api: ["class A { final static int x = 1; final static int y = -1; }",
                     """import java.lang.annotation.Retention;
               import java.lang.annotation.RetentionPolicy;
               @Retention(RetentionPolicy.RUNTIME)
               @interface B { int value(); }"""
        ], impl: [
            // cases where it's relevant, ABI-wise
            "@B(A.x) class OnClass {}",
            "class OnMethod { @B(A.y) void foo() {} }",
            "class OnField { @B(A.x) String foo; }",
            "class OnParameter { void foo(@B(A.y) int x) {} }",
            "class InMethodBody { void foo(int x) { @B(A.x) int value = 5; } }"
        ]

        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { final static int x = $newXValue; final static int y = $newYValue; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses(compiledClasses.split(','))

        where:
        newXValue | newYValue | compiledClasses
        '2'       | '-1'      | 'OnClass,OnField,InMethodBody'
        '1'       | '-2'      | 'OnMethod,OnParameter'
    }

    def "recompiles outermost class when #visibility inner class contains constant reference"() {
        source api: [
            "class A { public static final int EVIL = 666; }",
        ], impl: [
            "class B {}",
            "class C { $visibility static class Inner { int foo() { return A.EVIL; } } }",
            "class D { $visibility class Inner { int foo() { return A.EVIL; } } }",
            "class E { void foo() { Runnable r = new Runnable() { public void run() { int x = A.EVIL; } }; } }",
            """class F {
                    int foo() { return A.EVIL; }
                    $visibility static class Inner { }
                }""",
        ]

        impl.snapshot { run("impl:${language.compileTaskName}") }

        when:
        source api: ["class A { public static final int EVIL = 0; }"]
        run("impl:${language.compileTaskName}")

        then:
        impl.recompiledClasses('C', 'C$Inner', 'D', 'D$Inner', 'E', 'E$1', 'F', 'F$Inner')

        where:
        visibility << ['public', 'private', '']
    }

    def "recognizes change of constant value in annotation"() {
        source api: [
            "class A { public static final int CST = 0; }",
            """import java.lang.annotation.Retention;
               import java.lang.annotation.RetentionPolicy;
               @Retention(RetentionPolicy.RUNTIME)
               @interface B { int value(); }"""
        ], impl: [
            // cases where it's relevant, ABI-wise
            "class X {}",
            "@B(A.CST) class OnClass {}",
            "class OnMethod { @B(A.CST) void foo() {} }",
            "class OnField { @B(A.CST) String foo; }",
            "class OnParameter { void foo(@B(A.CST) int x) {} }",
            "class InMethodBody { void foo(int x) { @B(A.CST) int value = 5; } }",
        ]

        impl.snapshot { run("impl:${language.compileTaskName}") }

        when:
        source api: ["class A { public static final int CST = 1234; }"]
        run("impl:${language.compileTaskName}")

        then:
        impl.recompiledClasses("OnClass", "OnMethod", "OnParameter", "OnField", "InMethodBody")
    }

    def "recompiles dependent class in case a constant is switched"() {
        source api: ["class A { public static final int FOO = 10; public static final int BAR = 20; }"],
            impl: ["class B { void foo() { int x = A.FOO; } }", "class C { void foo() { int x = A.BAR; } }", "class D { }"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ['class A { public static final int FOO = 20; public static final int BAR = 10; }']
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses 'B', 'C'
    }

    def "recompiles dependent class in case a constant is computed from another constant"() {
        source api: ["class A { public static final int FOO = 10; }"], impl: ['class B { public static final int BAR = 2 + A.FOO; }', 'class C { }']
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ['class A { public static final int FOO = 100; }']
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses 'B'
    }

    // We don't know if constant changed or some method signature changed,
    // so we have to check also private first level dependents
    def "recompile first-level private dependents on constant change"() {
        source api: ["class A { static final int X = 1; int method() { return 1; } }"],
            impl: ["class X { int foo() { return new A().method(); }}", "class Y { int foo() { return new X().foo(); }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { static final int X = 2; int method() { return 1; } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses 'X'
    }

    def "recompile subclass and super class when super class has accessible constant on constant origin change"() {
        source api: ["class A { static final int X = 1; }"],
            impl: ["class B { static final int X = A.X; }", "class C extends B { }"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { static final int X = 2; int method() { return 1; } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses 'B', 'C'
    }

    def "recompile only super class when super class has private constant on constant origin change"() {
        source api: ["class A { static final int X = 1; }"],
            impl: ["class B { int foo() { return A.X; } }", "class C extends B { }"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { static final int X = 2; int method() { return 1; } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses 'B'
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "recompiles all if constant used by annotation on module-info is changed"() {
        given:
        source api: "package constant; public class Const { public static final String CONST = \"unchecked\"; }"
        def apiModuleInfo = file("api/src/main/${language.name}/module-info.${language.name}")
        apiModuleInfo.text = """
            module api {
                exports constant;
            }
        """
        def moduleInfo = file("impl/src/main/${language.name}/module-info.${language.name}")
        moduleInfo.text = """
            import constant.Const;

            @SuppressWarnings(Const.CONST)
            module impl {
                requires api;
            }
        """
        source impl: ["package foo; class A { }"]
        impl.snapshot { run language.compileTaskName }
        when:
        source api: ["package constant; public class Const { public static final String CONST = \"raw-types\"; }"]
        run "impl:${language.compileTaskName}"
        then:
        impl.recompiledClasses 'module-info', 'A'
    }

    def "recompiles all classes in a package if constant used by annotation on package-info is changed"() {
        source api: [
            "package constant; public class Const { public static final int X = 1; }",
            """
            package annotations;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.PACKAGE)
            public @interface Anno {
                   int value();
            }
            """
        ]
        def packageFile = file("impl/src/main/${languageName}/foo/package-info.${languageName}")
        packageFile.text = """@Deprecated @annotations.Anno(constant.Const.X + 1) package foo;"""
        source impl: [
            "package foo; class A {}",
            "package foo; public class B {}",
            "package foo.bar; class C {}",
            "package baz; class D {}",
            "package baz; import foo.B; class E extends B {}"
        ]
        impl.snapshot { run language.compileTaskName }
        when:
        source api: ["package constant; public class Const { public static final int X = 2; }"]
        run "impl:${language.compileTaskName}"
        then:
        impl.recompiledClasses "A", "B", "E", "package-info"
    }
}

class CrossTaskConstantChangesIncrementalJavaCompilationUsingClassDirectoryIntegrationTest extends CrossTaskConstantChangesIncrementalJavaCompilationIntegrationTest {
    boolean useJar = false
}

class CrossTaskConstantChangesIncrementalJavaCompilationUsingJarIntegrationTest extends CrossTaskConstantChangesIncrementalJavaCompilationIntegrationTest {
    boolean useJar = true
}
