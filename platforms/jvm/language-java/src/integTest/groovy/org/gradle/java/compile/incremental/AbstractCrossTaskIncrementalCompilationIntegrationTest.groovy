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

import org.gradle.integtests.fixtures.CompiledLanguage

abstract class AbstractCrossTaskIncrementalCompilationIntegrationTest extends AbstractCrossTaskIncrementalCompilationSupport {

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

    def "handles multiple compile tasks in the same project"() {
        createDirs("other")
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
        source api: ["package org.junit; public class Assert {}"]
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
        source(
            api: ["package org.junit; public class Assert {}"],
            impl: ["class A extends org.junit.Assert {}"]
        )

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
}
