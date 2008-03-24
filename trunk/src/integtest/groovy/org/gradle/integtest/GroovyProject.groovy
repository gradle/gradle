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

package org.gradle.integtest

/**
 * @author Hans Dockter
 */
class GroovyProject {
    static final String GROOVY_PROJECT_NAME = 'groovyproject'

    static void main(String[] args) {
        String packagePrefix = 'build/classes/org/gradle'
        String testPackagePrefix = 'build/test-classes/org/gradle'

        String samplesDirName = args[0]
        String gradleHome = args[1]

        List mainFiles = ['JavaPerson', 'GroovyPerson', 'GroovyJavaPerson']
        List testFiles = ['JavaPersonTest', 'GroovyPersonTest', 'GroovyJavaPersonTest']

        File groovyProjectDir = new File(samplesDirName, GROOVY_PROJECT_NAME)
        Executer.execute(gradleHome, groovyProjectDir.absolutePath, ['clean', 'test'])
        mainFiles.each { JavaProject.checkExistence(groovyProjectDir, packagePrefix, it + ".class")}

        testFiles.each { JavaProject.checkExistence(groovyProjectDir, testPackagePrefix, it + ".class") }

        testFiles.each { JavaProject.checkExistence(groovyProjectDir, 'build', it) }

        // This test is also important for test cleanup
        Executer.execute(gradleHome, groovyProjectDir.absolutePath, ['clean'])
        assert !(new File(groovyProjectDir, "build").exists())
    }

     

    
}
