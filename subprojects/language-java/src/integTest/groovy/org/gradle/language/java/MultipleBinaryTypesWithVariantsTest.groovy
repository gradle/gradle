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

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import spock.lang.Unroll

import static org.gradle.language.java.JavaIntegrationTesting.applyJavaPlugin
import static org.gradle.language.java.JavaIntegrationTesting.expectJavaLangPluginDeprecationWarnings
import static org.gradle.util.Matchers.containsText

class MultipleBinaryTypesWithVariantsTest extends VariantAwareDependencyResolutionSpec {

    @Unroll("Component A(#binaryTypeA) fails resolving on B(#binaryTypeB) because of incompatible variant types")
    @ToBeFixedForInstantExecution
    def "binaries have the same variant dimension names but incompatible types"() {
        given:
        applyJavaPlugin(buildFile, executer)
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
        expectJavaLangPluginDeprecationWarnings(executer)
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
}

class BuildTypeBinary extends DefaultJarBinarySpec implements BuildTypeAsBuildTypeJarBinarySpec {
}

class AnotherBuildTypeBinary extends DefaultJarBinarySpec implements BuildTypeAsAnotherBuildTypeJarBinarySpec {
}

// define the 3 concrete library types
@Managed interface StringBuildTypeLib extends LibrarySpec {}

@Managed interface BuildTypeBuildTypeLib extends LibrarySpec {}

@Managed interface AnotherBuildTypeBuildTypeLib extends LibrarySpec {}

class ComponentTypeRules extends RuleSource {

    @ComponentType
    void registerStringBuildTypeComponent(TypeBuilder<StringBuildTypeLib> builder) {
    }

    @ComponentType
    void registerBuildTypeBuildTypeComponent(TypeBuilder<BuildTypeBuildTypeLib> builder) {
    }

    @ComponentType
    void registerAnotherBuildTypeBuildTypeComponent(TypeBuilder<AnotherBuildTypeBuildTypeLib> builder) {
    }

    @ComponentType
    void registerStringBuildTypeJar(TypeBuilder<BuildTypeAsStringJarBinarySpec> builder) {
        builder.defaultImplementation(StringBinary)
    }

    @ComponentType
    void registerBuildTypeBuildTypeJar(TypeBuilder<BuildTypeAsBuildTypeJarBinarySpec> builder) {
        builder.defaultImplementation(BuildTypeBinary)
    }

    @ComponentType
    void registerAnotherBuildTypeBuildTypeJar(TypeBuilder<BuildTypeAsAnotherBuildTypeJarBinarySpec> builder) {
        builder.defaultImplementation(AnotherBuildTypeBinary)
    }

    private void createBinary(library, platforms, toolChains, binaries, jarBuildType) {

        def platform = platforms.resolve(JavaPlatform, DefaultPlatformRequirement.create("java${JavaVersion.current().majorVersion}"))
        def toolChain = toolChains.getForPlatform(platform)
        binaries.create('jar') { jar ->
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
