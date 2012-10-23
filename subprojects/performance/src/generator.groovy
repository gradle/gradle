import groovy.text.SimpleTemplateEngine
import groovy.text.Template
import org.gradle.api.tasks.TaskAction
import java.text.SimpleDateFormat
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.jar.JarEntry

class TestProject {
    final String name
    final Object defaults
    Integer sourceFiles
    Integer testSourceFiles
    Integer linesOfCodePerSourceFile
    List<MavenModule> dependencies
    MavenRepository repository;

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
    boolean scalaProject
    int sourceFiles = 1
    Integer testSourceFiles
    int linesOfCodePerSourceFile = 5
    @InputFiles FileCollection testDependencies

    final List<TestProject> projects = []
    final SimpleTemplateEngine engine = new SimpleTemplateEngine()
    final Map<File, Template> templates = [:]

    final DependencyGraph dependencyGraph = new DependencyGraph()

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
        subprojects.each {
            if(repo){
                it.setRepository(repo)
                it.setDependencies(repo.getDependenciesOfTransitiveLevel(1))
            }
            generateSubProject(it)
        }
    }

    List getSubprojectNames() {
        return getSubprojects().collect { it.name }
    }

    TestProject getRootProject() {
        return projects[0]
    }

    MavenRepository generateDependencyRepository(){
        MavenRepository repo = new RepositoryBuilder(getDestDir())
                .withArtifacts(dependencyGraph.size)
                .withDepth(dependencyGraph.depth)
                .withSnapshotVersions(dependencyGraph.useSnapshotVersions)
                .create()
        return repo;
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
        logger.lifecycle "Generating test project '$testProject.name' into $projectDir"

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

        args += [projectName: testProject.name, groovyProject: groovyProject, scalaProject: scalaProject,
                propertyCount: (testProject.linesOfCodePerSourceFile.intdiv(7)), repository: testProject.repository, dependencies:testProject.dependencies]

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
            if (scalaProject) {
                testProject.sourceFiles.times {
                    String packageName = "org.gradle.test.performance${(int) (it / 100) + 1}"
                    Map classArgs = args + [packageName: packageName, productionClassName: "ProductionScala${it + 1}"]
                    generate("src/main/scala/${packageName.replace('.', '/')}/${classArgs.productionClassName}.scala", 'Production.scala', classArgs)
                }
                testProject.testSourceFiles.times {
                    String packageName = "org.gradle.test.performance${(int) (it / 100) + 1}"
                    Map classArgs = args + [packageName: packageName, productionClassName: "ProductionScala${it + 1}", testClassName: "TestScala${it + 1}"]
                    generate("src/test/scala/${packageName.replace('.', '/')}/${classArgs.testClassName}.scala", 'Test.scala', classArgs)
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

class DependencyGraph {
    int size = 0
    int depth = 1
    boolean useSnapshotVersions = false
    boolean isEmpty(){
        size==0
    }
}

class MavenRepository {
    int depth = 1
    final File rootDir
    List<MavenModule> modules = []

    MavenRepository(File rootDir) {
        println rootDir
        this.rootDir = rootDir
    }

    URI getUri() {
        return rootDir.toURI()
    }

    MavenModule addModule(String groupId, String artifactId, Object version = '1.0') {
        def artifactDir = new File(rootDir, "${groupId.replace('.', '/')}/$artifactId/$version")
        def module = new MavenModule(artifactDir, groupId, artifactId, version as String);
        modules << module
        return module
    }

    void publish(){
        modules.each{
            it.publish()
        }
    }

    List<MavenModule> getDependenciesOfTransitiveLevel(int level){
        return modules.findAll{((int)(it.artifactId - "artifact").toInteger() % depth) == level - 1 }
    }
}

class MavenModule {
    final File moduleDir
    final String groupId
    final String artifactId
    final String version
    String parentPomSection
    String type = 'jar'
    private final List dependencies = []
    int publishCount = 1
    final updateFormat = new SimpleDateFormat("yyyyMMddHHmmss")
    final timestampFormat = new SimpleDateFormat("yyyyMMdd.HHmmss")
    private final List artifacts = []
    private boolean uniqueSnapshots = true;

    MavenModule(File moduleDir, String groupId, String artifactId, String version) {
        this.moduleDir = moduleDir
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
    }

    MavenModule dependsOn(String dependencyArtifactId) {
        dependsOn(groupId, dependencyArtifactId, '1.0')
        return this
    }

    MavenModule dependsOn(String group, String artifactId, String version) {
        this.dependencies << [groupId: group, artifactId: artifactId, version: version]
        return this
    }

    String shortNotation(){
        return "$groupId:$artifactId:$version"
    }

    File getPomFile() {
        return new File(moduleDir, "$artifactId-${publishArtifactVersion}.pom")
    }

    File artifactFile(Map<String, ?> options) {
        def artifact = toArtifact(options)
        def fileName = "$artifactId-${publishArtifactVersion}.${artifact.type}"
        if (artifact.classifier) {
            fileName = "$artifactId-$publishArtifactVersion-${artifact.classifier}.${artifact.type}"
        }
        return new File(moduleDir, fileName)
    }

    String getPublishArtifactVersion() {
        if (uniqueSnapshots && version.endsWith("-SNAPSHOT")) {
            return "${version.replaceFirst('-SNAPSHOT$', '')}-${timestampFormat.format(publishTimestamp)}-${publishCount}"
        }
        return version
    }

    Date getPublishTimestamp() {
        return new Date(updateFormat.parse("20100101120000").time + publishCount * 1000)
    }

    /**
     * Publishes the pom.xml plus main artifact, plus any additional artifacts for this module.
     */
    MavenModule publish() {
        moduleDir.mkdirs()

        if (uniqueSnapshots && version.endsWith("-SNAPSHOT")) {
            def metaDataFile = new File(moduleDir, 'maven-metadata.xml')
            metaDataFile.text = """
<metadata>
  <groupId>$groupId</groupId>
  <artifactId>$artifactId</artifactId>
  <version>$version</version>
  <versioning>
    <snapshot>
      <timestamp>${timestampFormat.format(publishTimestamp)}</timestamp>
      <buildNumber>$publishCount</buildNumber>
    </snapshot>
    <lastUpdated>${updateFormat.format(publishTimestamp)}</lastUpdated>
  </versioning>
</metadata>
"""
        }

        pomFile.text = ""
        pomFile << """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>$groupId</groupId>
  <artifactId>$artifactId</artifactId>
  <packaging>$type</packaging>
  <version>$version</version>
  <description>Published on $publishTimestamp</description>"""

        if (parentPomSection) {
            pomFile << "\n$parentPomSection\n"
        }

        dependencies.each { dependency ->
            pomFile << """
  <dependencies>
    <dependency>
      <groupId>$dependency.groupId</groupId>
      <artifactId>$dependency.artifactId</artifactId>
      <version>$dependency.version</version>
    </dependency>
  </dependencies>"""
        }

        pomFile << "\n</project>"

        artifacts.each { artifact ->
            publishArtifact(artifact)
        }
        publishArtifact([:])
        return this
    }

    void createEmptyJar(File artifactFile) {
        String content = "testcontent"
        try {
            FileOutputStream stream = new FileOutputStream(artifactFile);
            JarOutputStream out = new JarOutputStream(stream, new Manifest());

            // Add archive entry
            JarEntry jarAdd = new JarEntry(artifactFile.name + ".properties");
            jarAdd.setTime(System.currentTimeMillis());
            out.putNextEntry(jarAdd);

            // Write file to archive
            out.write(content.getBytes("utf-8"), 0, content.getBytes("utf-8").length);

            out.close();
            stream.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("Error: " + ex.getMessage());
        }
    }

    private File publishArtifact(Map<String, ?> artifact) {
        def artifactFile = artifactFile(artifact)
        if (type != 'pom') {
            if (type == 'jar') {
                createEmptyJar(artifactFile)
            } else {
                artifactFile << "add some content so that file size isn't zero: $publishCount"
            }
        }

        return artifactFile
    }

    private Map<String, Object> toArtifact(Map<String, ?> options) {
        options = new HashMap<String, Object>(options)
        def artifact = [type: options.remove('type') ?: type, classifier: options.remove('classifier') ?: null]
        assert options.isEmpty(): "Unknown options : ${options.keySet()}"
        return artifact
    }
}

class MavenPom {
    final Map<String, MavenScope> scopes = [:]

    MavenPom(File pomFile) {
        def pom = new XmlParser().parse(pomFile)
        pom.dependencies.dependency.each { dep ->
            def scopeElement = dep.scope
            def scopeName = scopeElement ? scopeElement.text() : "runtime"
            def scope = scopes[scopeName]
            if (!scope) {
                scope = new MavenScope()
                scopes[scopeName] = scope
            }
            scope.addDependency(dep.groupId.text(), dep.artifactId.text(), dep.version.text())
        }
    }
}

class MavenScope {
    final dependencies = []

    void addDependency(String groupId, String artifactId, String version) {
        dependencies << [groupId: groupId, artifactId: artifactId, version: version]
    }
}

class RepositoryBuilder {
    private int depth = 1
    private int numberOfArtifacts = 0
    private File targetDir
    boolean withSnapshotVersions = false

    public RepositoryBuilder(File targetDir) {
        this.targetDir = targetDir;
    }

    RepositoryBuilder withArtifacts(int numberOfArtifacts) {
        this.numberOfArtifacts = numberOfArtifacts
        return this;
    }

    RepositoryBuilder withDepth(int depth) {
        this.depth = depth
        return this;
    }

    RepositoryBuilder withSnapshotVersions(boolean withSnapshotVersions) {
        this.withSnapshotVersions = withSnapshotVersions
        return this;
    }

    MavenRepository create() {
        if(numberOfArtifacts==0){
            return null;
        }
        targetDir.mkdirs();
        MavenRepository repo = new MavenRepository(new File(targetDir, "mavenRepo"))
        numberOfArtifacts.times {
            if(withSnapshotVersions){
                repo.addModule('group', "artifact$it", "1.0-SNAPSHOT")
            }else{
                repo.addModule('group', "artifact$it")
            }
        }

        transformGraphToDepth(repo.modules, depth)
        repo.setDepth(depth)
        repo.publish()
        repo
    }

    void transformGraphToDepth(List<MavenModule> modules, int depth) {
        def depGroups = modules.groupBy { (int) (it.artifactId - "artifact").toInteger() / depth }
        depGroups.each {idx, groupModules ->
            for (int i = 0; i < groupModules.size() - 1; i++) {
                def next = groupModules[i + 1]
                groupModules[i].dependsOn(next.groupId, next.artifactId, next.version)
            }
        }
    }
}