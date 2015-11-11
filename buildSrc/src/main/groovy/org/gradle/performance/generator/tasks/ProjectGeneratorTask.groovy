/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.performance.generator.tasks

import org.gradle.api.GradleException
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.OutputDirectory

import groovy.text.SimpleTemplateEngine
import groovy.text.Template

import org.gradle.performance.generator.*

abstract class ProjectGeneratorTask extends DefaultTask {
    @OutputDirectory
    File destDir

    int sourceFiles = 1
    Integer testSourceFiles
    int linesOfCodePerSourceFile = 5
    int filesPerPackage = 100
    boolean useSubProjectNumberInSourceFileNames = false
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

    Random random = new Random(1L)

    def ProjectGeneratorTask() {
        outputs.upToDateWhen { false }
        setProjects(1)
        destDir = project.file("${project.buildDir}/${name}")
    }

    int getTestSourceFiles() {
        return testSourceFiles ?: sourceFiles
    }

    void setProjects(int projectCount) {
        if (projects.size() > projectCount) {
            projects.subList(projectCount, projects.size()).clear()
        } else {
            while (projects.size() < projectCount) {
                def project = projects.empty ? new TestProject("root", this) : new TestProject("project${projects.size()}", this, projects.size())
                projects << project
            }
        }
    }

    int getProjectCount() {
        projects.size()
    }

    void dependencyGraph(Closure configClosure) {
        configClosure.setDelegate(dependencyGraph)
        configClosure.setResolveStrategy(Closure.DELEGATE_ONLY)
        configClosure.call()
    }

    @TaskAction
    void generate() {
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
            Collections.shuffle(dependencies, random)
            subproject.setDependencies(dependencies.take(numberOfExternalDependencies))
        } else {
            subproject.setDependencies(repo.getDependenciesOfTransitiveLevel(1))
        }
    }

    List getSubprojectNames() {
        return getSubprojects().collect { it.name }
    }

    TestProject getRootProject() {
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

    List<TestProject> getSubprojects() {
        return projects.subList(1, projects.size())
    }

    def generateRootProject() {
        def templates = [] + (subprojectNames.empty ? subProjectTemplates : rootProjectTemplates)
        if (!templates.empty) {
            templates.addAll(['build-event-timestamps', 'heap-capture'])
        }
        generateProject(rootProject,
            subprojects: subprojectNames,
            projectDir: destDir,
            files: subprojectNames.empty ? [] : ['settings.gradle', 'checkstyle.xml'],
            templates: templates,
            includeSource: subprojectNames.empty)

        project.copy {
            from "src/templates/init.gradle"
            into(getDestDir())
        }
    }

    def generateSubProject(TestProject testProject) {
        generateProject(testProject,
            subprojects: [],
            projectDir: new File(destDir, testProject.name),
            files: [],
            templates: subProjectTemplates,
            includeSource: true)
    }

    def generateProject(Map args, TestProject testProject) {
        File projectDir = args.projectDir
        logger.lifecycle "Generating test project '$testProject.name' into $projectDir"

        def files = []
        files.addAll(args.files)
        files.addAll(['build.gradle', 'pom.xml', 'build.xml'])
        files.addAll(additionalProjectFiles)

        args += [projectName  : testProject.name,
                 subprojectNumber: testProject.subprojectNumber,
                 propertyCount: (testProject.linesOfCodePerSourceFile.intdiv(7)),
                 repository: testProject.repository,
                 dependencies: testProject.dependencies,
                 testProject: testProject ]

        args += templateArgs

        args += taskArgs

        files.each { String name ->
            generateWithTemplate(projectDir, name, name, args)
        }

        if (args.includeSource) {
            generateProjectSource(projectDir, testProject, args)
        }
    }

    void generateWithTemplate(File projectDir, String name, String templateName, Map templateArgs) {
        File destFile = new File(projectDir, name)
        File baseFile = project.file("src/templates/$templateName")

        List<File> templateFiles = []
        if (baseFile.exists()) {
            templateFiles << baseFile
        }
        List<String> templates = templateArgs.templates
        templateFiles.addAll templates.collect { project.file("src/templates/$it/$templateName") }.findAll { it.exists() }
        if (templateFiles.empty) {
            return
        }
        templateFiles.subList(0, templateFiles.size() - 1).each {
            def writer = new StringWriter()
            getTemplate(it).make(templateArgs).writeTo(writer)
            templateArgs.original = writer.toString()
        }

        destFile.parentFile.mkdirs()
        destFile.withWriter { Writer writer ->
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
