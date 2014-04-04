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
import org.gradle.integtests.fixtures.OutputsTrackingFixture

class IncrementalJavaCompilationIntegrationTest extends AbstractIntegrationSpec {

    OutputsTrackingFixture outputs

    def setup() {
        outputs = new OutputsTrackingFixture(file("build/classes/main"))

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

    def "compiles only a single class that was changed"() {
        outputs.snapshot { run "compileJava" }

        file("src/main/java/org/AnotherPersonImpl.java").text = """package org;
        public class AnotherPersonImpl implements Person {
            public String getName() { return "Hans"; }
        }"""

        when: run "compileJava"

        then: outputs.changedClasses 'AnotherPersonImpl'
    }

    def "refreshes the class dependencies with each run"() {
        outputs.snapshot { run "compileJava" }

        file("src/main/java/org/AnotherPersonImpl.java").text = """package org;
        public class AnotherPersonImpl {}""" //remove the dependency to the interface

        when: run "compileJava"

        then: outputs.changedClasses 'AnotherPersonImpl'

        when:
        file("src/main/java/org/Person.java").text = """package org;
        public interface Person {
            String getName();
            String toString();
        }"""
        outputs.snapshot()
        run "compileJava"

        then: outputs.changedClasses 'PersonImpl', 'Person'
    }

    def "detects class transitive dependents"() {
        outputs.snapshot { run "compileJava" }

        when:
        file("src/main/java/org/Person.java").text = """package org;
        public interface Person {
            String toString();
        }"""

        run "compileJava"

        then:
        outputs.changedClasses 'AnotherPersonImpl', 'PersonImpl', 'Person'
    }

    def "is sensitive to deletion and change"() {
        outputs.snapshot { run "compileJava" }

        assert file("src/main/java/org/PersonImpl.java").delete()

        file("src/main/java/org/AnotherPersonImpl.java").text = """package org;
        public class AnotherPersonImpl implements Person {
            public String getName() { return "Hans"; }
        }"""

        when: run "compileJava"

        then:
        !file("build/classes/main/org/PersonImpl.class").exists()
        outputs.changedClasses 'AnotherPersonImpl'
    }

    def "is sensitive to inlined constants"() {
        outputs.snapshot { run "compileJava" }

        file("src/main/java/org/WithConst.java").text = """package org;
        public class WithConst {
            static final int X = 20;
        }"""

        when: run "compileJava"

        then:
        outputs.changedClasses 'WithConst', 'AnotherPersonImpl', 'PersonImpl', 'Person'
    }

    def "is sensitive to source annotations"() {
        file("src/main/java/org/ClassAnnotation.java").text = """package org; import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME) public @interface ClassAnnotation {}
        """
        file("src/main/java/org/SourceAnnotation.java").text = """package org; import java.lang.annotation.*;
            @Retention(RetentionPolicy.SOURCE) public @interface SourceAnnotation {}
        """
        file("src/main/java/org/UsesClassAnnotation.java").text = """package org;
            @ClassAnnotation public class UsesClassAnnotation {}
        """
        file("src/main/java/org/UsesSourceAnnotation.java").text = """package org;
            @SourceAnnotation public class UsesSourceAnnotation {}
        """
        outputs.snapshot { run "compileJava" }

        file("src/main/java/org/ClassAnnotation.java").text = """package org; import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME) public @interface ClassAnnotation {
                String foo() default "foo";
            }"""

        when: run "compileJava"

        then:
        outputs.changedClasses 'WithConst', 'UsesSourceAnnotation', 'ClassAnnotation', 'UsesClassAnnotation', 'SourceAnnotation', 'AnotherPersonImpl', 'PersonImpl', 'Person'
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
        outputs.changedClasses 'ConsumesB'
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
        outputs.changedClasses 'WithConst', 'AnotherPersonImpl', 'B', 'C', 'PersonImpl', 'Person'
    }

    def "removal of class causes deletion of inner classes"() {
        file("src/main/java/org/B.java") << """package org;
            public class B {
                public static class InnerB {}
            }
        """

        when: run "compileJava"

        then:
        def classes = [file('build/classes/main/org/B.class'), file('build/classes/main/org/B$InnerB.class')]
        classes.each { assert it.exists() }

        when:
        assert file("src/main/java/org/B.java").delete()
        run "compileJava"

        then:
        classes.each { assert !it.exists() }
    }
}