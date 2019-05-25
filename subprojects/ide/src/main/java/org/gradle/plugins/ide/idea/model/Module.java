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
package org.gradle.plugins.ide.idea.model;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.util.Node;
import org.gradle.api.Incubating;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Represents the customizable elements of an iml (via XML hooks everything of the iml is customizable).
 */
public class Module extends XmlPersistableConfigurationObject {

    public static final String INHERITED = "inherited";

    private static final String CONTENT = "content";

    private Path contentPath;
    private Set<Path> sourceFolders = Sets.newLinkedHashSet();
    private Set<Path> testSourceFolders = Sets.newLinkedHashSet();
    private Set<Path> resourceFolders = Sets.newLinkedHashSet();
    private Set<Path> testResourceFolders = Sets.newLinkedHashSet();
    private Set<Path> generatedSourceFolders = Sets.newLinkedHashSet();
    private Set<Path> excludeFolders = Sets.newLinkedHashSet();
    private boolean inheritOutputDirs;
    private Path outputDir;
    private Path testOutputDir;
    private Set<Dependency> dependencies = Sets.newLinkedHashSet();
    private String jdkName;
    private final PathFactory pathFactory;
    private String languageLevel;

    public Module(XmlTransformer withXmlActions, PathFactory pathFactory) {
        super(withXmlActions);
        this.pathFactory = pathFactory;
    }

    /**
     * The directory for the content root of the module.
     * Defaults to the project directory.
     * If null, the directory containing the output file will be used.
     */
    public Path getContentPath() {
        return contentPath;
    }

    public void setContentPath(Path contentPath) {
        this.contentPath = contentPath;
    }

    /**
     * The directories containing the production sources.
     * Must not be null.
     */
    public Set<Path> getSourceFolders() {
        return sourceFolders;
    }

    public void setSourceFolders(Set<Path> sourceFolders) {
        this.sourceFolders = sourceFolders;
    }

    /**
     * The directories containing the test sources.
     * Must not be null.
     */
    public Set<Path> getTestSourceFolders() {
        return testSourceFolders;
    }

    public void setTestSourceFolders(Set<Path> testSourceFolders) {
        this.testSourceFolders = testSourceFolders;
    }

    /**
     * The directories containing resources.
     * Must not be null.
     * @since 4.7
     */
    @Incubating
    public Set<Path> getResourceFolders() {
        return resourceFolders;
    }

    /**
     * Sets the directories containing resources.
     * @since 4.7
     */
    @Incubating
    public void setResourceFolders(Set<Path> resourceFolders) {
        this.resourceFolders = resourceFolders;
    }

    /**
     * The directories containing test resources.
     * Must not be null.
     * @since 4.7
     */
    @Incubating
    public Set<Path> getTestResourceFolders() {
        return testResourceFolders;
    }

    /**
     * Sets the directories containing test resources.
     * @since 4.7
     */
    @Incubating
    public void setTestResourceFolders(Set<Path> testResourceFolders) {
        this.testResourceFolders = testResourceFolders;
    }
    /**
     * The directories containing generated the production sources.
     * Must not be null.
     */
    public Set<Path> getGeneratedSourceFolders() {
        return generatedSourceFolders;
    }

    public void setGeneratedSourceFolders(Set<Path> generatedSourceFolders) {
        this.generatedSourceFolders = generatedSourceFolders;
    }

    /**
     * The directories to be excluded.
     * Must not be null.
     */
    public Set<Path> getExcludeFolders() {
        return excludeFolders;
    }

    public void setExcludeFolders(Set<Path> excludeFolders) {
        this.excludeFolders = excludeFolders;
    }

    /**
     * If true, output directories for this module will be located below the output directory for the project;
     * otherwise, {@link #outputDir} and {@link #testOutputDir} will take effect.
     */
    public boolean isInheritOutputDirs() {
        return inheritOutputDirs;
    }

    public void setInheritOutputDirs(boolean inheritOutputDirs) {
        this.inheritOutputDirs = inheritOutputDirs;
    }

    /**
     * The output directory for production classes.
     * If {@code null}, no entry will be created.
     */
    public Path getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(Path outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * The output directory for test classes.
     * If {@code null}, no entry will be created.
     */
    public Path getTestOutputDir() {
        return testOutputDir;
    }

    public void setTestOutputDir(Path testOutputDir) {
        this.testOutputDir = testOutputDir;
    }

    /**
     * The dependencies of this module.
     * Must not be null.
     */
    public Set<Dependency> getDependencies() {
        return dependencies;
    }

    public void setDependencies(Set<Dependency> dependencies) {
        this.dependencies = dependencies;
    }

    public String getJdkName() {
        return jdkName;
    }

    public void setJdkName(String jdkName) {
        this.jdkName = jdkName;
    }

    @Override
    protected String getDefaultResourceName() {
        return "defaultModule.xml";
    }

    protected Object configure(Path contentPath,
                               Set<Path> sourceFolders, Set<Path> testSourceFolders,
                               Set<Path> resourceFolders, Set<Path> testResourceFolders,
                               Set<Path> generatedSourceFolders,
                               Set<Path> excludeFolders,
                               Boolean inheritOutputDirs, Path outputDir, Path testOutputDir,
                               Set<Dependency> dependencies, String jdkName, String languageLevel) {
        this.languageLevel = languageLevel;
        this.contentPath = contentPath;
        this.sourceFolders.addAll(sourceFolders);
        this.testSourceFolders.addAll(testSourceFolders);
        this.resourceFolders.addAll(resourceFolders);
        this.testResourceFolders.addAll(testResourceFolders);
        this.generatedSourceFolders.addAll(generatedSourceFolders);
        this.excludeFolders.addAll(excludeFolders);
        if (inheritOutputDirs != null) {
            this.inheritOutputDirs = inheritOutputDirs;
        }
        if (outputDir != null) {
            this.outputDir = outputDir;
        }
        if (testOutputDir != null) {
            this.testOutputDir = testOutputDir;
        }
        this.dependencies = dependencies; // overwrite rather than append dependencies
        if (!isNullOrEmpty(jdkName)) {
            this.jdkName = jdkName;
        } else {
            this.jdkName = Module.INHERITED;
        }
        return this.jdkName;
    }

    @Override
    protected void load(Node xml) {
        readJdkFromXml();
        readSourceAndExcludeFolderFromXml();
        readInheritOutputDirsFromXml();
        readOutputDirsFromXml();
        readDependenciesFromXml();
    }

    @Override
    protected void store(Node xml) {
        addJdkToXml();
        setContentURL();
        removeSourceAndExcludeFolderFromXml();
        addSourceAndExcludeFolderToXml();
        writeInheritOutputDirsToXml();
        writeSourceLanguageLevel();
        addOutputDirsToXml();

        removeDependenciesFromXml();
        addDependenciesToXml();
    }

    private void readJdkFromXml() {
        Node jdk = findFirstWithAttributeValue(findOrderEntries(), "type", "jdk");
        jdkName = jdk != null ? (String) jdk.attribute("jdkName") : INHERITED;
    }

    private void readSourceAndExcludeFolderFromXml() {
        for (Node sourceFolder : findSourceFolder()) {
            String url = (String) sourceFolder.attribute("url");
            String isTestSource = (String) sourceFolder.attribute("isTestSource");
            if (isTestSource != null) {
                if ("false".equals(isTestSource)) {
                    sourceFolders.add(pathFactory.path(url));
                } else {
                    testSourceFolders.add(pathFactory.path(url));
                }
            }
            if ("true".equals(sourceFolder.attribute("generated"))) {
                generatedSourceFolders.add(pathFactory.path(url));
            }
            String type = (String) sourceFolder.attribute("type");
            if ("java-resource".equals(type)) {
                resourceFolders.add(pathFactory.path(url));
            } else if ("java-test-resource".equals(type)) {
                testResourceFolders.add(pathFactory.path(url));
            }
        }
        for (Node excludeFolder : findExcludeFolder()) {
            excludeFolders.add(pathFactory.path((String) excludeFolder.attribute("url")));
        }

    }

    private boolean readInheritOutputDirsFromXml() {
        return inheritOutputDirs = "true".equals(getNewModuleRootManager().attribute("inherit-compiler-output"));
    }

    private Path readOutputDirsFromXml() {
        Node outputDirNode = findOutputDir();
        Node testOutputDirNode = findTestOutputDir();
        String outputDirUrl = outputDirNode != null ? (String) outputDirNode.attribute("url") : null;
        String testOutputDirUrl = testOutputDirNode != null ? (String) testOutputDirNode.attribute("url") : null;
        outputDir = outputDirUrl != null ? pathFactory.path(outputDirUrl) : null;
        return testOutputDir = testOutputDirUrl != null ? pathFactory.path(testOutputDirUrl) : null;
    }

    private void readDependenciesFromXml() {
        for (Node orderEntry : findOrderEntries()) {
            Object orderEntryType = orderEntry.attribute("type");
            if ("module-library".equals(orderEntryType)) {
                Set<Path> classes = Sets.newLinkedHashSet();
                Set<Path> javadoc = Sets.newLinkedHashSet();
                Set<Path> sources = Sets.newLinkedHashSet();
                Set<JarDirectory> jarDirectories = Sets.newLinkedHashSet();
                for (Node library : getChildren(orderEntry, "library")) {
                    for (Node classesNode : getChildren(library, "CLASSES")) {
                        readDependenciesPathsFromXml(classes, classesNode);
                    }
                    for (Node javadocNode : getChildren(library, "JAVADOC")) {
                        readDependenciesPathsFromXml(javadoc, javadocNode);
                    }
                    for (Node sourcesNode : getChildren(library, "SOURCES")) {
                        readDependenciesPathsFromXml(sources, sourcesNode);
                    }
                    for (Node jarDirNode : getChildren(library, "jarDirectory")) {
                        jarDirectories.add(new JarDirectory(pathFactory.path((String) jarDirNode.attribute("url")), Boolean.parseBoolean((String) jarDirNode.attribute("recursive"))));
                    }
                }
                ModuleLibrary moduleLibrary = new ModuleLibrary(classes, javadoc, sources, jarDirectories, (String) orderEntry.attribute("scope"));
                dependencies.add(moduleLibrary);
            } else if ("module".equals(orderEntryType)) {
                dependencies.add(new ModuleDependency((String) orderEntry.attribute("module-name"), (String) orderEntry.attribute("scope")));
            }
        }
    }

    private void readDependenciesPathsFromXml(Set<Path> paths, Node node) {
        for (Node classesRoot : getChildren(node, "root")) {
            paths.add(pathFactory.path((String) classesRoot.attribute("url")));
        }
    }

    private void addJdkToXml() {
        Preconditions.checkNotNull(jdkName);
        List<Node> orderEntries = findOrderEntries();
        Node moduleJdk = findFirstWithAttributeValue(orderEntries, "type", "jdk");
        Node moduleRootManager = getNewModuleRootManager();
        if (!jdkName.equals(INHERITED)) {
            Node inheritedJdk = findFirstWithAttributeValue(orderEntries, "type", "inheritedJdk");
            if (inheritedJdk != null) {
                inheritedJdk.parent().remove(inheritedJdk);
            }
            if (moduleJdk != null) {
                moduleRootManager.remove(moduleJdk);
            }
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put("type", "jdk");
            attributes.put("jdkName", jdkName);
            attributes.put("jdkType", "JavaSDK");
            moduleRootManager.appendNode("orderEntry", attributes);
        } else if (findFirstWithAttributeValue(orderEntries, "type", "inheritedJdk") == null) {
            if (moduleJdk != null) {
                moduleRootManager.remove(moduleJdk);
            }
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put("type", "inheritedJdk");
            moduleRootManager.appendNode("orderEntry", attributes);
        }
    }

    private void setContentURL() {
        if (contentPath != null) {
            findOrCreateContentNode().attributes().put("url", contentPath.getUrl());
        }
    }

    private void removeSourceAndExcludeFolderFromXml() {
        Node content = findOrCreateContentNode();
        for (Node sourceFolder : findSourceFolder()) {
            content.remove(sourceFolder);
        }
        for (Node excludeFolder : findExcludeFolder()) {
            content.remove(excludeFolder);
        }
    }

    private void addSourceAndExcludeFolderToXml() {
        Node content = findOrCreateContentNode();
        for (Path path : sourceFolders) {
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put("url", path.getUrl());
            attributes.put("isTestSource", "false");
            if (generatedSourceFolders.contains(path)) {
                attributes.put("generated", "true");
            }
            content.appendNode("sourceFolder", attributes);
        }
        for (Path path : testSourceFolders) {
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put("url", path.getUrl());
            attributes.put("isTestSource", "true");
            if (generatedSourceFolders.contains(path)) {
                attributes.put("generated", "true");
            }
            content.appendNode("sourceFolder", attributes);
        }
        for (Path path : resourceFolders) {
            if (sourceFolders.contains(path)) {
                continue;
            }
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put("url", path.getUrl());
            attributes.put("type", "java-resource");
            if (generatedSourceFolders.contains(path)) {
                attributes.put("generated", "true");
            }
            content.appendNode("sourceFolder", attributes);
        }
        for (Path path : testResourceFolders) {
            if (testSourceFolders.contains(path)) {
                continue;
            }
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put("url", path.getUrl());
            attributes.put("type", "java-test-resource");
            if (generatedSourceFolders.contains(path)) {
                attributes.put("generated", "true");
            }
            content.appendNode("sourceFolder", attributes);
        }
        for (Path path : excludeFolders) {
            Map<String, Object> attributes = Maps.newLinkedHashMap();
            attributes.put("url", path.getUrl());
            content.appendNode("excludeFolder", attributes);
        }
    }

    private void writeInheritOutputDirsToXml() {
        getNewModuleRootManager().attributes().put("inherit-compiler-output", inheritOutputDirs);
    }

    private void writeSourceLanguageLevel() {
        if (languageLevel != null) {
            getNewModuleRootManager().attributes().put("LANGUAGE_LEVEL", languageLevel);
        }
    }

    private void addOutputDirsToXml() {
        if (outputDir != null) {
            findOrCreateOutputDir().attributes().put("url", outputDir.getUrl());
        }
        if (testOutputDir != null) {
            findOrCreateTestOutputDir().attributes().put("url", testOutputDir.getUrl());
        }
    }

    private void removeDependenciesFromXml() {
        Node moduleRoot = getNewModuleRootManager();
        for (Node orderEntry : findOrderEntries()) {
            if (isDependencyOrderEntry(orderEntry)) {
                moduleRoot.remove(orderEntry);
            }
        }
    }

    protected boolean isDependencyOrderEntry(Object orderEntry) {
        return Arrays.asList("module-library", "module").contains(((Node) orderEntry).attribute("type"));
    }

    private void addDependenciesToXml() {
        Node moduleRoot = getNewModuleRootManager();
        for (Dependency dependency : dependencies) {
            dependency.addToNode(moduleRoot);
        }
    }

    private Node getNewModuleRootManager() {
        Node newModuleRootManager = findFirstWithAttributeValue(getChildren(getXml(), "component"), "name", "NewModuleRootManager");
        Preconditions.checkNotNull(newModuleRootManager);
        return newModuleRootManager;
    }

    private Node findOrCreateOutputDir() {
        Node outputDirNode = findOutputDir();
        if (outputDirNode != null) {
            return outputDirNode;
        }
        return getNewModuleRootManager().appendNode("output");
    }

    private Node findOrCreateTestOutputDir() {
        Node testOutputDirNode = findTestOutputDir();
        if (testOutputDirNode != null) {
            return testOutputDirNode;
        }
        return getNewModuleRootManager().appendNode("output-test");
    }

    private Node findOrCreateContentNode() {
        Node newModuleRootManager = getNewModuleRootManager();
        Node contentNode = findFirstChildNamed(newModuleRootManager, CONTENT);
        if (contentNode != null) {
            return contentNode;
        }
        return newModuleRootManager.appendNode(CONTENT);
    }

    private List<Node> findSourceFolder() {
        return getChildren(findOrCreateContentNode(), "sourceFolder");
    }

    private List<Node> findExcludeFolder() {
        return getChildren(findOrCreateContentNode(), "excludeFolder");
    }

    @Nullable
    private Node findOutputDir() {
        return findFirstChildNamed(getNewModuleRootManager(), "output");
    }

    @Nullable
    private Node findTestOutputDir() {
        return findFirstChildNamed(getNewModuleRootManager(), "output-test");
    }

    private List<Node> findOrderEntries() {
        return getChildren(getNewModuleRootManager(), "orderEntry");
    }

    @Override
    public String toString() {
        return "Module{"
            + "dependencies=" + dependencies
            + ", sourceFolders=" + sourceFolders
            + ", testSourceFolders=" + testSourceFolders
            + ", resourceFolders=" + resourceFolders
            + ", testResourceFolders=" + testResourceFolders
            + ", generatedSourceFolders=" + generatedSourceFolders
            + ", excludeFolders=" + excludeFolders
            + ", inheritOutputDirs=" + inheritOutputDirs
            + ", jdkName=" + jdkName
            + ", outputDir=" + outputDir
            + ", testOutputDir=" + testOutputDir + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!getClass().equals(o.getClass())) {
            return false;
        }
        Module module = (Module) o;
        return Objects.equal(dependencies, module.dependencies)
            && Objects.equal(excludeFolders, module.excludeFolders)
            && Objects.equal(outputDir, module.outputDir)
            && Objects.equal(sourceFolders, module.sourceFolders)
            && Objects.equal(generatedSourceFolders, module.generatedSourceFolders)
            && Objects.equal(jdkName, module.jdkName)
            && Objects.equal(testOutputDir, module.testOutputDir)
            && Objects.equal(testSourceFolders, module.testSourceFolders)
            && Objects.equal(resourceFolders, module.resourceFolders)
            && Objects.equal(testResourceFolders, module.testResourceFolders);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(sourceFolders,
            generatedSourceFolders,
            testSourceFolders,
            resourceFolders,
            testResourceFolders,
            excludeFolders,
            inheritOutputDirs,
            jdkName,
            outputDir,
            testOutputDir,
            dependencies);
    }

}
