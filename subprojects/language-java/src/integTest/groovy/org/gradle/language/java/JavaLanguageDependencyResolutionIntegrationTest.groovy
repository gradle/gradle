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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.util.Matchers.matchesRegexp

class JavaLanguageDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {

    def "can resolve dependency on local library"() {
        setup:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        zdep(JvmLibrarySpec)
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'zdep'
                    }
                }
            }
        }
    }

    tasks {
        assemble.finalizedBy('checkDependencies')
        create('checkDependencies') {
            assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(createZdepJar)
        }
    }
}
'''
        file('src/zdep/java/Dep.java') << 'public class Dep {}'
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'

        expect:
        succeeds 'assemble'

    }

    def "can define a dependency on the same library"() {
        setup:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'main'
                    }
                }
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        fails 'assemble'
        failure.assertHasDescription('Circular dependency between the following tasks')

    }

    def "can define a cyclic dependency"() {
        setup:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'main2'
                    }
                }
            }
        }
        main2(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'main'
                    }
                }
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'
        file('src/main2/java/TestApp2.java') << 'public class TestApp2 {}'

        expect:
        fails 'assemble'
        failure.assertHasDescription('Circular dependency between the following tasks')

    }

    def "should fail if library doesn't exist"() {
        setup:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'someLib'
                    }
                }
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect: "build fails"
        fails 'assemble'
        failure.assertHasDescription("Could not resolve all dependencies for source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve dependency 'project : library someLib'")

        and: "displays the possible solution"
        failure.assertThatCause(matchesRegexp(".*Did you want to use 'main'.*"))
    }

    def "can resolve dependency on a different project library"() {
        setup:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        project ':dep' library 'main'
                    }
                }
            }
        }
    }

    tasks {
        assemble.finalizedBy('checkDependencies')
        create('checkDependencies') {
            assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).path.contains(':dep:createMainJar')
        }
    }
}
'''
        file('settings.gradle') << 'include "dep"'
        file('dep/build.gradle') << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec)
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'
        file('dep/src/main/java/Dep.java') << 'public class Dep {}'

        expect:
        succeeds 'assemble'

    }

    def "should fail if project doesn't exist" () {
        setup:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        project ':sub' library 'main'
                    }
                }
            }
        }
    }
}
'''
        file('settings.gradle') << 'include "dep"'
        file('dep/build.gradle') << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec)
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp/* extends Dep */{}'

        expect: "build fails"
        fails 'assemble'
        failure.assertHasDescription("Could not resolve all dependencies for source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve dependency 'project :sub library main'")

        and: "displays that the project doesn't exist"
        failure.assertThatCause(matchesRegexp(".*Project ':sub' not found.*"))
    }

    def "should fail if project exists but not library" () {
        setup:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        project ':dep' library 'doesNotExist'
                    }
                }
            }
        }
    }
}
'''
        file('settings.gradle') << 'include "dep"'
        file('dep/build.gradle') << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec)
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp/* extends Dep */{}'

        expect:
        fails 'assemble'
        failure.assertHasDescription("Could not resolve all dependencies for source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve dependency 'project :dep library doesNotExist'")

        and: "displays that the project doesn't exist"
        failure.assertThatCause(matchesRegexp(".*Did you want to use 'main'.*"))
    }

    def "should display the list of candidate libraries in case a library is not found" () {
        setup:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        project ':dep' library 'doesNotExist'
                    }
                }
            }
        }
    }
}
'''
        file('settings.gradle') << 'include "dep"'
        file('dep/build.gradle') << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        awesome(JvmLibrarySpec)
        lib(JvmLibrarySpec)
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp/* extends Dep */{}'

        expect:
        fails 'assemble'
        failure.assertHasDescription("Could not resolve all dependencies for source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve dependency 'project :dep library doesNotExist'")

        and: "displays that the project doesn't exist"
        failure.assertThatCause(matchesRegexp(".*Did you want to use one of 'awesome', 'lib'\\?.*"))
    }

    def "can resolve dependencies on a different projects"() {
        setup:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        other(JvmLibrarySpec)
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'other'
                        project ':dep' library 'main'
                    }
                }
            }
        }
    }

    tasks {
        assemble.finalizedBy('checkDependencies')
        create('checkDependencies') {
            assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).path.containsAll(
            [':createOtherJar',':dep:createMainJar'])
        }
    }
}
'''
        file('settings.gradle') << 'include "dep"'
        file('dep/build.gradle') << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec)
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep implements SomeInterface {}'
        file('src/other/java/Dep.java') << 'public class Dep {}'
        file('dep/src/main/java/SomeInterface.java') << 'public interface SomeInterface {}'

        expect:
        succeeds 'assemble'

    }

    def "should fail and display the list of candidate libraries in case a library is required but multiple candidates available" () {
        setup:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        project ':dep'
                    }
                }
            }
        }
    }
}
'''
        file('settings.gradle') << 'include "dep"'
        file('dep/build.gradle') << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        awesome(JvmLibrarySpec)
        lib(JvmLibrarySpec)
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp/* extends Dep */{}'

        expect:
        fails 'assemble'
        failure.assertHasDescription("Could not resolve all dependencies for source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve dependency 'project :dep library <default>'")

        and: "displays that the project doesn't exist"
        failure.assertThatCause(matchesRegexp(".*Project ':dep' contains more than one library. Please select one of 'awesome', 'lib'.*"))
    }

    def "should fail and display a sensible error message if target project doesn't define any library" () {
        setup:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        project ':dep'
                    }
                }
            }
        }
    }
}
'''
        file('settings.gradle') << 'include "dep"'
        file('dep/build.gradle') << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

'''
        file('src/main/java/TestApp.java') << 'public class TestApp/* extends Dep */{}'

        expect:
        fails 'assemble'
        failure.assertHasDescription("Could not resolve all dependencies for source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve dependency 'project :dep library <default>'")

        and: "displays that the project doesn't exist"
        failure.assertThatCause(matchesRegexp(".*Project ':dep' doesn't define any library..*"))
    }

   def "should fail and display a sensible error message if target project doesn't use new model" () {
        setup:
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        project ':dep'
                    }
                }
            }
        }
    }
}
'''
        file('settings.gradle') << 'include "dep"'
        file('dep/build.gradle') << ''

        file('src/main/java/TestApp.java') << 'public class TestApp/* extends Dep */{}'

        expect:
        fails 'assemble'
        failure.assertHasDescription("Could not resolve all dependencies for source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve dependency 'project :dep library <default>'")

        and:
        failure.assertThatCause(matchesRegexp(".*Project ':dep' doesn't define any library..*"))
    }

}
