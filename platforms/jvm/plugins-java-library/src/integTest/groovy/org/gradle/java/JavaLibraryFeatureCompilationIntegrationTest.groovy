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
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
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

            sourceSets {
                myFeature
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

        where:
        compileClasspathPackaging | configuration
        false                     | "myFeatureApi"
        true                      | "myFeatureApi"
        false                     | "myFeatureImplementation"
        true                      | "myFeatureImplementation"
    }

    @ToBeFixedForIsolatedProjects(because = "Property dynamic lookup")
    def "Java Library can depend on feature of component [compileClasspathPackaging=#compileClasspathPackaging]"() {
        toggleCompileClasspathPackaging(compileClasspathPackaging)
        settingsFile << """
            include 'b', 'c', 'd', 'e', 'f', 'g'
        """
        given:
        file("b/build.gradle") << """
            apply plugin: 'java-library'

            group = 'org.gradle.test'

            sourceSets {
                myFeature
            }

            java {
                registerFeature("myFeature") {
                    usingSourceSet(sourceSets.myFeature)
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
        executedAndNotSkipped ':b:compileMyFeatureJava', ':c:compileJava', ':d:compileJava', ':e:compileJava', ':f:compileJava'
        packagingTasks(compileClasspathPackaging, 'b', 'myFeature')
        packagingTasks(compileClasspathPackaging, 'c')
        packagingTasks(compileClasspathPackaging, 'd')
        packagingTasks(compileClasspathPackaging, 'e')
        packagingTasks(compileClasspathPackaging, 'f')

        when:
        succeeds 'clean', ':verifyClasspath'

        then:
        executedAndNotSkipped ':b:myFeatureJar', ':c:jar', ':d:jar', ':g:jar' // runtime
        packagingTasks(compileClasspathPackaging, 'e') // compile time only
        packagingTasks(compileClasspathPackaging, 'f') // compile time only

        where:
        compileClasspathPackaging | _
        false                     | _
        true                      | _

    }

    @ToBeFixedForIsolatedProjects(because = "Property dynamic lookup")
    def "main component doesn't expose dependencies from feature [compileClasspathPackaging=#compileClasspathPackaging]"() {
        toggleCompileClasspathPackaging(compileClasspathPackaging)
        settingsFile << """
            include 'b', 'c'
        """
        given:
        file("b/build.gradle") << """
            apply plugin: 'java-library'

            sourceSets {
                myFeature
            }

            java {
                registerFeature("myFeature") {
                    usingSourceSet(sourceSets.myFeature)
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
        succeeds ':compileJava'

        then:
        executedAndNotSkipped ':b:compileJava'
        packagingTasks(compileClasspathPackaging, 'b')

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

    @Issue("gradle/gradle#10999")
    def "registerFeature can be used when there is no main SourceSet"() {
        given:
        buildFile << """
            plugins {
                id("java-base")
            }

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

    def "creates main feature with main source set when java plugin not applied" () {
        given:
        buildFile << """
            plugins {
                id("java-base")
            }

            sourceSets {
               main
            }

            java {
               registerFeature('main') {
                  usingSourceSet(sourceSets.main)
               }
            }
        """

        when:
        succeeds 'dependencies'

        then:
        outputContains("runtimeOnly")
        outputContains("compileOnly")
        outputContains("implementation")
        outputContains("api")
        outputContains("compileOnlyApi")
        outputContains("runtimeElements")
        outputContains("apiElements")
    }

    def "creates configurations when using main source set, non-main feature, java plugin is not applied" () {
        given:
        buildFile << """
            plugins {
                id("java-base")
            }

            sourceSets {
               main
            }

            java {
               registerFeature('feature') {
                  usingSourceSet(sourceSets.main)
               }
            }
        """

        when:
        succeeds 'dependencies'

        then:
        outputContains("runtimeOnly")
        outputContains("compileOnly")
        outputContains("implementation")
        outputContains("api")
        outputContains("compileOnlyApi")
        outputContains("runtimeElements")
        outputContains("apiElements")
    }

    @Issue("https://github.com/gradle/gradle/issues/10778")
    def "cannot create feature using main source set when java library plugin is applied"() {
        buildFile << """
            plugins {
                id("java-library")
            }

            java {
                registerFeature('feat') {
                   usingSourceSet(sourceSets.main)
                }
            }
        """

        when:
        fails("help")

        then:
        failure.assertHasCause("Cannot create feature 'feat' for source set 'main' since configuration 'apiElements' already exists. A feature may have already been created with this source set. A source set can only be used by one feature at a time.")
    }

    def "elements configurations have the correct roles"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
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
                    assert it.canBeDeclared == false

                    assert it.deprecatedForDeclarationAgainst == false
                    assert it.deprecatedForResolution == false
                    assert it.deprecatedForConsumption == false
                }
            }
        """

        expect:
        succeeds("verifyConfigurations")
    }

    def "can depend on a feature using requireFeature"() {
        settingsFile << """
            include("other")
        """

        file("other/build.gradle") << """
            plugins {
                id("java-library")
            }

            sourceSets {
                create("foo")
            }

            java {
                registerFeature("foo") {
                    usingSourceSet(sourceSets.foo)
                }
            }
        """

        buildFile << """
            plugins {
                id("java-library")
            }

            dependencies {
                implementation(project(":other")) {
                    capabilities {
                        requireFeature("foo")
                    }
                }
            }

            task resolve {
                def files = configurations.runtimeClasspath.incoming.files
                doLast {
                    assert files*.name == ["other-foo.jar"]
                }
            }
        """

        expect:
        succeeds(":resolve")
    }

    def "can create feature without creating source set first"() {
        buildFile << """
            plugins {
                id("java-base")
            }

            ${mavenCentralRepository()}

            java {
                registerFeature("foo") {}
            }

            dependencies {
                fooImplementation("com.google.guava:guava:33.4.6-jre")
            }
        """

        file("src/foo/java/Foo.java") << """
            import com.google.common.collect.ImmutableList;

            public class Foo {
                public static void main(String[] args) {
                    ImmutableList im = ImmutableList.of("foo");
                }
            }
        """

        when:
        succeeds(":compileFooJava")

        then:
        file("build/classes/java/foo/Foo.class").exists()
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
