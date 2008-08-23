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

package org.gradle.build.integtests

import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class JavaProject {
    static final String JAVA_PROJECT_NAME = 'javaproject'
    static final String SHARED_NAME = 'shared'
    static final String API_NAME = 'api'
    static final String WEBAPP_1_NAME = 'webapp1'
    static final String SERVICES_NAME = 'services'
    static final String WEBAPP_1_PATH = "$SERVICES_NAME/$WEBAPP_1_NAME" as String

    static void execute(String gradleHome, String samplesDirName) {
        List projects = [SHARED_NAME, API_NAME, WEBAPP_1_NAME, SERVICES_NAME].collect {"JAVA_PROJECT_NAME/$it"} + JAVA_PROJECT_NAME
        String packagePrefix = 'build/classes/org/gradle'
        String testPackagePrefix = 'build/test-classes/org/gradle'
        File javaprojectDir = new File(samplesDirName, 'javaproject')

        // Build and test projects
        Executer.execute(gradleHome, javaprojectDir.absolutePath, ['clean', 'test'], [], '', Executer.DEBUG)

        // Check classes and resources
        checkExistence(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        checkExistence(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'main.properties')

        // Check test classes and resources
        checkExistence(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'PersonTest.class')
        checkExistence(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'test.properties')
        checkExistence(javaprojectDir, API_NAME, packagePrefix, API_NAME, 'PersonList.class')
        checkExistence(javaprojectDir, WEBAPP_1_PATH, packagePrefix, WEBAPP_1_NAME, 'TestTest.class')

        // Check test results and report
        checkExistence(javaprojectDir, SHARED_NAME, 'build/test-results/TEST-org.gradle.shared.PersonTest.xml')
        checkExistence(javaprojectDir, SHARED_NAME, 'build/test-results/TESTS-TestSuites.xml')
        checkExistence(javaprojectDir, SHARED_NAME, 'build/reports/tests/index.html')
        checkExistence(javaprojectDir, WEBAPP_1_PATH, 'build/test-results/TEST-org.gradle.webapp1.TestTestTest.xml')
        checkExistence(javaprojectDir, WEBAPP_1_PATH, 'build/test-results/TESTS-TestSuites.xml')
        checkExistence(javaprojectDir, WEBAPP_1_PATH, 'build/reports/tests/index.html')

        // Javdoc build
        Executer.execute(gradleHome, javaprojectDir.absolutePath, ['clean', 'javadoc'], [], '', Executer.DEBUG)
        checkExistence(javaprojectDir, SHARED_NAME, 'build/docs/javadoc/index.html')
        assertTrue(fileText(javaprojectDir, SHARED_NAME, 'build/docs/javadoc/org/gradle/shared/package-summary.html').contains("These are the shared classes."))
        checkExistence(javaprojectDir, API_NAME, 'build/docs/javadoc/index.html')
        assertTrue(fileText(javaprojectDir, API_NAME, 'build/docs/javadoc/org/gradle/api/package-summary.html').contains("These are the API classes"))
        checkExistence(javaprojectDir, WEBAPP_1_PATH, 'build/docs/javadoc/index.html')

        // Partial build using current directory
        Executer.execute(gradleHome, new File(javaprojectDir, "$SERVICES_NAME/$WEBAPP_1_NAME").absolutePath,
                ['clean', 'libs'], [], '', Executer.DEBUG)
        checkPartialWebAppBuild(packagePrefix, javaprojectDir, testPackagePrefix)

        // Partial build using task path
        Executer.execute(gradleHome, javaprojectDir.absolutePath,
                ['clean', "$SHARED_NAME:compile"], [], '', Executer.DEBUG)
        checkExistence(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        checkExistence(javaprojectDir, false, API_NAME, packagePrefix, API_NAME, 'PersonList.class')

        // This test is also important for test cleanup
        Executer.execute(gradleHome, javaprojectDir.absolutePath, ['clean'], [], '', Executer.DEBUG)
        projects.each {assert !(new File(samplesDirName, "$it/build").exists())}
    }

    private static def checkPartialWebAppBuild(String packagePrefix, File javaprojectDir, String testPackagePrefix) {
        checkExistence(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'Person.class')
        checkExistence(javaprojectDir, SHARED_NAME, packagePrefix, SHARED_NAME, 'main.properties')
        checkExistence(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'PersonTest.class')
        checkExistence(javaprojectDir, SHARED_NAME, testPackagePrefix, SHARED_NAME, 'test.properties')
        checkExistence(javaprojectDir, "$SERVICES_NAME/$WEBAPP_1_NAME" as String, packagePrefix, WEBAPP_1_NAME, 'TestTest.class')
        checkExistence(javaprojectDir, "$SERVICES_NAME/$WEBAPP_1_NAME" as String, 'build', 'webapp1-2.5.war')
        checkExistence(javaprojectDir, false, "$SERVICES_NAME/$WEBAPP_1_NAME" as String, 'build', 'webapp1-2.5.jar')
    }

    static void checkExistence(File baseDir, String[] path) {
        checkExistence(baseDir, true, path)
    }

    static void checkExistence(File baseDir, boolean shouldExists, String[] path) {
        File file = new File(baseDir, path.join('/'))
        try {
            assert shouldExists ? file.exists() : !file.exists()
        } catch (AssertionError e) {
            if (shouldExists) {
                println("File: $file should exists, but does not!")
            } else {
                println("File: $file should not exists, but does!")
            }
            throw e
        }
    }

    static void main(String[] args) {
        execute(args[0], args[1])
    }

    static String fileText(File baseDir, String[] path) {
        new File(baseDir, path.join('/')).text
    }
}
