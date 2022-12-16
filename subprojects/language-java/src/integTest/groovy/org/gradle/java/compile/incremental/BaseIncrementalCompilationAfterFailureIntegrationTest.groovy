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

import org.gradle.api.JavaVersion
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationAccess
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

import static org.junit.Assume.assumeFalse

abstract class BaseIncrementalCompilationAfterFailureIntegrationTest extends AbstractJavaGroovyIncrementalCompilationSupport {

    def "detects deletion of a source base class that leads to compilation failure but keeps old files"() {
        def a = source "class A {}"
        source "class B extends A {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        assert a.delete()
        then:
        fails language.compileTaskName
        outputs.noneRecompiled()
        // We keep old classes since we support incremental compilation after failure
        outputs.deletedClasses()
    }

    def "old classes are restored after the compile failure"() {
        source("class A {}", "class B {}")
        outputs.snapshot { run language.compileTaskName }

        when:
        source("class A { garbage }")
        runAndFail language.compileTaskName

        then:
        outputs.noneRecompiled()
        outputs.hasFiles(file("A.class"), file("B.class"))
    }

    def "incremental compilation works after a compile failure"() {
        source("class A {}", "class B {}")
        outputs.snapshot { run language.compileTaskName }
        source("class A { garbage }")
        runAndFail language.compileTaskName
        outputs.noneRecompiled()

        when:
        source("class A { }")
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A")
        outputs.hasFiles(file("A.class"), file("B.class"))
    }

    def "incremental compilation works after multiple compile failures"() {
        source("class A {}", "class B {}")
        outputs.snapshot { run language.compileTaskName }
        source("class A { garbage }")
        runAndFail language.compileTaskName
        outputs.noneRecompiled()
        runAndFail language.compileTaskName
        outputs.noneRecompiled()

        when:
        source("class A { }")
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A")
        outputs.hasFiles(file("A.class"), file("B.class"))
    }

    def "nothing is recompiled after a compile failure when file is reverted"() {
        source("class A {}", "class B {}")
        outputs.snapshot { run language.compileTaskName }
        source("class A { garbage }")
        runAndFail language.compileTaskName
        outputs.noneRecompiled()

        when:
        source("class A {}")
        run language.compileTaskName

        then:
        outputs.noneRecompiled()
        outputs.hasFiles(file("A.class"), file("B.class"))
    }

    def "writes relative paths to source-to-class mapping after incremental compilation"() {
        given:
        assumeFalse("Source-to-class mapping is not written for CLI compiler", this.class == JavaSourceCliIncrementalCompilationIntegrationTest.class)
        source("package test.gradle; class A {}", "package test2.gradle; class B {}")
        outputs.snapshot { run language.compileTaskName }
        def previousCompilationDataFile = file("build/tmp/${language.compileTaskName}/previous-compilation-data.bin")
        def previousCompilationAccess = new PreviousCompilationAccess(new StringInterner())

        when:
        source("package test.gradle; class A { static class C {} }")
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", 'A$C')
        def previousCompilationData = previousCompilationAccess.readPreviousCompilationData(previousCompilationDataFile)
        previousCompilationData.compilerApiData.sourceToClassMapping == [
            ("test/gradle/A.${languageName}" as String): ["test.gradle.A", "test.gradle.A\$C"] as Set,
            ("test2/gradle/B.${languageName}" as String): ["test2.gradle.B"] as Set
        ]
    }

    def "nothing is stashed on full recompilation but it's stashed on incremental compilation"() {
        given:
        source("class A {}", "class B {}")
        def compileTransactionDir = file("build/tmp/${language.compileTaskName}/compileTransaction")

        when: "First compilation is always full compilation"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B")
        !compileTransactionDir.exists()

        when: "Compilation after failure with clean is full recompilation"
        source("class A { garbage }")
        runAndFail language.compileTaskName
        outputs.snapshot { source("class A { }") }
        run "clean", language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B")
        !compileTransactionDir.exists()

        when: "Incremental compilation"
        outputs.snapshot { source("class A {}") }
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A")
        compileTransactionDir.exists()
        compileTransactionDir.file("stash-dir/A.class.uniqueId0").exists()
    }

    @Issue("https://github.com/gradle/gradle/issues/21644")
    def "unrelated class in the same source file as affected class is recompiled and stashed on incremental compilation"() {
        given:
        source "class A {}", "class B extends A {}", "class D {}"
        // F is not affected by changes, but it is recompiled
        // and should still be stashed since it's in the same source file as C
        source """
        class C extends B {}
        class F {}
        """
        def compileTransactionDir = file("build/tmp/${language.compileTaskName}/compileTransaction")
        def stashDirClasses = {
            compileTransactionDir.file("stash-dir")
                .list()
                .collect { it.replaceAll(".uniqueId.", "") }
                .sort()
        }

        when: "First compilation is always full compilation"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "C", "D", "F")
        !compileTransactionDir.exists()

        when:
        outputs.snapshot { source("class A { /* comment */ }") }
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "C", "F")
        compileTransactionDir.exists()
        stashDirClasses() == ["A.class", "B.class", "C.class", "F.class"]
    }

    def "can disable incremental compilation after failure and forces full recompilation"() {
        given:
        def compileTask = language == CompiledLanguage.GROOVY ? "GroovyCompile" : "JavaCompile"
        buildFile << """
        tasks.withType($compileTask) {
            options.incremental = true
            options.incrementalAfterFailure = false
        }
        """
        source "class A {}", "class B extends A {}", "class C {}"

        when: "First compilation is always full compilation"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "C")

        when: "Compilation after failure is full recompilation when optimization is disabled"
        outputs.snapshot { source("class A { garbage }") }
        runAndFail language.compileTaskName
        outputs.snapshot { source("class A {}") }
        run language.compileTaskName, "--info"

        then:
        outputs.recompiledClasses("A", "B", "C")
        outputContains("Full recompilation is required")
    }

    def "is not incremental compilation after failure when cli compiler is used"() {
        given:
        def compileTask = language == CompiledLanguage.GROOVY ? "GroovyCompile" : "JavaCompile"
        buildFile << """
        tasks.withType($compileTask) {
            options.incremental = true
            options.fork = true
            options.forkOptions.executable = '${TextUtil.escapeString(AvailableJavaHomes.getJdk(JavaVersion.current()).javacExecutable)}'
        }
        """
        source "class A {}", "class B extends A {}", "class C {}"

        when: "First compilation is always full compilation"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "C")

        when: "Compilation after failure is full recompilation when optimization is disabled"
        outputs.snapshot { source("class A { garbage }") }
        runAndFail language.compileTaskName
        outputs.snapshot { source("class A {}") }
        run language.compileTaskName, "--info"

        then:
        outputs.recompiledClasses("A", "B", "C")
        outputContains("Full recompilation is required")
    }
}

class JavaIncrementalCompilationAfterFailureIntegrationTest extends BaseIncrementalCompilationAfterFailureIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "incremental compilation after failure works with modules #description"() {
        file("impl/build.gradle") << """
            def layout = project.layout
            tasks.compileJava {
                modularity.inferModulePath = $inferModulePath
                options.compilerArgs.addAll($compileArgs)
                doFirst {
                    $doFirst
                }
            }
        """
        source "package a; import b.B; public class A {}",
            "package b; public class B {}",
            "package c; public class C {}"
        file("src/main/${language.name}/module-info.${language.name}").text = """
            module impl {
                exports a;
                exports b;
                exports c;
            }
        """
        succeeds language.compileTaskName
        outputs.recompiledClasses("A", "B", "C", "module-info")

        when:
        outputs.snapshot {
            source "package a; import b.B; public class A { void m1() {}; }",
                "package b; import a.A; public class B { A m1() { return new B(); } }"
        }

        then:
        fails language.compileTaskName

        when:
        outputs.snapshot {
            source "package a; import b.B; public class A { void m1() {}; }",
                "package b; import a.A; public class B { A m1() { return new A(); } }"
        }
        succeeds language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "module-info")

        where:
        description                 | inferModulePath | compileArgs                                                  | doFirst
        "with inferred module-path" | "true"          | "[]"                                                         | ""
        "with manual module-path"   | "false"         | "[\"--module-path=\${classpath.join(File.pathSeparator)}\"]" | "classpath = layout.files()"
    }
}

class GroovyIncrementalCompilationAfterFailureIntegrationTest extends BaseIncrementalCompilationAfterFailureIntegrationTest {
    CompiledLanguage language = CompiledLanguage.GROOVY

    def setup() {
        configureGroovyIncrementalCompilation()
    }

    @Issue("https://github.com/gradle/gradle/issues/21644")
    def "removes all classes for a recompiled source from output to stash dir for Spock tests when super class is changed"() {
        given:
        buildScript """
            plugins {
                id 'groovy'
                id 'java-library'
            }
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'org.codehaus.groovy:groovy:3.0.13'
                testImplementation 'org.spockframework:spock-core:2.1-groovy-3.0'
            }
            tasks.withType(GroovyCompile) {
                options.incremental = true
            }
            tasks.named('test') {
                useJUnitPlatform()
            }
        """
        file("src/test/groovy/BaseTest.groovy").text = """
            import spock.lang.Specification
            class BaseTest extends Specification {}
        """
        file("src/test/groovy/UnrelatedClass.groovy").text = "class UnrelatedClass {}"
        file("src/test/groovy/AppTest.groovy").text = """
            class AppTest extends BaseTest {
                def "some test"() {
                    // These nested closures create nested \$closureXY.class
                    with("") {
                        with("") {
                        }
                    }
                }
            }
        """
        def compileTransactionDir = file("build/tmp/compileTestGroovy/compileTransaction")
        def stashDirClasses = {
            compileTransactionDir.file("stash-dir")
                .list()
                .collect { it.replaceAll(".uniqueId.", "") }
                .sort() as Set<String>
        }

        when: "First compilation is always full compilation"
        run "compileTestGroovy"

        then:
        outputs.recompiledClasses("AppTest", "AppTest\$_some_test_closure1", "AppTest\$_some_test_closure1\$_closure2", "BaseTest", "UnrelatedClass")
        !compileTransactionDir.exists()

        when:
        outputs.snapshot {
            file("src/test/groovy/BaseTest.groovy").text = """
                import spock.lang.Specification
                class BaseTest extends Specification { void hello() {} }
            """
        }
        run "compileTestGroovy"

        then:
        outputs.recompiledClasses("AppTest", "AppTest\$_some_test_closure1", "AppTest\$_some_test_closure1\$_closure2", "BaseTest")
        compileTransactionDir.exists()
        stashDirClasses() == ["AppTest.class", "AppTest\$_some_test_closure1.class", "AppTest\$_some_test_closure1\$_closure2.class", "BaseTest.class"] as Set<String>
    }
}
