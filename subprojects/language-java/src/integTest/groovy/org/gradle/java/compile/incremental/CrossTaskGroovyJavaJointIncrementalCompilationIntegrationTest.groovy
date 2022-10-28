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
import spock.lang.Issue

class CrossTaskGroovyJavaJointIncrementalCompilationIntegrationTest extends AbstractCrossTaskIncrementalCompilationSupport {
    CompiledLanguage language = CompiledLanguage.GROOVY
    boolean useJar = false

    def setup() {
        configureGroovyIncrementalCompilation()
    }

    @Issue("https://github.com/gradle/gradle/issues/22531")
    def 'incremental compilation does not fail when Java class in api is changed and affected Java class in impl is referenced through a property of a Groovy class'() {
        given:
        // A is a private dependency of B and B is referenced in E.isCacheEnabled through inheritance
        sourceWithFileSuffixForProject("java", "api", "class A { void m1() {}; }")
        sourceWithFileSuffixForProject("java", "impl", "class B { void m1() { new A().m1(); }; }")
        sourceWithFileSuffixForProject("java", "impl", "class C extends B {}")
        sourceWithFileSuffixForProject("java", "impl", "class D extends C { static boolean getCache() { return true; } }")
        sourceWithFileSuffixForProject("groovy", "impl", "class E { boolean isCacheEnabled = D.cache }")
        run ":impl:compileGroovy"

        when:
        sourceWithFileSuffixForProject("java", "api", "class A { void m1() {}; void m2() {}; }")
        sourceWithFileSuffixForProject("groovy", "impl", "class E { boolean isCacheEnabled = D.cache; int a = 0; }")
        impl.snapshot()

        then:
        run ":impl:compileGroovy"
        impl.recompiledClasses("B", "E")
    }

    def 'incremental compilation does not fail when Java class in api is changed and we only compile Groovy extension'() {
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
        sourceWithFileSuffixForProject("java", "api", "class A { void m1() {}; }")
        sourceWithFileSuffixForProject("java", "impl", "class B { void m1() { new A().m1(); }; }")
        sourceWithFileSuffixForProject("java", "impl", "class C extends B {}")
        sourceWithFileSuffixForProject("java", "impl", "class D extends C { static boolean getCache() { return true; } }")
        sourceWithFileSuffixForProject("groovy", "impl", "class E { boolean isCacheEnabled = D.cache }")
        run ":impl:compileGroovy"

        when:
        sourceWithFileSuffixForProject("java", "api", "class A { void m1() {}; void m2() {}; }")
        sourceWithFileSuffixForProject("groovy", "impl", "class E { boolean isCacheEnabled = D.cache; int a = 0; }")
        impl.snapshot()

        then:
        run ":impl:compileGroovy"
        impl.recompiledClasses("E")
    }

    def 'incremental compilation after a failure works on api dependency change'() {
        given:
        sourceWithFileSuffixForProject("java", "api", "class A { void m1() {}; }")
        sourceWithFileSuffixForProject("java", "impl", "class B { void m1() { new A().m1(); }; }")
        sourceWithFileSuffixForProject("java", "impl", "class C extends B {}")
        sourceWithFileSuffixForProject("java", "impl", "class D extends C { static boolean getCache() { return true; } }")
        sourceWithFileSuffixForProject("groovy", "impl", "class E { boolean isCacheEnabled = D.cache }")
        run ":impl:compileGroovy"

        when:
        sourceWithFileSuffixForProject("java", "api", "class A { void m1() {}; void m2() {}; }")
        sourceWithFileSuffixForProject("groovy", "impl", "class E { garbage }")

        then:
        runAndFail ":impl:compileGroovy", "--info"

        when:
        sourceWithFileSuffixForProject("java", "api", "class A { void m1() {}; void m2() {}; }")
        sourceWithFileSuffixForProject("groovy", "impl", "class E { boolean isCacheEnabled = D.cache }")
        impl.snapshot()

        then:
        run ":impl:compileGroovy"
        impl.recompiledClasses("B")
    }
}
