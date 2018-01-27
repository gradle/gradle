/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.ide.visualstudio.internal

import spock.lang.Specification

import static org.gradle.ide.visualstudio.internal.VisualStudioTargetBinary.ProjectType.*

class VisualStudioProjectMapperTest extends Specification {
    def "maps executable binary types to visual studio project"() {
        when:
        def targetBinary = targetBinary(componentName: "exeName", projectType: EXE)

        then:
        checkNames targetBinary, "exeNameExe", 'buildTypeOne'
    }

    def "maps library binary types to visual studio projects"() {
        when:
        def targetBinary = targetBinary(componentName: "libName", projectType: type)

        then:
        checkNames targetBinary, exepectedName, 'buildTypeOne'

        where:
        type | exepectedName
        DLL  | "libNameDll"
        LIB  | "libNameLib"
    }

    def "includes project path in visual studio project name"() {
        when:
        def targetBinary = targetBinary(projectPath: ":subproject:name")

        then:
        checkNames targetBinary, "subproject_name_exeNameExe", 'buildTypeOne'
    }

    def "uses single variant dimension for configuration name where not empty"() {
        when:
        def targetBinary = targetBinary(variantDimensions: ["flavorOne"])

        then:
        checkNames targetBinary, "exeNameExe", 'flavorOne'
    }

    def "includes variant dimensions in configuration where component has multiple dimensions"() {
        when:
        def targetBinary = targetBinary(variantDimensions: ["platformOne", "buildTypeOne", "flavorOne"])

        then:
        checkNames targetBinary, "exeNameExe", 'platformOneBuildTypeOneFlavorOne'
    }

    private VisualStudioTargetBinary targetBinary(Map<String, ?> values) {
        VisualStudioTargetBinary targetBinary = Mock(VisualStudioTargetBinary)
        targetBinary.projectPath >> values.get("projectPath", ":")
        targetBinary.componentName >> values.get("componentName", "exeName")
        targetBinary.variantDimensions >> values.get("variantDimensions", ['buildTypeOne'])
        targetBinary.projectType >> values.get("projectType", EXE)
        return targetBinary
    }

    private static checkNames(VisualStudioTargetBinary binary, def projectName, def configurationName) {
        assert VisualStudioProjectMapper.getProjectName(binary) == projectName
        assert VisualStudioProjectMapper.getConfigurationName(binary.getVariantDimensions()) == configurationName
        true
    }
}
