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

import org.gradle.integtests.fixtures.CompiledLanguage
import spock.lang.Issue
import spock.lang.Unroll

abstract class AbstractSourceIncrementalCompilationIntegrationTest extends AbstractJavaGroovyIncrementalCompilationSupport {

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

    def "detects class changes in subsequent runs ensuring the class dependency data is refreshed"() {
        source "class A {}", "class B {}", "class C {}"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class B extends A {}"
        run language.compileTaskName

        then:
        outputs.recompiledClasses('B')

        when:
        outputs.snapshot()
        source "class A { /* change */ }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses('A', 'B')
    }

    def "handles multiple compile tasks within a single project"() {
        source "class A {}", "class B extends A {}"
        file("src/integTest/${languageName}/X.${languageName}") << "class X {}"
        file("src/integTest/${languageName}/Y.${languageName}") << "class Y extends X {}"
        //new separate compile task (integTestCompile)
        file("build.gradle") << """
            sourceSets { integTest.${languageName}.srcDir "src/integTest/${languageName}" }
        """
        if (language == CompiledLanguage.GROOVY) {
            buildFile << """
                dependencies {
                    integTestImplementation localGroovy()
                }
"""
        }

        outputs.snapshot { run "compileIntegTest${language.capitalizedName}", language.compileTaskName }

        when: //when A class is changed
        source "class A { String change; }"
        run "compileIntegTest${language.capitalizedName}", language.compileTaskName, "-i"

        then: //only B and A are recompiled
        outputs.recompiledClasses("A", "B")

        when: //when X class is changed
        outputs.snapshot()
        file("src/integTest/${languageName}/X.${languageName}").text = "class X { String change;}"
        run "compileIntegTest${language.capitalizedName}", language.compileTaskName, "-i"

        then: //only X and Y are recompiled
        outputs.recompiledClasses("X", "Y")
    }

    def "recompiles classes from extra source directories"() {
        buildFile << "sourceSets.main.${languageName}.srcDir 'extra'"

        source("class B {}")
        file("extra/A.${languageName}") << "class A extends B {}"
        file("extra/C.${languageName}") << "class C {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        source("class B { String change; } ")
        run language.compileTaskName

        then:
        outputs.recompiledClasses("B", "A")
    }

    def 'can move classes between source dirs'() {
        given:
        buildFile << "sourceSets.main.${languageName}.srcDir 'extra'"
        source('class A1 {}')
        file("extra/A2.${languageName}") << "class A2 {}"
        def movedFile = file("extra/some/dir/B.${languageName}") << """package some.dir;
        public class B {
            public static class Inner { }
        }"""

        run language.compileTaskName

        when:
        movedFile.moveToDirectory(file("src/main/${languageName}/some/dir"))
        outputs.snapshot { run language.compileTaskName, '-i' }

        then:
        skipped(":${language.compileTaskName}")

        when:
        file("src/main/${languageName}/some/dir/B.${languageName}").text = """package some.dir;
        public class B {
            public static class NewInner { }
        }""" // in B.java/B.groovy
        run language.compileTaskName

        then:
        executedAndNotSkipped(":${language.compileTaskName}")
        outputs.recompiledClasses('B', 'B$NewInner')
        outputs.deletedClasses('B$Inner')
    }

    def "recompilation considers changes from dependent sourceSet"() {
        buildFile << """
sourceSets {
    other {}
    main { compileClasspath += sourceSets.other.output }
}
"""
        if (language == CompiledLanguage.GROOVY) {
            buildFile << """
        dependencies {
            otherImplementation localGroovy()
        }
"""
        }

        source("class Main extends com.foo.Other {}")
        file("src/other/${languageName}/com/foo/Other.${languageName}") << "package com.foo; public class Other {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/other/${languageName}/com/foo/Other.${languageName}").text = "package com.foo; public class Other { String change; }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("Other", "Main")
    }

    def "recompilation does not process removed classes from dependent sourceSet"() {
        def unusedClass = source("public class Unused {}")
        // Need another class or :compileJava will always be considered UP-TO-DATE
        source("public class Other {}")

        file("src/test/${languageName}/BazTest.${languageName}") << "public class BazTest {}"

        outputs.snapshot { run "compileTest${language.capitalizedName}" }

        when:
        file("src/test/${languageName}/BazTest.${languageName}").text = "public class BazTest { String change; }"
        unusedClass.delete()

        run "compileTest${language.capitalizedName}"

        then:
        outputs.recompiledClasses("BazTest")
        outputs.deletedClasses("Unused")
    }

    def "detects changes to source in extra source directories"() {
        buildFile << "sourceSets.main.${languageName}.srcDir 'extra'"

        source("class A extends B {}")
        file("extra/B.${languageName}") << "class B {}"
        file("extra/C.${languageName}") << "class C {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        file("extra/B.${languageName}").text = "class B { String change; }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("B", "A")
    }

    @Unroll
    def "recompiles classes from extra source directory provided as #type"() {
        given:
        buildFile << "${language.compileTaskName}.source $method('extra')"

        source("class B {}")
        file("extra/A.${languageName}") << "class A extends B {}"
        file("extra/C.${languageName}") << "class C {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        source("class B { String change; } ")
        run language.compileTaskName

        then:
        outputs.recompiledClasses("B", "A")

        where:
        type            | method
        "File"          | "file"
        "DirectoryTree" | "fileTree"
    }

    @Unroll
    def "detects changes to source in extra source directory provided as #type"() {
        buildFile << "${language.compileTaskName}.source $method('extra')"

        source("class A extends B {}")
        file("extra/B.${languageName}") << "class B {}"
        file("extra/C.${languageName}") << "class C {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        file("extra/B.${languageName}").text = "class B { String change; }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("B", "A")

        where:
        type            | method
        "File"          | "file"
        "DirectoryTree" | "fileTree"
    }

    def "missing files are ignored as source roots"() {
        buildFile << """
            ${language.compileTaskName} {
                source([
                    fileTree('missing-tree'),
                    file('missing-file')
                ])
            }"""

        source("class A extends B {}")
        source("class B {}")
        source("class C {}")

        outputs.snapshot { run language.compileTaskName }

        when:
        source("class B { String change; }")
        executer.withArgument "--info"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B")
    }

    def "can remove source root"() {
        def toBeRemoved = file("to-be-removed")
        buildFile << """
            ${language.getCompileTaskName()} {
                source([fileTree('to-be-removed')])
            }"""

        source("class A extends B {}")
        source("class B {}")
        toBeRemoved.file("C.${languageName}").text = "class C {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        toBeRemoved.deleteDir()
        executer.withArgument "--info"
        run language.compileTaskName

        then:
        outputs.recompiledClasses()
    }

    def "handles duplicate class across source directories"() {
        //compiler does not allow this scenario, documenting it here
        buildFile << "sourceSets.main.${languageName}.srcDir 'java'"

        source("class A {}")
        file("java/A.${languageName}") << "class A {}"

        when:
        fails language.compileTaskName
        then:
        failure.assertHasCause("Compilation failed")
    }

    @Issue("GRADLE-3426")
    def "supports Java 1.2 dependencies"() {
        source "class A {}"

        buildFile << """
            ${mavenCentralRepository()}
            dependencies { implementation 'com.ibm.icu:icu4j:2.6.1' }
        """
        expect:
        succeeds language.compileTaskName
    }

    @Issue("GRADLE-3426")
    def "fully recompiles when a non-analyzable jar is changed"() {
        def a = source """
            import com.ibm.icu.util.Calendar;
            class A {
                Calendar cal;
            }
        """

        buildFile << """
            ${mavenCentralRepository()}
            if (providers.gradleProperty("withIcu").forUseAtConfigurationTime().isPresent()) {
                dependencies { implementation 'com.ibm.icu:icu4j:2.6.1' }
            }

        """
        succeeds language.compileTaskName, "-PwithIcu"

        when:
        a.text = "class A {}"

        then:
        succeeds language.compileTaskName, "--info"
        outputContains("Full recompilation is required because LocaleElements_zh__PINYIN.class could not be analyzed for incremental compilation.")
    }

    @Issue("GRADLE-3495")
    def "supports Java 1.1 dependencies"() {
        source "class A {}"

        buildFile << """
            ${mavenCentralRepository()}
            dependencies { implementation 'net.sf.ehcache:ehcache:2.10.2' }
        """
        expect:
        run language.compileTaskName
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

    def "deletes empty packages dirs"() {
        given:
        def a = file("src/main/${languageName}/com/foo/internal/A.${languageName}") << """
            package com.foo.internal;
            public class A {}
        """
        file("src/main/${languageName}/com/bar/B.${languageName}") << """
            package com.bar;
            public class B {}
        """

        succeeds language.compileTaskName
        a.delete()

        when:
        succeeds language.compileTaskName

        then:
        !file("build/classes/java/main/com/foo").exists()
    }

    def "recompiles types whose names look like inner classes even if they aren't"() {
        given:
        file("src/main/${languageName}/Test.${languageName}") << 'public class Test{}'
        file("src/main/${languageName}/Test\$\$InnerClass.${languageName}") << 'public class Test$$InnerClass{}'

        when:
        succeeds ":${language.compileTaskName}"

        then:
        executedAndNotSkipped ":${language.compileTaskName}"
        file("build/classes/${languageName}/main/Test.class").assertExists()
        file("build/classes/${languageName}/main/Test\$\$InnerClass.class").assertExists()

        when:
        file("src/main/${languageName}/Test.${languageName}").text = 'public class Test{ void foo() {} }'
        succeeds ":${language.compileTaskName}"

        then:
        executedAndNotSkipped ":${language.compileTaskName}"
        file("build/classes/${languageName}/main/Test.class").assertExists()
        file("build/classes/${languageName}/main/Test\$\$InnerClass.class").assertExists()
    }

    def "incremental java compilation ignores empty packages"() {
        given:
        file("src/main/${languageName}/org/gradle/test/MyTest.${languageName}").text = """
            package org.gradle.test;

            class MyTest {}
        """

        when:
        run language.compileTaskName
        then:
        executedAndNotSkipped(":${language.compileTaskName}")

        when:
        file('src/main/${languageName}/org/gradle/different').createDir()
        run(language.compileTaskName)

        then:
        skipped(":${language.compileTaskName}")
    }

    def "recompiles all classes in a package if the package-info file changes"() {
        given:
        file("src/main/${languageName}/annotations/Anno.${languageName}").text = """
            package annotations;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.PACKAGE)
            public @interface Anno {}
        """
        def packageFile = file("src/main/${languageName}/foo/package-info.${languageName}")
        packageFile.text = """@Deprecated package foo;"""
        file("src/main/${languageName}/foo/A.${languageName}").text = "package foo; class A {}"
        file("src/main/${languageName}/foo/B.${languageName}").text = "package foo; public class B {}"
        file("src/main/${languageName}/foo/bar/C.${languageName}").text = "package foo.bar; class C {}"
        file("src/main/${languageName}/baz/D.${languageName}").text = "package baz; class D {}"
        file("src/main/${languageName}/baz/E.${languageName}").text = "package baz; import foo.B; class E extends B {}"

        outputs.snapshot { succeeds language.compileTaskName }

        when:
        packageFile.text = """@Deprecated @annotations.Anno package foo;"""
        succeeds language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "E", "package-info")
    }

    def "recompiles all classes in a package if the package-info file is added"() {
        given:
        def packageFile = file("src/main/${languageName}/foo/package-info.${languageName}")
        file("src/main/${languageName}/foo/A.${languageName}").text = "package foo; class A {}"
        file("src/main/${languageName}/foo/B.${languageName}").text = "package foo; public class B {}"
        file("src/main/${languageName}/foo/bar/C.${languageName}").text = "package foo.bar; class C {}"
        file("src/main/${languageName}/baz/D.${languageName}").text = "package baz; class D {}"
        file("src/main/${languageName}/baz/E.${languageName}").text = "package baz; import foo.B; class E extends B {}"

        outputs.snapshot { succeeds language.compileTaskName }

        when:
        packageFile.text = """@Deprecated package foo;"""
        succeeds language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "E", "package-info")
    }

    def "recompiles all classes in a package if the package-info file is removed"() {
        given:
        def packageFile = file("src/main/${languageName}/foo/package-info.${languageName}")
        packageFile.text = """@Deprecated package foo;"""
        file("src/main/${languageName}/foo/A.${languageName}").text = "package foo; class A {}"
        file("src/main/${languageName}/foo/B.${languageName}").text = "package foo; public class B {}"
        file("src/main/${languageName}/foo/bar/C.${languageName}").text = "package foo.bar; class C {}"
        file("src/main/${languageName}/baz/D.${languageName}").text = "package baz; class D {}"
        file("src/main/${languageName}/baz/E.${languageName}").text = "package baz; import foo.B; class E extends B {}"

        outputs.snapshot { succeeds language.compileTaskName }

        when:
        packageFile.delete()
        succeeds language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "E")
        outputs.deletedClasses("package-info")
    }

    @Issue('https://github.com/gradle/gradle/issues/9380')
    def 'can move source sets'() {
        given:
        buildFile << "sourceSets.main.${languageName}.srcDir 'src/other/${languageName}'"
        source('class Sub extends Base {}')
        file("src/other/${languageName}/Base.${languageName}") << 'class Base { }'

        outputs.snapshot { run language.compileTaskName }

        when:
        // Remove last line
        buildFile.text = buildFile.text.readLines().findAll { !it.trim().startsWith('sourceSets') }.join('\n')
        fails language.compileTaskName

        then:
        failureCauseContains('Compilation failed')
    }

    protected String getLanguageName() {
        language.name
    }
}
