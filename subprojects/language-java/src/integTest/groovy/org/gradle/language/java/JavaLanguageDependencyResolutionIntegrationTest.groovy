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

class JavaLanguageDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {

    def "can resolve dependency on local library"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
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
        mainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(zdepJar)
        }
    }
}
'''
        file('src/zdep/java/Dep.java') << 'public class Dep {}'
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'

        when:
        succeeds ':mainJar'

        then:
        executedAndNotSkipped ':createZdepJar'

    }

    def "can define a dependency on the same library"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
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

        when:
        succeeds ':mainJar'

        then:
        executedAndNotSkipped(':createMainJar',':mainJar')

    }

    def "can define a cyclic dependency"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
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
        fails ':mainJar'
        failure.assertHasDescription('Circular dependency between the following tasks')

    }

    def "should fail if library doesn't exist"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
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

        when: "build fails"
        fails ':mainJar'

        then: "displays the possible solution"
        failure.assertHasDescription("Could not resolve all dependencies for 'Jar 'mainJar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':' library 'someLib'")
        failure.assertHasCause("Project ':' does not contain library 'someLib'. Did you want to use 'main'?")

    }

    def "can resolve dependency on a different project library"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''

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
        mainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).path.contains(':dep:mainJar')
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

        when:
        succeeds ':mainJar'

        then:
        executedAndNotSkipped ':dep:createMainJar'

    }

    def "should fail if project doesn't exist" () {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
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

        when: "build fails"
        fails ':mainJar'

        then:
        failure.assertHasDescription("Could not resolve all dependencies for 'Jar 'mainJar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':sub' library 'main'")
        failure.assertHasCause("Project ':sub' not found.")

    }

    def "should fail if project exists but not library" () {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
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

        when:
        fails ':mainJar'

        then:
        failure.assertHasDescription("Could not resolve all dependencies for 'Jar 'mainJar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':dep' library 'doesNotExist'")

        and: "displays a suggestion about the library to use"
        failure.assertHasCause("Project ':dep' does not contain library 'doesNotExist'. Did you want to use 'main'?")
    }

    def "should display the list of candidate libraries in case a library is not found" () {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
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

        when:
        fails ':mainJar'

        then:
        failure.assertHasDescription("Could not resolve all dependencies for 'Jar 'mainJar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':dep' library 'doesNotExist'")

        and: "displays a list of suggestion for libraries to use"
        failure.assertHasCause("Project ':dep' does not contain library 'doesNotExist'. Did you want to use one of 'awesome', 'lib'?")
    }

    def "can resolve dependencies on a different projects"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
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
        mainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).path.containsAll(
            [':otherJar',':dep:mainJar'])
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

        when:
        succeeds ':mainJar'

        then:
        executedAndNotSkipped ':dep:createMainJar', ':createOtherJar'

    }

    def "should fail and display the list of candidate libraries in case a library is required but multiple candidates available" () {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
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

        when:
        fails ':mainJar'

        then:
        failure.assertHasDescription("Could not resolve all dependencies for 'Jar 'mainJar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':dep'")

        and: "displays a list of suggestions for libraries in dependent project"
        failure.assertHasCause("Project ':dep' contains more than one library. Please select one of 'awesome', 'lib'")
    }

    def "should fail and display a sensible error message if target project doesn't define any library" () {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
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

        when:
        fails ':mainJar'

        then:
        failure.assertHasDescription("Could not resolve all dependencies for 'Jar 'mainJar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':dep'")

        and: "displays that the dependent project doesn't define any dependency"
        failure.assertHasCause("Project ':dep' doesn't define any library.")
    }

   def "should fail and display a sensible error message if target project doesn't use new model" () {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
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

        when:
        fails ':mainJar'

        then:
        failure.assertHasDescription("Could not resolve all dependencies for 'Jar 'mainJar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':dep'")

        and:
        failure.assertHasCause("Project ':dep' doesn't define any library.")
    }

    def "classpath for sourceset excludes transitive sourceset jar"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        project ':b' library 'main'
                    }
                }
            }
        }
    }

    tasks {
        create('checkClasspath') {
            doLast {
                def cp = compileMainJarMainJava.classpath.files
                assert cp.contains(project(':b').createMainJar.archivePath)
                assert !cp.contains(project(':c').createMainJar.archivePath)
            }
        }
        mainJar.finalizedBy('checkClasspath')
    }

}
'''
        file('settings.gradle') << 'include "b","c"'
        file('b/build.gradle') << '''
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
                        project ':c' library 'main'
                    }
                }
            }
        }
    }
}
'''
        file('c/build.gradle') << '''
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
        file('b/src/main/java/Dep.java') << 'public class Dep { void someMethod(Deeper deeper) {} }'
        file('c/src/main/java/Deeper.java') << 'public class Deeper {}'

        when:
        succeeds ':mainJar'

        then:
        executedAndNotSkipped ':c:createMainJar',':b:createMainJar'

    }

    def "dependency resolution should be limited to the scope of the API of a single project"() {
        given: "project 'a' depending on project 'b' depending itself on project 'c' but 'c' doesn't exist"
        applyJavaPlugin(buildFile)
        buildFile << '''
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.type.ModelType

class DependencyResolutionObserver extends RuleSource {
    @Mutate void createCheckTask(CollectionBuilder<Task> tasks) {
        tasks.create('checkDependenciesForMainJar') {
        doLast {
            def task = tasks.get('compileMainJarMainJava')
            def cp = task.classpath.files
            assert cp == [task.project.project(':b').modelRegistry.find(ModelPath.path('tasks.createMainJar'), ModelType.of(Task)).archivePath] as Set

        }
        }
    }
}
apply plugin: DependencyResolutionObserver

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        project ':b' library 'main'
                    }
                }
            }
        }
    }

}
'''
        file('settings.gradle') << 'include "b"'
        file('b/build.gradle') << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}

import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.type.ModelType

class DependencyResolutionObserver extends RuleSource {
    @Mutate void createCheckTask(CollectionBuilder<Task> tasks) {
        tasks.create('checkDependenciesForMainJar') {
        doLast {
            def task = tasks.get('compileMainJarMainJava')
            task.classpath.files
        }
        }
    }
}
apply plugin: DependencyResolutionObserver

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        project ':c' library 'main'
                    }
                }
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'
        file('b/src/main/java/Dep.java') << 'public class Dep {}'

        when: "we query the classpath for project 'a' library 'main'"
        succeeds ':checkDependenciesForMainJar'

        then: "dependency resolution resolves the classpath"
        executedAndNotSkipped ':checkDependenciesForMainJar'

        when: "we query the classpath for project 'b' library 'main'"
        fails ':b:checkDependenciesForMainJar'

        then: "dependency resolution fails because project 'c' doesn't exist"
        failure.assertHasCause(/Could not resolve all dependencies for 'Jar 'mainJar'' source set 'Java source 'main:java''/)
        failure.assertHasCause(/Could not resolve project ':c' library 'main'./)
        failure.assertHasCause(/Project ':c' not found./)

    }

    def "classpath for sourceset excludes transitive sourceset jar if no explicit library name is used"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        project ':b'
                    }
                }
            }
        }
    }

    tasks {
        create('checkClasspath') {
            doLast {
                def cp = compileMainJarMainJava.classpath.files
                assert cp.contains(project(':b').createMainJar.archivePath)
                assert !cp.contains(project(':c').createMainJar.archivePath)
            }
        }
        mainJar.finalizedBy('checkClasspath')
    }

}
'''
        file('settings.gradle') << 'include "b","c"'
        file('b/build.gradle') << '''
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
                        project ':c'
                    }
                }
            }
        }
    }
}
'''
        file('c/build.gradle') << '''
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
        file('b/src/main/java/Dep.java') << 'public class Dep { void someMethod(Deeper deeper) {} }'
        file('c/src/main/java/Deeper.java') << 'public class Deeper {}'

        when:
        succeeds ':mainJar'

        then:
        executedAndNotSkipped ':c:createMainJar', ':b:createMainJar'

    }

    def "should fail resolution if more than one binary is available"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        zdep(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
        }

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
}
'''
        file('src/zdep/java/Dep.java') << 'public class Dep {}'
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'

        when:
        fails ':mainJar'

        then:
        failure.assertHasCause("Multiple binaries available for library 'zdep'")
    }

    def "fails if a dependency is not a JvmLibrarySpec library"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        zdep(CustomLibrary)
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
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        when:
        fails ':mainJar'

        then:
        failure.assertHasDescription("Could not resolve all dependencies for 'Jar 'mainJar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':' library 'zdep'")

        and:
        failure.assertHasCause("Project ':' contains a library named 'zdep' but it is not a JvmLibrarySpec")
    }

    def "successfully selects a JVM library if no library name is provided and 2 components are available"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {

        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        project ':b'
                    }
                }
            }
        }
    }
}
'''
        def projectB = file('b/build.gradle')
        applyJavaPlugin(projectB)
        addCustomLibraryType(projectB)
        projectB << '''
model {
    components {
        main(JvmLibrarySpec)
        other(CustomLibrary)
    }
}
'''
        settingsFile << /include 'b'/

        file('b/src/main/java/Dep.java') << 'public class Dep {}'
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'

        when:
        succeeds ':mainJar'

        then:
        executedAndNotSkipped(':b:createMainJar')
    }

    void applyJavaPlugin(File buildFile) {
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}
'''
    }

    void addCustomLibraryType(File buildFile) {
        buildFile << """
            interface CustomLibrary extends LibrarySpec {}
            class DefaultCustomLibrary extends BaseComponentSpec implements CustomLibrary {}

            class ComponentTypeRules extends RuleSource {
                @ComponentType
                void registerCustomComponentType(ComponentTypeBuilder<CustomLibrary> builder) {
                    builder.defaultImplementation(DefaultCustomLibrary)
                }
            }

            apply type: ComponentTypeRules
        """
    }
}
