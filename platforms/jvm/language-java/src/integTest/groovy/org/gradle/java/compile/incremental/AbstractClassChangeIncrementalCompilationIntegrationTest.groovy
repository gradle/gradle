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

import spock.lang.Unroll

abstract class AbstractClassChangeIncrementalCompilationIntegrationTest extends AbstractJavaGroovyIncrementalCompilationSupport {

    abstract void recompiledWithFailure(String expectedFailure, String... recompiledClasses)

    def "changes to transitive private classes do not force recompilation"() {
        source """class A {
            private B b;
        }"""
        source """class B {
            private C c;
        }"""
        source 'class C {}'

        outputs.snapshot { run language.compileTaskName }

        when:
        source """class C {
            private String foo = "blah";
        }"""
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'B', 'C'
    }

    class IncrementalLib {
        void writeToProject() {
            source 'class AccessedFromPackagePrivateField {}'
            source 'class AccessedFromPrivateMethod {}'
            source 'class AccessedFromPrivateMethodBody {}'
            source 'class AccessedFromPrivateField {}'
            source 'class AccessedFromPrivateClass {}'
            source 'class AccessedFromPrivateClassPublicField {}'
            source """class SomeClass {
                java.util.List<Integer> field = new java.util.LinkedList<Integer>();

                private AccessedFromPrivateField accessedFromPrivateField;

                AccessedFromPackagePrivateField someField;

                private AccessedFromPrivateMethod accessedFromPrivateMethod() {
                    return null;
                }

                public String accessedFromPrivateMethodBody() {
                    return new AccessedFromPrivateMethodBody().toString();
                }

                private java.util.Set<String> stuff(java.util.HashMap<String, String> map) {
                    System.out.println(new Foo());
                    return new java.util.HashSet<String>();
                }

                private class Foo {
                    // Hint: this field won't appear in the ClassAnalysis for SomeClass
                    public AccessedFromPrivateClassPublicField anotherField;

                    Foo() {}

                    public String toString() {
                        return "" + new AccessedFromPrivateClass();
                    }
                }
            }"""
            source """class UsingSomeClass {
                SomeClass someClassField;
            }"""
        }

        void applyModificationToClassAccessedFromPrivateMethod() {
            source """class AccessedFromPrivateMethod {
                private String foo = "blah";
            }"""
        }

        void applyModificationToClassAccessedFromPrivateMethodBody() {
            source """class AccessedFromPrivateMethodBody {
                private String foo = "blah";
            }"""
        }

        void applyModificationToClassAccessedFromPackagePrivateField() {
            source """class AccessedFromPackagePrivateField {
                private String foo = "blah";
            }"""
        }

        void applyModificationToClassAccessedFromPrivateField() {
            source """class AccessedFromPrivateField {
                private String foo = "blah";
            }"""
        }

        void applyModificationToClassAccessedFromPrivateClassPublicField() {
            source """class AccessedFromPrivateClassPublicField {
                private String foo = "blah";
            }"""
        }

        void applyModificationToClassAccessedFromPrivateClass() {
            source """class AccessedFromPrivateClass {
                private String foo = "blah";
            }"""
        }
    }

    def "change to class accessed from private method only recompile that class and the direct consumer"() {
        def componentUnderTest = new IncrementalLib()
        componentUnderTest.writeToProject()

        outputs.snapshot { run language.compileTaskName }

        when:
        componentUnderTest.applyModificationToClassAccessedFromPrivateMethod()
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'AccessedFromPrivateMethod', 'SomeClass', 'SomeClass$Foo'
    }

    def "change to class accessed from private method body only recompile that class and the direct consumer"() {
        def componentUnderTest = new IncrementalLib()
        componentUnderTest.writeToProject()

        outputs.snapshot { run language.compileTaskName }

        when:
        componentUnderTest.applyModificationToClassAccessedFromPrivateMethodBody()
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'AccessedFromPrivateMethodBody', 'SomeClass', 'SomeClass$Foo'
    }

    def "change to class accessed from package private field only recompile that class and transitive consumer"() {
        def componentUnderTest = new IncrementalLib()
        componentUnderTest.writeToProject()

        outputs.snapshot { run language.compileTaskName }

        when:
        componentUnderTest.applyModificationToClassAccessedFromPackagePrivateField()
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'AccessedFromPackagePrivateField', 'SomeClass', 'SomeClass$Foo', 'UsingSomeClass'
    }

    def "change to class accessed from private field only recompile that class and direct consumer"() {
        def componentUnderTest = new IncrementalLib()
        componentUnderTest.writeToProject()

        outputs.snapshot { run language.compileTaskName }

        when:
        componentUnderTest.applyModificationToClassAccessedFromPrivateField()
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'AccessedFromPrivateField', 'SomeClass', 'SomeClass$Foo'
    }

    def "change to class accessed from private inner class's public field only recompile that class and direct consumer"() {
        def componentUnderTest = new IncrementalLib()
        componentUnderTest.writeToProject()

        outputs.snapshot { run language.compileTaskName }

        when:
        componentUnderTest.applyModificationToClassAccessedFromPrivateClassPublicField()
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'AccessedFromPrivateClassPublicField', 'SomeClass', 'SomeClass$Foo'
    }

    def "change to class accessed from private inner class's public method body only recompile that class and direct consumer"() {
        def componentUnderTest = new IncrementalLib()
        componentUnderTest.writeToProject()

        outputs.snapshot { run language.compileTaskName }

        when:
        componentUnderTest.applyModificationToClassAccessedFromPrivateClass()
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'AccessedFromPrivateClass', 'SomeClass', 'SomeClass$Foo'
    }

    def "detects deletion of an isolated source class with an inner class"() {
        def a = source """class A {
            class InnerA {}
        }"""
        source "class B {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        assert a.delete()
        run language.compileTaskName

        then:
        outputs.noneRecompiled() //B is not recompiled
        outputs.deletedClasses 'A', 'A$InnerA' //inner class is also deleted
    }

    def "detects change of an isolated source class with an inner class"() {
        source """class A {
            class InnerA {}
        }"""
        source "class B {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        source """class A {
            class InnerA { /* change */ }
        }"""
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'A', 'A$InnerA'
    }

    def "detects change of an isolated class"() {
        source "class A {}", "class B {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        source "class A { /* change */ }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'A'
    }

    def "detects deletion of an inner class"() {
        source """class A {
            class InnerA {}
        }"""
        source "class B {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        source "class A {}"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'A'
        outputs.deletedClasses 'A$InnerA'
    }

    def "detects rename of an inner class"() {
        source """class A {
            class InnerA {}
        }"""
        source "class B {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        source """class A {
            class InnerA2 {}
        }"""
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'A', 'A$InnerA2'
        outputs.deletedClasses 'A$InnerA'
    }

    def "detects addition af a new class with an inner class"() {
        source "class B {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        source """class A {
            class InnerA {}
        }"""
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'A', 'A$InnerA'
    }

    def "detects transitive dependencies"() {
        source "class A {}", "class B extends A {}", "class C extends B {}", "class D {}"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class A { /* change */ }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'A', 'B', 'C'

        when:
        outputs.snapshot()
        source "class B { /* change */ }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'B', 'C'
    }

    def "detects transitive dependencies with inner classes"() {
        source "class A {}", "class B extends A {}", "class D {}"
        source """class C extends B {
            class InnerC {}
        }
        """
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class A { /* change */ }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'A', 'B', 'C', 'C$InnerC'
    }

    def "handles cycles in class dependencies"() {
        source "class A {}", "class D {}"
        source "class B extends A { C c; }", "class C extends B {}" //cycle
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class A { /* change */ }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'A', 'B', 'C'
    }

    def "change to class referenced by an annotation recompiles annotated types"() {
        source """
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.CLASS)
            public @interface B {
                Class<?> value();
            }
        """
        def a = source "class A {}"
        source "@B(A.class) class OnClass {}",
            "class OnMethod { @B(A.class) void foo() {} }",
            "class OnField { @B(A.class) String foo; }",
            "class OnParameter { void foo(@B(A.class) int x) {} }"
        outputs.snapshot { run language.compileTaskName }

        when:
        a.text += "/* change */"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "OnClass", "OnMethod", "OnParameter", "OnField")
    }

    def "change to class referenced by an array value in an annotation recompiles annotated types"() {
        source """
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.CLASS)
            public @interface B {
                Class<?>[] value();
            }
        """
        def a = source "class A {}"
        source "@B(A.class) class OnClass {}",
            "class OnMethod { @B(A.class) void foo() {} }",
            "class OnField { @B(A.class) String foo; }",
            "class OnParameter { void foo(@B(A.class) int x) {} }"
        outputs.snapshot { run language.compileTaskName }

        when:
        a.text += "/* change */"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "OnClass", "OnMethod", "OnParameter", "OnField")
    }

    def "change to enum referenced by an annotation recompiles annotated types"() {
        source """
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.CLASS)
            public @interface B {
                A value();
            }
        """
        def a = source "enum A { FOO }"
        source "@B(A.FOO) class OnClass {}",
            "class OnMethod { @B(A.FOO) void foo() {} }",
            "class OnField { public @B(A.FOO) String foo; }",
            "class OnParameter { void foo(@B(A.FOO) int x) {} }"
        outputs.snapshot { run language.compileTaskName }

        when:
        a.text += "/* change */"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "OnClass", "OnMethod", "OnParameter", "OnField")
    }

    def "change to value in nested annotation recompiles annotated types"() {
        source """
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.CLASS)
            public @interface B {
                A value();
            }
        """
        source "public @interface A { Class<?> value(); }"
        def c = source "class C {}"
        source "@B(@A(C.class)) class OnClass {}",
            "class OnMethod { @B(@A(C.class)) void foo() {} }",
            "class OnField { @B(@A(C.class)) String foo; }",
            "class OnParameter { void foo(@B(@A(C.class)) int x) {} }"
        outputs.snapshot { run language.compileTaskName }

        when:
        c.text += "/* change */"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("C", "OnClass", "OnMethod", "OnParameter", "OnField")
    }

    def "changed class with private constant does not incur full rebuild"() {
        source "class A {}", "class B { private final static int x = 1;}"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class B { /* change */ }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'B'
    }

    def "dependent class with non-private constant does not incur full rebuild"() {
        source "class A {}", "class B extends A { final static int x = 1;}", "class C {}"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class A { /* change */ }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'B', 'A'
    }

    @Unroll("detects changes to class referenced through a #modifier field")
    def "detects changes to class referenced through a field"() {
        given:
        source """class A {
    $modifier B b;
    void doSomething() {
        Runnable r = b;
        r.run();
    }
}"""
        source '''abstract class B implements Runnable { }'''

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/B.${languageName}").text = "class B { }"

        then:
        recompiledWithFailure('Runnable r = b;', 'A', 'B')

        where:
        modifier << ['', 'static']
    }

    @Unroll("detects changes to class referenced through a #modifier array field")
    def "detects changes to class referenced through an array field"() {
        given:
        source """class A {
    $modifier B[] b;
    void doSomething() {
        Runnable r = b[0];
        r.run();
    }
}"""
        source '''abstract class B implements Runnable { }'''

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/B.${languageName}").text = "class B { }"

        then:
        recompiledWithFailure('Runnable r = b[0];', 'A', 'B')

        where:
        modifier << ['', 'static']
    }

    @Unroll("detects changes to class referenced through a #modifier multi-dimensional array field")
    def "detects changes to class referenced through an multi-dimensional array field"() {
        given:
        source """class A {
    $modifier B[][] b;
    void doSomething() {
        Runnable r = b[0][0];
        r.run();
    }
}"""
        source '''abstract class B implements Runnable { }'''

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/B.${languageName}").text = "class B { }"

        then:
        recompiledWithFailure('Runnable r = b[0][0];', 'A', 'B')

        where:
        modifier << ['', 'static']
    }

    def "detects changes to class referenced in method body"() {
        given:
        source '''class A {
    void doSomething(Object b) {
        Runnable r = (B) b;
        r.run();
    }
}'''
        source '''abstract class B implements Runnable { }'''

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/B.${languageName}").text = "class B { }"

        then:
        recompiledWithFailure('Runnable r = (B) b;', 'A', 'B')
    }

    def "detects changes to class referenced through return type"() {
        given:
        source '''class A {
    B b() { return null; }

    void doSomething() {
        Runnable r = b();
        r.run();
    }
}'''
        source '''abstract class B implements Runnable { }'''

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/B.${languageName}").text = "class B { }"

        then:
        recompiledWithFailure('Runnable r = b();', 'A', 'B')
    }

    def "detects changes to class referenced through method signature"() {
        given:
        source '''class A {
    Runnable go(B b) {
        Runnable r = b;
        r.run();
        return b;
    }
}'''
        source '''abstract class B implements Runnable { }'''

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/B.${languageName}").text = "class B { }"

        then:
        recompiledWithFailure('Runnable r = b;', 'A', 'B')
    }

    def "detects changes to class referenced through type argument in field"() {
        given:
        source '''class A {
    java.util.List<B> bs;
    void doSomething() {
        for (B b: bs) {
           Runnable r = b;
           r.run();
        }
    }
}'''
        source '''abstract class B implements Runnable { }'''

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/B.${languageName}").text = "class B { }"

        then:
        recompiledWithFailure('Runnable r = b;', 'A', 'B')
    }

    def "detects changes to class referenced through type argument in return type"() {
        given:
        source '''class A {
    java.util.List<B> bs() { return null; }

    void doSomething() {
        for (B b: bs()) {
           Runnable r = b;
           r.run();
        }
    }
}'''
        source '''abstract class B implements Runnable { }'''

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/B.${languageName}").text = "class B { }"

        then:
        recompiledWithFailure('Runnable r = b;', 'A', 'B')
    }

    def "detects changes to class referenced through type argument in parameter"() {
        given:
        source '''class A {

    void doSomething(java.util.List<B> bs) {
        for (B b: bs) {
           Runnable r = b;
           r.run();
        }
    }
}'''
        source '''abstract class B implements Runnable { }'''

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/B.${languageName}").text = "class B { }"

        then:
        recompiledWithFailure('Runnable r = b;', 'A', 'B')
    }
}
