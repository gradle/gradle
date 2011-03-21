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
package org.gradle.integtests

import org.junit.Test

// TODO: run prepareWebProject() only once per class for performance reasons (not as simply as it seems)
class EclipseWtpIntegrationTest extends AbstractEclipseIntegrationTest {
    @Test
    void projectDependenciesOfWebProjectAreMarkedAsJstUtilityProjects() {
        prepareWebProject()

        hasUtilityFacet("java1")
        hasUtilityFacet("java2")
        hasUtilityFacet("groovy")
    }

    @Test
    void projectDependenciesOfWebProjectHaveNecessaryNaturesAdded() {
        prepareWebProject()

        hasNecessaryNaturesAdded("java1")
        hasNecessaryNaturesAdded("java2")
        hasNecessaryNaturesAdded("groovy")
    }

    @Test
    void projectDependenciesOfWebProjectHaveNecessaryBuildersAdded() {
        prepareWebProject()

        hasNecessaryBuildersAdded("java1")
        hasNecessaryBuildersAdded("java2")
        hasNecessaryBuildersAdded("groovy")
    }

    @Test
    void projectDependenciesOfWebProjectHaveTrimmedDownComponentSettingsFile() {
        prepareWebProject()

        hasTrimmedDownComponentSettingsFile("java1", "src/main/java", "src/main/resources")
        hasTrimmedDownComponentSettingsFile("java2", "src/main/java", "src/main/resources")
        hasTrimmedDownComponentSettingsFile("groovy", "src/main/java", "src/main/groovy", "src/main/resources")
    }

    @Test
    void jarDependenciesOfUtilityProjectsAreFlaggedAsRuntimeDependency() {
        prepareWebProject()

        def classpath = parseClasspathFile(project: "java1")

        def firstLevelDep = classpath.classpathentry.find { it.@path.text().endsWith("myartifact-1.0.jar") }
        assert firstLevelDep.attributes.attribute.find { it.@name.text() == "org.eclipse.jst.component.dependency" }

        def secondLevelDep = classpath.classpathentry.find { it.@path.text().endsWith("myartifactdep-1.0.jar") }
        assert secondLevelDep.attributes.attribute.find { it.@name.text() == "org.eclipse.jst.component.dependency" }

    }

    @Test
    void allProjectDependenciesOfWebProjectAreAddedAsRuntimeDependencies() {
        prepareWebProject()

        def projectModules = parseComponentFile(project: "web")

		assert getDeployName(projectModules) == "web"
		assert getHandleFilenames(projectModules) == ["java1", "java2", "groovy", "myartifact-1.0.jar", "myartifactdep-1.0.jar"] as Set
		assert getDependencyTypes(projectModules) == ["uses"] * 5 as Set
    }

    private prepareWebProject() {
        def repoDir = file("repo")
        publishArtifact(repoDir, "mygroup", "myartifact", "myartifactdep")
        publishArtifact(repoDir, "mygroup", "myartifactdep")

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
apply plugin: "eclipse"
apply plugin: "war"

repositories {
    mavenRepo(name: "repo", urls: "${repoDir.toURI()}")
}

dependencies {
    compile project(":java1")
    compile project(":groovy")
    runtime "mygroup:myartifact:1.0"
}
        """

        def java1BuildFile = getFile(project: "java1", "build.gradle")
        createJavaSourceDirs(java1BuildFile)

        java1BuildFile << """
apply plugin: "eclipse"
apply plugin: "java"

repositories {
    mavenRepo(name: "repo", urls: "${repoDir.toURI()}")
}

dependencies {
    compile project(":java2")
    runtime "mygroup:myartifact:1.0"
}
        """

        def java2BuildFile = getFile(project: "java2", "build.gradle")
        createJavaSourceDirs(java2BuildFile)

        java2BuildFile << """
apply plugin: "eclipse"
apply plugin: "java"

repositories {
    mavenRepo(name: "repo", urls: "${repoDir.toURI()}")
}

dependencies {
    runtime "mygroup:myartifact:1.0"
}
        """

        def groovyBuildFile = getFile(project: "groovy", "build.gradle")
        createJavaSourceDirs(groovyBuildFile)
        groovyBuildFile.parentFile.file("src/main/groovy").createDir()

        groovyBuildFile << """
apply plugin: "eclipse"
apply plugin: "groovy"
        """

        executer.usingSettingsFile(settingsFile).withTasks("eclipse").run()
    }

    private void hasUtilityFacet(String project) {
        def file = getFacetFile(project: project)
        def facetedProject = new XmlSlurper().parse(file)
        assert facetedProject.children().any { it.@facet.text() == "jst.utility" && it.@version.text() == "1.0" }
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

    private void hasTrimmedDownComponentSettingsFile(String projectName, String... sourcePaths) {
        def projectModules = parseComponentFile(project: projectName, print: true)

        assert getDeployName(projectModules) == projectName
        assert getSourcePaths(projectModules) == sourcePaths as Set
        assert getDeployPaths(projectModules) == ["/"] * sourcePaths.size() as Set
        assert getHandleFilenames(projectModules) == [] as Set
        assert getDependencyTypes(projectModules) == [] as Set
    }

    private String getDeployName(projectModules) {
		def names = projectModules."wb-module".@"deploy-name"*.text()
        assert names.size() == 1
        names[0]
	}

    private Set getSourcePaths(projectModules) {
        projectModules."wb-module"."wb-resource".@"source-path"*.text() as Set
    }

    private Set getDeployPaths(projectModules) {
        projectModules."wb-module"."wb-resource".@"deploy-path"*.text() as Set
    }

	private Set getHandleFilenames(projectModules) {
		projectModules."wb-module"."dependent-module".@handle*.text().collect { it.substring(it.lastIndexOf("/") + 1) } as Set
	}

	private Set getDependencyTypes(projectModules) {
		projectModules."wb-module"."dependent-module"."dependency-type"*.text() as Set
	}
}
