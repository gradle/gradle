/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.CompilationOutputsFixture
import spock.lang.Unroll

abstract class AbstractCrossTaskIncrementalJavaCompilationIntegrationTest extends AbstractIntegrationSpec {

    CompilationOutputsFixture impl

    def setup() {
        executer.requireOwnGradleUserHomeDir()
        impl = new CompilationOutputsFixture(file("impl/build/classes"))

        buildFile << """
            subprojects {
                apply plugin: 'java'
                tasks.withType(JavaCompile) {
                    options.incremental = true
                    options.fork = true
                }
                repositories { mavenCentral() }
            }
            $projectDependencyBlock
        """
        settingsFile << "include 'api', 'impl'"
    }

    protected abstract String getProjectDependencyBlock()

    private File java(Map projectToClassBodies) {
        File out
        projectToClassBodies.each { project, bodies ->
            bodies.each { body ->
                def className = (body =~ /(?s).*?(?:class|interface) (\w+) .*/)[0][1]
                assert className: "unable to find class name"
                def f = file("$project/src/main/java/${className}.java")
                f.createFile()
                f.text = body
                out = f
            }
        }
        out
    }

    def "detects changed class in an upstream project"() {
        java api: ["class A {}", "class B {}"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run "compileJava" }

        when:
        java api: ["class A { String change; }"]
        run "impl:compileJava"

        then:
        impl.recompiledClasses("ImplA")
    }

    def "detects change to transitive dependency in an upstream project"() {
        java api: ["class A {}", "class B extends A {}"]
        java impl: ["class SomeImpl {}", "class ImplB extends B {}", "class ImplB2 extends ImplB {}"]
        impl.snapshot { run "compileJava" }

        when:
        java api: ["class A { String change; }"]
        run "impl:compileJava"

        then:
        impl.recompiledClasses("ImplB", "ImplB2")
    }

    def "deletion of jar without dependents does not recompile any classes"() {
        java api: ["class A {}"], impl: ["class SomeImpl {}"]
        impl.snapshot { run "compileJava" }

        when:
        buildFile << """
            project(':impl') {
                configurations.compile.dependencies.clear() //so that api jar is no longer on classpath
            }
        """
        run "impl:compileJava"

        then:
        impl.noneRecompiled()
    }

    def "deletion of jar with dependents causes compilation failure"() {
        java api: ["class A {}"], impl: ["class ImplA extends A {}"]
        impl.snapshot { run "compileJava" }

        when:
        buildFile << """
            project(':impl') {
                configurations.compile.dependencies.clear() //so that api jar is no longer on classpath
            }
        """
        fails "impl:compileJava"

        then:
        impl.noneRecompiled()
    }

    def "detects change to dependency and ensures class dependency info refreshed"() {
        java api: ["class A {}", "class B extends A {}"]
        java impl: ["class SomeImpl {}", "class ImplB extends B {}", "class ImplB2 extends ImplB {}"]
        impl.snapshot { run "compileJava" }

        when:
        java api: ["class B { /* remove extends */ }"]
        run "impl:compileJava"

        then:
        impl.recompiledClasses("ImplB", "ImplB2")

        when:
        impl.snapshot()
        java api: ["class A { /* change */ }"]
        run "impl:compileJava"

        then:
        impl.noneRecompiled() //because after earlier change to B, class A is no longer a dependency
    }

    def "detects deleted class in an upstream project and fails compilation"() {
        def b = java(api: ["class A {}", "class B {}"])
        java impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run "compileJava" }

        when:
        assert b.delete()
        fails "impl:compileJava"

        then:
        impl.noneRecompiled()
    }

    def "recompilation not necessary when upstream does not change any of the actual dependencies"() {
        java api: ["class A {}", "class B {}"], impl: ["class ImplA extends A {}"]
        impl.snapshot { run "compileJava" }

        when:
        java api: ["class B { String change; }"]
        run "impl:compileJava"

        then:
        impl.noneRecompiled()
    }

    def "deletion of jar with non-private constant causes rebuild if constant is used"() {
        java api: ["class A { public final static int x = 1; }"], impl: ["class X { int x() { return 1;} }", "class Y {}"]
        impl.snapshot { run "compileJava" }

        when:
        buildFile << """
            project(':impl') {
                configurations.compile.dependencies.clear() //so that api jar is no longer on classpath
            }
        """
        run "impl:compileJava"

        then:
        impl.recompiledClasses("X")
    }

    def "change in an upstream class with non-private constant doesn't cause full rebuild if constant is not used"() {
        java api: ["class A {}", "class B { final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run "compileJava" }

        when:
        java api: ["class B { /* change */ }"]
        run "impl:compileJava"

        then:
        impl.recompiledClasses('ImplB')
    }

    @Unroll
    def "change in an upstream class with non-private constant causes rebuild if same constant is used (#constantType)"() {
        java api: ["class A {}", "class B { final static $constantType x = $constantValue; }"], impl: ["class ImplA extends A { $constantType foo() { return $constantValue; }}", "class ImplB {int foo() { return 2; }}"]
        impl.snapshot { run "compileJava" }

        when:
        java api: ["class B { /* change */ }"]
        run "impl:compileJava"

        then:
        impl.recompiledClasses('ImplA')

        where:
        constantType | constantValue
        'boolean'    | 'false'
        'byte'       | '(byte) 125'
        'short'      | '(short) 666'
        'int'        | '55542'
        'long'       | '5L'
        'float'      | '6f'
        'double'     | '7d'
        'String'     | '"foo"'
        'String'     | '"foo" + "bar"'
    }

    @Unroll
    def "change in an upstream class with non-private constant causes rebuild only if same constant is used and no direct dependency (#constantType)"() {
        java api: ["class A {}", "class B { final static $constantType x = $constantValue; }"], impl: ["class X { $constantType foo() { return $constantValue; }}", "class Y {int foo() { return -2; }}"]
        impl.snapshot { run "compileJava" }

        when:
        java api: ["class B { /* change */ }"]
        run "impl:compileJava"

        then:
        impl.recompiledClasses('X')

        where:
        constantType | constantValue
        'boolean'    | 'false'
        'byte'       | '(byte) 125'
        'short'      | '(short) 666'
        'int'        | '55542'
        'long'       | '5L'
        'float'      | '6f'
        'double'     | '7d'
        'String'     | '"foo"'
        'String'     | '"foo" + "bar"'
    }

    @Unroll
    def "constant value change in an upstream class causes rebuild if previous constant value was used in previous build (#constantType)"() {
        java api: ["class A {}", "class B { final static $constantType x = $constantValue; }"], impl: ["class X { $constantType foo() { return $constantValue; }}", "class Y {int foo() { return -2; }}"]
        impl.snapshot { run "compileJava" }

        when:
        java api: ["class B { final static $constantType x = $newValue; /* change value */ ; void blah() { /* avoid flakiness by changing compiled file length*/ } }"]
        run "impl:compileJava"

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

    def "ignores irrelevant changes to constant values"() {
        java api: ["class A {}", "class B { final static int x = 3; final static int y = -2; }"],
            impl: ["class X { int foo() { return 3; }}", "class Y {int foo() { return -2; }}"]
        impl.snapshot { run "compileJava" }

        when:
        java api: ["class B { final static int x = 3 ; final static int y = -3;  void blah() { /*  change irrelevant to constant value x */ } }"]
        run "impl:compileJava"

        then:
        impl.recompiledClasses('Y')
    }

    def "recompiles in case of conflicting changing constant values"() {
        java api: ["class A { final static int x = 3; }", "class B { final static int x = 3; final static int y = -2; }"],
            impl: ["class X { int foo() { return 3; }}", "class Y {int foo() { return -2; }}"]
        impl.snapshot { run "compileJava" }

        when:
        java api: ["class B { final static int x = 3 ; final static int y = -3;  void blah() { /*  change irrelevant to constant value x */ } }"]
        java api: ["class A { final static int x = 2 ; final static int y = -2;  void blah() { /*  change irrelevant to constant value y */ } }"]
        run "impl:compileJava"

        then:
        impl.recompiledClasses('X', 'Y')
    }

    def "change in an upstream transitive class with non-private constant does not cause full rebuild"() {
        java api: ["class A { final static int x = 1; }", "class B extends A {}"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run "compileJava" }

        when:
        java api: ["class B { /* change */ }"]
        run "impl:compileJava"

        then:
        impl.recompiledClasses('ImplB')
    }

    def "private constant in upstream project does not trigger full rebuild"() {
        java api: ["class A {}", "class B { private final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run "compileJava" }

        when:
        java api: ["class B { /* change */ }"]
        run "impl:compileJava"

        then:
        impl.recompiledClasses('ImplB')
    }

    def "detects changed classes when upstream project was built in isolation"() {
        java api: ["class A {}", "class B {}"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run "compileJava" }

        when:
        java api: ["class A { String change; }"]
        run "api:compileJava"
        run "impl:compileJava"

        then:
        impl.recompiledClasses("ImplA")
    }

    def "detects class changes in subsequent runs ensuring the jar snapshots are refreshed"() {
        java api: ["class A {}", "class B {}"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run "compileJava" }

        when:
        java api: ["class A { String change; }"]
        run "api:compileJava"
        run "impl:compileJava" //different build invocation

        then:
        impl.recompiledClasses("ImplA")

        when:
        impl.snapshot()
        java api: ["class B { String change; }"]
        run "compileJava"

        then:
        impl.recompiledClasses("ImplB")
    }

    def "changes to resources in jar do not incur recompilation"() {
        java impl: ["class A {}"]
        impl.snapshot { run "impl:compileJava" }

        when:
        file("api/src/main/resources/some-resource.txt") << "xxx"
        java api: ["class A { String change; }"]
        run "impl:compileJava"

        then:
        impl.noneRecompiled()
    }

    def "handles multiple compile tasks in the same project"() {
        settingsFile << "\n include 'other'" //add an extra project
        java impl: ["class ImplA extends A {}"], api: ["class A {}"], other: ["class Other {}"]

        //new separate compile task (integTestCompile) depends on class from the extra project
        file("impl/build.gradle") << """
            sourceSets { integTest.java.srcDir 'src/integTest/java' }
            dependencies { integTestCompile project(":other") }
        """
        file("impl/src/integTest/java/SomeIntegTest.java") << "class SomeIntegTest extends Other {}"

        impl.snapshot { run "compileIntegTestJava", "compileJava" }

        when: //when api class is changed
        java api: ["class A { String change; }"]
        run "compileIntegTestJava", "compileJava"

        then: //only impl class is recompiled
        impl.recompiledClasses("ImplA")

        when: //when other class is changed
        impl.snapshot()
        java other: ["class Other { String change; }"]
        run "compileIntegTestJava", "compileJava"

        then: //only integTest class is recompiled
        impl.recompiledClasses("SomeIntegTest")
    }

    def "the order of classpath items is unchanged"() {
        java api: ["class A {}"], impl: ["class B {}"]
        file("impl/build.gradle") << """
            dependencies { compile "org.mockito:mockito-core:1.9.5", "junit:junit:4.12" }
            compileJava.doFirst {
                file("classpath.txt").createNewFile(); file("classpath.txt").text = classpath.files*.name.collect { it.replace('main','api.jar') }.join(', ')
            }
        """

        when:
        run("impl:compileJava") //initial run
        then:
        file("impl/classpath.txt").text == "api.jar, mockito-core-1.9.5.jar, junit-4.12.jar, objenesis-1.0.jar, hamcrest-core-1.3.jar"

        when: //project dependency changes
        java api: ["class A { String change; }"]
        run("impl:compileJava")

        then:
        file("impl/classpath.txt").text == "api.jar, mockito-core-1.9.5.jar, junit-4.12.jar, objenesis-1.0.jar, hamcrest-core-1.3.jar"

        when: //transitive dependency is excluded
        file("impl/build.gradle") << "configurations.compile.exclude module: 'hamcrest-core' \n"
        run("impl:compileJava")

        then:
        file("impl/classpath.txt").text == "api.jar, mockito-core-1.9.5.jar, junit-4.12.jar, objenesis-1.0.jar"

        when: //direct dependency is excluded
        file("impl/build.gradle") << "configurations.compile.exclude module: 'junit' \n"
        run("impl:compileJava")

        then:
        file("impl/classpath.txt").text == "api.jar, mockito-core-1.9.5.jar, objenesis-1.0.jar"

        when: //new dependency is added
        file("impl/build.gradle") << "dependencies { compile 'org.testng:testng:6.8.7' } \n"
        run("impl:compileJava")

        then:
        file("impl/classpath.txt").text == "api.jar, mockito-core-1.9.5.jar, testng-6.8.7.jar, objenesis-1.0.jar, bsh-2.0b4.jar, jcommander-1.27.jar, snakeyaml-1.12.jar"
    }

    def "handles duplicate class found in jar"() {
        java api: ["class A extends B {}", "class B {}"], impl: ["class A extends C {}", "class C {}"]

        impl.snapshot { run("impl:compileJava") }

        when:
        //change to source dependency duplicate triggers recompilation
        java impl: ["class C { String change; }"]
        run("impl:compileJava")

        then:
        impl.recompiledClasses("A", "C")

        when:
        //change to jar dependency duplicate is ignored because source duplicate wins
        impl.snapshot()
        java api: ["class B { String change; } "]
        run("impl:compileJava")

        then:
        impl.noneRecompiled()
    }

    def "new jar with duplicate class appearing earlier on classpath must trigger compilation"() {
        java impl: ["class A extends org.junit.Assert {}"]
        file("impl/build.gradle") << """
            configurations.compile.dependencies.clear()
            dependencies { compile 'junit:junit:4.12' }
        """

        impl.snapshot { run("impl:compileJava") }

        when:
        //add new jar with duplicate class that will be earlier on the classpath (project dependencies are earlier on classpath)
        file("api/src/main/java/org/junit/Assert.java") << "package org.junit; public class Assert {}"
        file("impl/build.gradle") << "dependencies { compile project(':api') }"
        run("impl:compileJava")

        then:
        impl.recompiledClasses("A")
    }

    def "new jar without duplicate class does not trigger compilation"() {
        java impl: ["class A {}"]
        impl.snapshot { run("impl:compileJava") }

        when:
        file("impl/build.gradle") << "dependencies { compile 'junit:junit:4.12' }"
        run("impl:compileJava")

        then:
        impl.noneRecompiled()
    }

    def "changed jar with duplicate class appearing earlier on classpath must trigger compilation"() {
        java impl: ["class A extends org.junit.Assert {}"]
        file("impl/build.gradle") << """
            dependencies { compile 'junit:junit:4.12' }
        """

        impl.snapshot { run("impl:compileJava") }

        when:
        //update existing jar with duplicate class that will be earlier on the classpath (project dependencies are earlier on classpath)
        file("api/src/main/java/org/junit/Assert.java") << "package org.junit; public class Assert {}"
        run("impl:compileJava")

        then:
        impl.recompiledClasses("A")
    }

    def "deletion of a jar with duplicate class causes recompilation"() {
        file("api/src/main/java/org/junit/Assert.java") << "package org.junit; public class Assert {}"
        java impl: ["class A extends org.junit.Assert {}"]

        file("impl/build.gradle") << "dependencies { compile 'junit:junit:4.12' }"

        impl.snapshot { run("impl:compileJava") }

        when:
        file("impl/build.gradle").text = """
            configurations.compile.dependencies.clear()  //kill project dependency
            dependencies { compile 'junit:junit:4.11' }  //leave only junit
        """
        run("impl:compileJava")

        then:
        impl.recompiledClasses("A")
    }

    def "recognizes change of constant value in annotation, even if we know it's a bad practice"() {
        java api: [
            "class A { public static final int CST = 0; }",
            """import java.lang.annotation.Retention;
               import java.lang.annotation.RetentionPolicy;
               @Retention(RetentionPolicy.RUNTIME)
               @interface B { int value(); }"""
        ], impl: [
            // cases where it's relevant, ABI-wise
            "@B(A.CST) class OnClass {}",
            "class OnMethod { @B(A.CST) void foo() {} }",
            "class OnParameter { void foo(@B(A.CST) int x) {} }"
        ]

        impl.snapshot { run("impl:compileJava") }

        when:
        //change to source dependency duplicate triggers recompilation
        java api: ["class A { public static final int CST = 1234; }"]
        run("impl:compileJava")

        then:
        impl.recompiledClasses("OnClass", "OnMethod", "OnParameter")

    }
}
