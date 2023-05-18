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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

import java.nio.file.Files
import java.nio.file.Paths

class JavaSourceIncrementalCompilationIntegrationTest extends BaseJavaSourceIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "recompiles when module info changes"() {
        given:
        source("""
            import java.util.logging.Logger;
            class Foo {
                Logger logger;
            }
        """)
        def moduleInfo = file("src/main/${language.name}/module-info.${language.name}")
        moduleInfo.text = """
            module foo {
                requires java.logging;
            }
        """

        succeeds language.compileTaskName

        when:
        moduleInfo.text = """
            module foo {
            }
        """

        then:
        fails language.compileTaskName
        result.assertHasErrorOutput("package java.util.logging is not visible")
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "recompiles when module info is added"() {
        given:
        source("""
            import java.util.logging.Logger;
            class Foo {
                Logger logger;
            }
        """)

        succeeds language.compileTaskName

        when:
        def moduleInfo = file("src/main/${language.name}/module-info.${language.name}")
        moduleInfo.text = """
            module foo {
            }
        """

        then:
        fails language.compileTaskName
        result.assertHasErrorOutput("package java.util.logging is not visible")
    }

    @Requires(UnitTestPreconditions.Symlinks)
    @Issue("https://github.com/gradle/gradle/issues/9202")
    def "source mapping file works with symlinks"() {
        given:
        buildFile << """
            sourceSets {
                main {
                    ${languageName} {
                        srcDirs = ['src/main/${languageName}/build', 'src/main/${languageName}/linkparent']
                    }
                }
            }
        """
        file("other/foo/a/MyClass.${languageName}") << """package foo.a;
            public class MyClass {
                public void foo() { }
            }
        """
        file("src/main/${languageName}/build/foo/b/Other.${languageName}") << """package foo.b;
            import foo.a.MyClass;

            public class Other {
                public void hello(MyClass my) { my.foo(); }
            }
        """
        Files.createSymbolicLink(Paths.get(file("src/main/${languageName}/linkparent").toURI()), Paths.get(file("other").toURI()))
        outputs.snapshot { run language.compileTaskName }

        when:
        file("other/foo/a/MyClass.${languageName}").text = """package foo.a;
            public class MyClass {
                public void foo() { }
                public void bar() { }
            }
        """

        then:
        succeeds language.compileTaskName
        outputs.recompiledClasses('MyClass', 'Other')
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "recompiles all when constant used by annotation on module-info is changed"() {
        given:
        source("""
            import java.util.logging.Logger;
            class Foo {
                Logger logger;
            }
        """)
        source("package constant; public class Const { public static final String CONST = \"unchecked\"; }")
        def moduleInfo = file("src/main/${language.name}/module-info.${language.name}")
        moduleInfo.text = """
            import constant.Const;

            @SuppressWarnings(Const.CONST)
            module foo {
                requires java.logging;
            }
        """
        outputs.snapshot { succeeds language.compileTaskName }

        when:
        source("package constant; public class Const { public static final String CONST = \"raw-types\"; }")
        succeeds language.compileTaskName

        then:
        outputs.recompiledClasses('Const', 'Foo', 'module-info')
    }

    def "recompiles all classes in a package if constant used by annotation on package-info is changed"() {
        given:
        file("src/main/${languageName}/annotations/Anno.${languageName}").text = """
            package annotations;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.PACKAGE)
            public @interface Anno {
                   int value();
            }
        """
        def packageFile = file("src/main/${languageName}/foo/package-info.${languageName}")
        packageFile.text = """@Deprecated @annotations.Anno(Const.CONST + 1) package foo; import constant.Const;"""
        source(
            "package foo; class A {}",
            "package foo; public class B {}",
            "package foo.bar; class C {}",
            "package baz; class D {}",
            "package baz; import foo.B; class E extends B {}",
            "package constant; public class Const { public static final int CONST = 1; }"
        )

        outputs.snapshot { succeeds language.compileTaskName }

        when:
        source("package constant; public class Const { public static final int CONST = 2; }")
        succeeds language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "E", "Const", "package-info")
    }
}
