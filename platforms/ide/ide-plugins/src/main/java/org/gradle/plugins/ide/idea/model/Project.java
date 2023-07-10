/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea.model;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.util.Node;
import groovy.util.NodeList;
import org.gradle.api.JavaVersion;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Represents the customizable elements of an ipr (via XML hooks everything of the ipr is customizable).
 */
public class Project extends XmlPersistableConfigurationObject {

    private final PathFactory pathFactory;
    private List<IdeaModule> modules;
    private JavaVersion bytecodeVersion;

    private Set<Path> modulePaths = Sets.newLinkedHashSet();
    private Set<String> wildcards = Sets.newLinkedHashSet();
    private Jdk jdk;
    private String vcs;
    private Set<ProjectLibrary> projectLibraries = Sets.newLinkedHashSet();

    public Project(XmlTransformer xmlTransformer, Object pathFactory) {
        super(xmlTransformer);
        this.pathFactory = (PathFactory) pathFactory;
    }

    /**
     * A set of {@link Path} instances pointing to the modules contained in the ipr.
     */
    public Set<Path> getModulePaths() {
        return modulePaths;
    }

    public void setModulePaths(Set<Path> modulePaths) {
        this.modulePaths = modulePaths;
    }

    /**
     * Adds a module to the module paths included in the Project.
     *
     * @param moduleFile path to the module's module file
     *
     * @since 4.0
     */
    public void addModulePath(File moduleFile) {
        modulePaths.add(pathFactory.relativePath("PROJECT_DIR", moduleFile));
    }

    /**
     * A set of wildcard string to be included/excluded from the resources.
     */
    public Set<String> getWildcards() {
        return wildcards;
    }

    public void setWildcards(Set<String> wildcards) {
        this.wildcards = wildcards;
    }

    /**
     * Represent the jdk information of the project java sdk.
     */
    public Jdk getJdk() {
        return jdk;
    }

    public void setJdk(Jdk jdk) {
        this.jdk = jdk;
    }

    /**
     * The vcs used by the project.
     */
    public String getVcs() {
        return vcs;
    }

    public void setVcs(String vcs) {
        this.vcs = vcs;
    }

    /**
     * The project-level libraries of the IDEA project.
     */
    public Set<ProjectLibrary> getProjectLibraries() {
        return projectLibraries;
    }

    public void setProjectLibraries(Set<ProjectLibrary> projectLibraries) {
        this.projectLibraries = projectLibraries;
    }

    @Override
    protected String getDefaultResourceName() {
        return "defaultProject.xml";
    }

    public void configure(List<IdeaModule> modules,
                          String jdkName, IdeaLanguageLevel languageLevel, JavaVersion bytecodeVersion,
                          Collection<String> wildcards, Collection<ProjectLibrary> projectLibraries, String vcs) {
        if (!isNullOrEmpty(jdkName)) {
            jdk = new Jdk(jdkName, languageLevel);
        }
        this.bytecodeVersion = bytecodeVersion;
        this.modules = modules;
        for (IdeaModule module : modules) {
            addModulePath(module.getOutputFile());
        }
        this.wildcards.addAll(wildcards);
        // overwrite rather than append libraries
        this.projectLibraries = Sets.newLinkedHashSet(projectLibraries);
        this.vcs = vcs;
    }

    @Override
    protected void load(Node xml) {
        loadModulePaths();
        loadWildcards();
        loadJdk();
        loadProjectLibraries();
    }

    @Override
    protected void store(Node xml) {
        storeModulePaths();
        storeWildcards();
        storeJdk();
        storeBytecodeLevels();
        storeVcs();
        storeProjectLibraries();
    }

    private void loadModulePaths() {
        for (Node moduleNode : getChildren(findOrCreateModules(), "module")) {
            String fileurl = (String) moduleNode.attribute("fileurl");
            String filepath = (String) moduleNode.attribute("filepath");
            modulePaths.add(pathFactory.path(fileurl, filepath));
        }
    }

    private void loadWildcards() {
        List<Node> wildcardsNodes = getChildren(findCompilerConfiguration(), "wildcardResourcePatterns");
        for (Node wildcardsNode : wildcardsNodes) {
            for (Node entry : getChildren(wildcardsNode, "entry")) {
                this.wildcards.add((String) entry.attribute("name"));
            }
        }
    }

    private void loadJdk() {
        Node projectRoot = findProjectRootManager();
        boolean assertKeyword = Boolean.parseBoolean((String) projectRoot.attribute("assert-keyword"));
        boolean jdk15 = Boolean.parseBoolean((String) projectRoot.attribute("jdk-15"));
        String languageLevel = (String) projectRoot.attribute("languageLevel");
        String jdkName = (String) projectRoot.attribute("project-jdk-name");
        jdk = new Jdk(assertKeyword, jdk15, languageLevel, jdkName);
    }

    private void loadProjectLibraries() {
        Node libraryTable = findOrCreateLibraryTable();
        for (Node library : getChildren(libraryTable, "library")) {
            ProjectLibrary projectLibrary = new ProjectLibrary();
            projectLibrary.setName((String) library.attribute("name"));
            projectLibrary.setClasses(collectRootUrlAsFiles(getChildren(library, "CLASSES")));
            projectLibrary.setJavadoc(collectRootUrlAsFiles(getChildren(library, "JAVADOC")));
            projectLibrary.setSources(collectRootUrlAsFiles(getChildren(library, "SOURCES")));
            projectLibraries.add(projectLibrary);
        }
    }

    private Set<File> collectRootUrlAsFiles(List<Node> nodes) {
        Set<File> files = Sets.newLinkedHashSet();
        for (Node node : nodes) {
            for (Node root : getChildren(node, "root")) {
                String url = (String) root.attribute("url");
                files.add(new File(url));
            }
        }
        return files;
    }

    private void storeModulePaths() {
        Node modulesNode = new Node(null, "modules");
        for (Path modulePath : modulePaths) {
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put("fileurl", modulePath.getUrl());
            attributes.put("filepath", modulePath.getRelPath());
            modulesNode.appendNode("module", attributes);
        }
        findOrCreateModules().replaceNode(modulesNode);
    }

    private void storeWildcards() {
        Node compilerConfigNode = findCompilerConfiguration();
        Node existingNode = findOrCreateFirstChildNamed(compilerConfigNode, "wildcardResourcePatterns");
        Node wildcardsNode = new Node(null, "wildcardResourcePatterns");
        for (String wildcard : wildcards) {
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put("name", wildcard);
            wildcardsNode.appendNode("entry", attributes);
        }
        existingNode.replaceNode(wildcardsNode);
    }

    @SuppressWarnings("unchecked")
    private void storeJdk() {
        Node projectRoot = findProjectRootManager();
        projectRoot.attributes().put("assert-keyword", jdk.isAssertKeyword());
        projectRoot.attributes().put("assert-jdk-15", jdk.isJdk15());
        projectRoot.attributes().put("languageLevel", jdk.getLanguageLevel());
        projectRoot.attributes().put("project-jdk-name", jdk.getProjectJdkName());
    }

    @SuppressWarnings("unchecked")
    private void storeBytecodeLevels() {
        Node bytecodeLevelConfiguration = findOrCreateBytecodeLevelConfiguration();
        bytecodeLevelConfiguration.attributes().put("target", bytecodeVersion.toString());
        for (IdeaModule module : modules) {
            List<Node> bytecodeLevelModules = getChildren(bytecodeLevelConfiguration, "module");
            Node moduleNode = findFirstWithAttributeValue(bytecodeLevelModules, "name", module.getName());
            JavaVersion moduleBytecodeVersionOverwrite = module.getTargetBytecodeVersion();
            if (moduleBytecodeVersionOverwrite == null) {
                if (moduleNode != null) {
                    bytecodeLevelConfiguration.remove(moduleNode);
                }
            } else {
                if (moduleNode == null) {
                    moduleNode = bytecodeLevelConfiguration.appendNode("module");
                    moduleNode.attributes().put("name", module.getName());
                }
                moduleNode.attributes().put("target", moduleBytecodeVersionOverwrite.toString());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void storeVcs() {
        if (!isNullOrEmpty(vcs)) {
            findVcsDirectoryMappings().attributes().put("vcs", vcs);
        }
    }

    private void storeProjectLibraries() {
        Node libraryTable = findOrCreateLibraryTable();
        if (projectLibraries.isEmpty()) {
            getXml().remove(libraryTable);
            return;
        }
        libraryTable.setValue(new NodeList());
        for (ProjectLibrary library : projectLibraries) {
            library.addToNode(libraryTable, pathFactory);
        }
    }

    private Node findProjectRootManager() {
        return findFirstWithAttributeValue(getChildren(getXml(), "component"), "name", "ProjectRootManager");
    }

    private Node findOrCreateModules() {
        Node moduleManager = findFirstWithAttributeValue(getChildren(getXml(), "component"), "name", "ProjectModuleManager");
        Preconditions.checkNotNull(moduleManager);
        Node modules = findFirstChildNamed(moduleManager, "modules");
        if (modules == null) {
            modules = moduleManager.appendNode("modules");
        }
        return modules;
    }

    private Node findCompilerConfiguration() {
        return findFirstWithAttributeValue(getChildren(getXml(), "component"), "name", "CompilerConfiguration");
    }

    private Node findOrCreateBytecodeLevelConfiguration() {
        Node compilerConfiguration = findCompilerConfiguration();
        if (compilerConfiguration == null) {
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put("name", "CompilerConfiguration");
            compilerConfiguration = getXml().appendNode("component", attributes);
        }
        return findOrCreateFirstChildNamed(compilerConfiguration, "bytecodeTargetLevel");
    }

    private Node findVcsDirectoryMappings() {
        Node vcsDirMappings = findFirstWithAttributeValue(getChildren(getXml(), "component"), "name", "VcsDirectoryMappings");
        return findFirstChildNamed(vcsDirMappings, "mapping");
    }

    private Node findOrCreateLibraryTable() {
        Node libraryTable = findFirstWithAttributeValue(getChildren(getXml(), "component"), "name", "libraryTable");
        if (libraryTable == null) {
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put("name", "libraryTable");
            libraryTable = getXml().appendNode("component", attributes);
        }
        return libraryTable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!getClass().equals(o.getClass())) {
            return false;
        }
        Project project = (Project) o;
        return Objects.equal(jdk, project.jdk)
            && Objects.equal(modulePaths, project.modulePaths)
            && Objects.equal(projectLibraries, project.projectLibraries)
            && Objects.equal(wildcards, project.wildcards)
            && Objects.equal(vcs, project.vcs);
    }

    @Override
    public int hashCode() {
        int result;
        result = modulePaths != null ? modulePaths.hashCode() : 0;
        result = 31 * result + (wildcards != null ? wildcards.hashCode() : 0);
        result = 31 * result + (projectLibraries != null ? projectLibraries.hashCode() : 0);
        result = 31 * result + (jdk != null ? jdk.hashCode() : 0);
        result = 31 * result + (vcs != null ? vcs.hashCode() : 0);
        return result;
    }
}
