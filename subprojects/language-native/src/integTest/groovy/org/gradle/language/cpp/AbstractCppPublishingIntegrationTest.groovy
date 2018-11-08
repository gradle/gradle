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

import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec
import org.gradle.nativeplatform.fixtures.ExecutableFixture
import org.gradle.test.fixtures.file.TestFile

abstract class AbstractCppPublishingIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {

    abstract int getVariantCount(List<Map<String,String>> targetMachines)
    abstract List<String> getLinkages()
    abstract List<String> getMainModuleArtifacts(String module, String version)
    abstract List<String> getVariantModuleArtifacts(String variantModuleNameWithVersion)
    abstract TestFile getVariantSourceFile(String module, String buildType, Map<String, String> targetMachine)
    abstract Map<String, String> getVariantFileInformation(String linkage, String module, String variantModuleNameWithVersion)
    abstract boolean publishesArtifactForLinkage(String linkage)

    void assertMainModuleIsPublished(String group, String module, String version, List<Map<String, String>> targetMachines, List<String> apiDependencies = []) {
        def mainModule = mavenRepo.module(group, module, version)
        mainModule.assertArtifactsPublished(getMainModuleArtifacts(module, version))
        assert mainModule.parsedPom.scopes.size() == apiDependencies.isEmpty() ? 0 : 1
        if (!apiDependencies.isEmpty()) {
            mainModule.parsedPom.scopes.runtime.assertDependsOn(apiDependencies as String[])
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
                    String normalizedArchitecture = machine.architecture.replace('-', '_')
                    String osFamilyNormalized = machine.os.toLowerCase()
                    assert mainMetadata.variant("${buildType}${osFamilyNormalized.capitalize()}${machine.architecture.capitalize()}${linkage}").availableAt.coords == "${group}:${module}_${buildType}_${osFamilyNormalized}_${normalizedArchitecture}:${version}"
                }
            }
        }
    }

    void assertVariantIsPublished(String group, String module, String version, String buildType, Map<String, String> targetMachine, List<String> dependencies = []) {
        String normalizedArchitecture = targetMachine.architecture.replace('-', '_')
        String variantModuleName = "${module}_${buildType}_${targetMachine.os.toLowerCase()}_${normalizedArchitecture}"
        String variantModuleNameWithVersion = "${variantModuleName}-${version}"
        def publishedModule = mavenRepo.module(group, variantModuleName, version)
        publishedModule.assertPublished()
        publishedModule.assertArtifactsPublished(getVariantModuleArtifacts(variantModuleNameWithVersion))
        publishedModule.artifactFile(type: getVariantFileInformation('Runtime', module, variantModuleNameWithVersion).extension).assertIsCopyOf(getVariantSourceFile(module, buildType, targetMachine))

        assert publishedModule.parsedPom.scopes.size() == dependencies.isEmpty() ? 0 : 1
        if (!dependencies.isEmpty()) {
            publishedModule.parsedPom.scopes.runtime.assertDependsOn(dependencies as String[])
        }

        def publishedMetadata = publishedModule.parsedModuleMetadata
        assert publishedMetadata.variants.size() == linkages.size()
        linkages.each { linkage ->
            def publishedVariant = publishedMetadata.variant("${buildType}${targetMachine.os.toLowerCase().capitalize()}${targetMachine.architecture.capitalize()}${linkage}")
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
        buildTypes.each { buildType ->
            targetMachines.each { machine ->
                assertVariantIsPublished(group, module, version, buildType, machine, dependencies)
            }
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
        return ["os": os, "architecture": architecture]
    }
}
