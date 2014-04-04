/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.java.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.CompilationOutputsFixture

class IncrementalJavaCompilationIntegrationTest extends AbstractIntegrationSpec {

    CompilationOutputsFixture outputs

    def setup() {
        outputs = new CompilationOutputsFixture(file("build/classes/main"))

        buildFile << """
            allprojects {
                apply plugin: 'java'
                compileJava.options.incremental = true
            }
        """

        file("src/main/java/org/Person.java") << """package org;
        public interface Person {
            String getName();
        }"""
        file("src/main/java/org/PersonImpl.java") << """package org;
        public class PersonImpl implements Person {
            public String getName() { return "Szczepan"; }
        }"""
        file("src/main/java/org/AnotherPersonImpl.java") << """package org;
        public class AnotherPersonImpl extends PersonImpl {
            public String getName() { return "Szczepan Faber " + WithConst.X; }
        }"""
        file("src/main/java/org/WithConst.java") << """package org;
        public class WithConst {
            final static int X = 100;
        }"""
    }

    def "understands inter-project dependencies"() {
        settingsFile << "include 'api'"
        buildFile << "dependencies { compile project(':api') }"

        file("api/src/main/java/org/A.java") << """package org; public class A {}"""
        file("api/src/main/java/org/B.java") << """package org; public class B {}"""

        file("src/main/java/org/ConsumesA.java") << """package org;
            public class ConsumesA { A a = new A(); }
        """
        file("src/main/java/org/ConsumesB.java") << """package org;
            public class ConsumesB { B b = new B(); }
        """

        outputs.snapshot { run "compileJava" }

        file("api/src/main/java/org/B.java").text = """package org; public class B {
            public B() { System.out.println("foo"); }
        }
        """

        when: run "compileJava"

        then:
        outputs.recompiledClasses 'ConsumesB'
    }

    def "understands inter-project dependency that forces full rebuild"() {
        settingsFile << "include 'api'"
        buildFile << "dependencies { compile project(':api') }"

        file("api/src/main/java/org/A.java") << """package org; public class A {
            public static final String x = "foo";
        }"""

        file("src/main/java/org/B.java") << """package org; public class B {  }"""
        file("src/main/java/org/C.java") << """package org; public class C {  }"""

        outputs.snapshot { run "compileJava" }

        file("api/src/main/java/org/A.java").text = "package org; public class A {}"

        when: run "compileJava"

        then:
        outputs.recompiledClasses 'WithConst', 'AnotherPersonImpl', 'B', 'C', 'PersonImpl', 'Person'
    }
}