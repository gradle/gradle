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

import static org.gradle.util.Matchers.containsText

abstract class VariantAwareDependencyResolutionSpec extends AbstractIntegrationSpec {
    protected static String generateCheckDependenciesDSLBlock(Map<String, String> selected, Closure loop) {
        def checkTasks = [:]
        def taskNames = []

        loop { taskName ->
            if (selected[taskName]) {
                def target = selected[taskName]
                checkTasks[taskName] = """
                $taskName {
                    doLast {
                        def t = $taskName
                        while (!(t instanceof PlatformJavaCompile)) {
                            t = t.taskDependencies.getDependencies(t)[0]
                        }
                        assert t.classpath.files == [file("\${buildDir}/jars/$target/second.jar")] as Set
                    }
                }
"""
            } else {
                taskNames << taskName
            }
        }

        if (checkTasks.keySet() != selected.keySet()) {
            throw new IllegalArgumentException("The following tasks are declared in the datatable 'selected' column but not found in the generated tasks: ${selected.keySet() - checkTasks.keySet()}. Possible solutions = $taskNames")
        }

        def tasksBlock = checkTasks ? """
            tasks {
                ${checkTasks.values().join('\n')}
            }
""" : ''
        tasksBlock
    }

    protected static void addCustomLibraryType(File buildFile) {
        buildFile << '''import org.gradle.jvm.internal.DefaultJarBinarySpec
import org.gradle.jvm.toolchain.JavaToolChainRegistry
import org.gradle.platform.base.internal.DefaultPlatformRequirement
import org.gradle.platform.base.internal.PlatformResolvers

trait JavaVersionsAware {
    List<Integer> javaVersions = []

    void javaVersions(int ... platforms) { javaVersions.addAll(platforms) }
}

trait FlavorAware {
    List<String> flavors = []

    void flavors(String... fvs) { flavors.addAll(fvs) }
}

trait BuildTypeAware {
    List<BuildType> buildTypes = []

    void buildTypes(String... bts) { buildTypes.addAll(bts.collect { new DefaultBuildType(name: it) }) }
}

// define the 3 types of libraries used in the tests: flavor only, build type only, and both (all of them include Java version)
trait FlavorOnlyLibrary implements LibrarySpec, JavaVersionsAware, FlavorAware {}

trait BuildTypeOnlyLibrary implements LibrarySpec, JavaVersionsAware, BuildTypeAware {}

trait FlavorAndBuildTypeAwareLibrary implements LibrarySpec, JavaVersionsAware, FlavorAware, BuildTypeAware {}

interface BuildType extends Named {}

class DefaultBuildType implements BuildType {
    String name
}

trait FlavorJarBinarySpec implements JarBinarySpec {
    String flavor

    @Variant
    String getFlavor() { flavor }
}

trait BuildTypeJarBinarySpec implements JarBinarySpec {
    BuildType buildType

    @Variant
    BuildType getBuildType() { buildType }
}

trait FlavorAndBuildTypeJarBinarySpec implements FlavorJarBinarySpec, BuildTypeJarBinarySpec {}

// define the 3 concrete binary types used in tests (flavor, build type and both)
class FlavorBinary extends DefaultJarBinarySpec implements FlavorJarBinarySpec {
    // workaround for Groovy bug
    JvmBinaryTasks getTasks() { super.tasks }
}

class BuildTypeBinary extends DefaultJarBinarySpec implements BuildTypeJarBinarySpec {
    // workaround for Groovy bug
    JvmBinaryTasks getTasks() { super.tasks }
}

class FlavorAndBuildTypeBinary extends DefaultJarBinarySpec implements FlavorAndBuildTypeJarBinarySpec {
    // workaround for Groovy bug
    JvmBinaryTasks getTasks() { super.tasks }
}

// define the 3 concrete library types
class DefaultFlavorOnlyLibrary extends BaseComponentSpec implements FlavorOnlyLibrary {}

class DefaultBuildTypeOnlyLibrary extends BaseComponentSpec implements BuildTypeOnlyLibrary {}

class DefaultFlavorAndBuildTypeAwareLibrary extends BaseComponentSpec implements FlavorAndBuildTypeAwareLibrary {}

class ComponentTypeRules extends RuleSource {

    @ComponentType
    void registerFlavorAndBuildTypeComponent(ComponentTypeBuilder<FlavorAndBuildTypeAwareLibrary> builder) {
        builder.defaultImplementation(DefaultFlavorAndBuildTypeAwareLibrary)
    }

    @ComponentType
    void registerFlavorOnlyComponent(ComponentTypeBuilder<FlavorOnlyLibrary> builder) {
        builder.defaultImplementation(DefaultFlavorOnlyLibrary)
    }

    @ComponentType
    void registerBuildTypeOnlyComponent(ComponentTypeBuilder<BuildTypeOnlyLibrary> builder) {
        builder.defaultImplementation(DefaultBuildTypeOnlyLibrary)
    }

    @BinaryType
    void registerFlavorAndBuildTypeJar(BinaryTypeBuilder<FlavorAndBuildTypeJarBinarySpec> builder) {
        builder.defaultImplementation(FlavorAndBuildTypeBinary)
    }

    @BinaryType
    void registerFlavorOnlyJar(BinaryTypeBuilder<FlavorJarBinarySpec> builder) {
        builder.defaultImplementation(FlavorBinary)
    }

    @BinaryType
    void registerBuildTypeOnlyJar(BinaryTypeBuilder<BuildTypeJarBinarySpec> builder) {
        builder.defaultImplementation(BuildTypeBinary)
    }

    @ComponentBinaries
    void createFlavorAndBuildTypeBinaries(ModelMap<FlavorAndBuildTypeJarBinarySpec> binaries,
                                          FlavorAndBuildTypeAwareLibrary library,
                                          PlatformResolvers platforms,
                                          @Path("buildDir") File buildDir,
                                          JavaToolChainRegistry toolChains) {

        def javaVersions = library.javaVersions ?: [JavaVersion.current().majorVersion]
        def flavors = library.flavors ?: ['default']
        def buildTypes = library.buildTypes ?: [new DefaultBuildType(name: 'default')]
        javaVersions.each { version ->
            flavors.each { flavor ->
                buildTypes.each { buildType ->
                    def platform = platforms.resolve(JavaPlatform, DefaultPlatformRequirement.create("java${version}"))
                    def toolChain = toolChains.getForPlatform(platform)
                    def baseName = "${library.name}${flavor.capitalize()}${buildType.name.capitalize()}"
                    String binaryName = "$baseName${javaVersions.size() > 1 ? version : ''}Jar"
                    binaries.create(binaryName) { jar ->
                        jar.toolChain = toolChain
                        jar.targetPlatform = platform
                        if (library.flavors) {
                            jar.flavor = flavor
                        }
                        if (library.buildTypes) {
                            jar.buildType = buildType
                        }
                    }
                }
            }
        }
    }

    @ComponentBinaries
    void createFlavorOnlyBinaries(ModelMap<FlavorJarBinarySpec> binaries,
                                  FlavorOnlyLibrary library,
                                  PlatformResolvers platforms,
                                  @Path("buildDir") File buildDir,
                                  JavaToolChainRegistry toolChains) {

        def javaVersions = library.javaVersions ?: [JavaVersion.current().majorVersion]
        def flavors = library.flavors ?: ['default']
        javaVersions.each { version ->
            flavors.each { flavor ->
                def platform = platforms.resolve(JavaPlatform, DefaultPlatformRequirement.create("java${version}"))
                def toolChain = toolChains.getForPlatform(platform)
                def baseName = "${library.name}${flavor.capitalize()}"
                String binaryName = "$baseName${javaVersions.size() > 1 ? version : ''}Jar"
                binaries.create(binaryName) { jar ->
                    jar.toolChain = toolChain
                    jar.targetPlatform = platform
                    if (library.flavors) {
                        jar.flavor = flavor
                    }
                }
            }
        }
    }

   @ComponentBinaries
    void createBuildTypeOnlyBinaries(ModelMap<BuildTypeJarBinarySpec> binaries,
                                  BuildTypeOnlyLibrary library,
                                  PlatformResolvers platforms,
                                  @Path("buildDir") File buildDir,
                                  JavaToolChainRegistry toolChains) {

        def javaVersions = library.javaVersions ?: [JavaVersion.current().majorVersion]
        def buildTypes = library.buildTypes ?: [ new DefaultBuildType(name:'default')]
        javaVersions.each { version ->
            buildTypes.each { buildType ->
                def platform = platforms.resolve(JavaPlatform, DefaultPlatformRequirement.create("java${version}"))
                def toolChain = toolChains.getForPlatform(platform)
                def baseName = "${library.name}${buildType.name.capitalize()}"
                String binaryName = "$baseName${javaVersions.size() > 1 ? version : ''}Jar"
                binaries.create(binaryName) { jar ->
                    jar.toolChain = toolChain
                    jar.targetPlatform = platform
                    if (library.buildTypes) {
                        jar.buildType = buildType
                    }
                }
            }
        }
    }

}

apply type: ComponentTypeRules
        '''
    }

    protected static void applyJavaPlugin(File buildFile) {
        buildFile << '''
plugins {
    id 'jvm-component'
    id 'java-lang'
}
'''
    }

    protected void checkResolution(Map<String, String> errors, Set<String> consumedErrors, String taskName) {
        if (errors[taskName]) {
            consumedErrors << taskName
            fails taskName
            failure.assertHasDescription("Could not resolve all dependencies for 'Jar '$taskName'' source set 'Java source 'first:java''")
            errors[taskName].each { err ->
                failure.assertThatCause(containsText(err))
            }
        } else {
            succeeds taskName
        }
    }

    protected static void forEachJavaBinary(List<Integer> platforms, Closure calledWithTaskName) {
        if (platforms.size() == 1) {
            calledWithTaskName 'firstJar'
        } else {
            platforms.each { platform ->
                calledWithTaskName "java${platform}FirstJar"
            }
        }
    }

    protected static void forEachFlavorAndBuildTypeBinary(List<String> buildTypesToTest, List<String> flavorsToTest, List<Integer> jdksToTest, Closure calledWithTaskName) {
        buildTypesToTest.each { buildType ->
            flavorsToTest.each { flavor ->
                jdksToTest.each { jdk ->
                    String javaVersion = jdksToTest.size() > 1 ? "$jdk" : ''
                    calledWithTaskName "first${flavor.capitalize()}${buildType.capitalize()}${javaVersion}Jar"
                }
            }
        }
    }

    protected static void forEachFlavor(List<String> flavorsToTest, List<Integer> jdksToTest, Closure calledWithTaskName) {
        flavorsToTest.each { flavor ->
            jdksToTest.each { jdk ->
                String javaVersion = jdksToTest.size() > 1 ? "$jdk" : ''
                calledWithTaskName "first${flavor.capitalize()}${javaVersion}Jar"
            }
        }
    }

    protected static String generateCheckDependenciesDSLBlockForCustomComponent(Map<String, String> selected, List<String> buildTypesToTest, List<String> flavorsToTest, List<Integer> jdksToTest) {
        generateCheckDependenciesDSLBlock(selected, this.&forEachFlavorAndBuildTypeBinary.curry(buildTypesToTest, flavorsToTest, jdksToTest))
    }

    protected static String generateCheckDependenciesDSLBlockForJavaLibrary(Map<String, String> selected, List<Integer> jdksToTest) {
        generateCheckDependenciesDSLBlock(selected, this.&forEachJavaBinary.curry(jdksToTest))
    }

    protected static String generateCheckDependenciesDSLBlockForFlavorLibrary(Map<String, String> selected, List<String> flavors, List<Integer> jdksToTest) {
        generateCheckDependenciesDSLBlock(selected, this.&forEachFlavor.curry(flavors, jdksToTest))
    }


}
