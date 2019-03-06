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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

import static org.gradle.language.java.JavaIntegrationTesting.applyJavaPlugin
import static org.gradle.util.TextUtil.normaliseLineSeparators

class JavaLanguageDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {

    @Unroll
    def "can resolve #scope level dependency on local library"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << """
model {
    components {
        zdep(JvmLibrarySpec)
        main(JvmLibrarySpec) {
            ${scope.declarationFor 'zdep'}
        }
    }
}
"""
        file('src/zdep/java/Dep.java') << 'public class Dep {}'
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'

        when:
        succeeds ':tasks', ':mainJar'

        then:
        executedAndNotSkipped ':tasks', ':compileZdepJarZdepJava', ':zdepApiJar', ':compileMainJarMainJava', ':mainApiJar', ':mainJar'

        where:
        scope << DependencyScope.values()
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
        succeeds ':tasks', ':mainJar'

        then:
        executedAndNotSkipped(':tasks', ':createMainJar', ':mainJar')
    }

    def "can define a cyclic dependency but building fails"() {
        given: "a build file that defines a cyclic dependency"
        applyJavaPlugin(buildFile)
        buildFile << '''
class DependencyResolutionObserver extends RuleSource {
    @Validate
    void checkThatCyclicDependencyIsDefined(ModelMap<Task> tasks) {
        def mainJar =  tasks.get('compileMainJarMainJava')
        def main2Jar = tasks.get('compileMain2JarMain2Java')
    }
}

apply plugin: DependencyResolutionObserver

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

        when: 'Try to compile main Jar'
        fails ':mainJar'

        then: 'A cyclic dependency is found'
        failure.assertHasDescription('Circular dependency between the following tasks')
    }

    @Unroll
    def "should fail if library doesn't exist (#scope)"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << """
model {
    components {
        main(JvmLibrarySpec) {
            ${scope.declarationFor 'someLib'}
        }
    }
}
"""
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and: "build fails"
        fails ':mainJar'

        then: "displays the possible solution"
        failure.assertHasCause("Could not resolve all dependencies for 'Jar 'main:jar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':' library 'someLib'")
        failure.assertHasCause("Project ':' does not contain library 'someLib'. Did you want to use 'main'?")

        where:
        scope << DependencyScope.values()
    }

    @Unroll
    def "can resolve #scope level dependency on a different project library"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << """
model {
    components {
        main(JvmLibrarySpec) {
            ${scope.declarationFor('main', ':dep')}
        }
    }

    tasks {
        mainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).path.contains(':dep:mainApiJar')
        }
    }
}
"""

        file('settings.gradle') << 'include "dep"'
        def depBuildFile = file('dep/build.gradle')
        applyJavaPlugin(depBuildFile)
        depBuildFile << '''
model {
    components {
        main(JvmLibrarySpec)
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'
        file('dep/src/main/java/Dep.java') << 'public class Dep {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and:
        succeeds ':mainJar'

        then:
        executedAndNotSkipped ':dep:mainApiJar'

        where:
        scope << DependencyScope.values()
    }

    def "should fail if project doesn't exist"() {
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
        def depBuildFile = file('dep/build.gradle')
        applyJavaPlugin(depBuildFile)
        depBuildFile << '''
model {
    components {
        main(JvmLibrarySpec)
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp/* extends Dep */{}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and: "build fails"
        fails ':mainJar'

        then:
        failure.assertHasCause("Could not resolve all dependencies for 'Jar 'main:jar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':sub' library 'main'")
        failure.assertHasCause("Project ':sub' not found.")
    }

    @Unroll
    def "should fail if project exists but not library (#scope)"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << """
model {
    components {
        main(JvmLibrarySpec) {
            $scope.begin
                project ':dep' library 'doesNotExist'
            $scope.end
        }
    }
}
"""
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
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and:
        fails ':mainJar'

        then:
        failure.assertHasCause("Could not resolve all dependencies for 'Jar 'main:jar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':dep' library 'doesNotExist'")

        and: "displays a suggestion about the library to use"
        failure.assertHasCause("Project ':dep' does not contain library 'doesNotExist'. Did you want to use 'main'?")

        where:
        scope << DependencyScope.values()
    }

    @Unroll
    def "should display the list of candidate libraries in case a library is not found (#scope)"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << """
model {
    components {
        main(JvmLibrarySpec) {
            $scope.begin
                project ':dep' library 'doesNotExist'
            $scope.end
        }
    }
}
"""
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
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and:
        fails ':mainJar'

        then:
        failure.assertHasCause("Could not resolve all dependencies for 'Jar 'main:jar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':dep' library 'doesNotExist'")

        and: "displays a list of suggestion for libraries to use"
        failure.assertHasCause("Project ':dep' does not contain library 'doesNotExist'. Did you want to use one of 'awesome', 'lib'?")

        where:
        scope << DependencyScope.values()
    }

    @Unroll
    def "can resolve #scope level dependencies on a different projects"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << """
model {
    components {
        other(JvmLibrarySpec)
        main(JvmLibrarySpec) {
            $scope.begin
                library 'other'
                project ':dep' library 'main'
            $scope.end
        }
    }

    tasks {
        mainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).path.containsAll(
            [':otherApiJar',':dep:mainApiJar'])
        }
    }
}
"""
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
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and:
        succeeds ':mainJar'

        then:
        executedAndNotSkipped ':dep:mainApiJar', ':otherApiJar'

        where:
        scope << DependencyScope.values()
    }

    @Unroll
    def "should fail and display the list of candidate libraries in case a library is required but multiple candidates available (#scope)"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << """
model {
    components {
        main(JvmLibrarySpec) {
            $scope.begin
                project ':dep'
            $scope.end
        }
    }
}
"""
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
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and:
        fails ':mainJar'

        then:
        failure.assertHasCause("Could not resolve all dependencies for 'Jar 'main:jar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':dep'")

        and: "displays a list of suggestions for libraries in dependent project"
        failure.assertHasCause("Project ':dep' contains more than one library. Please select one of 'awesome', 'lib'")

        where:
        scope << DependencyScope.values()
    }

    def "should fail and display a sensible error message if target project doesn't define any library"() {
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
        applyJavaPlugin(file('dep/build.gradle'))
        file('src/main/java/TestApp.java') << 'public class TestApp/* extends Dep */{}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and:
        fails ':mainJar'

        then:
        failure.assertHasCause("Could not resolve all dependencies for 'Jar 'main:jar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':dep'")

        and: "displays that the dependent project doesn't define any dependency"
        failure.assertHasCause("Project ':dep' doesn't define any library.")
    }

    def "should fail and display a sensible error message if target project doesn't use new model"() {
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
        file('dep/build.gradle') << "apply plugin: 'java'"
        file('src/main/java/TestApp.java') << 'public class TestApp/* extends Dep */{}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and:
        fails ':mainJar'

        then:
        failure.assertHasCause("Could not resolve all dependencies for 'Jar 'main:jar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':dep'")

        and:
        failure.assertHasCause("Project ':dep' doesn't define any library.")
    }

    @Unroll
    def "compile classpath for #mainScope dependency #excludesOrIncludes transitive #libScope dependency"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << """
            model {
                components {
                    main(JvmLibrarySpec) {
                        $mainScope.begin
                            project ':b' library 'main'
                        $mainScope.end
                    }
                }

                tasks {
                    create('checkClasspath') {
                        doLast {
                            def cp = compileMainJarMainJava.classpath.files
                            assert cp.contains(project(':b').mainApiJar.outputFile)
                            def cJar = project(':c').mainApiJar.outputFile
                            assert ${excludesOrIncludes == 'excludes' ? '!' : ''}cp.contains(cJar)
                        }
                    }
                    mainJar.finalizedBy('checkClasspath')
                }

            }
        """
        file('settings.gradle') << 'include "b","c"'
        file('b/build.gradle').with {
            applyJavaPlugin(it)
            it << """
                model {
                    components {
                        main(JvmLibrarySpec) {
                            $libScope.begin
                                project ':c' library 'main'
                            $libScope.end
                        }
                    }
                }
            """
        }
        file('c/build.gradle').with {
            applyJavaPlugin(it)
            it << '''
                model {
                    components {
                        main(JvmLibrarySpec)
                    }
                }
            '''
        }
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'
        file('b/src/main/java/Dep.java') << 'public class Dep { void someMethod(Deeper deeper) {} }'
        file('c/src/main/java/Deeper.java') << 'public class Deeper {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and:
        succeeds ':mainJar'

        then:
        executedAndNotSkipped ':c:mainApiJar', ':b:mainApiJar'

        where:
        mainScope                 | libScope
        DependencyScope.SOURCES   | DependencyScope.SOURCES
        DependencyScope.SOURCES   | DependencyScope.COMPONENT
        DependencyScope.SOURCES   | DependencyScope.API
        DependencyScope.COMPONENT | DependencyScope.SOURCES
        DependencyScope.COMPONENT | DependencyScope.COMPONENT
        DependencyScope.COMPONENT | DependencyScope.API
        DependencyScope.API       | DependencyScope.SOURCES
        DependencyScope.API       | DependencyScope.COMPONENT
        DependencyScope.API       | DependencyScope.API

        excludesOrIncludes = libScope == DependencyScope.API ? 'includes' : 'excludes'
    }

    def "dependency resolution should be limited to the scope of the API of a single project"() {
        given: "project 'a' depending on project 'b' depending itself on project 'c' but 'c' doesn't exist"
        applyJavaPlugin(buildFile)
        buildFile << '''
import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.type.ModelType

class DependencyResolutionObserver extends RuleSource {
    @Mutate void createCheckTask(ModelMap<Task> tasks) {
        tasks.create('checkDependenciesForMainJar') {
            doLast {
                def task = tasks.get('compileMainJarMainJava')
                def cp = task.classpath.files
                assert cp == [task.project.project(':b').tasks.mainApiJar.outputFile] as Set
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
    @Mutate void createCheckTask(ModelMap<Task> tasks) {
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

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and: "we query the classpath for project 'a' library 'main'"
        succeeds ':checkDependenciesForMainJar'

        then: "dependency resolution resolves the classpath"
        executedAndNotSkipped ':checkDependenciesForMainJar'

        when: "we query the classpath for project 'b' library 'main'"
        fails ':b:checkDependenciesForMainJar'

        then: "dependency resolution fails because project 'c' doesn't exist"
        failure.assertHasCause(/Could not resolve all dependencies for 'Jar 'main:jar'' source set 'Java source 'main:java''/)
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
                assert cp.contains(project(':b').mainApiJar.outputFile)
                assert !cp.contains(project(':c').mainApiJar.outputFile)
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
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and:
        succeeds ':mainJar'

        then:
        executedAndNotSkipped ':c:mainApiJar', ':b:mainApiJar'
    }

    def "fails if a dependency does not provide any JarBinarySpec"() {
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
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        when:
        fails ':mainJar'

        then:
        failure.assertHasCause("Could not resolve all dependencies for 'Jar 'main:jar'' source set 'Java source 'main:java'")
        failure.assertHasCause("Could not resolve project ':' library 'zdep'")

        and:
        failure.assertHasCause("Project ':' contains a library named 'zdep' but it doesn't have any binary of type JvmBinarySpec")
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
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and:
        succeeds ':mainJar'

        then:
        executedAndNotSkipped(':b:mainApiJar')
    }

    @Unroll
    def "should choose appropriate Java variants for #scope level dependency"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << """
model {
    components {
        dep(JvmLibrarySpec) {
            targetPlatform 'java7'
        }

        main(JvmLibrarySpec) {
            targetPlatform 'java8'
            targetPlatform 'java7'
            $scope.begin
                library 'dep'
            $scope.end
        }
    }

    tasks {
        mainJava7Jar.finalizedBy('checkDependencies')
        mainJava8Jar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            assert compileMainJava7JarMainJava.taskDependencies.getDependencies(compileMainJava7JarMainJava).contains(depApiJar)
            assert compileMainJava8JarMainJava.taskDependencies.getDependencies(compileMainJava8JarMainJava).contains(depApiJar)
        }
    }
}
"""
        file('src/dep/java/Dep.java') << 'public class Dep {}'
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and:
        succeeds 'mainJava7Jar'

        and:
        succeeds 'mainJava8Jar'

        where:
        scope << DependencyScope.values()
    }

    def "should fail because multiple binaries match for the same variant"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''

class CustomBinaries extends RuleSource {
   @ComponentBinaries
   void createBinaries(ModelMap<JarBinarySpec> binaries, JvmLibrarySpec spec) {
       // duplicate binaries, to make sure we have two binaries for the same platform
       def newBins = [:]
       binaries.keySet().each { bName ->
           def binary = binaries.get(bName)
           newBins["${bName}2"] = binary
       }

       newBins.each { k,v -> binaries.create(k) {
            targetPlatform = v.targetPlatform
            toolChain = v.toolChain
            jarFile = v.jarFile
       }}
    }
}

apply plugin: CustomBinaries

model {
    components {
        dep(JvmLibrarySpec) {
            targetPlatform 'java6'
        }

        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            sources {
                java {
                    dependencies {
                        library 'dep'
                    }
                }
            }
        }
    }
}
'''
        file('src/dep/java/Dep.java') << 'public class Dep {}'
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and:
        fails ':mainJar'

        then:
        failure.assertHasCause("Could not resolve all dependencies for 'Jar 'main:jar'' source set 'Java source 'main:java'")
        failure.assertHasCause(normaliseLineSeparators("""Multiple compatible variants found for library 'dep':
    - Jar 'dep:jar' [platform:'java6']
    - Jar 'dep:jar2' [platform:'java6']"""
        ))
    }

    def "should display reasonable error messages in case of multiple binaries available or no compatible variant is found"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''

class CustomBinaries extends RuleSource {
   @ComponentBinaries
   void createBinaries(ModelMap<JarBinarySpec> binaries, JvmLibrarySpec spec) {
       // duplicate binaries, to make sure we have two binaries for the same platform
       if (spec.name != 'dep') { return }
       def newBins = [:]
       binaries.keySet().each { bName ->
          def binary = binaries.get(bName)
          newBins["${bName}2"] = binary
       }

       newBins.each { k,v -> binaries.create(k) {
            targetPlatform = v.targetPlatform
            toolChain = v.toolChain
            jarFile = v.jarFile
       }}
    }
}

apply plugin: CustomBinaries

model {
    components {
        dep(JvmLibrarySpec) {
            targetPlatform 'java6'
        }

        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'dep'
                    }
                }
            }
        }
    }
}
'''
        file('src/dep/java/Dep.java') << 'public class Dep {}'
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and: "attempt to build main jar Java 6"
        fails ':mainJava6Jar'

        then: "fails because multiple binaries are available for the Java 6 variant of 'dep'"
        failure.assertHasCause("Could not resolve all dependencies for 'Jar 'main:java6Jar'' source set 'Java source 'main:java'")
        failure.assertHasCause(normaliseLineSeparators("""Multiple compatible variants found for library 'dep':
    - Jar 'dep:jar' [platform:'java6']
    - Jar 'dep:jar2' [platform:'java6']"""
        ))

        when: "attempt to build main jar Java 7"
        fails ':mainJava7Jar'

        then: "fails because multiple binaries are available for the Java 6 compatible variant of 'dep'"
        failure.assertHasCause("Could not resolve all dependencies for 'Jar 'main:java7Jar'' source set 'Java source 'main:java'")
        failure.assertHasCause(normaliseLineSeparators("""Multiple compatible variants found for library 'dep':
    - Jar 'dep:jar' [platform:'java6']
    - Jar 'dep:jar2' [platform:'java6']"""
        ))
    }

    @Unroll
    def "should choose matching variants from #scope level dependency"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << """
model {
    components {
        dep(JvmLibrarySpec) {
            targetPlatform 'java7'
            targetPlatform 'java8'
        }

        main(JvmLibrarySpec) {
            targetPlatform 'java7'
            targetPlatform 'java8'
            $scope.begin
                library 'dep'
            $scope.end
        }
    }

    tasks {
        mainJava7Jar.finalizedBy('checkDependencies')
        mainJava8Jar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            assert compileMainJava7JarMainJava.taskDependencies.getDependencies(compileMainJava7JarMainJava).contains(depJava7ApiJar)
            assert compileMainJava8JarMainJava.taskDependencies.getDependencies(compileMainJava8JarMainJava).contains(depJava8ApiJar)
        }
    }
}
"""
        file('src/dep/java/Dep.java') << 'public class Dep {}'
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and:
        succeeds 'mainJava7Jar', 'mainJava8Jar'

        where:
        scope << DependencyScope.values()
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    def "should not choose higher version than available"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        dep(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            targetPlatform 'java8'
        }

        main(JvmLibrarySpec) {
            targetPlatform 'java7'
            targetPlatform 'java6'
            sources {
                java {
                    dependencies {
                        library 'dep'
                    }
                }
            }
        }
    }

    tasks {
        mainJava6Jar.finalizedBy('checkDependencies')
        mainJava7Jar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            assert compileMainJava6JarMainJava.taskDependencies.getDependencies(compileMainJava6JarMainJava).contains(depJava6ApiJar)
            assert compileMainJava7JarMainJava.taskDependencies.getDependencies(compileMainJava7JarMainJava).contains(depJava7ApiJar)
        }
    }
}
'''
        file('src/dep/java/Dep.java') << 'public class Dep {}'
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        then:
        succeeds 'mainJava6Jar'

        and:
        succeeds 'mainJava7Jar'
    }

    def "should display candidate platforms if no one matches"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        dep(JvmLibrarySpec) {
            targetPlatform 'java7'
        }

        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            sources {
                java {
                    dependencies {
                        library 'dep'
                    }
                }
            }
        }
    }
}
'''
        file('src/dep/java/Dep.java') << 'public class Dep {}'
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and:
        fails 'mainJar'

        then:
        failure.assertHasCause(normaliseLineSeparators(
                "Cannot find a compatible variant for library 'dep'.\n" +
                        "    Required platform 'java6', available: 'java7'"
        ))
    }

    @Requires(TestPrecondition.JDK8_OR_LATER)
    def "should display candidate platforms if no one matches and multiple binaries are defined"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        dep(JvmLibrarySpec) {
            targetPlatform 'java7'
            targetPlatform 'java8'
        }

        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'dep'
                    }
                }
            }
        }
    }
}
'''
        file('src/dep/java/Dep.java') << 'public class Dep {}'
        file('src/main/java/TestApp.java') << 'public class TestApp extends Dep {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and:
        fails ':mainJava6Jar'

        then:
        failure.assertHasCause(normaliseLineSeparators(
                "Cannot find a compatible variant for library 'dep'.\n" +
                        "    Required platform 'java6', available: 'java7', 'java8'"
        ))
    }

    void addCustomLibraryType(File buildFile) {
        buildFile << """
            @Managed interface CustomLibrary extends LibrarySpec {}

            class ComponentTypeRules extends RuleSource {
                @ComponentType
                void registerCustomComponentType(TypeBuilder<CustomLibrary> builder) {}
            }

            apply type: ComponentTypeRules
        """
    }

    @Unroll
    def "collects all errors if there's more than one resolution failure for #scope level dependencies"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << """
model {
    components {
        main(JvmLibrarySpec) {
            $scope.begin
                library 'someLib' // first error
                project ':b' // second error
                project ':c' library 'foo' // third error
            $scope.end
        }
    }
}
"""
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        and: "build fails"
        fails ':mainJar'

        then: "displays a reasonable error message indicating the faulty source set"
        failure.assertHasCause("Could not resolve all dependencies for 'Jar 'main:jar'' source set 'Java source 'main:java'")

        and: "first resolution error is displayed"
        failure.assertHasCause("Could not resolve project ':' library 'someLib'")
        failure.assertHasCause("Project ':' does not contain library 'someLib'. Did you want to use 'main'?")

        and: "second resolution error is displayed"
        failure.assertHasCause("Could not resolve project ':b'")
        failure.assertHasCause("Project ':b' not found")

        and: "third resolution error is displayed"
        failure.assertHasCause("Could not resolve project ':c' library 'foo'")
        failure.assertHasCause("Project ':c' not found")

        where:
        scope << DependencyScope.values()
    }
}
