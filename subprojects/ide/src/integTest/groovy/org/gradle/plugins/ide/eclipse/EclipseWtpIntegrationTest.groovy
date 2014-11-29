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
package org.gradle.plugins.ide.eclipse

import org.junit.Test
import spock.lang.Issue

class EclipseWtpIntegrationTest extends AbstractEclipseIntegrationTest {
    void runSharedBuild() {
        generateEclipseFilesForWebProject()
    }

    @Test
    void projectDependenciesOfWebProjectAreMarkedAsJstUtilityProjects() {
        useSharedBuild = true;
        hasUtilityAndNoWebFacet("java1")
        hasUtilityAndNoWebFacet("java2")
        hasUtilityAndNoWebFacet("groovy")
    }

    @Test
    void projectDependenciesOfWebProjectHaveNecessaryNaturesAdded() {
        useSharedBuild = true;
        hasNecessaryNaturesAdded("java1")
        hasNecessaryNaturesAdded("java2")
        hasNecessaryNaturesAdded("groovy")
    }

    @Test
    void projectDependenciesOfWebProjectHaveNecessaryBuildersAdded() {
        useSharedBuild = true;
        hasNecessaryBuildersAdded("java1")
        hasNecessaryBuildersAdded("java2")
        hasNecessaryBuildersAdded("groovy")
    }

    @Test
    void projectDependenciesOfWebProjectHaveTrimmedDownComponentSettingsFile() {
        useSharedBuild = true;
        hasTrimmedDownComponentSettingsFile("java1", ["java2"], [ "src/main/java": "/", "src/main/resources": "/"])
        hasTrimmedDownComponentSettingsFile("java2", [], ["src/main/java": "/", "src/main/resources": "/"])
        hasTrimmedDownComponentSettingsFile("groovy", [], ["src/main/java": "/", "src/main/groovy": "/", "src/main/resources": "/"])
    }

    @Test
    void jarDependenciesOfUtilityProjectsAreFlaggedAsWtpDependency() {
        useSharedBuild = true;
        def classpath = parseClasspathFile(project: "java1")

        def firstLevelDep = classpath.classpathentry.find { it.@path.text().endsWith("myartifact-1.0.jar") }
        assert firstLevelDep.attributes.attribute.find { it.@name.text() == "org.eclipse.jst.component.dependency" }

        def secondLevelDep = classpath.classpathentry.find { it.@path.text().endsWith("myartifactdep-1.0.jar") }
        assert secondLevelDep.attributes.attribute.find { it.@name.text() == "org.eclipse.jst.component.dependency" }
    }

    @Test
    void allProjectDependenciesOfWebProjectAreAddedAsRuntimeDependencies() {
        useSharedBuild = true;
        def projectModules = parseComponentFile(project: "web", print: true)

		assert getDeployName(projectModules) == "web"
        assert getSourceAndDeployPaths(projectModules) ==
                ["src/main/java": "/WEB-INF/classes", "src/main/resources": "/WEB-INF/classes", "src/main/webapp": "/"]
        assert getHandleFilenames(projectModules) == ["java1", "java2", "groovy", "myartifact-1.0.jar", "myartifactdep-1.0.jar"] as Set
		assert getDependencyTypes(projectModules) == ["uses"] * 5 as Set
    }

    @Test
    @Issue("GRADLE-1415")
    void canUseSelfResolvingFiles() {
        useSharedBuild = false

        def buildFile = """
apply plugin: "war"
apply plugin: "eclipse"

dependencies {
    compile fileTree(dir: "libs", includes: ["*.jar"])
}
        """

        def libsDir = file("libs")
        libsDir.mkdir()
        libsDir.createFile("foo.jar")

        // when
        runEclipseTask(buildFile)

        // then
        libEntriesInClasspathFileHaveFilenames("foo.jar")
    }

    @Test
    @Issue("GRADLE-2526")
    void overwritesDependentModules() {
        useSharedBuild = false

        generateEclipseFilesForWebProject()
        def projectModules = parseComponentFile(project: "web")
        assert getHandleFilenames(projectModules) == ["java1", "java2", "groovy", "myartifact-1.0.jar", "myartifactdep-1.0.jar"] as Set

        generateEclipseFilesForWebProject("1.2.3")
        def projectModules2 = parseComponentFile(project: "web")
        assert getHandleFilenames(projectModules2) == ["java1", "java2", "groovy", "myartifact-1.2.3.jar", "myartifactdep-1.0.jar"] as Set
    }

    private generateEclipseFilesForWebProject(myArtifactVersion = "1.0") {
        def repoDir = file("repo")
        maven(repoDir).module("mygroup", "myartifact", myArtifactVersion).dependsOn("myartifactdep").publish()
        maven(repoDir).module("mygroup", "myartifactdep").publish()

        def settingsFile = file("settings.gradle")
        settingsFile << """
include("web")
include("java1")
include("java2")
include("groovy")
        """

        def webBuildFile = getFile(project: "web", "build.gradle")
        createJavaSourceDirs(webBuildFile)
        webBuildFile.parentFile.file("src/main/webapp").createDir()

        webBuildFile << """
apply plugin: "eclipse-wtp"
apply plugin: "war"

repositories {
    maven { url "${repoDir.toURI()}" }
}

dependencies {
    compile project(":java1")
    compile project(":groovy")
    runtime "mygroup:myartifact:$myArtifactVersion"
}
        """

        def java1BuildFile = getFile(project: "java1", "build.gradle")
        createJavaSourceDirs(java1BuildFile)

        java1BuildFile << """
apply plugin: "eclipse-wtp"
apply plugin: "java"

repositories {
    maven { url "${repoDir.toURI()}" }
}

dependencies {
    compile project(":java2")
    runtime "mygroup:myartifact:$myArtifactVersion"
}
        """

        def java2BuildFile = getFile(project: "java2", "build.gradle")
        createJavaSourceDirs(java2BuildFile)

        java2BuildFile << """
apply plugin: "eclipse-wtp"
apply plugin: "java"

repositories {
    maven { url "${repoDir.toURI()}" }
}

dependencies {
    runtime "mygroup:myartifact:$myArtifactVersion"
}
        """

        def groovyBuildFile = getFile(project: "groovy", "build.gradle")
        createJavaSourceDirs(groovyBuildFile)
        groovyBuildFile.parentFile.file("src/main/groovy").createDir()

        groovyBuildFile << """
apply plugin: "eclipse-wtp"
apply plugin: "groovy"
        """

        executer.usingSettingsFile(settingsFile).withTasks("eclipse").run()
    }

    /**
     * This method asserts that the given {@code project}s facet file fulfills the following requirements:
     * <ul>
     *     <li>contains the <code>jst.utility</code> facet
     *     <li>contains the <code>jst.java</code> (as <code>jst.utility</code> requires <code>jst.java</code>)</li>
     *     <li>does not contain <code>jst.web</code> (as <code>jst.web</code> and <code>jst.utility</code> are not allowed together)</li>
     * </ul>
     * For the WTP Project Facets documentation, see <a href="http://www.eclipse.org/webtools/development/proposals/WtpProjectFacets.html">here</a>.
     */
    private void hasUtilityAndNoWebFacet(String project) {
        def file = getFacetFile(project: project)
        def facetedProject = new XmlSlurper().parse(file)
        assert facetedProject.children().any{ it.name() == 'installed' && it.@facet.text() == 'jst.utility' && it.@version.text() == '1.0' }
        assert facetedProject.children().any{ it.name() == 'installed' && it.@facet.text() == 'jst.java' && it.@version.text() }
        assert !facetedProject.children().any{ it.@facet.text() == 'jst.web' }
    }

    private void hasNecessaryBuildersAdded(String project) {
        def projectDescription = parseProjectFile(project: project)
        assert projectDescription.buildSpec.buildCommand.name*.text().containsAll(
                ["org.eclipse.wst.common.project.facet.core.builder", "org.eclipse.wst.validation.validationbuilder"])
    }

    private void hasNecessaryNaturesAdded(String project) {
        def projectDescription = parseProjectFile(project: project)
        assert projectDescription.natures.nature*.text().containsAll(["org.eclipse.wst.common.project.facet.core.nature",
                "org.eclipse.jem.workbench.JavaEMFNature", "org.eclipse.wst.common.modulecore.ModuleCoreNature"])
    }

    private void hasTrimmedDownComponentSettingsFile(String projectName, List projects, Map sourceAndDeployPaths) {
        def projectModules = parseComponentFile(project: projectName, print: true)

        assert getDeployName(projectModules) == projectName
        assert getSourceAndDeployPaths(projectModules) == sourceAndDeployPaths
        assert getHandleFilenames(projectModules) == projects as Set
        assert getDependencyTypes(projectModules) == ['uses'] * projects.size() as Set
    }

    private String getDeployName(projectModules) {
		def names = projectModules."wb-module".@"deploy-name"*.text()
        assert names.size() == 1
        names[0]
	}

    private Map getSourceAndDeployPaths(projectModules) {
        Map result = [:]
        projectModules."wb-module"."wb-resource".collect {
            result.put(it.@"source-path".text(), it.@"deploy-path".text())
        }
        result
    }

	private Set getHandleFilenames(projectModules) {
		projectModules."wb-module"."dependent-module".@handle*.text().collect { it.substring(it.lastIndexOf("/") + 1) } as Set
	}

	private Set getDependencyTypes(projectModules) {
		projectModules."wb-module"."dependent-module"."dependency-type"*.text() as Set
	}
}
