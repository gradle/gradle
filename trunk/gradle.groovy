import java.text.DateFormat
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
import org.gradle.util.GradleVersion

// todo: create version.properties file

distName = 'gradle'
svn = new Svn(project)

type = 'jar'
version = new Version(svn, project, false)
group = 'org.gradle'
buildTime = new Date()
versionModifier = new SimpleDateFormat('yyMMddHHmmssZ').format(buildTime)

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
    compile "commons-cli:commons-cli:1.0",
            "commons-io:commons-io:1.3.1",
            "commons-lang:commons-lang:2.3",
            "commons-httpclient:commons-httpclient:3.0",
            "slide:webdavlib:2.0",
            "ch.qos.logback:logback-classic:0.9.8",
            "org.apache.ant:ant-launcher:1.7.0",
            "junit:junit:4.4",
            "org.apache.ivy:ivy:2.0.0.beta2_20080305165542"
    runtime "org.tmatesoft.svnkit:svnkit:1.1.6:jar",
            "org.tmatesoft.svnkit:svnkit-javahl:1.1.6:jar"

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

resources.doLast {
    logger.info('Write version properties')
    Properties versionProperties = new Properties()
    versionProperties.putAll([
            (GradleVersion.VERSION): version.toString(),
            (GradleVersion.BUILD_TIME): DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL).format(buildTime)
    ])
    versionProperties.store(new FileOutputStream(new File(classesDir, GradleVersion.FILE_NAME)), '')
}

test {
    include '**/*Test.class'
    exclude '**/Abstract*'
    // We set forkmode to ONCE as our tests are written in Groovy and the startup time of Groovy is significant.
    options.fork(forkMode: ForkMode.ONCE, jvmArgs: ["-ea", "-Dgradle.home=roadToNowhere"])
}

def jarBaseName = distName
// todo: Add DefaultArchiveTask
lib.lateInitalizeClosures << {it.project.task('gradle-core_jar').baseName = jarBaseName}

explodedDistDir = new File(distDir, 'exploded')
explodedDistSamplesDir = new File(explodedDistDir, 'samples')
explodedDistTutorialDir = new File(explodedDistDir, 'tutorial')


createTask('explodedDist', dependsOn: 'lib') {
    [explodedDistDir, explodedDistSamplesDir, explodedDistTutorialDir]*.mkdirs()
    File explodedDistBinDir = mkdir(explodedDistDir, 'bin')
    File explodedDistSrcDir = mkdir(explodedDistDir, 'src')
    File explodedDistLibDir = mkdir(explodedDistDir, 'lib')
    ant {
        logger.info('Generate lib dir')
        dependencies.resolveClasspath('runtime').each {File file ->
            copy(file: file, todir: explodedDistLibDir)
        }
        copy(file: task('gradle-core_jar').archivePath, toDir: explodedDistLibDir)
        logger.info('Generate start scripts')
        StartScriptsGenerator.generate(explodedDistLibDir, explodedDistBinDir, distName)
        logger.info('Generate tutorial')
        TutorialCreator.writeScripts(explodedDistTutorialDir)
        WaterProjectCreator.createProjectTree(explodedDistSamplesDir)
        copy(toDir: explodedDistSamplesDir) {fileset(dir: new File(srcRoot, 'samples'))}

        copy(toDir: explodedDistSrcDir) {
            (srcDirs + resourceDirs + groovySrcDirs).findAll {it.isDirectory()}.each {dir -> fileset(dir: dir)}
        }
        copy(toDir: explodedDistDir) {fileset(dir: new File(srcRoot, 'toplevel'))}
        chmod(dir: "$explodedDistDir/bin", perm: "ugo+rx", includes: "**/*")
    }
}

createTask('integTests', dependsOn: 'explodedDist') {
    String distDirPath = explodedDistDir.absolutePath
    org.gradle.build.integtests.Version.execute(distDirPath)
    TutorialTest.execute(distDirPath, explodedDistTutorialDir.absolutePath)
    WaterProject.execute(distDirPath, explodedDistSamplesDir.absolutePath)
    JavaProject.execute(distDirPath, explodedDistSamplesDir.absolutePath)
    GroovyProject.execute(distDirPath, explodedDistSamplesDir.absolutePath)
}.skipProperties << 'integtest.skip'

zipRootFolder = "$distName-$version"

dist {
    dependsOn 'integTests'
    childrenDependOn << 'integTests'
    zip() {

        destinationDir = distDir
        zipFileSet(dir: explodedDistDir, prefix: zipRootFolder) {
            exclude 'bin/*'
        }
        zipFileSet(dir: explodedDistDir, prefix: zipRootFolder, fileMode: '775') {
            include 'bin/*'
            exclude 'bin/*.*'
        }
        zipFileSet(dir: explodedDistDir, prefix: zipRootFolder) {
            include 'bin/*.*'
        }
    }
    zip("$project.name-src") {
        String prefix = "$distName-src-$version"
        destinationDir = distDir
        zipFileSet(dir: projectDir, prefix: prefix) {
            include 'src/', 'build.xml', 'build.properties', 'ivy.xml', 'ivysettings.xml', 'build.properties',
                    'ivy.xml', 'ivysettings.xml', 'gradle.groovy'
        }
    }
}

createTask('install', dependsOn: 'dist') {
    String installDirName = distName + '-SNAPSHOT'
    ant {
        delete(dir: "$installDir/$installDirName")
        exec(dir: installDir, executable: "rm") {
            arg(value: distName)
        }
        exec(dir: installDir, executable: "unzip") {
            arg(value: '-q')
            arg(value: '-d')
            arg(value: installDir)
            arg(value: "${task('gradle-core_zip').archivePath}")
        }
        exec(dir: installDir, executable: "mv") {
            arg(value: zipRootFolder)
            arg(value: installDirName)
        }
        exec(dir: installDir, executable: "ln") {
            arg(value: '-s')
            arg(value: "$installDir/$installDirName")
            arg(value: distName)
        }
    }
}

createTask('release', dependsOn: 'dist') {
    svn.release()
}

distribute.dependsOn 'release'

//createTask('check') {
//    ant.taskdef(resource: 'org/apache/ivy/ant/antlib.xml')
//    ant.cachepath(organisation: "net.sourceforge.cobertura", module: "cobertura", revision: "1.9",
//            inline: "true", conf: "default", pathid: "cobertura.classpath")
//}

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



