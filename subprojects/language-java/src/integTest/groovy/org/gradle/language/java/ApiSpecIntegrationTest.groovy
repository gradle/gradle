/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.java

import org.gradle.integtests.fixtures.jvm.JvmSourceFile
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.integtests.language.AbstractJvmLanguageIntegrationTest
import org.gradle.language.fixtures.TestJavaComponent
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.JDK8_OR_EARLIER)
class ApiSpecIntegrationTest extends AbstractJvmLanguageIntegrationTest {

    TestJvmComponent app = new TestJavaComponent()

    def "should succeed when public api specification is absent"() {
        when:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                    }
                }
            }
        """
        then:
        succeeds "assemble"
    }

    def "should succeed when public api specification is present but empty"() {
        when:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        api {
                        }
                    }
                }
            }
        """
        then:
        succeeds "assemble"
    }

    def "should succeed when public api specification is present and fully configured"() {
        when:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        api {
                            exports 'com.example.p1'
                            exports 'com.example.p2'
                        }
                    }
                }
            }
        """
        then:
        succeeds "assemble"
    }

    def "should succeed when public api exports an unnamed package"() {
        when:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        api {
                            exports ''
                        }
                    }
                }
            }
        """
        then:
        succeeds "assemble"
    }

    def "should fail when public api exports an invalid package name"() {
        when:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        api {
                            exports 'com.example.p-1'
                        }
                    }
                }
            }
        """
        then:
        fails "assemble"
        failure.assertHasCause("Invalid public API specification: 'com.example.p-1' is not a valid package name")
    }

    def "should fail when public api exports the same package more than once"() {
        when:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        api {
                            exports 'com.example.p1'
                            exports 'com.example.p1'
                        }
                    }
                }
            }
        """
        then:
        fails "assemble"
        failure.assertHasCause("Invalid public API specification: package 'com.example.p1' has already been exported")
    }

    def "api jar should contain only classes declared in packages exported in api spec"() {
        when:
        addNonApiClasses()

        and:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        api {
                            exports 'compile.test'
                        }
                    }
                }
            }
        """
        then:
        succeeds "assemble"

        and:
        def allClasses = app.sources*.classFile.fullPath as String[]
        def apiClassesOnly = (allClasses -
            ["non_api/pkg/InternalPerson.class", "compile/test/internal/Util.class"]) as String[];
        jarFile("build/jars/myLibJar/myLib.jar").hasDescendants(allClasses)
        jarFile("build/jars/myLibApiJar/myLib.jar").hasDescendants(apiClassesOnly)
    }

    def "api jar should include all library packages when no api specification is declared"() {
        when:
        addNonApiClasses()

        and:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                    }
                }
            }
        """
        then:
        succeeds "assemble"

        and:
        def allClasses = app.sources*.classFile.fullPath as String[]
        jarFile("build/jars/myLibJar/myLib.jar").hasDescendants(allClasses)
        jarFile("build/jars/myLibApiJar/myLib.jar").hasDescendants(allClasses)
    }

    def "api jar should include all library packages api specification is declared empty"() {
        when:
        addNonApiClasses()

        and:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        api {
                        }
                    }
                }
            }
        """
        then:
        succeeds "assemble"

        and:
        def allClasses = app.sources*.classFile.fullPath as String[]
        jarFile("build/jars/myLibJar/myLib.jar").hasDescendants(allClasses)
        jarFile("build/jars/myLibApiJar/myLib.jar").hasDescendants(allClasses)
    }

    def "api jar should be built for each variant and contain only api classes"() {
        when:
        addNonApiClasses()

        and:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        api {
                            exports 'compile.test'
                        }
                        targetPlatform 'java5'
                        targetPlatform 'java6'
                    }
                }
            }
        """
        then:
        succeeds "assemble"

        and:
        def allClasses = app.sources*.classFile.fullPath as String[]
        def apiClassesOnly = (allClasses -
            ["non_api/pkg/InternalPerson.class", "compile/test/internal/Util.class"]) as String[];
        jarFile("build/jars/myLibJava5Jar/myLib.jar").hasDescendants(allClasses)
        jarFile("build/jars/myLibJava5ApiJar/myLib.jar").hasDescendants(apiClassesOnly)
        jarFile("build/jars/myLibJava6Jar/myLib.jar").hasDescendants(allClasses)
        jarFile("build/jars/myLibJava6ApiJar/myLib.jar").hasDescendants(apiClassesOnly)
    }

    def "building api jar should trigger compilation of classes"() {
        when:
        addNonApiClasses()

        and:
        buildFile << """
            model {
                components {
                    myLib(JvmLibrarySpec) {
                        api {
                            exports 'compile.test'
                        }
                    }
                }
            }
        """
        then:
        succeeds "createMyLibApiJar"

        and:
        def allClasses = app.sources*.classFile.fullPath as String[]
        def apiClassesOnly = (allClasses -
            ["non_api/pkg/InternalPerson.class", "compile/test/internal/Util.class"]) as String[];
        jarFile("build/jars/myLibApiJar/myLib.jar").hasDescendants(apiClassesOnly)
        jarFile("build/jars/myLibJar/myLib.jar").hasDescendants(allClasses)
    }

    def "should support configuring exported packages from rule method"() {
        when:
        addNonApiClasses()

        and:
        file('buildSrc/src/main/java/Rules.java') << '''
            import org.gradle.api.Action;
            import org.gradle.jvm.ApiSpec;
            import org.gradle.jvm.JvmLibrarySpec;
            import org.gradle.model.Mutate;
            import org.gradle.model.Path;
            import org.gradle.model.RuleSource;

            class Rules extends RuleSource {
                @Mutate
                void specifyMyLibApi(@Path("components.myLib") JvmLibrarySpec myLib) {
                    myLib.api(new Action<ApiSpec>() {
                        @Override
                        public void execute(ApiSpec apiSpec) {
                            apiSpec.exports("compile.test");
                        }
                    });
                }
            }
        '''

        and:
        buildFile << '''
            apply type: Rules

            model {
                components {
                    myLib(JvmLibrarySpec) {
                    }
                }
            }
        '''
        then:
        succeeds "createMyLibApiJar"

        and:
        def allClasses = app.sources*.classFile.fullPath as String[]
        def apiClassesOnly = (allClasses -
            ["non_api/pkg/InternalPerson.class", "compile/test/internal/Util.class"]) as String[];
        jarFile("build/jars/myLibApiJar/myLib.jar").hasDescendants(apiClassesOnly)
        jarFile("build/jars/myLibJar/myLib.jar").hasDescendants(allClasses)
    }

    def addNonApiClasses() {
        app.sources.add(new JvmSourceFile("non_api/pkg", "InternalPerson.java", '''
            package non_api.pkg;

            public class InternalPerson {
            }
            '''
        ))
        app.sources.add(new JvmSourceFile("compile/test/internal", "Util.java", '''
            package compile.test.internal;

            public class Util {
            }
            '''
        ))
        app.sources*.writeToDir(file("src/myLib/java"))
    }

}
