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

import groovy.transform.CompileStatic
import org.apache.maven.model.Plugin
import org.apache.maven.model.PluginExecution
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.util.xml.Xpp3Dom
import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.buildinit.plugins.internal.BuildScriptBuilder
import org.gradle.buildinit.plugins.internal.BuildScriptBuilderFactory
import org.gradle.buildinit.plugins.internal.CrossConfigurationScriptBlockBuilder
import org.gradle.buildinit.plugins.internal.DependenciesBuilder
import org.gradle.buildinit.plugins.internal.RepositoriesBuilder
import org.gradle.buildinit.plugins.internal.ScriptBlockBuilder
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.util.RelativePathUtil

import javax.annotation.Nullable

/**
 * This script obtains the effective POM of the current project, reads its dependencies
 * and generates build.gradle scripts. It also generates settings.gradle for multi-module builds. <br/>
 *
 * It currently supports both single-module and multi-module POMs, inheritance, dependency management and properties.
 */
@CompileStatic
class Maven2Gradle {
    private final BuildScriptBuilderFactory scriptBuilderFactory

    private Set<MavenProject> allProjects
    private MavenProject rootProject

    private List<MavenProject> dependentWars = new ArrayList<>();
    private File workingDir

    Maven2Gradle(Set<MavenProject> mavenProjects, File workingDir, BuildScriptBuilderFactory scriptBuilderFactory) {
        assert !mavenProjects.empty: "No Maven projects provided."
        this.allProjects = mavenProjects
        this.rootProject = mavenProjects.iterator().next()
        this.workingDir = workingDir.canonicalFile
        this.scriptBuilderFactory = scriptBuilderFactory
    }

    void convert() {
        def scriptBuilder = scriptBuilderFactory.script(BuildInitDsl.GROOVY, "build")
        def multimodule = !rootProject.getModules().isEmpty()

        if (multimodule) {
            generateSettings(rootProject.getArtifactId(), allProjects)

            Map<String, List<Dependency>> dependencies = new LinkedHashMap<>();
            allProjects.each { project ->
                dependencies[project.artifactId] = getDependencies(project, allProjects)
            }

            def allprojectsBuilder = scriptBuilder.allprojects()
            coordinatesForProject(rootProject, allprojectsBuilder)

            def subprojectsBuilder = scriptBuilder.subprojects()
            subprojectsBuilder.plugin(null, "java")
            subprojectsBuilder.plugin(null, "maven-publish")
            compilerSettings(rootProject, subprojectsBuilder)

            repositoriesForProjects(allProjects, subprojectsBuilder)
            globalExclusions(rootProject, subprojectsBuilder)

            def commonDeps = dependencies.get(rootProject.artifactId)
            declareDependencies(commonDeps, subprojectsBuilder)
            testNg(commonDeps, subprojectsBuilder)
            configurePublishing(subprojectsBuilder, packagesSources(rootProject))

            modules(allProjects, false).each { module ->
                def id = module.artifactId
                def moduleDependencies = dependencies.get(id)
                def warPack = module.packaging.equals("war")
                def moduleScriptBuilder = scriptBuilderFactory.script(BuildInitDsl.GROOVY, projectDir(module).path + "/build")

                if (module.groupId != rootProject.groupId) {
                    moduleScriptBuilder.propertyAssignment(null, "group", module.groupId)
                }

                if (warPack) {
                    moduleScriptBuilder.plugin(null, "war")
                    if (dependentWars.any { project ->
                        project.groupId == module.groupId &&
                            project.artifactId == id
                    }) {
                        moduleScriptBuilder.taskPropertyAssignment(null, "jar", "Jar", "enabled", true)
                    }
                }

                descriptionForProject(module, moduleScriptBuilder)
                declareDependencies(moduleDependencies, wrap(moduleScriptBuilder))
                testNg(moduleDependencies, wrap(moduleScriptBuilder))

                if (packageTests(module, moduleScriptBuilder)) {
                    moduleScriptBuilder.methodInvocation(null, "publishing.publications.maven.artifact", moduleScriptBuilder.propertyExpression("testsJar"))
                }
                if (packagesJavadocs(module)) {
                    def javaExtension = moduleScriptBuilder.block(null, "java")
                    javaExtension.methodInvocation(null, "withJavadocJar")
                }

                moduleScriptBuilder.create().generate()
            }
            //TODO deployment
        } else {
            generateSettings(this.rootProject.artifactId, Collections.<MavenProject>emptySet())

            scriptBuilder.plugin(null, 'java')
            scriptBuilder.plugin(null, 'maven-publish')
            coordinatesForProject(this.rootProject, wrap(scriptBuilder))
            descriptionForProject(this.rootProject, scriptBuilder)
            compilerSettings(this.rootProject, wrap(scriptBuilder))
            globalExclusions(this.rootProject, wrap(scriptBuilder))
            def testsJarTaskGenerated = packageTests(this.rootProject, scriptBuilder)
            configurePublishing(wrap(scriptBuilder), packagesSources(this.rootProject), testsJarTaskGenerated, packagesJavadocs(this.rootProject))

            scriptBuilder.repositories().mavenLocal(null)
            Set<String> repoSet = new LinkedHashSet<String>()
            getRepositoriesForModule(this.rootProject, repoSet)
            repoSet.each {
                scriptBuilder.repositories().maven(null, it)
            }

            def dependencies = getDependencies(this.rootProject, null)
            declareDependencies(dependencies, new ScriptBuilderWrapper(scriptBuilder))
            testNg(dependencies, wrap(scriptBuilder))

        }

        scriptBuilder.create().generate()
    }

    def configurePublishing(CrossConfigurationScriptBlockBuilder builder, boolean publishesSources = false, boolean testsJarTaskGenerated = false, boolean publishesJavadoc = false) {
        if (publishesSources || publishesJavadoc) {
            def javaExtension = builder.block(null, "java")
            if (publishesSources) {
                javaExtension.methodInvocation(null, "withSourcesJar")
            }
            if (publishesJavadoc) {
                javaExtension.methodInvocation(null, "withJavadocJar")
            }
        }
        def publishing = builder.block(null, "publishing")
        def publications = publishing.block(null, "publications")
        def mavenPublication = publications.block(null, "maven(MavenPublication)")
        mavenPublication.methodInvocation(null, "from", mavenPublication.propertyExpression("components.java"))
        if (testsJarTaskGenerated) {
            mavenPublication.methodInvocation(null, "artifact", mavenPublication.propertyExpression("testsJar"))
        }
    }

    void declareDependencies(List<Dependency> dependencies, CrossConfigurationScriptBlockBuilder builder) {
        def dependenciesBuilder = builder.dependencies()
        dependencies.each { dep ->
            if (dep instanceof ProjectDependency) {
                dependenciesBuilder.projectDependency(dep.configuration, null, dep.projectPath)
            } else {
                ExternalDependency extDep = (ExternalDependency) dep
                dependenciesBuilder.dependency(dep.configuration, null, "$extDep.groupId:$extDep.module:$extDep.version")
            }
        }
    }

    void globalExclusions(MavenProject project, CrossConfigurationScriptBlockBuilder builder) {
        Plugin enforcerPlugin = plugin('maven-enforcer-plugin', project)
        PluginExecution enforceGoal = pluginGoal('enforce', enforcerPlugin)
        if (enforceGoal) {
            def block = builder.block(null, "configurations.all")
            Xpp3Dom configuration = (Xpp3Dom) enforceGoal.configuration
            configuration.getChild("rules").getChild("bannedDependencies").getChild("excludes").getChildren().each {
                def tokens = it.getValue().split(":")
                def params = [group: tokens[0]]
                if (tokens.size() > 1 && tokens[1] != '*') {
                    params.module = tokens[1]
                }
                block.methodInvocation(null, "exclude", params)
            }
        }
    }

    void testNg(List<Dependency> moduleDependencies, CrossConfigurationScriptBlockBuilder builder) {
        boolean testng = moduleDependencies.find { it instanceof ExternalDependency && it.groupId == 'org.testng' && it.module == 'testng' }
        if (testng) {
            builder.taskMethodInvocation(null, "test", "Test", "useTestNG")
        }
    }

    private Set<MavenProject> modules(Set<MavenProject> projects, boolean incReactors) {
        return projects.findAll { project ->
            def parentIsPartOfThisBuild = projects.find { proj ->
                project.parent != null && proj.artifactId == project.parent.artifactId && proj.groupId == project.parent.groupId
            }
            parentIsPartOfThisBuild && (incReactors || project.packaging != 'pom')
        }
    }

    private String fqn(MavenProject project, Set<MavenProject> allProjects) {
        def buffer = new StringBuilder()
        generateFqn(project, allProjects, buffer)
        return buffer.toString()
    }

    private generateFqn(MavenProject project, Set<MavenProject> allProjects, StringBuilder buffer) {
        def artifactId = project.artifactId
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

    private ModuleIdentifier getModuleIdentifier(MavenProject project) {
        def artifactId = project.artifactId
        def groupId = project.groupId ? project.groupId : project.parent.groupId
        return DefaultModuleIdentifier.newId(groupId, artifactId)
    }

    private void coordinatesForProject(MavenProject project, CrossConfigurationScriptBlockBuilder builder) {
        builder.propertyAssignment(null, "group", project.groupId)
        builder.propertyAssignment(null, "version", project.version)
    }

    private void descriptionForProject(MavenProject project, BuildScriptBuilder builder) {
        if (project.name) {
            builder.propertyAssignment(null, "description", project.name)
        }
    }

    private void repositoriesForProjects(Set<MavenProject> projects, CrossConfigurationScriptBlockBuilder builder) {
        builder.repositories().mavenLocal(null)
        def repoSet = new LinkedHashSet<String>()
        projects.each {
            getRepositoriesForModule(it, repoSet)
        }
        repoSet.each {
            builder.repositories().maven(null, it)
        }
    }

    private void getRepositoriesForModule(MavenProject module, Set<String> repoSet) {
        module.repositories.each {
            repoSet.add(it.url)
        }
        // No need to include plugin repos, as they won't be used by Gradle
    }

    private List<Dependency> getDependencies(MavenProject project, Set<MavenProject> allProjects) {
        List<org.apache.maven.model.Dependency> dependencies = new ArrayList<>();
        collectAllDependencies(project, dependencies)

        def war = project.packaging == "war"

        List<org.apache.maven.model.Dependency> compileTimeScope = new ArrayList<>();
        List<org.apache.maven.model.Dependency> runTimeScope = new ArrayList<>();
        List<org.apache.maven.model.Dependency> testScope = new ArrayList<>();
        List<org.apache.maven.model.Dependency> providedScope = new ArrayList<>();
        List<org.apache.maven.model.Dependency> systemScope = new ArrayList<>();

        //cleanup duplicates from parent
        dependencies.each() { org.apache.maven.model.Dependency mavenDependency ->
            if (!duplicateDependency(mavenDependency, project, allProjects)) {
                def scope = !mavenDependency.scope.isEmpty() ? mavenDependency.scope : "compile"
                switch (scope) {
                    case "compile":
                        compileTimeScope.add(mavenDependency)
                        break
                    case "test":
                        testScope.add(mavenDependency)
                        break
                    case "provided":
                        providedScope.add(mavenDependency)
                        break
                    case "runtime":
                        runTimeScope.add(mavenDependency)
                        break
                    case "system":
                        systemScope.add(mavenDependency)
                        break
                }
            }
        }

        List<Dependency> result = new ArrayList<>();
        if (!compileTimeScope.isEmpty() || !runTimeScope.isEmpty() || !testScope.isEmpty() || !providedScope.isEmpty() || !systemScope.isEmpty()) {
// for each collection, one at a time, we take each element and call our print function
            if (!compileTimeScope.isEmpty()) {
                compileTimeScope.each() { createGradleDep("implementation", result, it, war) }
            }
            if (!runTimeScope.isEmpty()) {
                runTimeScope.each() { createGradleDep("runtimeOnly", result, it, war) }
            }
            if (!testScope.isEmpty()) {
                testScope.each() { createGradleDep("testImplementation", result, it, war) }
            }
            if (!providedScope.isEmpty()) {
                providedScope.each() { createGradleDep("providedCompile", result, it, war) }
            }
            if (!systemScope.isEmpty()) {
                systemScope.each() { createGradleDep("system", result, it, war) }
            }
        }
        return result
    }

    /**
     * print function then checks the exclusions node to see if it exists, if
     * so it branches off, otherwise we call our simple print function
     */
    private void createGradleDep(String scope, List<Dependency> result, org.apache.maven.model.Dependency mavenDependency, boolean war) {
        MavenProject projectDep = allProjects.find { prj ->
            return prj.artifactId == mavenDependency.artifactId && prj.groupId == mavenDependency.groupId
        }

        if (projectDep != null) {
            createProjectDependency(projectDep, result, scope, allProjects)
        } else {
            if (!war && scope == 'providedCompile') {
                scope = 'compileOnly'
            }
            createExternalDependency(mavenDependency, result, scope)
        }
    }

    private void compilerSettings(MavenProject project, CrossConfigurationScriptBlockBuilder builder) {
        String source = "1.8";
        String target = "1.8";

        Plugin compilerPlugin = plugin("maven-compiler-plugin", project)
        if (compilerPlugin != null) {
            Xpp3Dom configuration = (Xpp3Dom) compilerPlugin.configuration
            source = configuration.getChild("source").getValue();
            target = configuration.getChild("target").getValue();
        }

        builder.propertyAssignment(null, "sourceCompatibility", source)
        if (target != source) {
            builder.propertyAssignment(null, "targetCompatibility", target)
        }

        def encoding = project.getProperties().get("project.build.sourceEncoding")
        if (encoding) {
            builder.taskPropertyAssignment(null, "JavaCompile", "options.encoding", encoding)
        }
    }

    private Plugin plugin(String artifactId, MavenProject project) {
        project.build.plugins.find { pluginTag ->
            pluginTag.artifactId == artifactId
        }
    }

    private PluginExecution pluginGoal(String goalName, Plugin plugin) {
        if (plugin == null) {
            return null;
        }
        plugin.executions.find { exec ->
            exec.goals.find { gl ->
                gl.startsWith(goalName)
            }
        }
    }

    boolean packagesSources(MavenProject project) {
        def sourcePlugin = plugin("maven-source-plugin", project)
        return sourcePlugin && pluginGoal("jar", sourcePlugin)
    }

    boolean packageTests(MavenProject project, BuildScriptBuilder builder) {
        def jarPlugin = plugin("maven-jar-plugin", project)
        if (pluginGoal("test-jar", jarPlugin)) {
            builder.taskRegistration(null, "testsJar", "Jar") { task ->
                task.propertyAssignment(null, "archiveClassifier", "tests")
                task.methodInvocation(null, "from", builder.propertyExpression("sourceSets.test.output"))
            }
            return true
        }
        return false
    }

    boolean packagesJavadocs(MavenProject project) {
        def jarPlugin = plugin("maven-javadoc-plugin", project)
        return jarPlugin && pluginGoal("jar", jarPlugin)
    }

    private boolean duplicateDependency(org.apache.maven.model.Dependency dependency, MavenProject project, Set<MavenProject> allProjects) {
        MavenProject parent = project.parent
        if (allProjects == null || !allProjects.contains(parent)) { // simple project or no parent in the build
            return false
        } else {
            def duplicate = parent.dependencies.any {
                it.groupId.equals(dependency.groupId) && it.artifactId.equals(dependency.artifactId)
            }
            if (duplicate) {
                return true
            } else {
                duplicateDependency(dependency, parent, allProjects)
            }
        }
    }

    private File projectDir(MavenProject project) {
        return new File(project.build.directory).parentFile
    }

    private void generateSettings(String mvnProjectName, Set<MavenProject> projects) {
        def scriptBuilder = scriptBuilderFactory.script(BuildInitDsl.GROOVY, "settings")

        scriptBuilder.propertyAssignment(null, "rootProject.name", mvnProjectName as String)

        def modulePoms = modules(projects, true)

        List<String> moduleNames = new ArrayList<String>()
        Map<String, String> artifactIdToDir = [:]
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

    private void createExternalDependency(org.apache.maven.model.Dependency mavenDependency, List<Dependency> result, String scope) {
        String classifier = mavenDependency.classifier ? mavenDependency.classifier : null
        List<String> exclusions = mavenDependency.exclusions.collect { it.artifactId }
        result.add(new ExternalDependency(scope, mavenDependency.groupId, mavenDependency.artifactId, mavenDependency.version, classifier, exclusions))
    }

    private void createProjectDependency(MavenProject projectDep, List<Dependency> result, String scope, Set<MavenProject> allProjects) {
        if (projectDep.packaging == 'war') {
            dependentWars += projectDep
        }
        result.add(new ProjectDependency(scope, fqn(projectDep, allProjects)))
    }

    static private CrossConfigurationScriptBlockBuilder wrap(BuildScriptBuilder scriptBuilder) {
        return new ScriptBuilderWrapper(scriptBuilder);
    }

    private void collectAllDependencies(MavenProject mavenProject, List<org.apache.maven.model.Dependency> dependencies) {
        if (mavenProject.parent != null) {
            collectAllDependencies(mavenProject.parent, dependencies)
        }
        dependencies.addAll(mavenProject.dependencies)
    }

    static private class ScriptBuilderWrapper implements CrossConfigurationScriptBlockBuilder {
        private final BuildScriptBuilder scriptBuilder;

        public ScriptBuilderWrapper(BuildScriptBuilder scriptBuilder) {
            this.scriptBuilder = scriptBuilder;
        }

        @Override
        public RepositoriesBuilder repositories() {
            return scriptBuilder.repositories();
        }

        @Override
        public DependenciesBuilder dependencies() {
            return scriptBuilder.dependencies();
        }

        @Override
        public void plugin(@Nullable String comment, String pluginId) {
            scriptBuilder.plugin(comment, pluginId);
        }

        @Override
        public void taskPropertyAssignment(@Nullable String comment, String taskType, String propertyName, Object propertyValue) {
            scriptBuilder.taskPropertyAssignment(comment, taskType, propertyName, propertyValue);
        }

        @Override
        public void taskMethodInvocation(@Nullable String comment, String taskName, String taskType, String methodName, Object... methodArgs) {
            scriptBuilder.taskMethodInvocation(comment, taskName, taskType, methodName, methodArgs);
        }

        @Override
        public BuildScriptBuilder.Expression taskRegistration(@Nullable String comment, String taskName, String taskType, Action<? super ScriptBlockBuilder> blockContentBuilder) {
            return scriptBuilder.taskRegistration(comment, taskName, taskType, blockContentBuilder);
        }

        @Override
        public void propertyAssignment(@Nullable String comment, String propertyName, Object propertyValue) {
            scriptBuilder.propertyAssignment(comment, propertyName, propertyValue);
        }

        @Override
        public void methodInvocation(@Nullable String comment, String methodName, Object... methodArgs) {
            scriptBuilder.methodInvocation(comment, methodName, methodArgs);
        }

        @Override
        public void methodInvocation(@Nullable String comment, BuildScriptBuilder.Expression target, String methodName, Object... methodArgs) {
            scriptBuilder.methodInvocation(comment, target, methodName, methodArgs);
        }

        @Override
        public ScriptBlockBuilder block(@Nullable String comment, String methodName) {
            return scriptBuilder.block(comment, methodName);
        }

        @Override
        public void block(@Nullable String comment, String methodName, Action<? super ScriptBlockBuilder> blockContentsBuilder) {
            scriptBuilder.block(comment, methodName, blockContentsBuilder);
        }

        @Override
        public BuildScriptBuilder.Expression containerElement(@Nullable String comment, String container, String elementName, Action<? super ScriptBlockBuilder> blockContentsBuilder) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BuildScriptBuilder.Expression propertyExpression(String value) {
            return scriptBuilder.propertyExpression(value);
        }
    }
}
