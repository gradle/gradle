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

import spock.lang.Unroll

import static org.gradle.util.Matchers.containsText

class MultipleBinaryTypesWithVariantsTest extends VariantAwareDependencyResolutionSpec {

    @Unroll("Component A(#binaryTypeA) fails resolving on B(#binaryTypeB) because of incompatible variant types")
    def "binaries have the same variant dimension names but incompatible types"() {
        given:
        applyJavaPlugin(buildFile)
        addConflictingVariantTypesComponents(buildFile)
        buildFile << """
model {
    components {
        first($binaryTypeA) {

            sources {
                java(JavaSourceSet) {
                    dependencies {
                        library 'second'
                    }
                }
            }
        }

        second($binaryTypeB) {

            sources {
                java(JavaSourceSet)
            }
        }
    }

}
"""

        file('src/first/java/FirstApp.java') << 'public class FirstApp extends SecondApp {}'
        file('src/second/java/SecondApp.java') << 'public class SecondApp {}'

        when:
        succeeds ':tasks'

        then:
        executedAndNotSkipped ':tasks'

        when:
        fails ':firstJar'

        then:
        failure.assertThatCause(containsText(errorMessage))

        where:
        binaryTypeA             | binaryTypeB                    | errorMessage
        'StringBuildTypeLib'    | 'BuildTypeBuildTypeLib'        | "Required buildType 'default', available: 'default' but with an incompatible type (expected 'java.lang.String' was 'BuildType')"
        'BuildTypeBuildTypeLib' | 'StringBuildTypeLib'           | "Required buildType 'default', available: 'default' but with an incompatible type (expected 'BuildType' was 'java.lang.String')"
        'BuildTypeBuildTypeLib' | 'AnotherBuildTypeBuildTypeLib' | "Required buildType 'default', available: 'default' but with an incompatible type (expected 'BuildType' was 'AnotherBuildType')"

    }

    void addConflictingVariantTypesComponents(File buildFile) {
        buildFile << '''import org.gradle.jvm.internal.DefaultJarBinarySpec
import org.gradle.jvm.toolchain.JavaToolChainRegistry
import org.gradle.platform.base.internal.DefaultPlatformRequirement
import org.gradle.platform.base.internal.PlatformResolvers

interface BuildType extends Named {}

interface AnotherBuildType extends Named {}

class DefaultBuildType implements BuildType {
    String name
}

class DefaultAnotherBuildType implements AnotherBuildType {
    String name
}

trait BuildTypeAsStringJarBinarySpec implements JarBinarySpec {
    String buildType

    @Variant
    String getBuildType() { buildType }
}

trait BuildTypeAsBuildTypeJarBinarySpec implements JarBinarySpec {
    BuildType buildType

    @Variant
    BuildType getBuildType() { buildType }
}

trait BuildTypeAsAnotherBuildTypeJarBinarySpec implements JarBinarySpec {
    AnotherBuildType buildType

    @Variant
    AnotherBuildType getBuildType() { buildType }
}

class StringBinary extends DefaultJarBinarySpec implements BuildTypeAsStringJarBinarySpec {
    // workaround for Groovy bug
    JvmBinaryTasks getTasks() { super.tasks }
}

class BuildTypeBinary extends DefaultJarBinarySpec implements BuildTypeAsBuildTypeJarBinarySpec {
    // workaround for Groovy bug
    JvmBinaryTasks getTasks() { super.tasks }
}

class AnotherBuildTypeBinary extends DefaultJarBinarySpec implements BuildTypeAsAnotherBuildTypeJarBinarySpec {
    // workaround for Groovy bug
    JvmBinaryTasks getTasks() { super.tasks }
}

// define the 3 concrete library types
interface StringBuildTypeLib extends LibrarySpec {}

interface BuildTypeBuildTypeLib extends LibrarySpec {}

interface AnotherBuildTypeBuildTypeLib extends LibrarySpec {}

class DefaultStringBuildTypeLib extends BaseComponentSpec implements StringBuildTypeLib {}

class DefaultBuildTypeBuildTypeLib extends BaseComponentSpec implements BuildTypeBuildTypeLib {}

class DefaultAnotherBuildTypeBuildTypeLib extends BaseComponentSpec implements AnotherBuildTypeBuildTypeLib {}


class ComponentTypeRules extends RuleSource {

    @ComponentType
    void registerStringBuildTypeComponent(ComponentTypeBuilder<StringBuildTypeLib> builder) {
        builder.defaultImplementation(DefaultStringBuildTypeLib)
    }

    @ComponentType
    void registerBuildTypeBuildTypeComponent(ComponentTypeBuilder<BuildTypeBuildTypeLib> builder) {
        builder.defaultImplementation(DefaultBuildTypeBuildTypeLib)
    }

    @ComponentType
    void registerAnotherBuildTypeBuildTypeComponent(ComponentTypeBuilder<AnotherBuildTypeBuildTypeLib> builder) {
        builder.defaultImplementation(DefaultAnotherBuildTypeBuildTypeLib)
    }

    @BinaryType
    void registerStringBuildTypeJar(BinaryTypeBuilder<BuildTypeAsStringJarBinarySpec> builder) {
        builder.defaultImplementation(StringBinary)
    }

    @BinaryType
    void registerBuildTypeBuildTypeJar(BinaryTypeBuilder<BuildTypeAsBuildTypeJarBinarySpec> builder) {
        builder.defaultImplementation(BuildTypeBinary)
    }

    @BinaryType
    void registerAnotherBuildTypeBuildTypeJar(BinaryTypeBuilder<BuildTypeAsAnotherBuildTypeJarBinarySpec> builder) {
        builder.defaultImplementation(AnotherBuildTypeBinary)
    }

    private void createBinary(library, platforms, toolChains, binaries, jarBuildType) {

        def platform = platforms.resolve(JavaPlatform, DefaultPlatformRequirement.create("java${JavaVersion.current().majorVersion}"))
        def toolChain = toolChains.getForPlatform(platform)
        def baseName = "${library.name}"
        String binaryName = "${baseName}Jar"
        binaries.create(binaryName) { jar ->
            jar.toolChain = toolChain
            jar.targetPlatform = platform
            jar.buildType = jarBuildType
        }

    }

    @ComponentBinaries
    void createBuildTypeBinaries(ModelMap<BuildTypeAsBuildTypeJarBinarySpec> binaries,
                                 BuildTypeBuildTypeLib library,
                                 PlatformResolvers platforms,
                                 @Path("buildDir") File buildDir,
                                 JavaToolChainRegistry toolChains) {

        createBinary(library, platforms, toolChains, binaries, new DefaultBuildType(name:'default'))
    }

    @ComponentBinaries
    void createStringBinaries(ModelMap<BuildTypeAsStringJarBinarySpec> binaries,
                              StringBuildTypeLib library,
                              PlatformResolvers platforms,
                              @Path("buildDir") File buildDir,
                              JavaToolChainRegistry toolChains) {

        createBinary(library, platforms, toolChains, binaries, 'default')
    }

    @ComponentBinaries
    void createAnotherBuildTypeBinaries(ModelMap<BuildTypeAsAnotherBuildTypeJarBinarySpec> binaries,
                                        AnotherBuildTypeBuildTypeLib library,
                                        PlatformResolvers platforms,
                                        @Path("buildDir") File buildDir,
                                        JavaToolChainRegistry toolChains) {

        createBinary(library, platforms, toolChains, binaries, new DefaultAnotherBuildType(name:'default'))
    }
}

apply type: ComponentTypeRules
'''
    }

}
