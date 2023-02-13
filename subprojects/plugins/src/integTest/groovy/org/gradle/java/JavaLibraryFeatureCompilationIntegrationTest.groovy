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

package org.gradle.java


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class JavaLibraryFeatureCompilationIntegrationTest extends AbstractIntegrationSpec {

    private toggleCompileClasspathPackaging(boolean activate) {
        if (activate) {
            propertiesFile << """
                systemProp.org.gradle.java.compile-classpath-packaging=true
            """.trim()
        }
    }

    def "project can declare and compile feature [configuration=#configuration][compileClasspathPackaging=#compileClasspathPackaging]"() {
        toggleCompileClasspathPackaging(compileClasspathPackaging)
        settingsFile << """
            include 'b'
        """
        given:
        buildFile << """
            apply plugin: 'java-library'

            java {
                registerFeature("myFeature") {
                    usingSourceSet(sourceSets.main)
                }
            }

            dependencies {
                $configuration project(":b")
            }
        """
        file("b/build.gradle") << """
            apply plugin: 'java-library'
        """
        file("b/src/main/java/com/foo/Foo.java") << """
            package com.foo;
            public class Foo {
                public void foo() {
                }
            }
        """
        file("src/main/java/com/bar/Bar.java") << """
            package com.bar;
            import com.foo.Foo;

            public class Bar {
                public void bar() {
                    Foo foo = new Foo();
                    foo.foo();
                }
            }
        """

        when:
        succeeds ':compileJava'

        then:
        executedAndNotSkipped ':b:compileJava'
        packagingTasks(compileClasspathPackaging, 'b')

        where:
        compileClasspathPackaging | configuration
        false                     | "myFeatureApi"
        true                      | "myFeatureApi"
        false                     | "myFeatureImplementation"
        true                      | "myFeatureImplementation"
    }

    def "Java Library can depend on feature of component [compileClasspathPackaging=#compileClasspathPackaging]"() {
        toggleCompileClasspathPackaging(compileClasspathPackaging)
        settingsFile << """
            include 'b', 'c', 'd', 'e', 'f', 'g'
        """
        given:
        file("b/build.gradle") << """
            apply plugin: 'java-library'

            group = 'org.gradle.test'

            java {
                registerFeature("myFeature") {
                    usingSourceSet(sourceSets.main)
                }
            }

            dependencies {
                myFeatureApi project(":c")
                myFeatureImplementation project(":d")
                myFeatureCompileOnlyApi project(":e")
                myFeatureCompileOnly project(":f")
                myFeatureRuntimeOnly project(":g")
            }

        """
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(project(":b")) {
                    capabilities {
                        requireCapability("org.gradle.test:b-my-feature")
                    }
                }
            }

            task verifyClasspath {
                dependsOn(configurations.compileClasspath)
                dependsOn(configurations.runtimeClasspath)
                def incomingCompileClasspath = provider {
                    configurations.compileClasspath.incoming.resolutionResult.allDependencies.collect {
                        it.toString()
                    } as Set
                }
                def incomingRuntimeClasspath = provider {
                    configurations.runtimeClasspath.incoming.resolutionResult.allDependencies.collect {
                        it.toString()
                    } as Set
                }
                doLast {
                    assert incomingCompileClasspath.get() == ['project :b', 'project :c', 'project :e'] as Set // only API dependencies
                    assert incomingRuntimeClasspath.get() == ['project :b', 'project :c', 'project :d', 'project :g'] as Set // all dependencies (except compile only)
                }
            }
        """
        ['c', 'd', 'e', 'f', 'g'].each {
            file("$it/build.gradle") << """
            apply plugin: 'java-library'
        """
            file("$it/src/main/java/com/baz/Baz${it}.java") << """
            package com.baz;
            public class Baz${it} {}
        """
        }

        file("b/src/main/java/com/foo/Foo.java") << """
            package com.foo;
            public class Foo {
                public void foo() {
                }
            }
        """
        file("src/main/java/com/bar/Bar.java") << """
            package com.bar;
            import com.foo.Foo;

            public class Bar {
                public void bar() {
                    Foo foo = new Foo();
                    foo.foo();
                }
            }
        """

        when:
        succeeds ':compileJava'

        then:
        executedAndNotSkipped ':b:compileJava', ':c:compileJava', ':d:compileJava', ':e:compileJava', ':f:compileJava'
        packagingTasks(compileClasspathPackaging, 'b')
        packagingTasks(compileClasspathPackaging, 'c')
        packagingTasks(compileClasspathPackaging, 'd')
        packagingTasks(compileClasspathPackaging, 'e')
        packagingTasks(compileClasspathPackaging, 'f')

        when:
        succeeds 'clean', ':verifyClasspath'

        then:
        executedAndNotSkipped ':b:jar', ':c:jar', ':d:jar', ':g:jar' // runtime
        packagingTasks(compileClasspathPackaging, 'e') // compile time only
        packagingTasks(compileClasspathPackaging, 'f') // compile time only

        where:
        compileClasspathPackaging | _
        false                     | _
        true                      | _

    }

    def "main component doesn't expose dependencies from feature [compileClasspathPackaging=#compileClasspathPackaging]"() {
        toggleCompileClasspathPackaging(compileClasspathPackaging)
        settingsFile << """
            include 'b', 'c'
        """
        given:
        file("b/build.gradle") << """
            apply plugin: 'java-library'

            java {
                registerFeature("myFeature") {
                    usingSourceSet(sourceSets.main)
                }
            }

            dependencies {
                myFeatureImplementation project(":c")
            }

        """
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(project(":b"))
            }

            task resolveRuntime {
                def runtimeClasspath = configurations.runtimeClasspath
                dependsOn(runtimeClasspath)
                doLast {
                    assert runtimeClasspath.files.name as Set == ['b.jar'] as Set
                }
            }
        """
        file("c/build.gradle") << """
            apply plugin: 'java-library'
        """
        file("c/src/main/java/com/baz/Baz.java") << """
            package com.baz;
            public class Baz {}
        """
        file("b/src/main/java/com/foo/Foo.java") << """
            package com.foo;
            public class Foo {
                public void foo() {
                }
            }
        """
        file("src/main/java/com/bar/Bar.java") << """
            package com.bar;
            import com.foo.Foo;

            public class Bar {
                public void bar() {
                    Foo foo = new Foo();
                    foo.foo();
                }
            }
        """

        when:
        succeeds ':compileJava'

        then:
        executedAndNotSkipped ':b:compileJava', ':c:compileJava'
        packagingTasks(compileClasspathPackaging, 'b')
        packagingTasks(compileClasspathPackaging, 'c')

        when:
        succeeds 'clean', ':resolveRuntime'

        then:
        executedAndNotSkipped ':b:jar'

        where:
        compileClasspathPackaging | _
        false                     | _
        true                      | _
    }

    def "can build a feature that uses its own source directory [compileClasspathPackaging=#compileClasspathPackaging]"() {
        toggleCompileClasspathPackaging(compileClasspathPackaging)
        settingsFile << """
            include 'b'
        """
        given:
        buildFile << """
            apply plugin: 'java-library'

            sourceSets {
                myFeature {
                    java {
                        srcDir "src/myFeature/java"
                    }
                }
            }

            java {
                registerFeature("myFeature") {
                    usingSourceSet(sourceSets.myFeature)
                }
            }

            dependencies {
                $configuration project(":b")
            }
        """
        file("b/build.gradle") << """
            apply plugin: 'java-library'
        """
        file("b/src/main/java/com/foo/Foo.java") << """
            package com.foo;
            public class Foo {
                public void foo() {
                }
            }
        """
        file("src/myFeature/java/com/bar/Bar.java") << """
            package com.bar;
            import com.foo.Foo;

            public class Bar {
                public void bar() {
                    Foo foo = new Foo();
                    foo.foo();
                }
            }
        """

        when:
        succeeds ':compileMyFeatureJava'

        then:
        executedAndNotSkipped ':b:compileJava'
        packagingTasks(compileClasspathPackaging, 'b')
        notExecuted ':compileJava'

        where:
        compileClasspathPackaging | configuration
        false                     | "myFeatureApi"
        true                      | "myFeatureApi"
        false                     | "myFeatureImplementation"
        true                      | "myFeatureImplementation"
    }

    def "Java Library can depend on feature of component which uses its own source set [compileClasspathPackaging=#compileClasspathPackaging]"() {
        toggleCompileClasspathPackaging(compileClasspathPackaging)
        settingsFile << """
            include 'b', 'c', 'd'
            rootProject.name = 'test'
        """
        given:
        file("b/build.gradle") << """
            apply plugin: 'java-library'

            group = 'org.gradle.test'

            sourceSets {
                myFeature {
                    java {
                        srcDir "src/myFeature/java"
                    }
                }
            }

            java {
                registerFeature("myFeature") {
                    usingSourceSet(sourceSets.myFeature)
                }
            }

            dependencies {
                myFeatureApi project(":c")
                myFeatureImplementation project(":d")
            }

        """
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                implementation(project(":b")) {
                    capabilities {
                        requireCapability("org.gradle.test:b-my-feature")
                    }
                }
            }

            task verifyClasspath {
                dependsOn(configurations.compileClasspath)
                dependsOn(configurations.runtimeClasspath)
                def incomingCompileClasspath = provider {
                    configurations.compileClasspath.incoming.resolutionResult.allDependencies.collect {
                        it.toString()
                    } as Set
                }
                def runtimeClasspath = configurations.runtimeClasspath
                def incomingRuntimeClasspath = provider {
                    runtimeClasspath.incoming.resolutionResult.allDependencies.collect {
                        it.toString()
                    } as Set
                }
                doLast {
                    assert incomingCompileClasspath.get() == ['project :b', 'project :c'] as Set // only API dependencies
                    assert incomingRuntimeClasspath.get() == ['project :b', 'project :c', 'project :d'] as Set // all dependencies
                    assert runtimeClasspath.files.name as Set == ['b-my-feature.jar', 'c.jar', 'd.jar'] as Set
                }
            }
        """
        ['c', 'd'].each {
            file("$it/build.gradle") << """
                apply plugin: 'java-library'
            """

            file("$it/src/main/java/com/baz/Baz${it}.java") << """
                package com.baz;
                public class Baz${it} {}
            """
        }

        file("b/src/myFeature/java/com/foo/Foo.java") << """
            package com.foo;
            public class Foo {
                public void foo() {
                }
            }
        """
        file("src/main/java/com/bar/Bar.java") << """
            package com.bar;
            import com.foo.Foo;

            public class Bar {
                public void bar() {
                    Foo foo = new Foo();
                    foo.foo();
                }
            }
        """

        when:
        succeeds ':compileJava'

        then:
        executedAndNotSkipped ':b:compileMyFeatureJava', ':c:compileJava', ':d:compileJava'
        packagingTasks(compileClasspathPackaging, 'b', 'myFeature')
        packagingTasks(compileClasspathPackaging, 'c')
        packagingTasks(compileClasspathPackaging, 'd')

        when:
        succeeds 'clean', ':verifyClasspath'

        then:
        executedAndNotSkipped ':b:myFeatureJar', ':c:jar', ':d:jar'
        notExecuted ':b:jar' // main jar should NOT be built in this case

        where:
        compileClasspathPackaging | _
        false                     | _
        true                      | _
    }

    @Issue("gradle/gradle#10778")
    def "dependencies of a feature that uses the main source set are available on test compile classpath"() {
        buildFile << """
            apply plugin: 'java-library'

            ${mavenCentralRepository()}

            java {
                registerFeature('feat') {
                   usingSourceSet(sourceSets.main)
                }
            }

            dependencies {
                testImplementation "junit:junit:4.13"
                featApi "org.apache.commons:commons-math3:3.6.1"
            }
        """
        file("src/test/java/com/acme/FeatureTest.java") << """package com.acme;
            import org.apache.commons.math3.complex.Complex;
            import org.junit.Test;
            import static org.junit.Assert.*;

            public class FeatureTest {
                @Test
                public void shouldCompileAndRun() {
                    Complex complex = new Complex(2.0, 1);
                    assertEquals(3, complex.pow(2.0).getReal(), 1e-5);
                }
            }
        """

        when:
        run 'test'

        then:
        executedAndNotSkipped ':compileTestJava', ':test'
    }

    @Issue("gradle/gradle#10999")
    def "registerFeature can be used when there is no main SourceSet"() {
        given:
        buildFile << """
            apply plugin: 'java-base'

            sourceSets {
               main211 {}
               main212 {}
            }
            java {
               registerFeature('scala211') {
                  usingSourceSet(sourceSets.main211)
               }
               registerFeature('scala212') {
                  usingSourceSet(sourceSets.main212)
               }
            }
        """
        file("src/main211/java/com/foo/Foo.java") << """
            package com.foo;
            public class Foo {
                public void foo() {
                }
            }
        """
        file("src/main212/java/com/bar/Bar.java") << """
            package com.bar;

            public class Bar {
                public void bar() {
                }
            }
        """

        when:
        succeeds ':compileMain211Java', ':compileMain212Java'

        then:
        executedAndNotSkipped ':compileMain211Java', ':compileMain212Java'
    }

    def "elements configurations have the correct roles"() {
        given:
        buildFile << """
            plugins {
                id 'java-library'
            }

            sourceSets {
                sources
            }

            java {
                registerFeature("example") {
                    usingSourceSet(sourceSets.sources)
                }
            }

            task verifyConfigurations {
                def apiElements = configurations.sourcesApiElements
                def runtimeElements = configurations.sourcesRuntimeElements

                [apiElements, runtimeElements].each {
                    assert it.canBeConsumed == true
                    assert it.canBeResolved == false
                    assert it.canBeDeclaredAgainst == true

                    assert it.declarationAlternatives == null
                    assert it.resolutionAlternatives == null
                    assert it.consumptionDeprecation == null
                }
            }
        """

        expect:
        succeeds("verifyConfigurations")
    }

    private void packagingTasks(boolean expectExecuted, String subproject, String feature = '') {
        def tasks = [":$subproject:process${feature.capitalize()}Resources", ":$subproject:${feature.isEmpty() ? 'classes' : feature + 'Classes'}", ":$subproject:${feature.isEmpty() ? 'jar' : feature + 'Jar'}"]
        if (expectExecuted) {
            executed(*tasks)
        } else {
            notExecuted(*tasks)
        }
    }
}
