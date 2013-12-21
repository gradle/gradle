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
package org.gradle.plugins.ide.idea.model

import org.gradle.api.internal.xml.XmlTransformer
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject
import org.gradle.util.DeprecationLogger

/**
 * Represents the customizable elements of an iml (via XML hooks everything of the iml is customizable).
 */
class Module extends XmlPersistableConfigurationObject {
    static final String INHERITED = "inherited"

    /**
     * The directory for the content root of the module.  Defaults to the project directory.
     * If null, the directory containing the output file will be used.
     */
    Path contentPath

    /**
     * The directories containing the production sources. Must not be null.
     */
    Set<Path> sourceFolders = [] as LinkedHashSet

    /**
     * The directories containing the test sources. Must not be null.
     */
    Set<Path> testSourceFolders = [] as LinkedHashSet

    /**
     * The directories to be excluded. Must not be null.
     */
    Set<Path> excludeFolders = [] as LinkedHashSet

    /**
     * If true, output directories for this module will be located below the output directory for the project;
     * otherwise, {@link #outputDir} and {@link #testOutputDir} will take effect.
     */
    boolean inheritOutputDirs

    /**
     * The output directory for production classes. If {@code null}, no entry will be created.
     */
    Path outputDir

    /**
     * The output directory for test classes. If {@code null}, no entry will be created.
     */
    Path testOutputDir

    /**
     * The dependencies of this module. Must not be null.
     */
    Set<Dependency> dependencies = [] as LinkedHashSet

    String jdkName

    String getJavaVersion() {
        DeprecationLogger.nagUserOfReplacedMethod("javaVersion", "jdkName")
        jdkName
    }

    void setJavaVersion(String jdkName) {
        DeprecationLogger.nagUserOfReplacedMethod("javaVersion", "jdkName")
        this.jdkName = jdkName
    }

    private final PathFactory pathFactory

    Module(XmlTransformer withXmlActions, PathFactory pathFactory) {
        super(withXmlActions)
        this.pathFactory = pathFactory
    }

    @Override protected String getDefaultResourceName() {
        return 'defaultModule.xml'
    }

    @Override protected void load(Node xml) {
        readJdkFromXml()
        readSourceAndExcludeFolderFromXml()
        readInheritOutputDirsFromXml()
        readOutputDirsFromXml()
        readDependenciesFromXml()
    }

    private readJdkFromXml() {
        def jdk = findOrderEntries().find { it.@type == 'jdk' }
        jdkName = jdk ? jdk.@jdkName : INHERITED
    }

    private readSourceAndExcludeFolderFromXml() {
        findSourceFolder().each { sourceFolder ->
            if (sourceFolder.@isTestSource == 'false') {
                sourceFolders.add(pathFactory.path(sourceFolder.@url))
            } else {
                testSourceFolders.add(pathFactory.path(sourceFolder.@url))
            }
        }
        findExcludeFolder().each { excludeFolder ->
            excludeFolders.add(pathFactory.path(excludeFolder.@url))
        }
    }

    private readInheritOutputDirsFromXml() {
        inheritOutputDirs = findNewModuleRootManager().@"inherit-compiler-output" == "true"
    }

    private readOutputDirsFromXml() {
        def outputDirUrl = findOutputDir()?.@url
        def testOutputDirUrl = findTestOutputDir()?.@url
        outputDir = outputDirUrl ? pathFactory.path(outputDirUrl) : null
        testOutputDir = testOutputDirUrl ? pathFactory.path(testOutputDirUrl) : null
    }

    private readDependenciesFromXml() {
        return findOrderEntries().each { orderEntry ->
            switch (orderEntry.@type) {
                case "module-library":
                    Set classes = orderEntry.library.CLASSES.root.collect {
                        pathFactory.path(it.@url)
                    }
                    Set javadoc = orderEntry.library.JAVADOC.root.collect {
                        pathFactory.path(it.@url)
                    }
                    Set sources = orderEntry.library.SOURCES.root.collect {
                        pathFactory.path(it.@url)
                    }
                    Set jarDirectories = orderEntry.library.jarDirectory.collect { new JarDirectory(pathFactory.path(it.@url), Boolean.parseBoolean(it.@recursive)) }
                    def moduleLibrary = new ModuleLibrary(classes, javadoc, sources, jarDirectories, orderEntry.@scope)
                    dependencies.add(moduleLibrary)
                    break
                case "module":
                    dependencies.add(new ModuleDependency(orderEntry.@'module-name', orderEntry.@scope))
            }
        }
    }

    protected def configure(Path contentPath, Set sourceFolders, Set testSourceFolders, Set excludeFolders,
                            Boolean inheritOutputDirs, Path outputDir, Path testOutputDir, Set dependencies, String jdkName) {
        this.contentPath = contentPath
        this.sourceFolders.addAll(sourceFolders)
        this.testSourceFolders.addAll(testSourceFolders)
        this.excludeFolders.addAll(excludeFolders)
        if (inheritOutputDirs != null) {
            this.inheritOutputDirs = inheritOutputDirs
        }
        if (outputDir) {
            this.outputDir = outputDir
        }
        if (testOutputDir) {
            this.testOutputDir = testOutputDir
        }
        this.dependencies = dependencies; // overwrite rather than append dependencies
        if (jdkName) {
            this.jdkName = jdkName
        } else {
            this.jdkName = Module.INHERITED
        }
    }

    @Override protected void store(Node xml) {
        addJdkToXml()
        setContentURL()
        removeSourceAndExcludeFolderFromXml()
        addSourceAndExcludeFolderToXml()
        writeInheritOutputDirsToXml()
        addOutputDirsToXml()

        removeDependenciesFromXml()
        addDependenciesToXml()
    }

    private addJdkToXml() {
        assert jdkName != null
        Node moduleJdk = findOrderEntries().find { it.@type == 'jdk' }
        if (jdkName != INHERITED) {
            Node inheritedJdk = findOrderEntries().find { it.@type == "inheritedJdk" }
            if (inheritedJdk) {
                inheritedJdk.parent().remove(inheritedJdk)
            }
            if (moduleJdk) {
                findNewModuleRootManager().remove(moduleJdk)
            }
            findNewModuleRootManager().appendNode("orderEntry", [type: "jdk", jdkName: jdkName, jdkType: "JavaSDK"])
        } else if (!(findOrderEntries().find { it.@type == "inheritedJdk" })) {
            if (moduleJdk) {
                findNewModuleRootManager().remove(moduleJdk)
            }
            findNewModuleRootManager().appendNode("orderEntry", [type: "inheritedJdk"])
        }
    }

    private setContentURL() {
        if (contentPath != null) {
            findContent().@url = contentPath.url
        }
    }

    private writeInheritOutputDirsToXml() {
        findNewModuleRootManager().@"inherit-compiler-output" = inheritOutputDirs
    }

    private addOutputDirsToXml() {
        if (outputDir) {
            findOrCreateOutputDir().@url = outputDir.url
        }
        if (testOutputDir) {
            findOrCreateTestOutputDir().@url = testOutputDir.url
        }
    }

    private Node findOrCreateOutputDir() {
        return findOutputDir() ?: findNewModuleRootManager().appendNode("output")
    }

    private Node findOrCreateTestOutputDir() {
        return findTestOutputDir() ?: findNewModuleRootManager().appendNode("output-test")
    }

    private Set addDependenciesToXml() {
        return dependencies.each { Dependency dependency ->
            dependency.addToNode(findNewModuleRootManager())
        }
    }

    private addSourceAndExcludeFolderToXml() {
        sourceFolders.each { Path path ->
            findContent().appendNode('sourceFolder', [url: path.url, isTestSource: 'false'])
        }
        testSourceFolders.each { Path path ->
            findContent().appendNode('sourceFolder', [url: path.url, isTestSource: 'true'])
        }
        excludeFolders.each { Path path ->
            findContent().appendNode('excludeFolder', [url: path.url])
        }
    }

    private removeSourceAndExcludeFolderFromXml() {
        findSourceFolder().each { sourceFolder ->
            findContent().remove(sourceFolder)
        }
        findExcludeFolder().each { excludeFolder ->
            findContent().remove(excludeFolder)
        }
    }

    private removeDependenciesFromXml() {
        return findOrderEntries().each { orderEntry ->
            if (isDependencyOrderEntry(orderEntry)) {
                findNewModuleRootManager().remove(orderEntry)
            }
        }
    }

    protected boolean isDependencyOrderEntry(def orderEntry) {
        ['module-library', 'module'].contains(orderEntry.@type)
    }

    private Node findContent() {
        findNewModuleRootManager().content[0]
    }

    private findSourceFolder() {
        findContent().sourceFolder
    }

    private findExcludeFolder() {
        findContent().excludeFolder
    }

    private Node findOutputDir() {
        findNewModuleRootManager().output[0]
    }

    private Node findNewModuleRootManager() {
        xml.component.find { it.@name == 'NewModuleRootManager'}
    }

    private Node findTestOutputDir() {
        return findNewModuleRootManager().'output-test'[0]
    }

    private findOrderEntries() {
        findNewModuleRootManager().orderEntry
    }


    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        Module module = (Module) o

        if (dependencies != module.dependencies) { return false }
        if (excludeFolders != module.excludeFolders) { return false }
        if (outputDir != module.outputDir) { return false }
        if (sourceFolders != module.sourceFolders) { return false }
        if (testOutputDir != module.testOutputDir) { return false }
        if (testSourceFolders != module.testSourceFolders) { return false }

        return true
    }

    int hashCode() {
        int result;

        result = (sourceFolders != null ? sourceFolders.hashCode() : 0)
        result = 31 * result + (testSourceFolders != null ? testSourceFolders.hashCode() : 0)
        result = 31 * result + (excludeFolders != null ? excludeFolders.hashCode() : 0)
        result = 31 * result + (inheritOutputDirs != null ? inheritOutputDirs.hashCode() : 0)
        result = 31 * result + outputDir.hashCode()
        result = 31 * result + testOutputDir.hashCode()
        result = 31 * result + (dependencies != null ? dependencies.hashCode() : 0)
        return result
    }


    String toString() {
        return "Module{" +
                "dependencies=" + dependencies +
                ", sourceFolders=" + sourceFolders +
                ", testSourceFolders=" + testSourceFolders +
                ", excludeFolders=" + excludeFolders +
                ", inheritOutputDirs=" + inheritOutputDirs +
                ", outputDir=" + outputDir +
                ", testOutputDir=" + testOutputDir +
                '}'
    }
}
