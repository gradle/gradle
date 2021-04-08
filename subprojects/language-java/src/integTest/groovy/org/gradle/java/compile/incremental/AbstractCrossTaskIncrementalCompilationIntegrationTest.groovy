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


import org.gradle.integtests.fixtures.CompilationOutputsFixture
import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Issue
import spock.lang.Unroll

abstract class AbstractCrossTaskIncrementalCompilationIntegrationTest extends AbstractJavaGroovyIncrementalCompilationSupport {
    CompilationOutputsFixture impl

    def setup() {
        impl = new CompilationOutputsFixture(file("impl/build/classes"))
        buildFile << """
            subprojects {
                apply plugin: '${language.name}'
                apply plugin: 'java-library'
                ${mavenCentralRepository()}
                configurations.compileClasspath.attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements, LibraryElements.${useJar ? 'JAR' : 'CLASSES'}))
            }
            $projectDependencyBlock
        """
        settingsFile << "include 'api', 'impl'\n"

        if (language == CompiledLanguage.GROOVY) {
            configureGroovyIncrementalCompilation('subprojects')
        }
    }

    protected String getProjectDependencyBlock() {
        '''
            project(':impl') {
                dependencies { api project(':api') }
            }
        '''
    }

    protected void addDependency(String from, String to) {
        buildFile << """
            project(':$from') {
                dependencies { api project(':$to') }
            }
        """
    }

    protected abstract boolean isUseJar()

    private void clearImplProjectDependencies() {
        buildFile << """
            project(':impl') {
                configurations.api.dependencies.clear() //so that api jar is no longer on classpath
            }
        """
        configureGroovyIncrementalCompilation('subprojects')
    }

    File source(Map projectToClassBodies) {
        File out
        projectToClassBodies.each { project, bodies ->
            bodies.each { body ->
                def className = (body =~ /(?s).*?(?:class|interface) (\w+) .*/)[0][1]
                assert className: "unable to find class name"
                def f = file("$project/src/main/${language.name}/${className}.${language.name}")
                f.createFile()
                f.text = body
                out = f
            }
        }
        out
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "detects changed class in an upstream project"() {
        source api: ["class A {}", "class B {}"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { String change; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses("ImplA")
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "detects change to transitive superclass in an upstream project"() {
        settingsFile << """
            include 'app'
        """
        addDependency("app", "impl")
        def app = new CompilationOutputsFixture(file("app/build/classes"))
        source api: ["class A {}"]
        source impl: ["class B extends A {}"]
        source app: ["class Unrelated {}", "class C extends B {}", "class D extends C {}"]
        app.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { String change; }"]
        run "app:${language.compileTaskName}"

        then:
        app.recompiledClasses("C", "D")
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "detects change to transitive dependency in an upstream project"() {
        settingsFile << """
            include 'app'
        """
        addDependency("app", "impl")
        def app = new CompilationOutputsFixture(file("app/build/classes"))
        source api: ["class A {}"]
        source impl: ["class B { public A a;}"]
        source app: ["class Unrelated {}", "class C { public B b; }"]
        app.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { String change; }"]
        run "app:${language.compileTaskName}"

        then:
        app.recompiledClasses("C")
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "distinguishes between api and implementation changes"() {
        settingsFile << """
            include 'app'
        """
        addDependency("app", "impl")
        def app = new CompilationOutputsFixture(file("app/build/classes"))
        source api: ["class A {}"]
        source impl: ["class B { public A a;}", "class C { private B b;}"]
        source app: ["class D { public B b; }", "class E { public C c; }"]
        app.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { String change; }"]
        run "app:${language.compileTaskName}"

        then:
        app.recompiledClasses("D")
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "detects deletions of transitive dependency in an upstream project"() {
        settingsFile << """
            include 'app'
        """
        addDependency("app", "impl")
        def app = new CompilationOutputsFixture(file("app/build/classes"))
        source api: ["class A {}"]
        source impl: ["class B { public A a;}"]
        source app: ["class Unrelated {}", "class C { public B b; }"]
        app.snapshot {
            impl.snapshot {
                run language.compileTaskName
            }
        }

        when:
        file("api/src/main/${language.name}/A.${language.name}").delete()
        run "app:${language.compileTaskName}", "-x", "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
        app.recompiledClasses("C")
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "deletion of jar without dependents does not recompile any classes"() {
        source api: ["class A {}"], impl: ["class SomeImpl {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        clearImplProjectDependencies()

        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "deletion of jar with dependents causes compilation failure"() {
        source api: ["class A {}"], impl: ["class ImplA extends A {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        clearImplProjectDependencies()
        fails "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "detects change to dependency and ensures class dependency info refreshed"() {
        source api: ["class A {}", "class B extends A {}"]
        source impl: ["class SomeImpl {}", "class ImplB extends B {}", "class ImplB2 extends ImplB {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* remove extends */ }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses("ImplB", "ImplB2")

        when:
        impl.snapshot()
        source api: ["class A { /* change */ }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled() //because after earlier change to B, class A is no longer a dependency
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "detects deleted class in an upstream project and fails compilation"() {
        def b = source(api: ["class A {}", "class B {}"])
        source impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        assert b.delete()
        fails "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "recompilation not necessary when upstream does not change any of the actual dependencies"() {
        source api: ["class A {}", "class B {}"], impl: ["class ImplA extends A {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { String change; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }


    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )

    def "change in an upstream transitive class with non-private constant does not cause full rebuild"() {
        source api: ["class A { final static int x = 1; }", "class B extends A {}"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* change */ }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('ImplB')
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )

    def "private constant in upstream project does not trigger full rebuild"() {
        source api: ["class A {}", "class B { private final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* change */ }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "addition of unused class in upstream project does not rebuild"() {
        source api: ["class A {}", "class B { private final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class C { }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "removal of unused class in upstream project does not rebuild"() {
        source api: ["class A {}", "class B { private final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        def c = source api: ["class C { }"]
        impl.snapshot { run language.compileTaskName }

        when:
        c.delete()
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "doesn't recompile if external dependency has ABI incompatible change but not on class we use"() {
        given:
        buildFile << """
            project(':impl') {
                ${mavenCentralRepository()}
                dependencies { implementation 'org.apache.commons:commons-lang3:3.3' }
            }
        """
        source api: ["class A {}", "class B { }"], impl: ["class ImplA extends A {}", """import org.apache.commons.lang3.StringUtils;

            class ImplB extends B {
               public static String HELLO = StringUtils.capitalize("hello");
            }"""]
        impl.snapshot { run language.compileTaskName }

        when:
        buildFile.text = buildFile.text.replace('3.3', '3.3.1')
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "detects changed classes when upstream project was built in isolation"() {
        source api: ["class A {}", "class B {}"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { String change; }"]
        run "api:${language.compileTaskName}"
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses("ImplA")
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "detects class changes in subsequent runs ensuring the jar snapshots are refreshed"() {
        source api: ["class A {}", "class B {}"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { String change; }"]
        run "api:${language.compileTaskName}"
        run "impl:${language.compileTaskName}" //different build invocation

        then:
        impl.recompiledClasses("ImplA")

        when:
        impl.snapshot()
        source api: ["class B { String change; }"]
        run language.compileTaskName

        then:
        impl.recompiledClasses("ImplB")
    }

    def "changes to resources in jar do not incur recompilation"() {
        source impl: ["class A {}"]
        impl.snapshot { run "impl:${language.compileTaskName}" }

        when:
        file("api/src/main/resources/some-resource.txt") << "xxx"
        source api: ["class A { String change; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "handles multiple compile tasks in the same project"() {
        settingsFile << "\n include 'other'" //add an extra project
        source impl: ["class ImplA extends A {}"], api: ["class A {}"], other: ["class Other {}"]

        //new separate compile task (compileIntegTest${language.captalizedName}) depends on class from the extra project
        file("impl/build.gradle") << """
            sourceSets { integTest.${language.name}.srcDir "src/integTest/${language.name}" }
            dependencies { integTestImplementation project(":other") }
            dependencies { integTestImplementation localGroovy() }
        """
        file("impl/src/integTest/${language.name}/SomeIntegTest.${language.name}") << "class SomeIntegTest extends Other {}"

        impl.snapshot { run "compileIntegTest${language.capitalizedName}", language.compileTaskName }

        when: //when api class is changed
        source api: ["class A { String change; }"]
        run "compileIntegTest${language.capitalizedName}", language.compileTaskName

        then: //only impl class is recompiled
        impl.recompiledClasses("ImplA")

        when: //when other class is changed
        impl.snapshot()
        source other: ["class Other { String change; }"]
        run "compileIntegTest${language.capitalizedName}", language.compileTaskName

        then: //only integTest class is recompiled
        impl.recompiledClasses("SomeIntegTest")
    }

    String wrapClassDirs(String classpath) {
        if (language == CompiledLanguage.GROOVY) {
            if (useJar) {
                return "api.jar, ${classpath}, main"
            } else {
                // api/build/classes/java/main and api/build/classes/groovy/main
                // impl/build/classes/java/main
                return "main, main, ${classpath}, main"
            }
        } else {
            if (useJar) {
                return "api.jar, $classpath"
            } else {
                // api/build/classes/java/main
                return "main, ${classpath}"
            }
        }
    }

    def "the order of classpath items is unchanged"() {
        source api: ["class A {}"], impl: ["class B {}"]
        file("impl/build.gradle") << """
            dependencies { implementation "org.mockito:mockito-core:1.9.5", "junit:junit:4.13" }
            tasks.named('${language.compileTaskName}') {
                def classpathTxt = file("classpath.txt")
                doFirst {
                    classpathTxt.createNewFile();
                    classpathTxt.text = classpath.files*.name.findAll { !it.startsWith('groovy') && !it.startsWith('javaparser-core') }.join(', ')
                }
            }
        """

        when:
        run("impl:${language.compileTaskName}") //initial run
        then:
        file("impl/classpath.txt").text == wrapClassDirs("mockito-core-1.9.5.jar, junit-4.13.jar, hamcrest-core-1.3.jar, objenesis-1.0.jar")

        when: //project dependency changes
        source api: ["class A { String change; }"]
        run("impl:${language.compileTaskName}")

        then:
        file("impl/classpath.txt").text == wrapClassDirs("mockito-core-1.9.5.jar, junit-4.13.jar, hamcrest-core-1.3.jar, objenesis-1.0.jar")

        when: //transitive dependency is excluded
        file("impl/build.gradle") << "configurations.implementation.exclude module: 'hamcrest-core' \n"
        run("impl:${language.compileTaskName}")

        then:
        file("impl/classpath.txt").text == wrapClassDirs("mockito-core-1.9.5.jar, junit-4.13.jar, objenesis-1.0.jar")

        when: //direct dependency is excluded
        file("impl/build.gradle") << "configurations.implementation.exclude module: 'junit' \n"
        run("impl:${language.compileTaskName}")

        then:
        file("impl/classpath.txt").text == wrapClassDirs("mockito-core-1.9.5.jar, objenesis-1.0.jar")

        when: //new dependency is added
        file("impl/build.gradle") << "dependencies { implementation 'org.testng:testng:6.8.7' } \n"
        run("impl:${language.compileTaskName}")

        then:
        file("impl/classpath.txt").text == wrapClassDirs("mockito-core-1.9.5.jar, testng-6.8.7.jar, objenesis-1.0.jar, bsh-2.0b4.jar, jcommander-1.27.jar, snakeyaml-1.12.jar")
    }

    def "handles duplicate class found in jar"() {
        source api: ["class A extends B {}", "class B {}"], impl: ["class A extends C {}", "class C {}"]

        impl.snapshot { run("impl:${language.compileTaskName}") }

        when:
        //change to source dependency duplicate triggers recompilation
        source impl: ["class C { String change; }"]
        run("impl:${language.compileTaskName}")

        then:
        impl.recompiledClasses("A", "C")

        when:
        //change to jar dependency duplicate is ignored because source duplicate wins
        impl.snapshot()
        source api: ["class B { String change; } "]
        run("impl:${language.compileTaskName}")

        then:
        impl.noneRecompiled()
    }

    def "new jar with duplicate class appearing earlier on classpath must trigger compilation"() {
        source impl: ["class A extends org.junit.Assert {}"]

        file("impl/build.gradle") << """
            configurations.implementation.dependencies.clear()
            dependencies {
                implementation 'junit:junit:4.13'
                implementation localGroovy()
            }
        """

        impl.snapshot { run("impl:${language.compileTaskName}") }

        when:
        //add new jar with duplicate class that will be earlier on the classpath (project dependencies are earlier on classpath)
        file("api/src/main/${language.name}/org/junit/Assert.${language.name}") << "package org.junit; public class Assert {}"
        file("impl/build.gradle") << "dependencies { implementation project(':api') }"
        run("impl:${language.compileTaskName}")

        then:
        impl.recompiledClasses("A")
    }

    def "new jar without duplicate class does not trigger compilation"() {
        source impl: ["class A {}"]
        impl.snapshot { run("impl:${language.compileTaskName}") }
        when:
        file("impl/build.gradle") << "dependencies { implementation 'junit:junit:4.13' }"
        run("impl:${language.compileTaskName}")
        then:
        impl.noneRecompiled()
    }

    def "changed jar with duplicate class appearing earlier on classpath must trigger compilation"() {
        source impl: ["class A extends org.junit.Assert {}"]
        file("impl/build.gradle") << """
            dependencies { implementation 'junit:junit:4.13' }
        """

        impl.snapshot { run("impl:${language.compileTaskName}") }

        when:
        //update existing jar with duplicate class that will be earlier on the classpath (project dependencies are earlier on classpath)
        file("api/src/main/${language.name}/org/junit/Assert.${language.name}") << "package org.junit; public class Assert {}"
        run("impl:${language.compileTaskName}")

        then:
        impl.recompiledClasses("A")
    }

    def "deletion of a jar with duplicate class causes recompilation"() {
        file("api/src/main/${language.name}/org/junit/Assert.${language.name}") << "package org.junit; public class Assert {}"
        source impl: ["class A extends org.junit.Assert {}"]

        file("impl/build.gradle") << "dependencies { implementation 'junit:junit:4.13' }"

        impl.snapshot { run("impl:${language.compileTaskName}") }

        when:
        file("impl/build.gradle").text = """
            configurations.implementation.dependencies.clear()  //kill project dependency
            dependencies {
                implementation 'junit:junit:4.11'
                implementation localGroovy()
            }  //leave only junit
        """
        run("impl:${language.compileTaskName}")

        then:
        impl.recompiledClasses("A")
    }

    @Unroll
    def "recompiles outermost class when #visibility inner class contains constant reference"() {
        source api: [
            "class A { public static final int EVIL = 666; }",
        ], impl: [
            "class B {}",
            "class C { $visibility static class Inner { int foo() { return A.EVIL; } } }",
            "class D { $visibility class Inner { int foo() { return A.EVIL; } } }",
            "class E { void foo() { Runnable r = new Runnable() { public void run() { int x = A.EVIL; } }; } }",
            """class F {
                    int foo() { return A.EVIL; }
                    $visibility static class Inner { }
                }""",
        ]

        impl.snapshot { run("impl:${language.compileTaskName}") }

        when:
        source api: ["class A { public static final int EVIL = 0; void blah() { /* avoid flakiness by changing compiled file length*/ } }"]
        run("impl:${language.compileTaskName}")

        then:
        impl.recompiledClasses('B', 'C', 'C$Inner', 'D', 'D$Inner', 'E', 'E$1', 'F', 'F$Inner')

        where:
        visibility << ['public', 'private', '']

    }

    def "recognizes change of constant value in annotation"() {
        source api: [
            "class A { public static final int CST = 0; }",
            """import java.lang.annotation.Retention;
               import java.lang.annotation.RetentionPolicy;
               @Retention(RetentionPolicy.RUNTIME)
               @interface B { int value(); }"""
        ], impl: [
            // cases where it's relevant, ABI-wise
            "@B(A.CST) class OnClass {}",
            "class OnMethod { @B(A.CST) void foo() {} }",
            "class OnField { @B(A.CST) String foo; }",
            "class OnParameter { void foo(@B(A.CST) int x) {} }"
        ]

        impl.snapshot { run("impl:${language.compileTaskName}") }

        when:
        source api: ["class A { public static final int CST = 1234; void blah() { /* avoid flakiness by changing compiled file length*/ } }"]
        run("impl:${language.compileTaskName}")

        then:
        impl.recompiledClasses("OnClass", "OnMethod", "OnParameter", "OnField")
    }

    @Unroll
    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "change to class referenced by an annotation recompiles annotated types"() {
        source api: [
            """
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.CLASS)
                public @interface B {
                    Class<?> value();
                }
            """,
            "class A {}"
        ], impl: [
            "@B(A.class) class OnClass {}",
            "class OnMethod { @B(A.class) void foo() {} }",
            "class OnField { @B(A.class) String foo; }",
            "class OnParameter { void foo(@B(A.class) int x) {} }"
        ]

        impl.snapshot { run language.compileTaskName }

        when:
        source api: [
            """
                class A { public void foo() {} }
            """
        ]
        run language.compileTaskName

        then:
        impl.recompiledClasses("OnClass", "OnMethod", "OnParameter", "OnField")
    }

    @Unroll
    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "change to class referenced by an array value in an annotation recompiles annotated types"() {
        source api: [
            """
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.CLASS)
                public @interface B {
                    Class<?>[] value();
                }
            """,
            "class A {}"
        ], impl: [
            "@B(A.class) class OnClass {}",
            "class OnMethod { @B(A.class) void foo() {} }",
            "class OnField { @B(A.class) String foo; }",
            "class OnParameter { void foo(@B(A.class) int x) {} }"
        ]

        impl.snapshot { run language.compileTaskName }

        when:
        source api: [
            """
                class A { public void foo() {} }
            """
        ]
        run language.compileTaskName

        then:
        impl.recompiledClasses("OnClass", "OnMethod", "OnParameter", "OnField")
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )

    def "recompiles dependent class in case a constant is switched"() {
        source api: ["class A { public static final int FOO = 10; public static final int BAR = 20; }"],
            impl: ['class B { void foo() { int x = 10; } }', 'class C { void foo() { int x = 20; } }']
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ['class A { public static final int FOO = 20; public static final int BAR = 10; }']
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses 'B', 'C'
    }

    @Issue("gradle/gradle#1474")
    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )

    def "recompiles dependent class in case a constant is computed from another constant"() {
        source api: ["class A { public static final int FOO = 10; }"], impl: ['class B { public static final int BAR = 2 + A.FOO; } ']
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ['class A { public static final int FOO = 100; }']
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses 'B'

    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )

    def "detects that changed class still has the same constants so no recompile is necessary"() {
        source api: ["class A { public static final int FOO = 123;}"],
            impl: ["class B { void foo() { int x = 123; }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { public static final int FOO = 123; void addSomeRandomMethod() {} }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "does not recompile on non-abi change across projects"() {
        source api: ["class A { }"],
            impl: ["class B { A a; }", "class C { B b; }"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { \n}"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    // This test checks the current behavior, not necessarily the desired one.
    // If all classes are compiled by the same compile task, we do not know if a
    // change is an abi change or not. Hence, an abi change is always assumed.
    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    def "does recompile on non-abi changes inside one project"() {
        source impl: ["class A { }", "class B { A a; }", "class C { B b; }"]
        impl.snapshot { run language.compileTaskName }

        when:
        source impl: ["class A { \n}"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses 'A', 'B', 'C'
    }

    def "recompiles downstream dependents of classes whose package-info changed"() {
        given:
        file("api/src/main/${language.name}/annotations/Anno.${language.name}").text = """
            package annotations;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.PACKAGE)
            public @interface Anno {}
        """
        def packageFile = file("api/src/main/${language.name}/foo/package-info.${language.name}")
        packageFile.text = """@Deprecated package foo;"""
        file("api/src/main/${language.name}/foo/A.${language.name}").text = "package foo; public class A {}"
        file("api/src/main/${language.name}/bar/B.${language.name}").text = "package bar; public class B {}"
        file("impl/src/main/${language.name}/baz/C.${language.name}").text = "package baz; import foo.A; class C extends A {}"
        file("impl/src/main/${language.name}/baz/D.${language.name}").text = "package baz; import bar.B; class D extends B {}"

        impl.snapshot { succeeds "impl:${language.compileTaskName}" }

        when:
        packageFile.text = """@Deprecated @annotations.Anno package foo;"""
        succeeds "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses("C")
    }

    def "recompiles downstream dependents of classes whose package-info was added"() {
        given:
        def packageFile = file("api/src/main/${language.name}/foo/package-info.${language.name}")
        file("api/src/main/${language.name}/foo/A.${language.name}").text = "package foo; public class A {}"
        file("api/src/main/${language.name}/bar/B.${language.name}").text = "package bar; public class B {}"
        file("impl/src/main/${language.name}/baz/C.${language.name}").text = "package baz; import foo.A; class C extends A {}"
        file("impl/src/main/${language.name}/baz/D.${language.name}").text = "package baz; import bar.B; class D extends B {}"

        impl.snapshot { succeeds "impl:${language.compileTaskName}" }

        when:
        packageFile.text = """@Deprecated package foo;"""
        succeeds "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses("C")
    }

    def "recompiles downstream dependents of classes whose package-info was removed"() {
        given:
        def packageFile = file("api/src/main/${language.name}/foo/package-info.${language.name}")
        packageFile.text = """@Deprecated package foo;"""
        file("api/src/main/${language.name}/foo/A.${language.name}").text = "package foo; public class A {}"
        file("api/src/main/${language.name}/bar/B.${language.name}").text = "package bar; public class B {}"
        file("impl/src/main/${language.name}/baz/C.${language.name}").text = "package baz; import foo.A; class C extends A {}"
        file("impl/src/main/${language.name}/baz/D.${language.name}").text = "package baz; import bar.B; class D extends B {}"

        impl.snapshot { succeeds "impl:${language.compileTaskName}" }

        when:
        packageFile.delete()
        succeeds "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses("C")
    }
}
