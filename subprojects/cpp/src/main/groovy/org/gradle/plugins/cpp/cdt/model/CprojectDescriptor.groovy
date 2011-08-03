/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.cpp.cdt.model

import org.gradle.api.internal.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject

/**
 * The actual .cproject descriptor file.
 */
class CprojectDescriptor extends XmlPersistableConfigurationObject {

    static public final String GNU_COMPILER_TOOL_ID_PREFIX = "cdt.managedbuild.tool.gnu.cpp.compiler"
    static public final String GNU_COMPILER_TOOL_INCLUDE_PATHS_OPTION_PREFIX = "gnu.cpp.compiler.option.include.paths"
    CprojectDescriptor() {
        super(new XmlTransformer())
    }

    protected String getDefaultResourceName() {
        'defaultCproject.xml'
    }

    NodeList getConfigurations() {
        new NodeList(xml.storageModule.cconfiguration.storageModule.findAll { it.@moduleId == "cdtBuildSystem" }.collect { it.configuration[0] })
    }

    NodeList getRootToolChains() {
        new NodeList(configurations.folderInfo.findAll { it.@resourcePath == "" }).toolChain
    }

    NodeList getRootCppCompilerTools() {
        new NodeList(rootToolChains.tool.findAll { isGnuCompilerTool(it) })
    }

    boolean isGnuCompilerTool(Node node) {
        node.name() == "tool" && node.@id.startsWith(GNU_COMPILER_TOOL_ID_PREFIX)
    }

    Node getOrCreateIncludePathsOption(compilerToolNode) {
        if (!isGnuCompilerTool(compilerToolNode)) {
            throw new IllegalArgumentException("Arg must be a gnu compiler tool def, was $compilerToolNode")
        }

        def includePathsOption = compilerToolNode.option.find { it.@id.startsWith(GNU_COMPILER_TOOL_INCLUDE_PATHS_OPTION_PREFIX) }
        if (!includePathsOption) {
            includePathsOption = compilerToolNode.appendNode(
                "option", [
                    id: createId(GNU_COMPILER_TOOL_INCLUDE_PATHS_OPTION_PREFIX),
                    superClass: GNU_COMPILER_TOOL_INCLUDE_PATHS_OPTION_PREFIX,
                    valueType: "includePath"
                ]
            )
        }

        includePathsOption
    }

    String createId(String prefix) {
        prefix + "." + new java.text.SimpleDateFormat("yyMMddHHmmssS").format(new Date())
    }

    protected void store(Node xml) {
        transformAction {
            StringBuilder xmlString = it.asString()
            xmlString.insert(xmlString.indexOf("\n") + 1, "<?fileVersion 4.0.0?>\n")
        }
    }
}