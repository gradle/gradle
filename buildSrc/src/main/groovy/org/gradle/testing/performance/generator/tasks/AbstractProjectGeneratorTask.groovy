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

package org.gradle.testing.performance.generator.tasks

import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.testing.performance.generator.*
/**
 * Original tangled mess of a project generator.
 */
abstract class AbstractProjectGeneratorTask extends ProjectGeneratorTask {
    int sourceFiles = 1
    int projectCount = 1
    Integer testSourceFiles
    int linesOfCodePerSourceFile = 5
    int filesPerPackage = 100
    List<String> additionalProjectFiles = []
    final List<TestProject> projects = []
    List<String> rootProjectTemplates = ['root-project']
    List<String> subProjectTemplates = ['project-with-source']
    final SimpleTemplateEngine engine = new SimpleTemplateEngine()
    final Map<File, Template> templates = [:]
    Map<String, Object> templateArgs = [:]
    final DependencyGraph dependencyGraph = new DependencyGraph()
    int numberOfExternalDependencies = 0
    MavenJarCreator mavenJarCreator = new MavenJarCreator()
    String buildSrcTemplate

    AbstractProjectGeneratorTask() {
        super()
    }

    int getTestSourceFiles() {
        return testSourceFiles ?: sourceFiles
    }

    void setProjects(int projectCount) {
        this.projectCount = projectCount
    }

    void dependencyGraph(Closure configClosure) {
        configClosure.setDelegate(dependencyGraph)
        configClosure.setResolveStrategy(Closure.DELEGATE_ONLY)
        configClosure.call()
    }

    @TaskAction
    void generate() {
        println "Generating test project ${destDir.name}"
        println "  projects: ${projectCount}"
        println "  source files: ${sourceFiles}"
        println "  LOC per source file: ${linesOfCodePerSourceFile}"
        println "  test source files: ${testSourceFiles}"
        println "  files per package: ${filesPerPackage}"
        println "  root project templates: ${rootProjectTemplates}"
        println "  project templates: ${subProjectTemplates}"
        println "  number of external dependencies: ${numberOfExternalDependencies}"

        while (projects.size() < projectCount) {
            def project = projects.empty ? new TestProject("root", this) : new TestProject("project${projects.size()}", this, projects.size())
            projects << project
        }

        ant.delete(dir: destDir)
        destDir.mkdirs()

        MavenRepository repo = generateDependencyRepository()
        generateRootProject()
        subprojects.each { subproject ->
            if (repo) {
                subproject.setRepository(repo)
                pickExternalDependencies(repo, subproject)
            }
            generateSubProject(subproject)
        }
    }

    void pickExternalDependencies(repo, subproject) {
        if (numberOfExternalDependencies > 0) {
            def dependencies = [] + repo.modules
            subproject.setDependencies(dependencies.take(numberOfExternalDependencies))
        } else {
            subproject.setDependencies(repo.getDependenciesOfTransitiveLevel(1))
        }
    }

    protected List getSubprojectNames() {
        return getSubprojects().collect { it.name }
    }

    protected TestProject getRootProject() {
        return projects[0]
    }

    MavenRepository generateDependencyRepository() {
        MavenRepository repo = new RepositoryBuilder(getDestDir())
                .withArtifacts(dependencyGraph.size)
                .withDepth(dependencyGraph.depth)
                .withSnapshotVersions(dependencyGraph.useSnapshotVersions)
                .withMavenJarCreator(mavenJarCreator)
                .create()
        return repo;
    }

    protected List<TestProject> getSubprojects() {
        return projects.subList(1, projects.size())
    }

    def generateRootProject() {
        generateProject(rootProject,
                subprojects: subprojectNames,
                projectDir: destDir,
                files: rootProjectFiles,
                templates: effectiveRootProjectTemplates,
                includeSource: subprojectNames.empty)

        project.copy {
            from resolveTemplate("init.gradle")
            into(getDestDir())
        }
        if (buildSrcTemplate) {
            project.copy {
                from resolveTemplate(buildSrcTemplate)
                into(getDestDir())
            }
        }
    }

    List<String> getEffectiveRootProjectTemplates() {
        subprojectNames.empty ? subProjectTemplates : rootProjectTemplates
    }

    List<String> getRootProjectFiles() {
        subprojectNames.empty ? [] : ['settings.gradle', 'gradle.properties', 'checkstyle.xml']
    }

    def generateSubProject(TestProject testProject) {
        generateProject(testProject,
                subprojects: [],
                projectDir: new File(destDir, testProject.name),
                files: [],
                templates: subProjectTemplates,
                includeSource: true)
    }

    def generateProject(Map parameterArgs, TestProject testProject) {
        def args = [:]
        args += parameterArgs

        File projectDir = args.projectDir
        logger.lifecycle "Generating test project '$testProject.name' into $projectDir"

        def files = []
        files.addAll(args.files)
        files.addAll(defaultProjectFiles)
        files.addAll(additionalProjectFiles)

        args += [
                projectName: testProject.name,
                subprojectNumber: testProject.subprojectNumber,
                propertyCount: (testProject.linesOfCodePerSourceFile.intdiv(7)),
                repository: testProject.repository,
                dependencies: testProject.dependencies,
                testProject: testProject
        ]

        args += templateArgs
        args += taskArgs

        files.each { String name ->
            generateWithTemplate(projectDir, name, name, args)
        }

        if (args.includeSource) {
            generateProjectSource(projectDir, testProject, args)
        }
    }

    List<String> getDefaultProjectFiles() {
        ['build.gradle', 'settings.gradle', 'gradle.properties', 'pom.xml', 'build.xml']
    }

    void generateWithTemplate(File projectDir, String name, String templateName, Map templateArgs) {
        File destFile = new File(projectDir, name).absoluteFile
        File baseFile = resolveTemplate(templateName)

        List<File> templateFiles = []
        if (baseFile.exists()) {
            templateFiles << baseFile
        }
        List<String> templates = templateArgs.templates
        templateFiles.addAll templates.collect { new File(resolveTemplate(it), templateName) }.findAll { it.exists() }
        if (templateFiles.empty) {
            return
        }
        templateFiles.subList(0, templateFiles.size() - 1).each {
            def writer = new StringWriter()
            getTemplate(it).make(templateArgs).writeTo(writer)
            def originalContents = writer.toString()
            int idx = originalContents.indexOf('plugins {')
            if (idx>0) { idx = originalContents.indexOf('}', idx) }
            def beforePlugins = idx>0?originalContents.substring(0, idx+1):''
            def afterPlugins = idx>0?originalContents.substring(idx+1):originalContents
            templateArgs.original = originalContents
            templateArgs.beforePlugins = beforePlugins
            templateArgs.afterPlugins = afterPlugins
        }

        destFile.parentFile.mkdirs()
        destFile.withWriter { Writer writer ->
            if (templateName.endsWith('.gradle')) {
                writer << "// Generated ${UUID.randomUUID()}\n"
            }
            getTemplate(templateFiles.last()).make(templateArgs).writeTo(writer)
        }
    }

    def getTemplate(File srcTemplate) {
        def template = templates[srcTemplate]
        if (!template) {
            try {
                template = engine.createTemplate(srcTemplate)
            } catch (Exception e) {
                throw new GradleException("Could not create template from source file '$srcTemplate'", e)
            }
            templates[srcTemplate] = template
        }
        return template
    }

    Map getTaskArgs() {
        [:]
    }

    abstract void generateProjectSource(File projectDir, TestProject testProject, Map args);
}
