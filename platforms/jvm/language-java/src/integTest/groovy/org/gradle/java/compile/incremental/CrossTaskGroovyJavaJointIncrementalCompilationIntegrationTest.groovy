/*
 * Copyright 2022 the original author or authors.
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

class CrossTaskGroovyJavaJointIncrementalCompilationIntegrationTest extends AbstractCrossTaskIncrementalCompilationSupport {
    CompiledLanguage language = CompiledLanguage.GROOVY
    boolean useJar = false

    def setup() {
        configureGroovyIncrementalCompilation()
    }

    @Issue("https://github.com/gradle/gradle/issues/22531")
    def 'incremental compilation does not fail on api change referenced via static property when affected class is #bCompileStatic#bSuffix'() {
        given:
        // A is a private dependency of B1 and B1 is referenced in E1.isCacheEnabled through inheritance.
        // B1 is also a private dependency of B2 that is referenced in E2.isCacheEnabled through inheritance.
        File aClass = sourceForLanguageForProject(CompiledLanguage.JAVA, "api", "class A { void m1() {}; }")
        sourceWithFileSuffixForProject(bSuffix, "impl", "$bCompileStatic class B1 { void m1() { A a = new A(); a.m1(); }; }")
        sourceWithFileSuffixForProject("java", "impl", "class C1 extends B1 {}")
        sourceWithFileSuffixForProject("java", "impl", "class D1 extends C1 { static boolean getCache() { return true; } }")
        File e1Class = sourceWithFileSuffixForProject("groovy", "impl", "class E1 { boolean isCacheEnabled = D1.cache }")

        sourceWithFileSuffixForProject("java", "impl", "class B2 { void m1() { B1 b = new B1(); b.m1(); }; }")
        sourceWithFileSuffixForProject("java", "impl", "class C2 extends B2 { }")
        sourceWithFileSuffixForProject("java", "impl", "class D2 extends C2 { static boolean getCache() { return true; } }")
        File e2Class = sourceWithFileSuffixForProject("groovy", "impl", "class E2 { boolean isCacheEnabled = D2.cache }")

        run ":impl:compileGroovy"

        when:
        impl.snapshot {
            aClass.text = "class A { void m1() {}; void m2() {}; }"
            e1Class.text = "class E1 { boolean isCacheEnabled = D1.cache; int a = 0; }"
            e2Class.text = "class E2 { boolean isCacheEnabled = D2.cache; int a = 0; }"
        }
        run ":impl:compileGroovy"

        then:
        impl.recompiledClasses(*expectedRecompiledClass)

        where:
        bSuffix  | bCompileStatic                     | expectedRecompiledClass
        "java"   | ""                                 | ["B1", "C1", "D1", "E1", "E2"]
        "groovy" | ""                                 | ["B1", "E1", "E2"]
        "groovy" | "@groovy.transform.CompileStatic " | ["B1", "E1", "E2"]
    }

    def 'incremental compilation does not fail when some deleted class with Java source is private referenced in class that is loaded by Groovy'() {
        given:
        // A is a private dependency of B1 and B1 is referenced in E1.isCacheEnabled through inheritance.
        // B1 is also a private dependency of B2 that is referenced in E2.isCacheEnabled through inheritance.
        File aClass = sourceForLanguageForProject(CompiledLanguage.JAVA, "api", "class A { void m1() {}; }")
        sourceWithFileSuffixForProject("java", "impl", "class B1 { static B1 m2() { return null; }; void m1() { A a = new A(); a.m1(); }; }")
        sourceWithFileSuffixForProject("java", "impl", "class C1 extends B1 {}")
        sourceWithFileSuffixForProject("java", "impl", "class D1 extends C1 { static boolean getCache() { return true; } }")
        File e1Class = sourceWithFileSuffixForProject("groovy", "impl", "class E1 { boolean isCacheEnabled = D1.cache }")

        sourceWithFileSuffixForProject("java", "impl", """
            class B2 {
                private static final Class<B1> bClass = B1.class;
                private static final B1 b1 = new B1();
                private static final B1 b2;
                static {
                    b2 = new B1();
                }
                private static B1 b3 = new B1();
                private B1 b4 = new B1();
                private B1 m1(B1 b) { return new B1(); };
            }
        """)
        sourceWithFileSuffixForProject("java", "impl", "class C2 extends B2 { }")
        sourceWithFileSuffixForProject("java", "impl", """
            class D2 extends C2 {

                private static final Class<B1> bClass = B1.class;
                private static final B1 b1 = new B1();
                private static final B1 b2;
                static {
                    b2 = new B1();
                }
                private static B1 b3 = new B1();
                private B1 b4 = new B1();
                private B1 m1(B1 b) { return new B1(); };
                static boolean getCache() { return true; }
            }
        """)
        File e2Class = sourceWithFileSuffixForProject("groovy", "impl", "class E2 { boolean isCacheEnabled = D2.cache }")

        run ":impl:compileGroovy"

        when:
        impl.snapshot {
            aClass.text = "class A { void m1() {}; void m2() {}; }"
            e1Class.text = "class E1 { boolean isCacheEnabled = D1.cache; int a = 0; }"
            e2Class.text = "class E2 { boolean isCacheEnabled = D2.cache; int a = 0; }"
        }
        run ":impl:compileGroovy"

        then:
        impl.recompiledClasses("B1", "C1", "D1", "E1", "E2")
    }

    def 'incremental compilation does not fail on api change when we compile only groovy in the dependent project and affected class is #bCompileStatic#bSuffix'() {
        given:
        // A is a private dependency of B and B is referenced in E.isCacheEnabled through inheritance.
        File aClass = sourceForLanguageForProject(CompiledLanguage.JAVA, "api", "class A { void m1() {}; }")
        sourceWithFileSuffixForProject("groovy", "impl", "$bCompileStatic class B { void m1() { A a = new A(); a.m1(); }; }")
        sourceWithFileSuffixForProject("groovy", "impl", "class C extends B {}")
        sourceWithFileSuffixForProject("groovy", "impl", "class D extends C { static boolean getCache() { return true; } }")
        File eClass = sourceWithFileSuffixForProject("groovy", "impl", "class E { boolean isCacheEnabled = D.cache }")

        run ":impl:compileGroovy"

        when:
        impl.snapshot {
            aClass.text = "class A { void m1() {}; void m2() {}; }"
            eClass.text = "class E { boolean isCacheEnabled = D.cache; int a = 0; }"
        }
        run ":impl:compileGroovy"

        then:
        impl.recompiledClasses(*expectedRecompiledClass)

        where:
        bSuffix  | bCompileStatic                     | expectedRecompiledClass
        "groovy" | ""                                 | ["B", "E"]
        "groovy" | "@groovy.transform.CompileStatic " | ["B", "E"]
    }

    def 'incremental compilation after a failure works on api dependency change'() {
        given:
        File aClass = sourceForLanguageForProject(CompiledLanguage.JAVA, "api", "class A { void m1() {}; }")
        sourceWithFileSuffixForProject("java", "impl", "class B { void m1() { new A().m1(); }; }")
        sourceWithFileSuffixForProject("java", "impl", "class C extends B {}")
        sourceWithFileSuffixForProject("java", "impl", "class D extends C { static boolean getCache() { return true; } }")
        File eClass = sourceWithFileSuffixForProject("groovy", "impl", "class E { boolean isCacheEnabled = D.cache }")
        sourceWithFileSuffixForProject("java", "impl", "class F {}")
        sourceWithFileSuffixForProject("groovy", "impl", "class G {}")
        run ":impl:compileGroovy"

        when:
        aClass.text = "class A { void m1() {}; void m2() {}; }"
        eClass.text = "class E { boolean isCacheEnabled = D.cache; garbage }"

        then:
        runAndFail ":impl:compileGroovy"

        when:
        impl.snapshot {
            aClass.text = "class A { void m1() {}; void m2() {}; }"
            eClass.text = "class E { boolean isCacheEnabled = D.cache; int i = 0; }"
        }
        run ":impl:compileGroovy"

        then:
        impl.recompiledClasses("B", "C", "D", "E")
    }
}
