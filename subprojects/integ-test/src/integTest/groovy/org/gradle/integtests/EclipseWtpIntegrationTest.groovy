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

// TODO: run prepareWebProject() only once per class (for performance reasons)
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

    private void hasNecessaryNaturesAdded(String project) {
        def projectDescription = parseProjectFile(project: project)
        assert projectDescription.natures.nature*.text().containsAll(["org.eclipse.wst.common.project.facet.core.nature",
                "org.eclipse.jem.workbench.JavaEMFNature", "org.eclipse.wst.common.modulecore.ModuleCoreNature"])
    }

    @Test
    void projectDependenciesOfWebProjectHaveNecessaryBuildersAdded() {
        prepareWebProject()

        hasNecessaryBuildersAdded("java1")
        hasNecessaryBuildersAdded("java2")
        hasNecessaryBuildersAdded("groovy")
    }

    private void hasNecessaryBuildersAdded(String project) {
        def projectDescription = parseProjectFile(project: project)
        assert projectDescription.buildSpec.buildCommand.name*.text().containsAll(
                ["org.eclipse.wst.common.project.facet.core.builder", "org.eclipse.wst.validation.validationbuilder"])
    }

    @Test
    void projectDependenciesOfWebProjectHaveComponentSettingsFile() {
        prepareWebProject()

        def projectModules = parseComponentFile(project: "java1")

        assert getDeployNames(projectModules) == ["java1"]
        assert getHandleFilenames(projectModules) == ["java2", "myartifact-1.0.jar", "myartifactdep-1.0.jar"] as Set
        assert getDependencyTypes(projectModules) == ["uses", "uses", "uses"] as Set
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

        def projectModules = parseComponentFile()

		assert getDeployNames(projectModules) == ["root"]
		assert getHandleFilenames(projectModules) == ["java1", "java2", "groovy", "myartifact-1.0.jar", "myartifactdep-1.0.jar"] as Set
		assert getDependencyTypes(projectModules) == ["uses", "uses", "uses", "uses", "uses"] as Set
    }

    private prepareWebProject() {
        def repoDir = file("repo")
        publishArtifact(repoDir, "mygroup", "myartifact", "myartifactdep")
        publishArtifact(repoDir, "mygroup", "myartifactdep")

        runEclipseTask """
rootProject.name = "root"

include("java1")
include("java2")
include("groovy")
        """, """
allprojects {
    apply plugin: "java"
    apply plugin: "eclipse"

    repositories {
        mavenRepo(name: "repo", urls: "${repoDir.toURI()}")
    }
}

apply plugin: "war"

dependencies {
    compile project(":java1")
    compile project(":groovy")
    runtime "mygroup:myartifact:1.0"
}

project("java1") {
    dependencies {
        compile project(":java2")
        runtime "mygroup:myartifact:1.0"
    }
}

project("java2") {
    dependencies {
        runtime "mygroup:myartifact:1.0"
    }
}

project("groovy") {
    apply plugin: "groovy"
}
        """
    }

    private void hasUtilityFacet(String project) {
        def file = getFacetFile(project: project)
        def facetedProject = new XmlSlurper().parse(file)
        assert facetedProject.children().any { it.@facet.text() == "jst.utility" && it.@version.text() == "1.0" }
    }

	private getDeployNames(projectModules) {
		projectModules."wb-module".@"deploy-name"*.text()
	}

	private getHandleFilenames(projectModules) {
		projectModules."wb-module"."dependent-module".@handle*.text().collect { it.substring(it.lastIndexOf("/") + 1) } as Set
	}

	private getDependencyTypes(projectModules) {
		projectModules."wb-module"."dependent-module"."dependency-type"*.text() as Set
	}
}
