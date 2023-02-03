/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.language.cpp

import org.gradle.language.VariantContext
import org.gradle.language.nativeplatform.internal.Dimensions
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.ExecutableFixture
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.language.VariantContext.dimensions

abstract class AbstractCppPublishingIntegrationTest extends AbstractInstalledToolChainIntegrationSpec implements CppTaskNames {

    abstract int getVariantCount(List<Map<String,String>> targetMachines)
    abstract List<String> getLinkages()
    abstract List<String> getMainModuleArtifacts(String module, String version)
    abstract List<String> getVariantModuleArtifacts(String variantModuleNameWithVersion)
    abstract TestFile getVariantSourceFile(String module, VariantContext variantContext)
    abstract Map<String, String> getVariantFileInformation(String linkage, String module, String variantModuleNameWithVersion)
    abstract boolean publishesArtifactForLinkage(String linkage)

    void assertMainModuleIsPublished(String group, String module, String version, List<Map<String, String>> targetMachines, List<String> apiDependencies = []) {
        def mainModule = mavenRepo.module(group, module, version)
        mainModule.assertArtifactsPublished(getMainModuleArtifacts(module, version))
        assert mainModule.parsedPom.scopes.size() == apiDependencies.isEmpty() ? 0 : 1
        if (!apiDependencies.isEmpty()) {
            mainModule.parsedPom.scopes.compile.assertDependsOn(apiDependencies as String[])
        }

        def mainMetadata = mainModule.parsedModuleMetadata

        if (!apiDependencies.isEmpty()) {
            def mainApi = mainMetadata.variant("api")
            mainApi.dependencies.size() == apiDependencies.size()
            apiDependencies.eachWithIndex { dependency, index ->
                def coordinates = dependency.split(':')
                assert mainApi.dependencies[index].group == coordinates[0]
                assert mainApi.dependencies[index].module == coordinates[1]
                assert mainApi.dependencies[index].version == coordinates[2]
            }
        }

        assert mainMetadata.variants.size() == getVariantCount(targetMachines)
        ['debug', 'release'].each { buildType ->
            linkages.each { linkage ->
                targetMachines.each { machine ->
                    String architectureNormalized = Dimensions.createDimensionSuffix(machine.architecture, targetMachines.collect { it.architecture }.unique())
                    String osFamilyNormalized = Dimensions.createDimensionSuffix(machine.os, targetMachines.collect { it.os }.unique())
                    assert mainMetadata.variant("${buildType}${osFamilyNormalized.capitalize()}${architectureNormalized.capitalize()}${linkage.capitalize()}").availableAt.coords == "${group}:${module}_${buildType}${osFamilyNormalized.empty ? "" : "_${osFamilyNormalized.toLowerCase()}"}${architectureNormalized.empty ? "" : "_${architectureNormalized.toLowerCase().replace("-", "_")}"}:${version}"
                }
            }
        }
    }

    void assertVariantIsPublished(String group, String module, String version, VariantContext variantContext, List<String> dependencies = []) {
        String variantModuleName = "${module}${variantContext.asPublishName}"
        String variantModuleNameWithVersion = "${variantModuleName}-${version}"
        def publishedModule = mavenRepo.module(group, variantModuleName, version)
        publishedModule.assertPublished()
        publishedModule.assertArtifactsPublished(getVariantModuleArtifacts(variantModuleNameWithVersion))
        publishedModule.artifactFile(type: getVariantFileInformation('Runtime', module, variantModuleNameWithVersion).extension).assertIsCopyOf(getVariantSourceFile(module, variantContext))

        assert publishedModule.parsedPom.scopes.size() == dependencies.isEmpty() ? 0 : 1
        if (!dependencies.isEmpty()) {
            publishedModule.parsedPom.scopes.runtime.assertDependsOn(dependencies as String[])
        }

        def publishedMetadata = publishedModule.parsedModuleMetadata
        assert publishedMetadata.variants.size() == linkages.size()
        linkages.each { linkage ->
            def publishedVariant = publishedMetadata.variant("${variantContext.asVariantName}${linkage}")
            assert publishedVariant.dependencies.size() == dependencies.size()
            publishedVariant.dependencies.eachWithIndex { dependency, int i ->
                assert dependency.coords == dependencies[i]
            }

            if (publishesArtifactForLinkage(linkage)) {
                def variantFileInfo = getVariantFileInformation(linkage, module, variantModuleNameWithVersion)
                assert publishedVariant.files.size() == 1
                assert publishedVariant.files[0].name == variantFileInfo.name
                assert publishedVariant.files[0].url == variantFileInfo.url
            }
        }
    }

    void assertVariantsArePublished(String group, String module, String version, List<String> buildTypes, List<Map<String, String>> targetMachines, List<String> dependencies = []) {
        VariantContext.from(dimensions("buildType", buildTypes), targetMachines).findAll { it.os.name == currentOsFamilyName }.each { variantContext ->
            assertVariantIsPublished(group, module, version, variantContext, dependencies)
        }
    }

    @Override
    ExecutableFixture executable(Object path) {
        ExecutableFixture executable = super.executable(path)
        // Executables synced from a binary repo lose their executable bit
        executable.file.setExecutable(true)
        executable
    }

    Map<String, String> machine(String os, String architecture) {
        return new LinkedHashMap<String, String>(["os": os, "architecture": architecture])
    }
}
