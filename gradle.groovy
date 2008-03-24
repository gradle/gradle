import java.text.SimpleDateFormat
import org.gradle.api.internal.dependencies.WebdavResolver
import org.gradle.api.tasks.testing.ForkMode
import org.gradle.build.integtests.GroovyProject
import org.gradle.build.integtests.JavaProject
import org.gradle.build.integtests.TutorialTest
import org.gradle.build.integtests.WaterProject
import org.gradle.build.release.Svn
import org.gradle.build.release.Version
import org.gradle.build.samples.TutorialCreator
import org.gradle.build.samples.WaterProjectCreator
import org.gradle.build.startscripts.StartScriptsGenerator
import org.gradle.execution.Dag

// todo: create version.properties file

svn = new Svn(project)

type = 'jar'
version = new Version(svn, project, false)
group = 'org.gradle'
versionModifier = new SimpleDateFormat('yyMMddHHmmssZ').format(new Date())

usePlugin('groovy')

configureByDag = {Dag dag ->
    if (dag.hasTask(':release')) {
        versionModifier = ''
    }
}

dependencies {
    addDependency(confs: ['compile'], id: "org.codehaus.groovy:groovy-all:1.5.5-SNAPSHOT") {
        exclude(module: 'jline')
        exclude(module: 'junit')
    }
    addDependency(confs: ['compile'], id: "org.apache.ant:ant-junit:1.7.0") {
        exclude(module: 'junit')
    }
    compile "commons-cli:commons-cli:1.0"
    compile "commons-io:commons-io:1.3.1"
    compile "commons-lang:commons-lang:2.3"
    compile "commons-httpclient:commons-httpclient:3.0"
    compile "slide:webdavlib:2.0"
    compile "ch.qos.logback:logback-classic:0.9.8"
    compile "org.apache.ant:ant-launcher:1.7.0"
    compile "junit:junit:4.4"
    compile "org.apache.ivy:ivy:2.0.0.beta2_20080305165542"
    runtime "org.tmatesoft.svnkit:svnkit:1.1.6:jar"
    runtime "org.tmatesoft.svnkit:svnkit-javahl:1.1.6:jar"

    resolvers.addBefore('http://gradle.sourceforge.net/repository', 'Maven2Repo')

    uploadResolvers.add(new WebdavResolver()) {
        name = 'gradleReleases'
        user = codehausUserName
        userPassword = codehausUserPassword
        addArtifactPattern("https://dav.codehaus.org/dist/gradle/[module]-[revision].[ext]")
    }
}

sourceCompatibility = 1.5
targetCompatibility = 1.5

test {
    include '**/*Test.class'
    exclude '**/Abstract*'
    // We set forkmode to ONCE as our tests are written in Groovy and the startup time of Groovy is significant.
    options.fork(forkMode: ForkMode.ONCE, jvmArgs: ["-ea", "-Dgradle.home=roadToNowhere"])
}

def jarBaseName = 'gradle'
// todo: Add DefaultArchiveTask
lib.lateInitalizeClosures << {it.project.task('gradle-core_jar').baseName = jarBaseName}

distDir = file('build/distribution/exploded')
distSamplesDir = new File(distDir, 'samples')
distTutorialDir = new File(distDir, 'tutorial')


createTask('explodedDist', dependsOn: 'lib') {
    [distDir, distSamplesDir, distTutorialDir]*.mkdirs()
    File distBinDir = mkdir(distDir, 'bin')
    File distSrcDir = mkdir(distDir, 'src')
    File distLibDir = mkdir(distDir, 'lib')
    ant {
        logger.info('Generate lib dir')
        dependencies.resolveClasspath('runtime').each {File file ->
            copy(file: file, todir: distLibDir)
        }
        copy(file: task('gradle-core_jar').archivePath, toDir: distLibDir)
        logger.info('Generate start scripts')
        StartScriptsGenerator.generate(distLibDir, distBinDir, 'gradle')
        logger.info('Generate tutorial')
        TutorialCreator.writeScripts(distTutorialDir)
        WaterProjectCreator.createProjectTree(distSamplesDir)
        copy(toDir: distSamplesDir) {fileset(dir: new File(srcRoot, 'samples'))}

        copy(toDir: distSrcDir) {
            (srcDirs + resourceDirs + groovySrcDirs).findAll {it.isDirectory()}.each {dir -> fileset(dir: dir)}
        }
        copy(toDir: distDir) {fileset(dir: new File(srcRoot, 'toplevel'))}
        chmod(dir: "$distDir/bin", perm: "ugo+rx", includes: "**/*")
    }
}

createTask('integTests', dependsOn: 'explodedDist') {
    String distDirPath = distDir.absolutePath
    TutorialTest.execute(distDirPath, distTutorialDir.absolutePath)
    WaterProject.execute(distDirPath, distSamplesDir.absolutePath)
    JavaProject.execute(distDirPath, distSamplesDir.absolutePath)
    GroovyProject.execute(distDirPath, distSamplesDir.absolutePath)
}.skipProperties << 'integtest.skip'

dist {
    dependsOn 'integTests'
    childrenDependOn << 'integTests'
    zip() {
        String prefix = "gradle-$version"
        destinationDir = file('build/distribution')
        zipFileSet(dir: distDir, prefix: prefix) {
            exclude 'bin/*'
        }
        zipFileSet(dir: distDir, prefix: prefix, fileMode: '775') {
            include 'bin/*'
            exclude 'bin/*.*'
        }
        zipFileSet(dir: distDir, prefix: prefix) {
            include 'bin/*.*'
        }
    }
    zip("$project.name-src") {
        String prefix = "gradle-src-$version"
        destinationDir = file('build/distribution')
        zipFileSet(dir: projectDir, prefix: prefix) {
            include 'src/', 'build.xml', 'build.properties', 'ivy.xml', 'ivysettings.xml', 'build.properties',
                    'ivy.xml', 'ivysettings.xml', 'gradle.groovy'
        }
    }
}

createTask('check') {
    ant.taskdef(resource: 'org/apache/ivy/ant/antlib.xml')
    ant.cachepath(organisation: "net.sourceforge.cobertura", module: "cobertura", revision: "1.9",
            inline: "true", conf: "default", pathid: "cobertura.classpath")
}
//<target name="instrument" depends="compile" unless="test.skip">
//        <ivy:cachepath organisation="net.sourceforge.cobertura" module="cobertura" revision="1.9"
//                       inline="true" conf="default" pathid="cobertura.classpath"/>
//        <taskdef classpathref="cobertura.classpath" resource="tasks.properties"/>
//        <delete file="cobertura.ser"/>
//
//        <cobertura-instrument todir="${buildInstrumentedCoverageClassesDirectory}">
//            <fileset dir="${buildClassesDirectory}">
//                <include name="**/*.class"/>
//            </fileset>
//        </cobertura-instrument>
//    </target>

createTask('release') {
    svn.release()
}



