/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.ide.cdt.model;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import groovy.lang.Closure;
import groovy.util.Node;
import groovy.util.NodeList;
import org.gradle.api.Incubating;
import org.gradle.api.XmlProvider;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The actual .cproject descriptor file.
 */
@Incubating
public class CprojectDescriptor extends XmlPersistableConfigurationObject {

    private static final boolean LINUX_NOT_MACOS = true;
    public static final String GNU_COMPILER_TOOL_ID_PREFIX = "cdt.managedbuild.tool.gnu.cpp.compiler";
    public static final String GNU_COMPILER_TOOL_INCLUDE_PATHS_OPTION_PREFIX = "gnu.cpp.compiler.option.include.paths";
    public static final String GNU_LINKER_TOOL_ID_PREFIX = LINUX_NOT_MACOS ? "cdt.managedbuild.tool.gnu.cpp.linker" : "cdt.managedbuild.tool.macosx.cpp.linker.macosx";
    public static final String GNU_LINKER_TOOL_LIBS_PATHS_OPTION_PREFIX = LINUX_NOT_MACOS ? "gnu.cpp.link.option.userobjs" : "macosx.cpp.link.option.userobjs";

    public CprojectDescriptor() {
        super(new XmlTransformer());
    }

    protected String getDefaultResourceName() {
        return LINUX_NOT_MACOS ? "defaultCproject-linux.xml" : "defaultCproject-macos.xml";
    }

    public NodeList getConfigurations() {
        Node storageModule = findFirstChildNamed(getXml(), "storageModule");
        Node cconfiguration = findFirstChildNamed(storageModule, "cconfiguration");
        List<Node> storageModules = getChildren(cconfiguration, "storageModule");
        Iterable<Node> cdtModules = Iterables.filter(storageModules, new Predicate<Node>() {
            @Override
            public boolean apply(Node module) {
                return module.attributes().get("moduleId").equals("cdtBuildSystem");
            }
        });
        Iterable<Node> cdtConfigurations = Iterables.transform(cdtModules, new Function<Node, Node>() {
            @Override
            public Node apply(Node module) {
                return findFirstChildNamed(module, "configuration");
            }
        });
        return new NodeList(Lists.newArrayList(cdtConfigurations));
    }

    @SuppressWarnings("unchecked")
    public NodeList getRootToolChains() {
        List<Node> toolChains = Lists.newArrayList();
        for (Node configuration : (List<Node>) getConfigurations()) {
            List<Node> folderInfo = getChildren(configuration, "folderInfo");
            for (Node folder : folderInfo) {
                if (folder.attributes().get("resourcePath").equals("")) {
                    toolChains.add(findFirstChildNamed(folder, "toolChain"));
                }
            }
        }
        return new NodeList(toolChains);
    }

    @SuppressWarnings("unchecked")
    public NodeList getRootCppCompilerTools() {
        List<Node> compilers = Lists.newArrayList();
        for (Node toolChain : (List<Node>) getRootToolChains()) {
            List<Node> tools = getChildren(toolChain, "tool");
            for (Node tool : tools) {
                if (isGnuCompilerTool(tool)) {
                    compilers.add(tool);
                }
            }
        }
        return new NodeList(compilers);
    }

    @SuppressWarnings("unchecked")
    public NodeList getRootCppLinkerTools() {
        List<Node> linkers = Lists.newArrayList();
        for (Node toolChain : (List<Node>) getRootToolChains()) {
            List<Node> tools = getChildren(toolChain, "tool");
            for (Node tool : tools) {
                if (isGnuLinkerTool(tool)) {
                    linkers.add(tool);
                }
            }
        }
        return new NodeList(linkers);
    }

    public boolean isGnuCompilerTool(Node node) {
        return node.name().equals("tool") && ((String) node.attributes().get("id")).startsWith(GNU_COMPILER_TOOL_ID_PREFIX);
    }

    public boolean isGnuLinkerTool(Node node) {
        return node.name().equals("tool") && ((String) node.attributes().get("id")).startsWith(GNU_LINKER_TOOL_ID_PREFIX);
    }

    public Node getOrCreateIncludePathsOption(Node compilerToolNode) {
        if (!isGnuCompilerTool(compilerToolNode)) {
            throw new IllegalArgumentException("Arg must be a gnu compiler tool def, was " + String.valueOf(compilerToolNode));
        }

        List<Node> options = getChildren(compilerToolNode, "option");
        Node includePathsOption = Iterables.find(options, new Predicate<Node>() {
            @Override
            public boolean apply(Node option) {
                return ((String) option.attributes().get("id")).startsWith(GNU_COMPILER_TOOL_INCLUDE_PATHS_OPTION_PREFIX);
            }
        });
        if (includePathsOption == null) {
            Map<String, String> map = new LinkedHashMap<String, String>(3);
            map.put("id", createId(GNU_COMPILER_TOOL_INCLUDE_PATHS_OPTION_PREFIX));
            map.put("superClass", GNU_COMPILER_TOOL_INCLUDE_PATHS_OPTION_PREFIX);
            map.put("valueType", "includePath");
            includePathsOption = compilerToolNode.appendNode("option", map);
        }


        return includePathsOption;
    }

    public Node getOrCreateLibsOption(Node linkerToolNode) {
        if (!isGnuLinkerTool(linkerToolNode)) {
            throw new IllegalArgumentException("Arg must be a gnu linker tool def, was " + String.valueOf(linkerToolNode));
        }

        List<Node> options = getChildren(linkerToolNode, "option");
        Node libsOption = Iterables.find(options, new Predicate<Node>() {
            @Override
            public boolean apply(Node option) {
                return ((String) option.attributes().get("id")).startsWith(GNU_LINKER_TOOL_LIBS_PATHS_OPTION_PREFIX);
            }
        });
        if (libsOption == null) {
            Map<String, String> map = new LinkedHashMap<String, String>(3);
            map.put("id", createId(GNU_LINKER_TOOL_LIBS_PATHS_OPTION_PREFIX));
            map.put("superClass", GNU_LINKER_TOOL_LIBS_PATHS_OPTION_PREFIX);
            map.put("valueType", "userObjs");
            libsOption = linkerToolNode.appendNode("option", map);
        }

        return libsOption;
    }

    public String createId(String prefix) {
        return prefix + "." + new SimpleDateFormat("yyMMddHHmmssS").format(new Date());
    }

    protected void store(Node xml) {
        transformAction(new Closure<StringBuilder>(this, this) {
            public StringBuilder doCall(XmlProvider xmlProvider) {
                StringBuilder xmlString = xmlProvider.asString();
                return xmlString.insert(xmlString.indexOf("\n") + 1, "<?fileVersion 4.0.0?>\n");
            }

        });
    }
}
