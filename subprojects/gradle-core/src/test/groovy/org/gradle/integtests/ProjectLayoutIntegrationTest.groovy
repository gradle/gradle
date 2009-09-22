package org.gradle.integtests

import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DistributionIntegrationTestRunner.class)
class ProjectLayoutIntegrationTest {
    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Test
    public void canHaveSomeSourceAndResourcesInSameDirectoryAndSomeInDifferentDirectories() {
        dist.testFile('settings.gradle') << 'rootProject.name = "sharedSource"'
        dist.testFile('build.gradle') << '''
usePlugin('java')
usePlugin('groovy')
usePlugin('scala')

repositories {
    mavenRepo urls: 'http://scala-tools.org/repo-releases/'
    mavenCentral()
}
dependencies {
    groovy group: 'org.codehaus.groovy', name: 'groovy-all', version: '1.6.0'
    scalaTools group: 'org.scala-lang', name: 'scala-compiler', version: '2.7.6'
    scalaTools group: 'org.scala-lang', name: 'scala-library', version: '2.7.6'

    compile group: 'org.scala-lang', name: 'scala-library', version: '2.7.6'
}

source.each {
    configure(it) {
        resources.srcDir 'src'
        resources.srcDir 'src/resources'
        resources.include "org/gradle/$name/**"
        java.srcDir 'src'
        java.srcDir 'src/java'
        java.include "org/gradle/$name/**"
        groovy.srcDir 'src'
        groovy.srcDir 'src/groovy'
        groovy.include "org/gradle/$name/**"
        scala.srcDir 'src'
        scala.srcDir 'src/scala'
        scala.include "org/gradle/$name/**"
    }
}
'''
        dist.testFile('src/org/gradle/main/resource.txt') << 'some text'
        dist.testFile('src/org/gradle/test/resource.txt') << 'some text'
        dist.testFile('src/resources/org/gradle/main/resource2.txt') << 'some text'
        dist.testFile('src/resources/org/gradle/test/resource2.txt') << 'some text'
        dist.testFile('src/org/gradle/main/JavaClass.java') << 'package org.gradle; class JavaClass { }'
        dist.testFile('src/org/gradle/test/JavaClassTest.java') << 'package org.gradle; class JavaClassTest { JavaClass c = new JavaClass(); }'
        dist.testFile('src/java/org/gradle/main/JavaClass2.java') << 'package org.gradle; class JavaClass2 { }'
        dist.testFile('src/java/org/gradle/test/JavaClassTest2.java') << 'package org.gradle; class JavaClassTest2 { JavaClass c = new JavaClass(); }'
        dist.testFile('src/org/gradle/main/GroovyClass.groovy') << 'package org.gradle; class GroovyClass { }'
        dist.testFile('src/org/gradle/test/GroovyClassTest.groovy') << 'package org.gradle; class GroovyClassTest { GroovyClass c = new GroovyClass() }'
        dist.testFile('src/groovy/org/gradle/main/GroovyClass2.groovy') << 'package org.gradle; class GroovyClass2 { }'
        dist.testFile('src/groovy/org/gradle/test/GroovyClassTest2.groovy') << 'package org.gradle; class GroovyClassTest2 { GroovyClass c = new GroovyClass() }'
        dist.testFile('src/org/gradle/main/ScalaClass.scala') << 'package org.gradle; class ScalaClass { }'
        dist.testFile('src/org/gradle/test/ScalaClassTest.scala') << 'package org.gradle; class ScalaClassTest { val c: ScalaClass = new ScalaClass() }'
        dist.testFile('src/scala/org/gradle/main/ScalaClass2.scala') << 'package org.gradle; class ScalaClass2 { }'
        dist.testFile('src/scala/org/gradle/test/ScalaClassTest2.scala') << 'package org.gradle; class ScalaClassTest2 { val c: ScalaClass = new ScalaClass() }'

        executer.withTasks('build').run()

        dist.testFile('build/classes/main').assertHasDescendants(
                'org/gradle/main/resource.txt',
                'org/gradle/main/resource2.txt',
                'org/gradle/JavaClass.class',
                'org/gradle/JavaClass2.class',
                'org/gradle/GroovyClass.class',
                'org/gradle/GroovyClass2.class',
                'org/gradle/ScalaClass.class',
                'org/gradle/ScalaClass2.class'
        )

        dist.testFile('build/classes/test').assertHasDescendants(
                'org/gradle/test/resource.txt',
                'org/gradle/test/resource2.txt',
                'org/gradle/JavaClassTest.class',
                'org/gradle/JavaClassTest2.class',
                'org/gradle/GroovyClassTest.class',
                'org/gradle/GroovyClassTest2.class',
                'org/gradle/ScalaClassTest.class',
                'org/gradle/ScalaClassTest2.class'
        )

        TestFile tmpDir = dist.testFile('jarContents')
        dist.testFile('build/libs/sharedSource-unspecified.jar').unzipTo(tmpDir)

        tmpDir.assertHasDescendants(
                'META-INF/MANIFEST.MF',
                'org/gradle/main/resource.txt',
                'org/gradle/main/resource2.txt',
                'org/gradle/JavaClass.class',
                'org/gradle/JavaClass2.class',
                'org/gradle/GroovyClass.class',
                'org/gradle/GroovyClass2.class',
                'org/gradle/ScalaClass.class',
                'org/gradle/ScalaClass2.class'
        )
    }
}
