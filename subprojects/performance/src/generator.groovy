import groovy.text.SimpleTemplateEngine
import groovy.text.Template

class TestProject {
    final String name
    final Object defaults
    Integer sourceFiles
    Integer testSourceFiles
    Integer linesOfCodePerSourceFile

    TestProject() {
    }

    TestProject(String name, Object defaults) {
        this.name = name
        this.defaults = defaults
    }

    int getSourceFiles() {
        return sourceFiles ?: defaults.sourceFiles
    }

    int getTestSourceFiles() {
        return testSourceFiles ?: defaults.testSourceFiles
    }

    int getLinesOfCodePerSourceFile() {
        return linesOfCodePerSourceFile ?: defaults.linesOfCodePerSourceFile
    }
}

class ProjectGeneratorTask extends DefaultTask {
    @OutputDirectory
    File destDir
    boolean groovyProject
    int sourceFiles = 1
    Integer testSourceFiles
    int linesOfCodePerSourceFile = 5
    @InputFiles FileCollection testDependencies

    final List<TestProject> projects = []
    final SimpleTemplateEngine engine = new SimpleTemplateEngine()
    final Map<File, Template> templates = [:]

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
                def project = projects.empty ? new TestProject("root", this) : new TestProject("project${projects.size()}", this)
                projects << project
            }
        }
    }

    @TaskAction
    void generate() {
        ant.delete(dir: destDir)
        destDir.mkdirs()

        generateRootProject()
        subprojects.each {
            generateSubProject(it)
        }
    }

    List getSubprojectNames() {
        return getSubprojects().collect { it.name }
    }

    TestProject getRootProject() {
        return projects[0]
    }

    List<TestProject> getSubprojects() {
        return projects.subList(1, projects.size())
    }

    def generateRootProject() {
        generateProject rootProject, subprojects: subprojectNames, projectDir: destDir,
                files: subprojectNames.empty ? [] : ['settings.gradle'],
                includeSource: subprojectNames.empty

        project.copy {
            from testDependencies
            into new File(getDestDir(), 'lib/test')
        }
    }

    def generateSubProject(TestProject testProject) {
        generateProject testProject, subprojects: [], projectDir: new File(destDir, testProject.name), files: [],
                includeSource: true
    }

    def generateProject(Map args, TestProject testProject) {
        File projectDir = args.projectDir
        logger.lifecycle"Generating test project '$testProject.name' into $projectDir"

        List files = args.files + [
                'build.gradle',
                'pom.xml',
                'build.xml',
        ]

        Closure generate = {String name, String templateName, Map templateArgs ->
            File destFile = new File(projectDir, name)
            File srcTemplate = project.file("src/templates/$templateName")
            destFile.parentFile.mkdirs()
            destFile.withWriter {Writer writer ->
                getTemplate(srcTemplate).make(templateArgs).writeTo(writer)
            }
        }

        args += [projectName: testProject.name, groovyProject: groovyProject, propertyCount: (testProject.linesOfCodePerSourceFile.intdiv(7))]

        files.each {String name ->
            generate(name, name, args)
        }

        if (args.includeSource) {
            testProject.sourceFiles.times {
                String packageName = "org.gradle.test.performance${(int) (it / 100) + 1}"
                Map classArgs = args + [packageName: packageName, productionClassName: "Production${it + 1}"]
                generate("src/main/java/${packageName.replace('.', '/')}/${classArgs.productionClassName}.java", 'Production.java', classArgs)
            }
            testProject.testSourceFiles.times {
                String packageName = "org.gradle.test.performance${(int) (it / 100) + 1}"
                Map classArgs = args + [packageName: packageName, productionClassName: "Production${it + 1}", testClassName: "Test${it + 1}"]
                generate("src/test/java/${packageName.replace('.', '/')}/${classArgs.testClassName}.java", 'Test.java', classArgs)
            }
            if (groovyProject) {
                testProject.sourceFiles.times {
                    String packageName = "org.gradle.test.performance${(int) (it / 100) + 1}"
                    Map classArgs = args + [packageName: packageName, productionClassName: "ProductionGroovy${it + 1}"]
                    generate("src/main/groovy/${packageName.replace('.', '/')}/${classArgs.productionClassName}.groovy", 'Production.groovy', classArgs)
                }
                testProject.testSourceFiles.times {
                    String packageName = "org.gradle.test.performance${(int) (it / 100) + 1}"
                    Map classArgs = args + [packageName: packageName, productionClassName: "ProductionGroovy${it + 1}", testClassName: "TestGroovy${it + 1}"]
                    generate("src/test/groovy/${packageName.replace('.', '/')}/${classArgs.testClassName}.groovy", 'Test.groovy', classArgs)
                }
            }
        }
    }

    def getTemplate(File srcTemplate) {
        def template = templates[srcTemplate]
        if (!template) {
            template = engine.createTemplate(srcTemplate)
            templates[srcTemplate] = template
        }
        return template
    }
}

//workaround for referring to task types defined in plugin scripts
project.ext.set('ProjectGeneratorTask', ProjectGeneratorTask)