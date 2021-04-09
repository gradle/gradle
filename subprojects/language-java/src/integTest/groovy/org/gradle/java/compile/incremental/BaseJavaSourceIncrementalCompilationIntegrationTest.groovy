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


import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue
import spock.lang.Unroll

abstract class BaseJavaSourceIncrementalCompilationIntegrationTest extends AbstractSourceIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    void recompiledWithFailure(String expectedFailure, String... recompiledClasses) {
        fails language.compileTaskName
        failure.assertHasErrorOutput(expectedFailure)
    }

    @Unroll
    def "change to #retention retention annotation class recompiles #desc"() {
        def annotationClass = file("src/main/${language.name}/SomeAnnotation.${language.name}") << """
            import java.lang.annotation.*;

            @Retention(RetentionPolicy.$retention)
            public @interface SomeAnnotation {}
        """
        source "@SomeAnnotation class A {}", "class B {}"
        outputs.snapshot { run language.compileTaskName }

        when:
        annotationClass.text += "/* change */"
        run language.compileTaskName

        then:
        outputs.recompiledClasses(expected as String[])

        where:
        desc              | retention | expected
        'all'             | 'SOURCE'  | ['A', 'B', 'SomeAnnotation']
        'annotated types' | 'CLASS'   | ['SomeAnnotation', 'A']
        'annotated types' | 'RUNTIME' | ['SomeAnnotation', 'A']
    }

    def "deletes headers when source file is deleted"() {
        given:
        def sourceFile = file("src/main/java/my/org/Foo.java")
        sourceFile.text = """
            package my.org;

            public class Foo {
                public native void foo();

                public static class Inner {
                    public native void anotherNative();
                }
            }
        """
        def generatedHeaderFile = file("build/generated/sources/headers/java/main/my_org_Foo.h")
        def generatedInnerClassHeaderFile = file("build/generated/sources/headers/java/main/my_org_Foo_Inner.h")

        source("""class Bar {
            public native void bar();
        }""")

        succeeds language.compileTaskName
        generatedHeaderFile.assertExists()
        generatedInnerClassHeaderFile.assertExists()

        when:
        sourceFile.delete()
        succeeds language.compileTaskName

        then:
        generatedHeaderFile.assertDoesNotExist()
        generatedInnerClassHeaderFile.assertDoesNotExist()
        file("build/generated/sources/headers/java/main/Bar.h").assertExists()
    }

    def "changed class with non-private constant incurs full rebuild"() {
        source "class A { int foo() { return 1; } }", "class B { final static int x = 1;}"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class B { /* change */ }"
        run language.compileTaskName, "--info"

        then:
        outputContains("Full recompilation is required because an inlineable constant in 'B' has changed.")
        outputs.recompiledClasses 'B', 'A'
    }

    def "reports source type that does not support detection of source root"() {
        given:
        buildFile << "${language.compileTaskName}.source([file('extra'), file('other'), file('text-file.txt')])"

        source("class A extends B {}")
        file("extra/B.${language.name}") << "class B {}"
        file("extra/C.${language.name}") << "class C {}"
        def textFile = file('text-file.txt')
        textFile.text = "text file as root"

        outputs.snapshot { run language.compileTaskName }

        when:
        file("extra/B.${language.name}").text = "class B { String change; }"
        executer.withArgument "--info"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "C")
        output.contains("Cannot infer source root(s) for source `file '${textFile.absolutePath}'`. Supported types are `File` (directories only), `DirectoryTree` and `SourceDirectorySet`.")
        output.contains("Full recompilation is required because the source roots could not be inferred.")
    }

    def "does not recompile when a resource changes"() {
        given:
        buildFile << """
            ${language.compileTaskName}.source 'src/main/resources'
        """
        source("class A {}")
        source("class B {}")
        def resource = file("src/main/resources/foo.txt")
        resource.text = 'foo'

        outputs.snapshot { succeeds language.compileTaskName }

        when:
        resource.text = 'bar'

        then:
        succeeds language.compileTaskName
        outputs.noneRecompiled()
    }

    @ToBeImplemented
    @Issue("https://github.com/gradle/gradle/issues/8590")
    def "adding a class with higher resolution priority should trigger recompilation"() {
        file("src/main/java/foo/A.java") << """
package foo;
import bar.*;
public class A {
  Other getOther() { return null; }
}
"""
        file("src/main/java/bar/Other.java") << """
package bar;
public class Other {}
"""
        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/java/foo/Other.java") << """
package foo;
public class Other {}
        """

        then:
        succeeds language.compileTaskName
        outputs.recompiledFqn("foo.Other") // should be foo.A and foo.Other
    }

    def "change to anonymous inner class recompiles enclosing class and consumer"() {
        def componentUnderTest = new LibWithAnonymousClasses()
        componentUnderTest.writeToProject()

        outputs.snapshot { run language.compileTaskName }

        when:
        componentUnderTest.modifyAnonymousImplementation()
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'Anonymous2', 'Anonymous2$1', 'Consumer'
    }

    def "change to method-local class recompiles enclosing class and consumer"() {
        def componentUnderTest = new LibWithAnonymousClasses()
        componentUnderTest.writeToProject()

        outputs.snapshot { run language.compileTaskName }

        when:
        componentUnderTest.modifyMethodLocalImplementation()
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'MethodLocal2', 'MethodLocal2$1Bar', 'Consumer'
    }

    def "change to lambda recompiles enclosing class and consumer"() {
        def componentUnderTest = new LibWithAnonymousClasses()
        componentUnderTest.writeToProject()

        outputs.snapshot { run language.compileTaskName }

        when:
        componentUnderTest.modifyLambdaImplementation()
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'Lambda2', 'Consumer'
    }

    class LibWithAnonymousClasses {
        void writeToProject() {
            source """
                interface SomeInterface {
                    int foo();
                }
            """
            source """
                class Anonymous1 {
                    SomeInterface getAnonymous() {
                        return new SomeInterface() {
                            public int foo() {
                                return 0;
                            }
                        };
                    }
                }
            """
            source """
                class Anonymous2 {
                    SomeInterface getAnonymous() {
                        return new SomeInterface() {
                            public int foo() {
                                return 1;
                            }
                        };
                    }
                }
            """
            source """
                class MethodLocal1 {
                    SomeInterface getAnonymous() {
                        class Foo implements SomeInterface {
                            public int foo() {
                                return 2;
                            }
                        }
                        return new Foo();
                    }
                }
            """
            source """
                class MethodLocal2 {
                    SomeInterface getAnonymous() {
                        class Bar implements SomeInterface {
                            public int foo() {
                                return 2;
                            }
                        }
                        return new Bar();
                    }
                }
            """
            source """
                class Lambda1 {
                    SomeInterface getAnonymous() {
                        return () -> 4;
                    }
                }
            """
            source """
                class Lambda2 {
                    SomeInterface getAnonymous() {
                        return () -> 5;
                    }
                }
            """
            source """
                class Consumer {
                    void consume() {
                        System.out.println(new Anonymous1().getAnonymous().foo());
                        System.out.println(new Anonymous2().getAnonymous().foo());
                        System.out.println(new MethodLocal1().getAnonymous().foo());
                        System.out.println(new MethodLocal2().getAnonymous().foo());
                        System.out.println(new Lambda1().getAnonymous().foo());
                        System.out.println(new Lambda2().getAnonymous().foo());
                    }
                }
            """
        }

        void modifyAnonymousImplementation() {
            source """
                class Anonymous2 {
                    SomeInterface getAnonymous() {
                        return new SomeInterface() {
                            public int foo() {
                                return 42;
                            }
                        };
                    }
                }
            """
        }

        void modifyMethodLocalImplementation() {
            source """
                class MethodLocal2 {
                    SomeInterface getAnonymous() {
                        class Bar implements SomeInterface {
                            public int foo() {
                                return 42;
                            }
                        }
                        return new Bar();
                    }
                }
            """
        }

        void modifyLambdaImplementation() {
            source """
                class Lambda2 {
                    SomeInterface getAnonymous() {
                        return () -> 42;
                    }
                }
            """
        }
    }

}
