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

package org.gradle.performance.generator

import org.gradle.api.GradleException
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.file.FileCollection

import groovy.text.SimpleTemplateEngine
import groovy.text.Template

class ProjectGeneratorTask extends DefaultTask {
    @OutputDirectory
    File destDir

    boolean groovyProject
    boolean scalaProject
    boolean nativeProject
    int sourceFiles = 1
    Integer testSourceFiles
    int linesOfCodePerSourceFile = 5
    int filesPerPackage = 100
    boolean useSubProjectNumberInSourceFileNames = false

    @InputFiles
    FileCollection testDependencies

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
        generateProject rootProject, subprojects: subprojectNames, projectDir: destDir,
                files: subprojectNames.empty ? [] : ['settings.gradle', 'checkstyle.xml'],
                templates: templates,
                includeSource: subprojectNames.empty

        project.copy {
            from "src/templates/init.gradle"
            into(getDestDir())
            into('lib/test') {
                from testDependencies
            }
        }
    }

    def generateSubProject(TestProject testProject) {
        generateProject testProject, subprojects: [], projectDir: new File(destDir, testProject.name), files: [],
                templates: subProjectTemplates, includeSource: true
    }

    def generateProject(Map args, TestProject testProject) {
        File projectDir = args.projectDir
        logger.lifecycle "Generating test project '$testProject.name' into $projectDir"

        def files = []
        files.addAll(args.files)
        files.addAll(['build.gradle', 'pom.xml', 'build.xml'])

        args += [projectName  : testProject.name, subprojectNumber: testProject.subprojectNumber, groovyProject: groovyProject, scalaProject: scalaProject,
                 propertyCount: (testProject.linesOfCodePerSourceFile.intdiv(7)), repository: testProject.repository, dependencies: testProject.dependencies,
                 testProject  : testProject
        ]

        args += templateArgs

        files.each { String name ->
            generateWithTemplate(projectDir, name, name, args)
        }

        if (args.includeSource) {
            if (nativeProject) {
                generateNativeProjectSource(projectDir, testProject, args)
            } else {
                generateJvmProjectSource(projectDir, "java", testProject, args)
                if (groovyProject) {
                    generateJvmProjectSource(projectDir, "groovy", testProject, args)
                }
                if (scalaProject) {
                    generateJvmProjectSource(projectDir, "scala", testProject, args)
                }
            }
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

    void generateNativeProjectSource(File projectDir, TestProject testProject, Map args) {
        args.moduleCount.times { m ->
            Map classArgs = args + [componentName: "lib${m + 1}"]
            generateWithTemplate(projectDir, "src/${classArgs.componentName}/headers/pch.h", 'pch.h', classArgs)
        }
        testProject.sourceFiles.times { s ->
            args.moduleCount.times { m ->
                Map classArgs = args + [componentName: "lib${m + 1}", functionName: "lib${s + 1}"]
                generateWithTemplate(projectDir, "src/${classArgs.componentName}/c/${classArgs.functionName}.c", 'lib.c', classArgs)
            }
        }
    }

    void generateJvmProjectSource(File projectDir, String sourceLang, TestProject testProject, Map args) {
        def classFilePrefix
        def classFileTemplate
        def testFilePrefix
        def testFileTemplate

        if (sourceLang == "groovy") {
            classFilePrefix = "ProductionGroovy"
            classFileTemplate = "Production.groovy"
            testFilePrefix = "TestGroovy"
            testFileTemplate = "Test.groovy"
        } else if (sourceLang == "scala") {
            classFilePrefix = "ProductionScala"
            classFileTemplate = "Production.scala"
            testFilePrefix = "TestScala"
            testFileTemplate = "Test.scala"
        } else {
            classFilePrefix = "Production"
            classFileTemplate = "Production.java"
            testFilePrefix = "Test"
            testFileTemplate = "Test.java"
        }

        def createPackageName = { fileNumber -> "org.gradle.test.performance${useSubProjectNumberInSourceFileNames ? "${testProject.subprojectNumber}_" : ''}${(int) (fileNumber / filesPerPackage) + 1}".toString() }
        def createFileName = { prefix, fileNumber -> "${prefix}${useSubProjectNumberInSourceFileNames ? "${testProject.subprojectNumber}_" : ''}${fileNumber + 1}".toString() }

        testProject.sourceFiles.times {
            String packageName = createPackageName(it)
            Map classArgs = args + [packageName: packageName, productionClassName: createFileName(classFilePrefix, it)]
            generateWithTemplate(projectDir, "src/main/${sourceLang}/${packageName.replace('.', '/')}/${classArgs.productionClassName}.${sourceLang}", classFileTemplate, classArgs)
        }
        testProject.testSourceFiles.times {
            String packageName = createPackageName(it)
            Map classArgs = args + [packageName: packageName, productionClassName: createFileName(classFilePrefix, it), testClassName: createFileName(testFilePrefix, it)]
            generateWithTemplate(projectDir, "src/test/${sourceLang}/${packageName.replace('.', '/')}/${classArgs.testClassName}.${sourceLang}", testFileTemplate, classArgs)
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
}
