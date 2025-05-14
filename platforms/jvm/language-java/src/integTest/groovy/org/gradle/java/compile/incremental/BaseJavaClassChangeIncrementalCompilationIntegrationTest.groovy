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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

abstract class BaseJavaClassChangeIncrementalCompilationIntegrationTest extends AbstractClassChangeIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    void recompiledWithFailure(String expectedFailure, String... recompiledClasses) {
        fails language.compileTaskName
        failure.assertHasErrorOutput(expectedFailure)
    }

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

    @Issue("https://github.com/gradle/gradle/issues/33161")
    def "detects changes to class referenced in method body via lambda"() {
        given:
        source '''class A {
            void doSomething() {
                takeRunnable((B) () -> {
                    System.out.println("Hello");
                });
            }

            void takeRunnable(Runnable r) {
                r.run();
            }
        }'''
        source "interface B extends Runnable {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/B.${languageName}").text = "class B { }"

        then:
        recompiledWithFailure('takeRunnable((B) () -> {', 'A', 'B')
    }

    def "does not recompile downstream class for change in method body lambda"() {
        given:
        source '''class A {
            void doSomething() {
                takeRunnable((B) () -> {
                    System.out.println("Hello");
                });
            }

            void takeRunnable(Runnable r) {
                r.run();
            }
        }'''
        source "interface B extends Runnable {}"
        source "class C extends A {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/B.${languageName}").text = """interface B extends Runnable {
            default void newMethod() {}
        }"""
        run language.compileTaskName

        then:
        // C should not be recompiled as the API of A has not changed
        outputs.recompiledClasses("A", "B")
    }

    @Issue("https://github.com/gradle/gradle/issues/33161")
    @Requires(value = UnitTestPreconditions.Jdk21OrLater, reason = "JDK 21+ required for new enum switch expressions")
    def "detects changes to class referenced in method body via enum in switch expression"() {
        given:
        source """class A {
            void doSomething(Object o) {
                switch (o) {
                    case B.INSTANCE -> System.out.println("B");
                    default -> System.out.println("Default");
                }
            }
        }"""
        source "enum B { INSTANCE }"

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/B.${languageName}").text = "class B { }"

        then:
        recompiledWithFailure('case B.INSTANCE ->', 'A', 'B')
    }

    def "renaming inner class removes stale class file"() {
        def componentUnderTest = new LibWithInnerClasses()
        componentUnderTest.writeToProject()

        outputs.snapshot { run language.compileTaskName }

        when:
        componentUnderTest.changeInnerName()
        run language.compileTaskName

        then:
        outputs.recompiledClasses 'WithInner2', 'WithInner2$Inner2', 'Consumer'
        outputs.deletedClasses('WithInner2$Inner')
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

    class LibWithInnerClasses {
        void writeToProject() {
            source """
                interface SomeInterface {
                    int foo();
                }
            """
            source """
                class WithInner1 {
                    SomeInterface getInner() {
                        return new Inner();
                    }

                    static class Inner implements SomeInterface {
                        public int foo() {
                            return 0;
                        }
                    }
                }
            """
            source """
                class WithInner2 {
                    SomeInterface getInner() {
                        return new Inner();
                    }

                    static class Inner implements SomeInterface {
                        public int foo() {
                            return 1;
                        }
                    }
                }
            """
            source """
                class Consumer {
                    void consume() {
                        System.out.println(new WithInner1().getInner().foo());
                        System.out.println(new WithInner2().getInner().foo());
                    }
                }
            """
        }

        void changeInnerName() {
            source """
                class WithInner2 {
                    SomeInterface getInner() {
                        return new Inner2();
                    }

                    static class Inner2 implements SomeInterface {
                        public int foo() {
                            return 42;
                        }
                    }
                }
            """
        }
    }
}
