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
import spock.lang.Unroll

class JavaLibraryFeatureCompilationIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << """
            rootProject.name = "test"
        """
        buildFile << """
            allprojects {
                group = 'org.gradle.test'
                version = '1.0'
            }
        """
    }

    @Unroll
    def "project can declare and compile feature (configuration=#configuration)"() {
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
        notExecuted ':b:processResources', ':b:classes', ':b:jar'

        where:
        configuration << ["myFeatureApi", "myFeatureImplementation"]
    }

    def "Java Library can depend on feature of component"() {
        settingsFile << """
            include 'b', 'c', 'd'
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
                doLast {
                    assert configurations.compileClasspath.incoming.resolutionResult.allDependencies.collect {
                        it.toString()
                    } as Set == ['project :b', 'project :c'] as Set // only API dependencies
                    assert configurations.runtimeClasspath.incoming.resolutionResult.allDependencies.collect {
                        it.toString()
                    } as Set == ['project :b', 'project :c', 'project :d'] as Set // all dependencies
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
        executedAndNotSkipped ':b:compileJava', ':c:compileJava', ':d:compileJava'
        notExecuted ':b:processResources', ':b:classes', ':b:jar', ':c:processResources', ':c:classes', ':c:jar', ':d:processResources', ':d:classes', ':d:jar'

        when:
        succeeds ':verifyClasspath'

        then:
        executedAndNotSkipped ':b:jar', ':c:jar', ':d:jar'

    }

    def "main component doesn't expose dependencies from feature"() {
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
                dependsOn(configurations.runtimeClasspath)
                doLast {
                    assert configurations.runtimeClasspath.files.name as Set == ['b-1.0.jar'] as Set
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
        notExecuted ':b:processResources', ':b:classes', ':b:jar', ':c:processResources', ':c:classes', ':c:jar'

        when:
        succeeds ':resolveRuntime'

        then:
        executedAndNotSkipped ':b:jar'

    }

    def "can build a feature that uses its own source directory"() {
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
        notExecuted ':b:processResources', ':b:classes', ':b:jar', ':compileJava'

        where:
        configuration << ["myFeatureApi", "myFeatureImplementation"]
    }

    def "Java Library can depend on feature of component which uses its own source set"() {
        settingsFile << """
            include 'b', 'c', 'd'
        """
        given:
        file("b/build.gradle") << """
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
                doLast {
                    assert configurations.compileClasspath.incoming.resolutionResult.allDependencies.collect {
                        it.toString()
                    } as Set == ['project :b', 'project :c'] as Set // only API dependencies
                    assert configurations.runtimeClasspath.incoming.resolutionResult.allDependencies.collect {
                        it.toString()
                    } as Set == ['project :b', 'project :c', 'project :d'] as Set // all dependencies
                    assert configurations.runtimeClasspath.files.name as Set == ['b-1.0-my-feature.jar', 'c-1.0.jar', 'd-1.0.jar'] as Set
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
        notExecuted ':b:processResources', ':b:classes', ':b:jar', ':c:processResources', ':c:classes', ':c:jar', ':d:processResources', ':d:classes', ':d:jar'

        when:
        succeeds ':verifyClasspath'

        then:
        executedAndNotSkipped ':b:myFeatureJar', ':c:jar', ':d:jar'
        notExecuted ':b:jar' // main jar should NOT be built in this case

    }
}
