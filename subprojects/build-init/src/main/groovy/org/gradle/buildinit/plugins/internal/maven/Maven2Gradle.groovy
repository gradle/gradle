/*
 * Copyright 2012 the original author or authors.
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


package org.gradle.buildinit.plugins.internal.maven

import groovy.util.slurpersupport.GPathResult
import org.apache.maven.project.MavenProject
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.buildinit.plugins.internal.BuildScriptBuilderFactory
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.util.RelativePathUtil

/**
 * This script obtains the effective POM of the current project, reads its dependencies
 * and generates build.gradle scripts. It also generates settings.gradle for multi-module builds. <br/>
 *
 * It currently supports both single-module and multi-module POMs, inheritance, dependency management and properties.
 */
class Maven2Gradle {
    private final BuildScriptBuilderFactory scriptBuilderFactory

    def dependentWars = []
    def workingDir
    def effectivePom

    Logger logger = Logging.getLogger(getClass())
    private Set<MavenProject> mavenProjects

    Maven2Gradle(Set<MavenProject> mavenProjects, File workingDir, BuildScriptBuilderFactory scriptBuilderFactory) {
        assert !mavenProjects.empty: "No Maven projects provided."
        this.mavenProjects = mavenProjects
        this.workingDir = workingDir.canonicalFile
        this.scriptBuilderFactory = scriptBuilderFactory
    }

    void convert() {
        //For now we're building the effective POM XML from the model
        //and then we parse the XML using slurper.
        //This way we don't have to rewrite the Maven2Gradle just yet.
        //Maven2Gradle should be rewritten (with coverage) so that feeds of the maven object model, not XML.
        def effectivePom = new MavenProjectXmlWriter().toXml(mavenProjects)
        //use the Groovy XmlSlurper library to parse the text string
        this.effectivePom = new XmlSlurper().parseText(effectivePom)

        def scriptBuilder = scriptBuilderFactory.script(BuildInitDsl.GROOVY, "build")

        def multimodule = this.effectivePom.name() == "projects"

        if (multimodule) {
            def allProjects = this.effectivePom.project
            def rootProject = allProjects[0]
            generateSettings(rootProject.artifactId, allProjects)

            def dependencies = [:];
            allProjects.each { project ->
                dependencies[project.artifactId.text()] = getDependencies(project, allProjects)
            }

            def allprojectsBuilder = scriptBuilder.allprojects()
            allprojectsBuilder.plugin(null, "maven")
            coordinatesForProject(rootProject, allprojectsBuilder)

            def subprojectsBuilder = scriptBuilder.subprojects()
            subprojectsBuilder.plugin(null, "java")
            compilerSettings(rootProject, subprojectsBuilder)
            packageSources(rootProject, subprojectsBuilder)

            repositoriesForProjects(allProjects, subprojectsBuilder)
            globalExclusions(rootProject, subprojectsBuilder)

            def commonDeps = dependencies.get(rootProject.artifactId.text())
            declareDependencies(commonDeps, subprojectsBuilder)
            testNg(commonDeps, subprojectsBuilder)

            modules(allProjects, false).each { module ->
                def id = module.artifactId.text()
                def moduleDependencies = dependencies.get(id)
                boolean warPack = module.packaging.text().equals("war")
                def moduleScriptBuilder = scriptBuilderFactory.script(BuildInitDsl.GROOVY, projectDir(module).path + "/build")

                if (module.groupId.text() != rootProject.groupId.text()) {
                    moduleScriptBuilder.propertyAssignment(null, "group", module.groupId.text())
                }

                if (warPack) {
                    moduleScriptBuilder.plugin(null, "war")
                    if (dependentWars.any { project ->
                        project.groupId.text() == module.groupId.text() &&
                            project.artifactId.text() == id
                    }) {
                        moduleScriptBuilder.taskPropertyAssignment(null, "jar", "Jar", "enabled", true)
                    }
                }

                descriptionForProject(module, moduleScriptBuilder)
                declareDependencies(moduleDependencies, moduleScriptBuilder)
                testNg(moduleDependencies, moduleScriptBuilder)

                packageTests(module, moduleScriptBuilder);

                moduleScriptBuilder.create().generate()
            }
            //TODO deployment
        } else {//simple
            generateSettings(this.effectivePom.artifactId, null);

            scriptBuilder.plugin(null, 'java')
            scriptBuilder.plugin(null, 'maven')
            coordinatesForProject(this.effectivePom, scriptBuilder)
            descriptionForProject(this.effectivePom, scriptBuilder)
            compilerSettings(this.effectivePom, scriptBuilder)
            globalExclusions(this.effectivePom, scriptBuilder)

            scriptBuilder.repositories().mavenLocal(null)
            Set<String> repoSet = new LinkedHashSet<String>();
            getRepositoriesForModule(this.effectivePom, repoSet)
            repoSet.each {
                scriptBuilder.repositories().maven(null, it)
            }

            def dependencies = getDependencies(this.effectivePom, null)
            declareDependencies(dependencies, scriptBuilder)
            testNg(dependencies, scriptBuilder)

            packageTests(this.effectivePom, scriptBuilder)
        }

        scriptBuilder.create().generate()
    }

    void declareDependencies(List<Dependency> dependencies, builder) {
        def dependenciesBuilder = builder.dependencies()
        dependencies.each { dep ->
            if (dep instanceof ProjectDependency) {
                dependenciesBuilder.projectDependency(dep.configuration, null, dep.projectPath)
            } else {
                dependenciesBuilder.dependency(dep.configuration, null, "$dep.group:$dep.module:$dep.version")
            }
        }
    }

    void globalExclusions(project, builder) {
        def enforcerPlugin = plugin('maven-enforcer-plugin', project)
        def enforceGoal = pluginGoal('enforce', enforcerPlugin)
        if (enforceGoal) {
            def block = builder.block(null, "configurations.all")
            enforceGoal.configuration.rules.bannedDependencies.excludes.childNodes().each {
                def tokens = it.text().tokenize(':')
                def params = [group: tokens[0]]
                if (tokens.size() > 1 && tokens[1] != '*') {
                    params.module = tokens[1]
                }
                block.methodInvocation(null, "exclude", params)
            }
        }
    }

    void testNg(List<Dependency> moduleDependencies, builder) {
        boolean testng = moduleDependencies.find { it instanceof ExternalDependency && it.groupId == 'org.testng' && it.module == 'testng' }
        if (testng) {
            builder.taskMethodInvocation(null, "test", "Test", "useTestNG")
        }
    }

    def modules = { allProjects, incReactors ->
        return allProjects.findAll { project ->
            def parentIsPartOfThisBuild = allProjects.find { proj ->
                proj.artifactId == project.parent.artifactId && proj.groupId == project.parent.groupId
            }
            project.parent.text().length() > 0 && parentIsPartOfThisBuild && (incReactors || project.packaging.text() != 'pom')
        }
    }

    def fqn = { project, allProjects ->
        def buffer = new StringBuilder()
        generateFqn(project, allProjects, buffer)
        return buffer.toString()
    }

    private generateFqn(GPathResult project, GPathResult allProjects, StringBuilder buffer) {
        def artifactId = project.artifactId.text()
        buffer.insert(0, ":${artifactId}")
        //we don't need the top-level parent in gradle, so we stop on it
        if (getModuleIdentifier(project.parent) != getModuleIdentifier(allProjects[0])) {
            def parentInBuild = allProjects.find { proj ->
                getModuleIdentifier(proj) == getModuleIdentifier(project.parent)
            }
            if (parentInBuild) {
                generateFqn(parentInBuild, allProjects, buffer)
            }
        }
    }

    private ModuleIdentifier getModuleIdentifier(GPathResult project) {
        def artifactId = project.artifactId.text()
        def groupId = project.groupId ? project.groupId.text() : project.parent.groupId.text()
        return new DefaultModuleIdentifier(groupId, artifactId)
    }

    private void coordinatesForProject(project, builder) {
        builder.propertyAssignment(null, "group", project.groupId.text())
        builder.propertyAssignment(null, "version", project.version.text())
    }

    private void descriptionForProject(project, builder) {
        if (project.name.text()) {
            builder.propertyAssignment(null, "description", project.name.text())
        }
    }

    private void repositoriesForProjects(projects, builder) {
        builder.repositories().mavenLocal(null)
        def repoSet = new LinkedHashSet<String>();
        projects.each {
            getRepositoriesForModule(it, repoSet)
        }
        repoSet.each {
            builder.repositories().maven(null, it)
        }
    }

    private void getRepositoriesForModule(module, repoSet) {
        module.repositories.repository.each {
            repoSet.add(it.url.text())
        }
        // No need to include plugin repos, as they won't be used by Gradle
    }

    private List<Dependency> getDependencies(project, allProjects) {
        // use GPath to navigate the object hierarchy and retrieve the collection of dependency nodes.
        def dependencies = project.dependencies.dependency
        def war = project.packaging == "war"

        def compileTimeScope = []
        def runTimeScope = []
        def testScope = []
        def providedScope = []
        def systemScope = []

        //cleanup duplicates from parent
        // using Groovy Looping and mapping a Groovy Closure to each element, we collect together all
        // the dependency nodes into corresponding collections depending on their scope value.
        dependencies.each() {
            if (!duplicateDependency(it, project, allProjects)) {
                def scope = (elementHasText(it.scope)) ? it.scope : "compile"
                switch (scope) {
                    case "compile":
                        compileTimeScope.add(it)
                        break
                    case "test":
                        testScope.add(it)
                        break
                    case "provided":
                        providedScope.add(it)
                        break
                    case "runtime":
                        runTimeScope.add(it)
                        break
                    case "system":
                        systemScope.add(it)
                        break
                }
            }
        }

        /**
         * print function then checks the exclusions node to see if it exists, if
         * so it branches off, otherwise we call our simple print function
         */
        def createGradleDep = { String scope, List<Dependency> result, mavenDependency ->
            def projectDep = allProjects.find { prj ->
                return prj.artifactId.text() == mavenDependency.artifactId.text() && prj.groupId.text() == mavenDependency.groupId.text()
            }

            if (projectDep) {
                createProjectDependency(projectDep, result, scope, allProjects)
            } else {
                if (!war && scope == 'providedCompile') {
                    scope = 'compileOnly'
                }
                createExternalDependency(mavenDependency, result, scope)
            }
        }

        def result = []
        if (!compileTimeScope.isEmpty() || !runTimeScope.isEmpty() || !testScope.isEmpty() || !providedScope.isEmpty() || !systemScope.isEmpty()) {
// for each collection, one at a time, we take each element and call our print function
            if (!compileTimeScope.isEmpty()) {
                compileTimeScope.each() { createGradleDep("compile", result, it) }
            }
            if (!runTimeScope.isEmpty()) {
                runTimeScope.each() { createGradleDep("runtime", result, it) }
            }
            if (!testScope.isEmpty()) {
                testScope.each() { createGradleDep("testCompile", result, it) }
            }
            if (!providedScope.isEmpty()) {
                providedScope.each() { createGradleDep("providedCompile", result, it) }
            }
            if (!systemScope.isEmpty()) {
                systemScope.each() { createGradleDep("system", result, it) }
            }
        }
        return result
    }

    private void compilerSettings(project, builder) {
        def configuration = plugin('maven-compiler-plugin', project).configuration
        def source = configuration.source.text() ?: '1.8'
        builder.propertyAssignment(null, "sourceCompatibility", source)

        def target = configuration.target.text() ?: '1.8'
        if (target != source) {
            builder.propertyAssignment(null, "targetCompatibility", target)
        }

        def encoding = project.properties.'project.build.sourceEncoding'.text()
        if (encoding) {
            builder.taskPropertyAssignment(null, "JavaCompile", "options.encoding", encoding)
        }
    }

    def plugin = { artifactId, project ->
        project.build.plugins.plugin.find { pluginTag ->
            pluginTag.artifactId.text() == artifactId
        }
    }

    def pluginGoal = { goalName, plugin ->
        plugin.executions.execution.find { exec ->
            exec.goals.goal.find { gl ->
                gl.text().startsWith(goalName)
            }
        }
    }

    void packageTests(project, builder) {
        def jarPlugin = plugin('maven-jar-plugin', project)
        if (pluginGoal('test-jar', jarPlugin)) {
            def taskConfigBuilder = builder.taskRegistration(null, "packageTests", "Jar")
            taskConfigBuilder.propertyAssignment(null, "classifier", "tests")
            taskConfigBuilder.methodInvocation(null, "from", builder.propertyExpression("sourceSets.test.output"))
            builder.methodInvocation(null, "artifacts.archives", builder.propertyExpression("tasks.packageTests"))
        }
    }

    void packageSources(project, builder) {
        def sourcePlugin = plugin('maven-source-plugin', project)
        def sourceSets = []
        if (sourcePlugin) {
            if (pluginGoal('jar', sourcePlugin)) {
                sourceSets += 'main'
            } else if (pluginGoal('test-jar', sourcePlugin)) {
                sourceSets += 'test'
            }
        }
        if (!sourceSets.empty) {
            def taskConfigBuilder = builder.taskRegistration(null, "packageSources", "Jar")
            taskConfigBuilder.propertyAssignment(null, "classifier", "sources")
            sourceSets.each { sourceSet ->
                taskConfigBuilder.methodInvocation(null, "from", builder.propertyExpression("sourceSets.${sourceSet}.allSource"))
            }
            builder.methodInvocation(null, "artifacts.archives", builder.propertyExpression("tasks.packageSources"))
        }
    }

    private boolean duplicateDependency(dependency, project, allProjects) {
        def parentTag = project.parent
        if (allProjects == null || parentTag.isEmpty()) {//simple project or no parent
            return false;
        } else {
            def parent = allProjects.find {
                it.groupId.equals(parentTag.groupId) && it.artifactId.equals(parentTag.artifactId)
            }
            def duplicate = parent.dependencies.dependency.any {
                it.groupId.equals(dependency.groupId) && it.artifactId.equals(dependency.artifactId)
            }
            if (duplicate) {
                return true;
            } else {
                duplicateDependency(dependency, parent, allProjects)
            }
        }
    }

    def artifactId = { File dir ->
        return new XmlSlurper().parse(new File(dir, 'pom.xml')).artifactId.text()
    }

    def projectDir = { project ->
        return new File(project.build.directory.text()).parentFile
    }

    private void generateSettings(def mvnProjectName, def projects) {
        def scriptBuilder = scriptBuilderFactory.script(BuildInitDsl.GROOVY, "settings")

        scriptBuilder.propertyAssignment(null, "rootProject.name", mvnProjectName as String)

        def modulePoms = modules(projects, true)

        List<String> moduleNames = new ArrayList<String>();
        def artifactIdToDir = [:]
        if (projects) {
            modulePoms.each { project ->
                def fqn = fqn(project, projects)
                File projectDirectory = projectDir(project)
                // don't add project if it's the rootproject
                if (!workingDir.equals(projectDirectory)) {
                    moduleNames.add(fqn)

                    // Calculate the path to the project, ignore this path if it's the default value
                    def relativePath = RelativePathUtil.relativePath(workingDir, projectDirectory)
                    if (fqn != ":${relativePath}") {
                        artifactIdToDir[fqn] = relativePath
                    }
                }
            }
        }

        moduleNames.each {
            scriptBuilder.methodInvocation(null, "include", it)
        }
        artifactIdToDir.each { entry ->
            def dirExpression = scriptBuilder.methodInvocationExpression("file", entry.value)
            scriptBuilder.propertyAssignment(null, "project('$entry.key').projectDir", dirExpression)
        }
        scriptBuilder.create().generate()
    }

    private def createExternalDependency(mavenDependency, List<Dependency> result, scope) {
        def classifier = mavenDependency.classifier ? mavenDependency.classifier.text() : null
        def exclusions = mavenDependency.exclusions.exclusion.collect { it.artifactId.text() }
        result.add(new ExternalDependency(scope, mavenDependency.groupId.text(), mavenDependency.artifactId.text(), mavenDependency.version.text(), classifier, exclusions))
    }

    private def createProjectDependency(projectDep, List<Dependency> result, String scope, allProjects) {
        if (projectDep.packaging.text() == 'war') {
            dependentWars += projectDep
        }
        result.add(new ProjectDependency(scope, fqn(projectDep, allProjects)))
    }

    /**
     * Check to see if the selected node has content
     */
    private boolean elementHasText(it) {
        return it.text().length() != 0
    }
}
