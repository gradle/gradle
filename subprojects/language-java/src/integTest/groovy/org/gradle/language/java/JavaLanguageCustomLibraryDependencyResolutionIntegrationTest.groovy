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

class JavaLanguageCustomLibraryDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {

    def "can depend on a custom component producing a JVM library"() {
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

    tasks {
        mainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(zdepJar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        succeeds ':mainJar'

    }

    @Unroll
    def "can depend on a custom component producing a JVM library in another project with dependency {#dependency}"() {
        given:
        applyJavaPlugin(buildFile)
        file('settings.gradle') << 'include "sub"'

        def subBuildFile = file('sub/build.gradle')
        subBuildFile << '''
plugins {
    id 'jvm-component'
}
'''
        addCustomLibraryType(subBuildFile)
        subBuildFile << '''
model {
    components {
        zdep(CustomLibrary)
    }
}
'''
        buildFile << """

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        $dependency
                    }
                }
            }
        }
    }

    tasks {
        mainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).path.contains(':sub:zdepJar')
            }
        }
    }
}
"""
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        succeeds ':mainJar'

        where:
        dependency << ["project ':sub' library 'zdep'","project ':sub'"]
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "can depend on a custom component producing a JVM library with corresponding platform"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        zdep(CustomLibrary) {
            targetPlatform 'java6'
            targetPlatform 'java7'
        }
        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
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
        java6MainJar.finalizedBy('checkDependencies')
        java7MainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileJava6MainJarMainJava.taskDependencies.getDependencies(compileJava6MainJarMainJava).contains(zdepJava6Jar)
                assert compileJava7MainJarMainJava.taskDependencies.getDependencies(compileJava7MainJarMainJava).contains(zdepJava7Jar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        succeeds ':java6MainJar'
        succeeds ':java7MainJar'

    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "should fail resolving dependencies only for the missing dependency variant"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        zdep(CustomLibrary) {
            targetPlatform 'java7'
        }
        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
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
        java7MainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileJava7MainJarMainJava.taskDependencies.getDependencies(compileJava7MainJarMainJava).contains(zdepJar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect: 'The Java 7 variant of the main jar can be built'
        succeeds ':java7MainJar'

        and: 'the Java 6 variant fails'
        fails ':java6MainJar'

        and: 'error message indicates the available platforms for the target dependency'
        failure.assertHasDescription("Could not resolve all dependencies for 'Jar 'java6MainJar'' source set 'Java source 'main:java''")
        failure.assertHasCause("Cannot find a compatible binary for library 'zdep' (Java SE 6). Available platforms: [Java SE 7]")

    }


    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "should choose the highest variant of the target binary when dependency is a JVM component"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        zdep(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
        }
        main(CustomLibrary) {
            targetPlatform 'java7'
            sources {
                java(JavaSourceSet) {
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
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(java7ZdepJar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        succeeds ':mainJar'
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "should choose the highest variant of the target binary when dependency is a custom component"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        zdep(CustomLibrary) {
            targetPlatform 'java6'
            targetPlatform 'java7'
        }
        main(JvmLibrarySpec) {
            targetPlatform 'java7'
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
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(zdepJava7Jar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        succeeds ':mainJar'

    }

    def "custom component can consume a JVM library"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        main(JvmLibrarySpec)
        zdep(CustomLibrary) {
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'main'
                    }
                }
            }
        }
    }

    tasks {
        zdepJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileZdepJarZdepJava.taskDependencies.getDependencies(compileZdepJarZdepJava).contains(mainJar)
            }
        }
    }
}
'''
        file('src/zdep/java/App.java') << 'public class App extends TestApp {}'
        file('src/main/java/TestApp.java') << 'public class TestApp {}'

        expect:
        succeeds ':zdepJar'

    }

    def "Java consumes custom component consuming Java component"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'third'
                    }
                }
            }
        }
        third(JvmLibrarySpec)
    }

    tasks {
        mainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(secondJar)
                assert compileSecondJarSecondJava.taskDependencies.getDependencies(compileSecondJarSecondJava).contains(thirdJar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp { void dependsOn(SecondApp app) {} }'
        file('src/second/java/SecondApp.java') << 'public class SecondApp { void dependsOn(ThirdApp app) {}  }'
        file('src/third/java/ThirdApp.java') << 'public class ThirdApp {}'

        expect:
        succeeds ':mainJar'
    }

    def "Custom consumes Java component consuming custom component"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        main(CustomLibrary) {
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'third'
                    }
                }
            }
        }
        third(CustomLibrary) {
            sources {
                java(JavaSourceSet)
            }
        }
    }

    tasks {
        mainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(secondJar)
                assert compileSecondJarSecondJava.taskDependencies.getDependencies(compileSecondJarSecondJava).contains(thirdJar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp { void dependsOn(SecondApp app) {} }'
        file('src/second/java/SecondApp.java') << 'public class SecondApp { void dependsOn(ThirdApp app) {}  }'
        file('src/third/java/ThirdApp.java') << 'public class ThirdApp {}'

        expect:
        succeeds ':mainJar'
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "Cannot build all variants of main component because of missing dependency variant"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            targetPlatform 'java7'
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'third'
                    }
                }
            }
        }
        third(JvmLibrarySpec) {
            targetPlatform 'java7'
        }
    }

    tasks {
        java7MainJar.finalizedBy('checkDependencies')
        create('checkDependencies') {
            doLast {
                assert compileJava7MainJarMainJava.taskDependencies.getDependencies(compileJava7MainJarMainJava).contains(secondJar)
                assert compileSecondJarSecondJava.taskDependencies.getDependencies(compileSecondJarSecondJava).contains(thirdJar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp { void dependsOn(SecondApp app) {} }'
        file('src/second/java/SecondApp.java') << 'public class SecondApp { void dependsOn(ThirdApp app) {}  }'
        file('src/third/java/ThirdApp.java') << 'public class ThirdApp {}'

        expect: "Can resolve dependencies and compile the Java 7 variant of the main Jar"
        succeeds ':java7MainJar'

        and: "Can resolve dependencies and compile any of the dependencies"
        succeeds ':secondJar'
        succeeds ':thirdJar'

        and: "Trying to compile the Java 6 variant fails"
        fails ':java6MainJar'
        failure.assertHasDescription("Could not resolve all dependencies for 'Jar 'java6MainJar'' source set 'Java source 'main:java''")
        failure.assertHasCause("Cannot find a compatible binary for library 'second' (Java SE 6). Available platforms: [Java SE 7]")
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "Not all components target the same Java platforms"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'third'
                    }
                }
            }
        }
        third(JvmLibrarySpec) {
            targetPlatform 'java7'
        }
    }

    tasks {
        create('checkMainDependencies') {
            doLast {
                assert compileJava7MainJarMainJava.taskDependencies.getDependencies(compileJava7MainJarMainJava).contains(secondJava7Jar)
                assert compileJava6MainJarMainJava.taskDependencies.getDependencies(compileJava6MainJarMainJava).contains(secondJava6Jar)
            }
        }
        create('checkSecondJava7VariantDependencies') {
            doLast {
                assert compileSecondJava7JarSecondJava.taskDependencies.getDependencies(compileSecondJava7JarSecondJava).contains(thirdJar)
            }
        }
        create('checkSecondJava6VariantDependencies') {
            doLast {
                assert compileSecondJava6JarSecondJava.taskDependencies.getDependencies(compileSecondJava6JarSecondJava)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp { void dependsOn(SecondApp app) {} }'
        file('src/second/java/SecondApp.java') << 'public class SecondApp { void dependsOn(ThirdApp app) {}  }'
        file('src/third/java/ThirdApp.java') << 'public class ThirdApp {}'

        expect: "Can resolve dependencies of the Java 6 and Java 7 variant of the main Jar"
        succeeds ':checkMainDependencies'

        and: "Resolving the dependencies and compiling the Java 7 variant of the second jar should work"
        succeeds ':checkSecondJava7VariantDependencies'
        succeeds ':secondJava7Jar'

        and: "Resolving the dependencies of the Java 6 version of the second jar should fail"
        fails ':checkSecondJava6VariantDependencies'
        failure.assertHasCause("Could not resolve all dependencies for 'Jar 'secondJava6Jar'' source set 'Java source 'second:java''")
        failure.assertHasCause("Cannot find a compatible binary for library 'third' (Java SE 6). Available platforms: [Java SE 7]")

        and: "Can build the Java 7 variant of all components"
        succeeds ':java7MainJar'
        succeeds ':secondJava7Jar'
        succeeds ':thirdJar'
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "All components should depend on the corresponding variants"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'third'
                    }
                }
            }
        }
        third(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
        }
    }

    tasks {
        create('checkMainDependencies') {
            doLast {
                assert compileJava7MainJarMainJava.taskDependencies.getDependencies(compileJava7MainJarMainJava).contains(secondJava7Jar)
                assert compileJava6MainJarMainJava.taskDependencies.getDependencies(compileJava6MainJarMainJava).contains(secondJava6Jar)
            }
        }
        create('checkSecondDependencies') {
            doLast {
                assert compileSecondJava7JarSecondJava.taskDependencies.getDependencies(compileSecondJava7JarSecondJava).contains(java7ThirdJar)
                assert compileSecondJava6JarSecondJava.taskDependencies.getDependencies(compileSecondJava6JarSecondJava).contains(java6ThirdJar)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp { void dependsOn(SecondApp app) {} }'
        file('src/second/java/SecondApp.java') << 'public class SecondApp { void dependsOn(ThirdApp app) {}  }'
        file('src/third/java/ThirdApp.java') << 'public class ThirdApp {}'

        expect: "Can resolve dependencies of the Java 6 and Java 7 variant of the main and second components"
        succeeds ':checkMainDependencies'
        succeeds ':checkSecondDependencies'

        and: "Can build the Java 7 variant of all components"
        succeeds ':java7MainJar'
        succeeds ':secondJava7Jar'
        succeeds ':java7ThirdJar'

        and: "Can build the Java 6 variant of all components"
        succeeds ':java6MainJar'
        succeeds ':secondJava6Jar'
        succeeds ':java6ThirdJar'
    }

    def "can define a cyclic dependency"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        main(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'third'
                    }
                }
            }
        }
        third(JvmLibrarySpec) {
            sources {
                java {
                    dependencies {
                        library 'main'
                    }
                }
            }
        }
    }

    tasks {
        create('checkDependencies') {
            doLast {
                assert compileMainJarMainJava.taskDependencies.getDependencies(compileMainJarMainJava).contains(secondJar)
                assert compileSecondJarSecondJava.taskDependencies.getDependencies(compileSecondJarSecondJava).contains(thirdJar)
                assert compileThirdJarThirdJava.taskDependencies.getDependencies(compileThirdJarThirdJava).contains(mainJar)
            }
        }
    }
}'''

        file('src/main/java/TestApp.java') << 'public class TestApp { void dependsOn(SecondApp app) {} }'
        file('src/second/java/SecondApp.java') << 'public class SecondApp { void dependsOn(ThirdApp app) {}  }'
        file('src/third/java/ThirdApp.java') << 'public class ThirdApp { void dependsOn(TestApp app) {} }'

        expect: "Can resolve the dependencies for each component"
        succeeds ':checkDependencies'

        and: 'building fails'
        fails ':mainJar'
        failure.assertHasDescription 'Circular dependency between the following tasks:'
    }

    @Requires(TestPrecondition.JDK7_OR_LATER)
    def "Fails if one of the dependencies provides more than one binary for the selected variant"() {
        given:
        applyJavaPlugin(buildFile)
        addCustomLibraryType(buildFile)

        buildFile << '''

model {
    components {
        main(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }
        second(CustomLibrary) {
            // duplication is intentional!
            targetPlatform 'java6'
            targetPlatform 'java6'
            targetPlatform 'java7'
            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'third'
                    }
                }
            }
        }
        third(JvmLibrarySpec) {
            targetPlatform 'java6'
            targetPlatform 'java7'
        }
    }

    tasks {
        create('checkJava7Dependencies') {
            doLast {
                assert compileJava7MainJarMainJava.taskDependencies.getDependencies(compileJava7MainJarMainJava).contains(secondJava7Jar)
                assert compileSecondJava7JarSecondJava.taskDependencies.getDependencies(compileSecondJava7JarSecondJava).contains(java7ThirdJar)
            }
        }
        create('checkMainJava6Dependencies') {
            doLast {
                compileJava6MainJarMainJava.taskDependencies.getDependencies(compileJava6MainJarMainJava)
            }
        }
    }
}
'''
        file('src/main/java/TestApp.java') << 'public class TestApp { void dependsOn(SecondApp app) {} }'
        file('src/second/java/SecondApp.java') << 'public class SecondApp { void dependsOn(ThirdApp app) {}  }'
        file('src/third/java/ThirdApp.java') << 'public class ThirdApp {}'

        expect: "Can resolve dependencies of the Java 7 variant of the main and second components"
        succeeds ':checkJava7Dependencies'

        and: "Fails resolving the dependencies of the Java 6 variant of the main component"
        fails ':checkMainJava6Dependencies'
        failure.assertHasCause "Could not resolve all dependencies for 'Jar 'java6MainJar'' source set 'Java source 'main:java''"
        failure.assertHasCause "Multiple binaries available for library 'second' (Java SE 6) : [Jar 'secondJava6Jar', Jar 'secondJava6Jarx']"

        and: "Can build the Java 7 variant of all components"
        succeeds ':java7MainJar'
        succeeds ':secondJava7Jar'
        succeeds ':java7ThirdJar'
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
        buildFile << '''
import org.gradle.internal.service.ServiceRegistry
import org.gradle.jvm.internal.DefaultJarBinarySpec
import org.gradle.platform.base.internal.PlatformResolvers
import org.gradle.jvm.toolchain.JavaToolChainRegistry
import org.gradle.jvm.platform.internal.DefaultJavaPlatform
import org.gradle.platform.base.internal.DefaultPlatformRequirement

interface CustomLibrary extends LibrarySpec {
    void targetPlatform(String platform)
    List<String> getTargetPlatforms()
}

class DefaultCustomLibrary extends BaseComponentSpec implements CustomLibrary {
    List<String> targetPlatforms = []
    void targetPlatform(String platform) { targetPlatforms << platform }
}

            class ComponentTypeRules extends RuleSource {

                @ComponentType
                void registerCustomComponentType(ComponentTypeBuilder<CustomLibrary> builder) {
                    builder.defaultImplementation(DefaultCustomLibrary)
                }

                @ComponentBinaries
                void createBinaries(ModelMap<JarBinarySpec> binaries,
                    CustomLibrary jvmLibrary,
                    PlatformResolvers platforms,
                    @Path("buildDir") File buildDir,
                    JavaToolChainRegistry toolChains) {

                    def binariesDir = new File(buildDir, "jars")
                    def classesDir = new File(buildDir, "classes")
                    def targetPlatforms = jvmLibrary.targetPlatforms
                    def selectedPlatforms = targetPlatforms.collect { platforms.resolve(JavaPlatform, DefaultPlatformRequirement.create(it)) }?:[new DefaultJavaPlatform(JavaVersion.current())]
                    def multipleTargets = selectedPlatforms.size()>1
                    selectedPlatforms.each { platform ->
                        def toolChain = toolChains.getForPlatform(platform)
                        String binaryName = "${jvmLibrary.name}${multipleTargets?platform.name.capitalize():''}Jar"
                        while (binaries.containsKey(binaryName)) { binaryName = "${binaryName}x" }
                        binaries.create(binaryName) { jar ->
                            jar.baseName = jvmLibrary.name
                            jar.toolChain = toolChain
                            jar.targetPlatform = platform

                            def outputDir = new File(classesDir, jar.name)
                            jar.classesDir = outputDir
                            jar.resourcesDir = outputDir
                            jar.jarFile = new File(binariesDir, "${jar.name}/${jar.baseName}.jar")
                        }
                    }
                }

            }

            apply type: ComponentTypeRules
        '''
    }
}
