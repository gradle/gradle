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
package org.gradle.ide.cdt.model

import org.gradle.api.Incubating
import org.gradle.api.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject

/**
 * The actual .cproject descriptor file.
 */
@Incubating
class CprojectDescriptor extends XmlPersistableConfigurationObject {

    private static final boolean LINUX_NOT_MACOS = true
    
    static public final String GNU_COMPILER_TOOL_ID_PREFIX = "cdt.managedbuild.tool.gnu.cpp.compiler"
    static public final String GNU_COMPILER_TOOL_INCLUDE_PATHS_OPTION_PREFIX = "gnu.cpp.compiler.option.include.paths"

    // linux
    static public final String GNU_LINKER_TOOL_ID_PREFIX = LINUX_NOT_MACOS ? "cdt.managedbuild.tool.gnu.cpp.linker" : "cdt.managedbuild.tool.macosx.cpp.linker.macosx"
    static public final String GNU_LINKER_TOOL_LIBS_PATHS_OPTION_PREFIX = LINUX_NOT_MACOS ? "gnu.cpp.link.option.userobjs" : "macosx.cpp.link.option.userobjs"

    CprojectDescriptor() {
        super(new XmlTransformer())
    }

    protected String getDefaultResourceName() {
        LINUX_NOT_MACOS ? 'defaultCproject-linux.xml' : 'defaultCproject-macos.xml'
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

    NodeList getRootCppLinkerTools() {
        new NodeList(rootToolChains.tool.findAll { isGnuLinkerTool(it) })
    }

    boolean isGnuCompilerTool(Node node) {
        node.name() == "tool" && node.@id.startsWith(GNU_COMPILER_TOOL_ID_PREFIX)
    }

    boolean isGnuLinkerTool(Node node) {
        node.name() == "tool" && node.@id.startsWith(GNU_LINKER_TOOL_ID_PREFIX)
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

    Node getOrCreateLibsOption(linkerToolNode) {
        if (!isGnuLinkerTool(linkerToolNode)) {
            throw new IllegalArgumentException("Arg must be a gnu linker tool def, was $linkerToolNode")
        }

        def libsOption = linkerToolNode.option.find { it.@id.startsWith(GNU_LINKER_TOOL_LIBS_PATHS_OPTION_PREFIX) }
        if (!libsOption) {
            libsOption = linkerToolNode.appendNode(
                "option", [
                    id: createId(GNU_LINKER_TOOL_LIBS_PATHS_OPTION_PREFIX),
                    superClass: GNU_LINKER_TOOL_LIBS_PATHS_OPTION_PREFIX,
                    valueType: "userObjs"
                ]
            )
        }

        libsOption
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