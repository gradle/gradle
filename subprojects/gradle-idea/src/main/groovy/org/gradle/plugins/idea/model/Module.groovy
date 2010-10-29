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
package org.gradle.plugins.idea.model

import org.gradle.api.Action
import org.gradle.api.internal.XmlTransformer

/**
 * Represents the customizable elements of an iml (via XML hooks everything of the iml is customizable).
 *
 * @author Hans Dockter
 */
class Module {
    static final String INHERITED = "inherited"

    /**
     * The dir for the content root of the module.  Defaults to the projectDir for the project.  If null,
     * the directory that contains the output file will be used.
     */
    Path contentPath;

    /**
     * The foldes for the production code. Must not be null.
     */
    Set sourceFolders = [] as LinkedHashSet

    /**
     * The folders for the test code. Must not be null.
     */
    Set testSourceFolders = [] as LinkedHashSet

    /**
     * Folders to be excluded. Must not be null.
     */
    Set excludeFolders = [] as LinkedHashSet

    /**
     * The dir for the production source classes. If null this output dir element is not added.
     */
    Path outputDir

    /**
     * The dir for the compiled test source classes. If null this output element is not added.
     */
    Path testOutputDir

    /**
     * The dependencies of this module. Must not be null. Has instances of type    {@link Dependency}   .
     */
    Set dependencies = [] as LinkedHashSet

    String javaVersion

    private Node xml

    private final XmlTransformer withXmlActions
    private final PathFactory pathFactory

    def Module(Path contentPath, Set sourceFolders, Set testSourceFolders, Set excludeFolders, Path outputDir, Path testOutputDir, Set dependencies,
               String javaVersion, Reader inputXml, Action<Module> beforeConfiguredAction, Action<Module> whenConfiguredAction,
               XmlTransformer withXmlActions, PathFactory pathFactory) {
        this.pathFactory = pathFactory
        initFromXml(inputXml)

        beforeConfiguredAction.execute(this)

        this.contentPath = contentPath
        this.sourceFolders.addAll(sourceFolders);
        this.testSourceFolders.addAll(testSourceFolders);
        this.excludeFolders.addAll(excludeFolders);
        if (outputDir) {
            this.outputDir = outputDir
        }
        if (testOutputDir) {
            this.testOutputDir = testOutputDir;
        }
        this.dependencies.addAll(dependencies);
        if (javaVersion) {
            this.javaVersion = javaVersion
        }
        this.withXmlActions = withXmlActions;

        whenConfiguredAction.execute(this)
    }

    private def initFromXml(Reader inputXml) {
        Reader reader = inputXml ?: new InputStreamReader(getClass().getResourceAsStream('defaultModule.xml'))
        xml = new XmlParser().parse(reader)
        readJdkFromXml()
        readSourceAndExcludeFolderFromXml()
        readOutputDirsFromXml()
        readDependenciesFromXml()
    }

    private def readJdkFromXml() {
        def jdk = findOrderEntries().find { it.@type == 'jdk' }
        if (jdk) {
            this.javaVersion = jdk.@jdkName
        } else {
            this.javaVersion = INHERITED
        }
    }

    private def readOutputDirsFromXml() {
        def outputDirUrl = findOutputDir()?.@url
        def testOutputDirUrl = findTestOutputDir()?.@url
        this.outputDir = outputDirUrl ? pathFactory.path(outputDirUrl) : null
        this.testOutputDir = testOutputDirUrl ? pathFactory.path(testOutputDirUrl) : null
    }

    private def readDependenciesFromXml() {
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

    private def readSourceAndExcludeFolderFromXml() {
        findSourceFolder().each { sourceFolder ->
            if (sourceFolder.@isTestSource == 'false') {
                this.sourceFolders.add(pathFactory.path(sourceFolder.@url))
            } else {
                this.testSourceFolders.add(pathFactory.path(sourceFolder.@url))
            }
        }
        findExcludeFolder().each { excludeFolder ->
            this.excludeFolders.add(pathFactory.path(excludeFolder.@url))
        }
    }

    /**
     * Generates the XML for the iml.
     *
     * @param writer The writer where the iml xml is generated into.
     */
    def toXml(Writer writer) {
        addJdkToXml()
        setContentURL()
        removeSourceAndExcludeFolderFromXml()
        addSourceAndExcludeFolderToXml()
        addOutputDirsToXml()

        removeDependenciesFromXml()
        addDependenciesToXml()

        withXmlActions.transform(xml, writer)
    }

    private def addJdkToXml() {
        assert javaVersion != null
        Node moduleJdk = findOrderEntries().find { it.@type == 'jdk' }
        if (javaVersion != INHERITED) {
            Node inheritedJdk = findOrderEntries().find { it.@type == "inheritedJdk" }
            if (inheritedJdk) {
                inheritedJdk.parent().remove(inheritedJdk)
            }
            if (moduleJdk) {
                findNewModuleRootManager().remove(moduleJdk)
            }
            findNewModuleRootManager().appendNode("orderEntry", [type: "jdk", jdkName: javaVersion, jdkType: "JavaSDK"])
        } else if (!(findOrderEntries().find { it.@type == "inheritedJdk" })) {
            if (moduleJdk) {
                findNewModuleRootManager().remove(moduleJdk)
            }
            findNewModuleRootManager().appendNode("orderEntry", [type: "inheritedJdk"])
        }
    }

    private def setContentURL() {
        if (contentPath != null) {
            findContent().@url = contentPath.url
        }
    }

    private def addOutputDirsToXml() {
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

    private def addSourceAndExcludeFolderToXml() {
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

    private def removeSourceAndExcludeFolderFromXml() {
        findSourceFolder().each { sourceFolder ->
            findContent().remove(sourceFolder)
        }
        findExcludeFolder().each { excludeFolder ->
            findContent().remove(excludeFolder)
        }
    }

    private def removeDependenciesFromXml() {
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

    private def findSourceFolder() {
        return findContent().sourceFolder
    }

    private def findExcludeFolder() {
        return findContent().excludeFolder
    }

    private Node findOutputDir() {
        return findNewModuleRootManager().output[0]
    }

    private Node findNewModuleRootManager() {
        return xml.component.find { it.@name == 'NewModuleRootManager'}
    }

    private Node findTestOutputDir() {
        return findNewModuleRootManager().'output-test'[0]
    }

    private def findOrderEntries() {
        return findNewModuleRootManager().orderEntry
    }


    boolean equals(o) {
        if (this.is(o)) { return true }

        if (getClass() != o.class) { return false }

        Module module = (Module) o;

        if (dependencies != module.dependencies) { return false }
        if (excludeFolders != module.excludeFolders) { return false }
        if (outputDir != module.outputDir) { return false }
        if (sourceFolders != module.sourceFolders) { return false }
        if (testOutputDir != module.testOutputDir) { return false }
        if (testSourceFolders != module.testSourceFolders) { return false }

        return true;
    }

    int hashCode() {
        int result;

        result = (sourceFolders != null ? sourceFolders.hashCode() : 0);
        result = 31 * result + (testSourceFolders != null ? testSourceFolders.hashCode() : 0);
        result = 31 * result + (excludeFolders != null ? excludeFolders.hashCode() : 0);
        result = 31 * result + outputDir.hashCode();
        result = 31 * result + testOutputDir.hashCode();
        result = 31 * result + (dependencies != null ? dependencies.hashCode() : 0);
        return result;
    }


    public String toString() {
        return "Module{" +
                "dependencies=" + dependencies +
                ", sourceFolders=" + sourceFolders +
                ", testSourceFolders=" + testSourceFolders +
                ", excludeFolders=" + excludeFolders +
                ", outputDir=" + outputDir +
                ", testOutputDir=" + testOutputDir +
                '}';
    }
}
