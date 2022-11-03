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
    def 'inc. compilation does not fail on api change referenced via static property when affected class is #bCompileStatic #bSuffix'() {
        given:
        // A is a private dependency of B and B is referenced in E.isCacheEnabled through inheritance
        File aClass = sourceForLanguageForProject(CompiledLanguage.JAVA, "api", "class A { void m1() {}; }")
        sourceWithFileSuffixForProject(bSuffix, "impl", "$bCompileStatic class B { void m1() { new A().m1(); }; }")
        sourceWithFileSuffixForProject("java", "impl", "class C extends B {}")
        sourceWithFileSuffixForProject("java", "impl", "class D extends C { static boolean getCache() { return true; } }")
        File eClass = sourceWithFileSuffixForProject("groovy", "impl", "class E { boolean isCacheEnabled = D.cache }")
        run ":impl:compileGroovy"

        when:
        aClass.text = "class A { void m1() {}; void m2() {}; }"
        eClass.text = "class E { boolean isCacheEnabled = D.cache; int a = 0; }"
        impl.snapshot()

        then:
        run ":impl:compileGroovy"
        impl.recompiledClasses(*expectedRecompiledClass)

        where:
        bSuffix  | bCompileStatic                    | expectedRecompiledClass
        "java"   | ""                                | ["B", "C", "D", "E"]
        "groovy" | ""                                | ["E"]
        "groovy" | "@groovy.transform.CompileStatic" | ["B", "E"]
    }

    def 'inc. compilation does not fail on api change when we compile only groovy and affected class is #bCompileStatic #bSuffix'() {
        given:
        buildFile << """
            allprojects {
                tasks.withType(GroovyCompile) {
                    options.incremental = true
                    groovyOptions.fileExtensions = ['groovy']
                }
            }
        """
        // A is a private dependency of B and B is referenced in E.isCacheEnabled through inheritance
        File aClass = sourceForLanguageForProject(CompiledLanguage.JAVA, "api", "class A { void m1() {}; }")
        sourceWithFileSuffixForProject(bSuffix, "impl", "$bCompileStatic class B { void m1() { new A().m1(); }; }")
        sourceWithFileSuffixForProject("java", "impl", "class C extends B {}")
        sourceWithFileSuffixForProject("java", "impl", "class D extends C { static boolean getCache() { return true; } }")
        File eClass = sourceWithFileSuffixForProject("groovy", "impl", "class E { boolean isCacheEnabled = D.cache }")
        run ":impl:compileGroovy"

        when:
        aClass.text = "class A { void m1() {}; void m2() {}; }"
        eClass.text = "class E { boolean isCacheEnabled = D.cache; int a = 0; }"
        impl.snapshot()

        then:
        run ":impl:compileGroovy"
        impl.recompiledClasses(*expectedRecompiledClass)

        where:
        bSuffix  | bCompileStatic                    | expectedRecompiledClass
        "java"   | ""                                | ["E"]
        "groovy" | ""                                | ["E"]
        "groovy" | "@groovy.transform.CompileStatic" | ["B", "E"]
    }

    def 'incremental compilation after a failure works on api dependency change'() {
        given:
        File aClass = sourceForLanguageForProject(CompiledLanguage.JAVA, "api", "class A { void m1() {}; }")
        sourceWithFileSuffixForProject("java", "impl", "class B { void m1() { new A().m1(); }; }")
        sourceWithFileSuffixForProject("java", "impl", "class C extends B {}")
        sourceWithFileSuffixForProject("java", "impl", "class D extends C { static boolean getCache() { return true; } }")
        File eClass = sourceWithFileSuffixForProject("groovy", "impl", "class E { boolean isCacheEnabled = D.cache }")
        run ":impl:compileGroovy"

        when:
        aClass.text = "class A { void m1() {}; void m2() {}; }"
        eClass.text = "class E { boolean isCacheEnabled = D.cache; garbage }"

        then:
        runAndFail ":impl:compileGroovy"

        when:
        aClass.text = "class A { void m1() {}; void m2() {}; }"
        eClass.text = "class E { boolean isCacheEnabled = D.cache; int i = 0; }"
        impl.snapshot()

        then:
        run ":impl:compileGroovy"
        impl.recompiledClasses("B", "C", "D", "E")
    }
}
