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

abstract class BaseJavaSourceIncrementalCompilationIntegrationTest extends AbstractSourceIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    def "deletes headers when source file is deleted"() {
        given:
        def sourceFile = file("src/main/java/my/org/Foo.java")
        sourceFile.text = """
            package my.org;

            public class Foo {
                public native void foo();

                public static class Inner {
                    public native void anotherNative();
                }
            }
        """
        def generatedHeaderFile = file("build/generated/sources/headers/java/main/my_org_Foo.h")
        def generatedInnerClassHeaderFile = file("build/generated/sources/headers/java/main/my_org_Foo_Inner.h")

        source("""class Bar {
            public native void bar();
        }""")

        succeeds language.compileTaskName
        generatedHeaderFile.assertExists()
        generatedInnerClassHeaderFile.assertExists()

        when:
        sourceFile.delete()
        succeeds language.compileTaskName

        then:
        generatedHeaderFile.assertDoesNotExist()
        generatedInnerClassHeaderFile.assertDoesNotExist()
        file("build/generated/sources/headers/java/main/Bar.h").assertExists()
    }

    def "reports source type that does not support detection of source root"() {
        given:
        buildFile << "${language.compileTaskName}.source([file('extra'), file('other'), file('text-file.txt')])"

        source("class A extends B {}")
        file("extra/B.${language.name}") << "class B {}"
        file("extra/C.${language.name}") << "class C {}"
        def textFile = file('text-file.txt')
        textFile.text = "text file as root"

        outputs.snapshot { run language.compileTaskName }

        when:
        file("extra/B.${language.name}").text = "class B { String change; }"
        executer.withArgument "--info"
        run language.compileTaskName

        then:
        outputs.recompiledClasses("A", "B", "C")
        output.contains("Cannot infer source root(s) for source `file '${textFile.absolutePath}'`. Supported types are `File` (directories only), `DirectoryTree` and `SourceDirectorySet`.")
        output.contains("Full recompilation is required because the source roots could not be inferred.")
    }

    def "does not recompile when a resource changes"() {
        given:
        buildFile << """
            ${language.compileTaskName}.source 'src/main/resources'
        """
        source("class A {}")
        source("class B {}")
        def resource = file("src/main/resources/foo.txt")
        resource.text = 'foo'

        outputs.snapshot { succeeds language.compileTaskName }

        when:
        resource.text = 'bar'

        then:
        succeeds language.compileTaskName
        outputs.noneRecompiled()
    }
}
