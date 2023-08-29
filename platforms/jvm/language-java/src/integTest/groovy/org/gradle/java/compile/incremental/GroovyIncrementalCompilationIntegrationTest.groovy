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
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import spock.lang.Issue

class GroovyIncrementalCompilationIntegrationTest extends AbstractJavaGroovyIncrementalCompilationSupport implements DirectoryBuildCacheFixture {
    CompiledLanguage language = CompiledLanguage.GROOVY

    def setup() {
        configureGroovyIncrementalCompilation()
    }

    def 'is incremental after loading from cache'() {
        given:
        def a = source "class A {}"
        source "class B {}"

        withBuildCache()
        run language.compileTaskName
        file('build/classes/groovy/main').forceDeleteDir()

        when:
        withBuildCache()
        outputs.snapshot {
            run language.compileTaskName, '-i'
        }

        then:
        outputContains('FROM-CACHE')

        when:
        a.text = 'class A { int a }'
        run language.compileTaskName, '-i'

        then:
        outputs.recompiledClasses('A')
    }

    def 'only recompile affected classes when multiple class in one groovy file'() {
        given:
        def a = file('src/main/groovy/org/gradle/A.groovy')
        a << """
package org.gradle

class A1{}

class A2{}
"""
        file('src/main/groovy/org/gradle/B.groovy') << 'package org.gradle; class B{}'
        outputs.snapshot { run "compileGroovy" }

        when:
        a.text = 'package org.gradle; class A1 {}'
        run "compileGroovy"

        then:
        outputs.recompiledClasses('A1')
        outputs.deletedClasses('A2')
    }

    def 'only recompile removed packages'() {
        given:
        file('src/main/groovy/org/gradle/Org.groovy') << 'package org.gradle; class Org {}'
        file('src/main/groovy/com/gradle/Com.groovy') << 'package com.gradle; class Com {}'

        outputs.snapshot { run 'compileGroovy' }

        when:
        file('src/main/groovy/com').forceDeleteDir()
        run 'compileGroovy'

        then:
        outputs.recompiledClasses()
        outputs.deletedClasses('Com')
    }

    def 'recompiles when #action class to source file'() {
        given:
        File src = source(oldFile)

        outputs.snapshot { run 'compileGroovy' }

        when:
        src.text = newFile
        run 'compileGroovy'

        then:
        outputs.recompiledClasses(recompileClasses as String[])
        outputs.deletedClasses(deletedClasses as String[])

        where:
        action     | oldFile                                   | newFile                                    | recompileClasses | deletedClasses
        'adding'   | 'class A { } \nclass B { } \n'            | 'class A{}\nclass B{}\nclass C{}'          | ['A', 'B', 'C']  | []
        'removing' | 'class A { } \nclass B { } \nclass C { }' | 'class A{}\nclass B{}\n'                   | ['A', 'B']       | ['C']
        'changing' | 'class A { } \nclass B { } \nclass C { }' | 'class A{}\nclass B{}\n class C { int i }' | ['A', 'B', 'C']  | []
    }

    def 'recompiles when moving class to another source file'() {
        given:
        File src1 = source('class A { }\n class B { }')
        File src2 = source('class C { }')

        outputs.snapshot { run 'compileGroovy' }

        when:
        src1.text = 'class A { }'
        src2.text = 'class C { } \n class B { }'
        run 'compileGroovy'

        then:
        outputs.recompiledClasses('A', 'B', 'C')
    }

    def "reports source type that does not support detection of source root"() {
        given:
        buildFile << "${language.compileTaskName}.source([file('extra'), file('other'), file('text-file.txt')])"

        source("class A extends B {}")
        file("extra/B.${language.name}") << "class B {}"
        file("extra/C.${language.name}") << "class C {}"
        def textFile = file('text-file.txt')
        textFile.text = "text file as root"

        expect:
        fails language.compileTaskName
        failure.assertHasCause(
            'Unable to infer source roots. ' +
                'Incremental Groovy compilation requires the source roots. ' +
                'Change the configuration of your sources or disable incremental Groovy compilation.')
    }

    def 'merge old class source mappings if no recompilation required'() {
        given:
        File a = source('class A { }')
        File b = source('class B { } \n class C { }')
        source('class Unrelated {}')
        run 'compileGroovy'

        when:
        a.delete()
        outputs.snapshot { run 'compileGroovy' }

        then:
        skipped(':compileGroovy')

        when:
        b.text = 'class B { }'
        run 'compileGroovy'

        then:
        outputs.recompiledClasses('B')
        outputs.deletedClasses( "C")
    }

    def "does recompile when a resource changes"() {
        // TODO wolfs:
        //  Currently, a change to any non-Groovy file causes a full recompile.
        //  Later, changes to Java files should be handled incrementally.
        //  Changes to the registration file for global transforms needs to cause a full recompile.
        //  Other resources can probably be used by AST transformations, so they probably should cause a full recompile as well.
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
        outputs.recompiledClasses("A", "B")
    }

    @Issue("https://github.com/gradle/gradle/issues/10340")
    def "recompiles class when constant from inner class is changed"() {
        given:
        file("src/main/${languageName}/MyAnnotation.${languageName}") << """
            public @interface MyAnnotation { int value(); }
        """
        file("src/main/${languageName}/TopLevel.${languageName}") << """
            public class TopLevel {
               static class Inner {
                  public static final int CONST = 9999;
               }
            }
        """
        file("src/main/${languageName}/MyClass.${languageName}") << """
            public class MyClass {
                @MyAnnotation(TopLevel.Inner.CONST)
                private void foo() { }
            }
        """

        outputs.snapshot { run language.compileTaskName }

        when:
        file("src/main/${languageName}/TopLevel.${languageName}").text = """
            public class TopLevel {
               static class Inner {
                  public static final int CONST = 1223;
               }
            }
        """

        then:
        succeeds language.compileTaskName
        outputs.recompiledClasses('MyClass', 'MyAnnotation', 'TopLevel$Inner', 'TopLevel')
    }
}
