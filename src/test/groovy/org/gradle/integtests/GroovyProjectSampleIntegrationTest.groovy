/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.integtests

import org.junit.runner.RunWith
import org.junit.Test

/**
 * @author Hans Dockter
 */
@RunWith(DistributionIntegrationTestRunner.class)
class GroovyProjectSampleIntegrationTest {
    static final String GROOVY_PROJECT_NAME = 'groovyproject'
    static final String TEST_PROJECT_NAME = 'testproject'

    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Test
    public void groovyProjectSamples() {
        String packagePrefix = 'build/classes/org/gradle'
        String testPackagePrefix = 'build/test-classes/org/gradle'

        List mainFiles = ['JavaPerson', 'GroovyPerson', 'GroovyJavaPerson']
        List excludedFiles = ['ExcludeJava', 'ExcludeGroovy', 'ExcludeGroovyJava']
        List testFiles = ['JavaPersonTest', 'GroovyPersonTest', 'GroovyJavaPersonTest']

        File groovyProjectDir = new File(dist.samplesDir, GROOVY_PROJECT_NAME)
        File testProjectDir = new File(groovyProjectDir, TEST_PROJECT_NAME)

        // Build libs
        executer.inDirectory(groovyProjectDir).withTasks('clean', 'libs').run()
        mainFiles.each { JavaProjectSampleIntegrationTest.checkExistence(testProjectDir, packagePrefix, it + ".class")}
        excludedFiles.each { JavaProjectSampleIntegrationTest.checkExistence(testProjectDir, false, packagePrefix, it + ".class")}

        testFiles.each { JavaProjectSampleIntegrationTest.checkExistence(testProjectDir, testPackagePrefix, it + ".class") }

        // The test produce marker files with the name of the test class
        testFiles.each { JavaProjectSampleIntegrationTest.checkExistence(testProjectDir, 'build', it) }

        String unjarPath = "$testProjectDir/build/unjar"
        AntBuilder ant = new AntBuilder()
        ant.unjar(src: "$testProjectDir/build/$TEST_PROJECT_NAME-1.0.jar", dest: unjarPath)
        assert new File("$unjarPath/META-INF/MANIFEST.MF").text.contains('myprop: myvalue')
        assert new File("$unjarPath/META-INF/myfile").isFile()

        // Build docs
        executer.inDirectory(groovyProjectDir).withTasks('clean', 'javadoc', 'groovydoc').run()
        JavaProjectSampleIntegrationTest.checkExistence(testProjectDir, 'build/docs/javadoc/index.html')
        JavaProjectSampleIntegrationTest.checkExistence(testProjectDir, 'build/docs/groovydoc/index.html')

        // This test is also important for test cleanup
        executer.inDirectory(groovyProjectDir).withTasks('clean').run()
        assert !(new File(testProjectDir, "build").exists())
    }
}
