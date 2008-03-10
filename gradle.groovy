import org.gradle.api.internal.dependencies.WebdavResolver
import org.gradle.api.tasks.testing.ForkMode
import org.tmatesoft.svn.core.wc.SVNClientManager
import ch.qos.logback.core.status.StatusManager
import org.tmatesoft.svn.core.wc.SVNStatusClient
import org.tmatesoft.svn.core.wc.SVNStatus
import org.tmatesoft.svn.core.wc.ISVNStatusHandler
import org.tmatesoft.svn.core.internal.wc.DefaultSVNAuthenticationManager
import org.tmatesoft.svn.core.wc.SVNWCUtil
import org.tmatesoft.svn.core.wc.SVNInfo
import org.tmatesoft.svn.core.wc.SVNRevision
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory
import org.gradle.api.GradleException

type = 'jar'
version = '0.1-snapshot'
group = 'org.gradle'
status = 'integration'

usePlugin('groovy')

dependencies {
    addDependency(confs: ['compile'], id: "org.codehaus.groovy:groovy-all:1.5.4") {
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
lib.lateInitalizeClosures << {it.project.task('gradle-core_jar').baseName = jarBaseName}

File buildIntegTestsDir

createTask('compileIntegtests') {
    buildIntegTestsDir = mkdir('integtest-classes')
    ant.taskdef(name: "groovyc", classname: "org.codehaus.groovy.ant.Groovyc")
    ant.groovyc(srcdir: "$srcRoot/integtest", destdir: buildIntegTestsDir)
}

distDirTask = dir('build/distribution/exploded')

createTask('explodedDist', dependsOn: ['lib', 'compileIntegtests', distDirTask.name]) {
    File distDir = file(distDirTask.name)
    File distSamplesDir = mkdir(distDir, 'samples')
    File distTutorialDir = mkdir(distSamplesDir, 'tutorial')
    File distBinDir = mkdir(distDir, 'bin')
    File distSrcDir = mkdir(distDir, 'src')
    File distLibDir = mkdir(distDir, 'lib')
    // todo: use the ant {} syntax when http://jira.codehaus.org/browse/GROOVY-2643 is fixed

    // Right now the ant build is still needed and therefore the script we call here need the ant properties set.
    // Later we want call them via Ant (as they are Groovy scripts)
    String buildSrcDir = "$srcRoot/build"
    ant.property(name: 'projectName', value: 'gradle')
    ant.property(name: 'distExplodedBinDir', value: distBinDir.absolutePath)
    ant.property(name: 'distExplodedLibDir', value: distBinDir.absolutePath)
    ant.property(name: 'distExplodedTutorialDir', value: distTutorialDir.absolutePath)
    ant.property(name: 'distExplodedSamplesDir', value: distSamplesDir.absolutePath)
    ant.property(name: 'buildSrcDir', value: buildSrcDir)
    ant.taskdef(name: "groovy", classname: "org.codehaus.groovy.ant.Groovy")
    logger.info('Generate start scripts')
    ant.groovy(src: "$buildSrcDir/StartScriptGenerator.groovy")
    logger.info('Generate tutorial')
    ant.groovy(src: "$buildSrcDir/SamplesGenerator.groovy") {
        delegate.classpath() {
            delegate.pathelement(location: "${buildIntegTestsDir}")
        }
    }
    ant.copy(file: task('gradle-core_jar').archivePath, toDir: distLibDir)
    // todo: enable when http://jira.codehaus.org/browse/GROOVY-2643 is fixed 
    //    ant.copy(toDir: distSrcDir) {
    //        [srcDirs + resourceDirs + groovySrcDirs].each {dir -> delegate.fileset(dir: dir)}
    //    }
    ant.copy(toDir: distSamplesDir) {delegate.fileset(dir: new File(srcRoot, 'samples'))}
    ant.copy(toDir: distSrcDir) {delegate.fileset(dir: new File(srcRoot, 'main/groovy'))}
    ant.copy(toDir: distSrcDir) {delegate.fileset(dir: new File(srcRoot, 'main/resources'))}
    ant.copy(toDir: distDir) {delegate.fileset(dir: new File(srcRoot, 'toplevel'))}

    dependencies.resolveClasspath('runtime').each {File file ->
        ant.copy(file: file, todir: distLibDir)
    }
    //        <chmod dir="${distExplodedDir}/bin" perm="ugo+rx" includes="**/*"/>

}

dist {
    dependsOn 'explodedDist'
    delegate.zip() {
        String prefix = "gradle-$version"
        destinationDir = file('build/distribution')
        zipFileSet(dir: file(distDirTask.name), prefix: prefix) {
            exclude 'bin/*'
        }
        zipFileSet(dir: file(distDirTask.name), prefix: prefix, fileMode: '775') {
            include 'bin/*'
            exclude 'bin/*.*'
        }
        zipFileSet(dir: file(distDirTask.name), prefix: prefix) {
            include 'bin/*.*'
        }
    }
    delegate.zip("$project.name-src") {
        String prefix = "gradle-src-$version"
        destinationDir = file('build/distribution')
        zipFileSet(dir: file(distDirTask.name), prefix: prefix) {
            include 'src/', 'build.xml', 'build.properties', 'ivy.xml', 'ivysettings.xml', 'build.properties',
                    'ivy.xml', 'ivysettings.xml', 'gradle.groovy'
        }
    }
}

SVNClientManager clientManager = null

createTask('svnInit') {
    DAVRepositoryFactory.setup();
    clientManager = SVNClientManager.newInstance(
            SVNWCUtil.createDefaultOptions(true), codehausUserName, codehausUserPassword)
}

createTask('checkWorkingCopy', dependsOn: 'svnInit') {
    SVNStatusClient statusClient = clientManager.getStatusClient()
    List statuses = []
    ISVNStatusHandler statusHandler = {SVNStatus status -> statuses << status} as ISVNStatusHandler
    statusClient.doStatus(projectDir, true, true, false, false, statusHandler)
    if (statuses) {
        StringWriter stringWriter = new StringWriter()
        statuses.each {SVNStatus status -> stringWriter.write(status.getURL().toString() + '\n') }
        throw new GradleException("The working copy is not in sync with HEAD. We can't release!\n$stringWriter")
    }
}

createTask('majorRelease', dependsOn: 'checkWorkingCopy') {
   
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
