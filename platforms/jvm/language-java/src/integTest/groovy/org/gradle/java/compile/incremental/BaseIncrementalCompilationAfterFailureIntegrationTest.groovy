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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.TextUtil
import spock.lang.Issue

abstract class BaseIncrementalCompilationAfterFailureIntegrationTest extends AbstractJavaGroovyIncrementalCompilationSupport {

    String compileStaticAnnotation = ""

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

    def "old classes are restored after the compile failure and incremental compilation works after a failure"() {
        source("class A {}", "class B {}")
        outputs.snapshot { run language.compileTaskName }

        when:
        def a = source("class A { garbage }")
        runAndFail language.compileTaskName

        then:
        outputs.noneRecompiled()
        outputs.hasFiles(file("A.class"), file("B.class"))

        when:
        a.text = "class A {}"
        run language.compileTaskName

        then:
        outputs.noneRecompiled()
    }

    def "new generated classes are removed on the compile failure and incremental compilation works after a failure"() {
        def b = source("package b; class B {}")
        source("class Unrelated {}")
        outputs.snapshot { run language.compileTaskName }

        when:
        // Compile more classes, so there is possibility some is generated before a compile failure.
        // Since order of passed classes to javac is unstable we can compile classes in different order on different platforms.
        // Compile .java also for Groovy, so the file is written before failure.
        sourceWithFileSuffix("java", "package a; class A {}")
        b.text = "package b; $compileStaticAnnotation class B { B m1() { return 0; } }"
        sourceWithFileSuffix("java", "package c; class C {}")
        runAndFail language.compileTaskName

        then:
        outputs.noneRecompiled()
        outputs.hasFiles(file("b/B.class"), file("Unrelated.class"))
        !file("build/classes/$languageName/main/a/").exists()
        !file("build/classes/$languageName/main/c/").exists()

        when:
        b.text = "package b; class B {}"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "C")
    }

    def "classes in renamed files are restored on the compile failure and incremental compilation works after a failure"() {
        def a = source"package a; class A {}"
        def b = source "package b; class B {}"
        def c = source "package c; class C {}"
        source "class Unrelated {}"
        outputs.snapshot { run language.compileTaskName }

        when:
        // Compile more classes, so there is possibility some is generated before a compile failure.
        // Since order of passed classes to javac is unstable we can compile classes in different order on different platforms.
        // Compile .java also for Groovy, so the file is written before failure.
        a.delete()
        c.delete()
        file("src/main/$languageName/a/A1.java").text = "package a; class A { void m1() {} }"
        b.text = "package b; $compileStaticAnnotation class B { B getB() { return 0; } }"
        file("src/main/$languageName/c/C1.java").text = "package c; class C { void m1() {} }"
        runAndFail language.compileTaskName

        then:
        outputs.noneRecompiled()
        outputs.hasFiles(file("a/A.class"), file("b/B.class"), file("c/C.class"), file("Unrelated.class"))

        when:
        b.text = "package b; class B {}"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "C")
    }

    def "overwritten classes are restored on the compile failure and incremental compilation works after a failure"() {
        source("package a; class A {}", "package c; class C {}", "class Unrelated {}")
        def b = source("package b; class B {}")
        outputs.snapshot { run language.compileTaskName }

        when:
        // Compile more classes, so there is possibility some is generated before a compile failure.
        // Since order of passed classes to javac is unstable we can compile classes in different order on different platforms.
        // Compile .java also for Groovy, so the file is written before failure.
        file("src/main/$languageName/a/A1.java").text = "package a; class A { void m1() {} }"
        b.text = "package b; $compileStaticAnnotation class B { B getB() { return 0; } }"
        file("src/main/$languageName/c/C1.java").text = "package c; class C { void m1() {} }"
        runAndFail language.compileTaskName

        then:
        outputs.noneRecompiled()
        outputs.hasFiles(file("a/A.class"), file("b/B.class"), file("c/C.class"), file("Unrelated.class"))

        when:
        b.text = "package b; class B {}"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "C")
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

    @Requires(UnitTestPreconditions.Jdk9OrLater)
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

    def "incremental compilation after failure works with header output"() {
        given:
        source("class Unrelated {}")
        def b = source("package b; class B { public native void foo(); }")
        succeeds "compileJava"

        when:
        outputs.snapshot {
            source("package a; class A { public native void foo(); }")
            b.text = "package b; class B { public native void foo(); String m1() { return 0; } }"
            source("package c; class C { public native void foo(); }")
        }

        then:
        runAndFail "compileJava"
        outputs.noneRecompiled()

        when:
        b.text = 'package b; class B { public native void foo(); String m1() { return ""; } }'
        run "compileJava"

        then:
        outputs.recompiledClasses("A", "B", "C")
    }
}

class GroovyIncrementalCompilationAfterFailureIntegrationTest extends BaseIncrementalCompilationAfterFailureIntegrationTest {

    private static final COMPILE_STATIC_ANNOTATION = "@groovy.transform.CompileStatic"

    CompiledLanguage language = CompiledLanguage.GROOVY

    def setup() {
        compileStaticAnnotation = COMPILE_STATIC_ANNOTATION
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
                testImplementation 'org.codehaus.groovy:groovy:3.0.18'
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

    /**
     * While it's possible to reproduce a compilation failure after some .class is written to a disk for Java, it's much harder for Groovy,
     * since Groovy compiler will first analyze all classes and only then write all on disk. While Java compiler can also generate classes before other are analysed.
     *
     * That is why we test restoring overwritten and deleting new files just with Java files.
     */
    def 'incremental compilation after a failure works with mix java/groovy sources when #description compilation fails and new classes are deleted and overwritten classes are restored'() {
        given:
        sourceWithFileSuffix("groovy", "package a; class A {}")
        File b = sourceWithFileSuffix(fileWithErrorSuffix, "package b; class B { }")
        sourceWithFileSuffix("groovy", "package c; class C {}")
        run "compileGroovy"

        when:
        outputs.snapshot {
            file("src/main/groovy/a/A1.java").text = "package a; class A { void m1() {} }"
            b.text = "package b; $compileErrorClassAnnotation class B { B m1() { return 0; }; }"
            file("src/main/groovy/c/C1.java").text = "package c; class C { void m1() {} }"
            sourceWithFileSuffix("java", "package d; class D {}")
        }

        then:
        runAndFail "compileGroovy"
        outputs.noneRecompiled()

        when:
        outputs.snapshot {
            b.text = "package b; class B { B m1() { return new B(); }; }"
        }
        file("src/main/groovy/a/A1.java").delete()
        file("src/main/groovy/c/C1.java").delete()
        run "compileGroovy"

        then:
        outputs.recompiledClasses("B", "D")

        where:
        // We must trigger failure in a "Semantic Analysis" stage and not in parse stage, otherwise compiler won't output anything on a disk.
        // So for example "class A { garbage }" will cause compiler to fail very early and nothing will be written on a disk.
        description | fileWithErrorSuffix | compileErrorClassAnnotation
        "Java"      | "java"              | ""
        "Groovy"    | "groovy"            | COMPILE_STATIC_ANNOTATION
    }

    @Issue("https://github.com/gradle/gradle/issues/22814")
    def 'does full recompilation on fatal failure'() {
        given:
        def a = source("class A extends ABase implements WithTrait { def m() { println('a') } }")
        source "class ABase { def mBase() { println(A) } }"
        source """
            import groovy.transform.SelfType
            @SelfType(ABase)
            trait WithTrait {
                final AllPluginsValidation allPlugins = new AllPluginsValidation(this)

                static class AllPluginsValidation {
                    final ABase base
                    AllPluginsValidation(ABase base) {
                        this.base = base
                    }
                }
            }
        """
        run "compileGroovy"
        outputs.recompiledClasses('ABase', 'A', 'WithTrait', 'WithTrait$Trait$FieldHelper', 'WithTrait$AllPluginsValidation', 'WithTrait$Trait$Helper')

        when:
        a.text = "class A extends ABase implements WithTrait { def m() { println('b') } }"

        then:
        executer.withStackTraceChecksDisabled()
        def execution = runAndFail "compileGroovy"
        execution.assertHasCause("Unrecoverable compilation error")

        when:
        executer.withStacktraceEnabled()
        run"compileGroovy", "--info"

        then:
        outputs.recompiledClasses('ABase', 'A', 'WithTrait', 'WithTrait$Trait$FieldHelper', 'WithTrait$AllPluginsValidation', 'WithTrait$Trait$Helper')
        outputContains("Full recompilation is required")
    }
}
