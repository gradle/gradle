/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

import static org.junit.Assume.assumeTrue

abstract class AbstractSourceIncrementalCompilationIntegrationTest extends AbstractJavaGroovyIncrementalCompilationSupport {

    def "detects class changes in subsequent runs ensuring the class dependency data is refreshed"() {
        source "class A {}", "class B {}", "class C {}"
        outputs.snapshot { run language.compileTaskName }

        when:
        source "class B extends A {}"
        run language.compileTaskName

        then:
        outputs.recompiledClasses('B')

        when:
        outputs.snapshot()
        source "class A { /* change */ }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses('A', 'B')
    }

    def "handles multiple compile tasks within a single project"() {
        source "class A {}", "class B extends A {}"
        file("src/integTest/${languageName}/X.${languageName}") << "class X {}"
        file("src/integTest/${languageName}/Y.${languageName}") << "class Y extends X {}"
        //new separate compile task (integTestCompile)
        file("build.gradle") << """
            sourceSets { integTest.${languageName}.srcDir "src/integTest/${languageName}" }
        """
        if (language == CompiledLanguage.GROOVY) {
            buildFile << """
                dependencies {
                    integTestImplementation localGroovy()
                }
"""
        }

        outputs.snapshot { run "compileIntegTest${language.capitalizedName}", language.compileTaskName }

        when: //when A class is changed
        source "class A { String change; }"
        run "compileIntegTest${language.capitalizedName}", language.compileTaskName, "-i"

        then: //only B and A are recompiled
        outputs.recompiledClasses("A", "B")

        when: //when X class is changed
        outputs.snapshot()
        file("src/integTest/${languageName}/X.${languageName}").text = "class X { String change;}"
        run "compileIntegTest${language.capitalizedName}", language.compileTaskName, "-i"

        then: //only X and Y are recompiled
        outputs.recompiledClasses("X", "Y")
    }

    def "recompiles classes from extra source directories"() {
        buildFile << "sourceSets.main.${languageName}.srcDir 'extra'"

        source("class B {}")
        file("extra/A.${languageName}") << "class A extends B {}"
        file("extra/C.${languageName}") << "class C {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        source("class B { String change; } ")
        run language.compileTaskName

        then:
        outputs.recompiledClasses("B", "A")
    }

    def 'can move classes between source dirs'() {
        given:
        buildFile << "sourceSets.main.${languageName}.srcDir 'extra'"
        source('class A1 {}')
        file("extra/A2.${languageName}") << "class A2 {}"
        def movedFile = file("extra/some/dir/B.${languageName}") << """package some.dir;
        public class B {
            public static class Inner { }
        }"""

        run language.compileTaskName

        when:
        movedFile.moveToDirectory(file("src/main/${languageName}/some/dir"))
        outputs.snapshot { run language.compileTaskName, '-i' }

        then:
        skipped(":${language.compileTaskName}")

        when:
        source( """package some.dir;
        public class B {
            public static class NewInner { }
        }""") // in B.java/B.groovy
        run language.compileTaskName

        then:
        executedAndNotSkipped(":${language.compileTaskName}")
        outputs.recompiledClasses('B', 'B$NewInner')
        outputs.deletedClasses('B$Inner')
    }

    def "recompilation considers changes from dependent sourceSet"() {
        buildFile << """
sourceSets {
    other {}
    main { compileClasspath += sourceSets.other.output }
}
"""
        if (language == CompiledLanguage.GROOVY) {
            buildFile << """
        dependencies {
            otherImplementation localGroovy()
        }
"""
        }

        source("class Main extends com.foo.Other {}")
        file("src/other/${languageName}/com/foo/Other.${languageName}") << "package com.foo; public class Other {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/other/${languageName}/com/foo/Other.${languageName}").text = "package com.foo; public class Other { String change; }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("Other", "Main")
    }

    def "recompilation does not process removed classes from dependent sourceSet"() {
        def unusedClass = source("public class Unused {}")
        // Need another class or :compileJava will always be considered UP-TO-DATE
        source("public class Other {}")

        file("src/test/${languageName}/BazTest.${languageName}") << "public class BazTest {}"

        outputs.snapshot { run "compileTest${language.capitalizedName}" }

        when:
        file("src/test/${languageName}/BazTest.${languageName}").text = "public class BazTest { String change; }"
        unusedClass.delete()

        run "compileTest${language.capitalizedName}"

        then:
        outputs.recompiledClasses("BazTest")
        outputs.deletedClasses("Unused")
    }

    def "detects changes to source in extra source directories"() {
        buildFile << "sourceSets.main.${languageName}.srcDir 'extra'"

        source("class A extends B {}")
        file("extra/B.${languageName}") << "class B {}"
        file("extra/C.${languageName}") << "class C {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        file("extra/B.${languageName}").text = "class B { String change; }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("B", "A")
    }

    def "recompiles classes from extra source directory provided as #type"() {
        given:
        buildFile << "${language.compileTaskName}.source $method('extra')"

        source("class B {}")
        file("extra/A.${languageName}") << "class A extends B {}"
        file("extra/C.${languageName}") << "class C {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        source("class B { String change; } ")
        run language.compileTaskName

        then:
        outputs.recompiledClasses("B", "A")

        where:
        type            | method
        "File"          | "file"
        "DirectoryTree" | "fileTree"
    }

    def "detects changes to source in extra source directory provided as #type"() {
        buildFile << "${language.compileTaskName}.source $method('extra')"

        source("class A extends B {}")
        file("extra/B.${languageName}") << "class B {}"
        file("extra/C.${languageName}") << "class C {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        file("extra/B.${languageName}").text = "class B { String change; }"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("B", "A")

        where:
        type            | method
        "File"          | "file"
        "DirectoryTree" | "fileTree"
    }

    def "missing files are ignored as source roots"() {
        buildFile << """
            ${language.compileTaskName} {
                source([
                    fileTree('missing-tree'),
                    file('missing-file')
                ])
            }"""

        source("class A extends B {}")
        source("class B {}")
        source("class C {}")

        outputs.snapshot { run language.compileTaskName }

        when:
        source("class B { String change; }")
        executer.withArgument "--info"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B")
    }

    def "can remove source root"() {
        def toBeRemoved = file("to-be-removed")
        buildFile << """
            ${language.getCompileTaskName()} {
                source([fileTree('to-be-removed')])
            }"""

        source("class A extends B {}")
        source("class B {}")
        toBeRemoved.file("C.${languageName}").text = "class C {}"

        outputs.snapshot { run language.compileTaskName }

        when:
        toBeRemoved.deleteDir()
        executer.withArgument "--info"
        run language.compileTaskName

        then:
        outputs.recompiledClasses()
    }

    def "handles duplicate class across source directories"() {
        //compiler does not allow this scenario, documenting it here
        buildFile << "sourceSets.main.${languageName}.srcDir 'java'"

        source("class A {}")
        file("java/A.${languageName}") << "class A {}"

        when:
        fails language.compileTaskName
        then:
        failure.assertHasCause("Compilation failed")
    }

    @Issue("GRADLE-3426")
    def "supports Java 1.2 dependencies"() {
        source "class A {}"

        buildFile << """
            ${mavenCentralRepository()}
            dependencies { implementation 'com.ibm.icu:icu4j:2.6.1' }
        """
        expect:
        succeeds language.compileTaskName
    }

    @Issue("GRADLE-3426")
    def "fully recompiles when a non-analyzable jar is changed"() {
        def a = source """
            import com.ibm.icu.util.Calendar;
            class A {
                Calendar cal;
            }
        """

        buildFile << """
            ${mavenCentralRepository()}
            if (providers.gradleProperty("withIcu").isPresent()) {
                dependencies { implementation 'com.ibm.icu:icu4j:2.6.1' }
            }

        """
        succeeds language.compileTaskName, "-PwithIcu"

        when:
        a.text = "class A {}"

        then:
        succeeds language.compileTaskName, "--info"
        outputContains("Full recompilation is required because LocaleElements_zh__PINYIN.class could not be analyzed for incremental compilation.")
    }

    @Issue("GRADLE-3495")
    def "supports Java 1.1 dependencies"() {
        source "class A {}"

        buildFile << """
            ${mavenCentralRepository()}
            dependencies { implementation 'net.sf.ehcache:ehcache:2.10.2' }
        """
        expect:
        run language.compileTaskName
    }

    def "deletes empty packages dirs"() {
        given:
        def a = file("src/main/${languageName}/com/foo/internal/A.${languageName}") << """
            package com.foo.internal;
            public class A {}
        """
        file("src/main/${languageName}/com/bar/B.${languageName}") << """
            package com.bar;
            public class B {}
        """

        succeeds language.compileTaskName
        a.delete()

        when:
        succeeds language.compileTaskName

        then:
        !file("build/classes/java/main/com/foo").exists()
    }

    def "recompiles types whose names look like inner classes even if they aren't"() {
        given:
        file("src/main/${languageName}/Test.${languageName}") << 'public class Test{}'
        file("src/main/${languageName}/Test\$\$InnerClass.${languageName}") << 'public class Test$$InnerClass{}'

        when:
        succeeds ":${language.compileTaskName}"

        then:
        executedAndNotSkipped ":${language.compileTaskName}"
        file("build/classes/${languageName}/main/Test.class").assertExists()
        file("build/classes/${languageName}/main/Test\$\$InnerClass.class").assertExists()

        when:
        file("src/main/${languageName}/Test.${languageName}").text = 'public class Test{ void foo() {} }'
        succeeds ":${language.compileTaskName}"

        then:
        executedAndNotSkipped ":${language.compileTaskName}"
        file("build/classes/${languageName}/main/Test.class").assertExists()
        file("build/classes/${languageName}/main/Test\$\$InnerClass.class").assertExists()
    }

    def "incremental java compilation ignores empty packages"() {
        given:
        file("src/main/${languageName}/org/gradle/test/MyTest.${languageName}").text = """
            package org.gradle.test;

            class MyTest {}
        """

        when:
        run language.compileTaskName
        then:
        executedAndNotSkipped(":${language.compileTaskName}")

        when:
        file('src/main/${languageName}/org/gradle/different').createDir()
        run(language.compileTaskName)

        then:
        skipped(":${language.compileTaskName}")
    }

    def "recompiles all classes in a package if the package-info file changes"() {
        given:
        source("""
            package annotations;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.PACKAGE)
            public @interface Anno {}
        """)
        def packageFile = file("src/main/${languageName}/foo/package-info.${languageName}")
        packageFile.text = """@Deprecated package foo;"""
        source(
            "package foo; class A {}",
            "package foo; public class B {}",
            "package foo.bar; class C {}",
            "package baz; class D {}",
            "package baz; import foo.B; class E extends B {}"
        )

        outputs.snapshot { succeeds language.compileTaskName }

        when:
        packageFile.text = """@Deprecated @annotations.Anno package foo;"""
        succeeds language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "E", "package-info")
    }

    def "recompiles all classes in a package if the package-info file is added"() {
        given:
        def packageFile = file("src/main/${languageName}/foo/package-info.${languageName}")
        source(
            "package foo; class A {}",
            "package foo; public class B {}",
            "package foo.bar; class C {}",
            "package baz; class D {}",
            "package baz; import foo.B; class E extends B {}"
        )

        outputs.snapshot { succeeds language.compileTaskName }

        when:
        packageFile.text = """@Deprecated package foo;"""
        succeeds language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "E", "package-info")
    }

    def "recompiles all classes in a package if the package-info file is removed"() {
        given:
        def packageFile = file("src/main/${languageName}/foo/package-info.${languageName}")
        packageFile.text = """@Deprecated package foo;"""
        source(
            "package foo; class A {}",
            "package foo; public class B {}",
            "package foo.bar; class C {}",
            "package baz; class D {}",
            "package baz; import foo.B; class E extends B {}"
        )

        outputs.snapshot { succeeds language.compileTaskName }

        when:
        packageFile.delete()
        succeeds language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "E")
        outputs.deletedClasses("package-info")
    }

    @Issue('https://github.com/gradle/gradle/issues/9380')
    def 'can move source sets'() {
        given:
        buildFile << "sourceSets.main.${languageName}.srcDir 'src/other/${languageName}'"
        source('class Sub extends Base {}')
        file("src/other/${languageName}/Base.${languageName}") << 'class Base { }'

        outputs.snapshot { run language.compileTaskName }

        when:
        // Remove last line
        buildFile.text = buildFile.text.readLines().findAll { !it.trim().startsWith('sourceSets') }.join('\n')
        fails language.compileTaskName

        then:
        failureCauseContains('Compilation failed')
    }

    @Issue("https://github.com/gradle/gradle/issues/19257")
    def "recompiles classes with \$ in class name after rename"() {
        def firstSource = source "class Class\$Name {}"
        source "class Main { public static void main(String[] args) { new Class\$Name(); } }"
        outputs.snapshot { run language.compileTaskName }

        when:
        firstSource.delete()
        source "class Class\$Name1 {}",
            "class Main { public static void main(String[] args) { new Class\$Name1(); } }"
        run language.compileTaskName

        then:
        outputs.deletedClasses("Class\$Name")
        outputs.recompiledClasses("Class\$Name1", "Main")
    }

    @Issue("https://github.com/gradle/gradle/issues/28916")
    @ToBeImplemented
    def "recompiles classes on file typo rename"() {
        // TODO: Delete this assume statement when fixed,
        //  since for Java Cli mode it already works accidentally,
        //  since Cli mode doesn't track file to class mapping accurately
        assumeTrue(this.class != JavaSourceCliIncrementalCompilationIntegrationTest.class)

        def fileWithTypo = file("src/main/${languageName}/ATypo.${languageName}") << """
            class A {}
        """
        outputs.snapshot { run language.compileTaskName }

        when:
        fileWithTypo.delete()
        file("src/main/${languageName}/A.${languageName}") << """
            class A {}
        """
        run language.compileTaskName

        then:
        // TODO: Fix this, A should be recompiled, e.g.:
        //   outputs.hasFiles(file("A.class"))
        //   outputs.recompiled("A")
        outputs.deletedClasses("A")
        outputs.noneRecompiled()
    }
}
