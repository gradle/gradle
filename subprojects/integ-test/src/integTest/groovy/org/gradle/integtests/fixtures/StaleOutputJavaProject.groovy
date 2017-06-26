/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.fixtures

import com.google.common.io.Files
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.internal.cleanup.DefaultBuildOutputDeleter
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.file.TestFile

class StaleOutputJavaProject {
    public final static String JAR_TASK_NAME = 'jar'
    private final TestFile testDir
    private final String projectPath
    private final String projectDir
    private final String buildDirName
    private final TestFile mainSourceFile
    private final TestFile redundantSourceFile
    private final TestFile mainClassFile
    private final TestFile redundantClassFile

    StaleOutputJavaProject(TestFile testDir) {
        this(testDir, "build")
    }

    StaleOutputJavaProject(TestFile testDir, String buildDirName) {
        this(testDir, buildDirName, null)
    }

    StaleOutputJavaProject(TestFile testDir, String buildDirName, String projectDir) {
        this.testDir = testDir
        this.projectDir = projectDir
        this.buildDirName = buildDirName
        this.projectPath = projectDir ? ":" + projectDir : ""
        mainSourceFile = writeJavaSourceFile('Main')
        redundantSourceFile = writeJavaSourceFile('Redundant')
        mainClassFile = determineClassFile(mainSourceFile)
        redundantClassFile = determineClassFile(redundantSourceFile)
    }

    private TestFile writeJavaSourceFile(String className) {
        String sourceFilePath = "src/main/java/${className}.java"
        sourceFilePath = prependRootDirName(sourceFilePath)
        def sourceFile = testDir.file(sourceFilePath)
        sourceFile << "public class $className {}"
        sourceFile
    }

    private TestFile determineClassFile(File sourceFile) {
        String classFilePath = "${defaultOutputDir()}/${Files.getNameWithoutExtension(sourceFile.name)}.class"
        classFilePath = prependRootDirName(classFilePath)
        testDir.file(classFilePath)
    }

    private String prependRootDirName(String filePath) {
        projectDir ? "$projectDir/$filePath" : filePath
    }

    String getBuildDirName() {
        buildDirName
    }

    TestFile getBuildDir() {
        testDir.file("$projectDir/$buildDirName")
    }

    TestFile getRedundantSourceFile() {
        redundantSourceFile
    }

    TestFile getMainClassFile() {
        mainClassFile
    }

    TestFile getRedundantClassFile() {
        redundantClassFile
    }

    TestFile getCustomOutputDir() {
        testDir.file("out")
    }

    TestFile getMainClassFileAlternate() {
        customOutputDir.file(mainClassFile.name)
    }

    TestFile getRedundantClassFileAlternate() {
        customOutputDir.file(redundantClassFile.name)
    }

    TestFile getJarFile() {
        String jarFileName = projectDir ? "${projectDir}.jar" : "${testDir.name}.jar"
        String path = prependRootDirName("$buildDirName/libs/$jarFileName")
        testDir.file(path)
    }

    String defaultOutputDir() {
        "$buildDirName/classes/java/main"
    }

    String getCompileTaskPath() {
        "${projectPath}:compileJava"
    }
    String getJarTaskPath() {
        "${projectPath}:$JAR_TASK_NAME"
    }

    void assertBuildTasksExecuted(ExecutionResult result) {
        result.assertTaskNotSkipped(getCompileTaskPath())
        result.assertTaskNotSkipped(getJarTaskPath())
    }

    void assertBuildTasksSkipped(ExecutionResult result) {
        result.assertTaskSkipped(getCompileTaskPath())
        result.assertTaskSkipped(getJarTaskPath())
    }

    void assertDoesNotHaveCleanupMessage(ExecutionResult result) {
        assert !result.output.contains(DefaultBuildOutputDeleter.STALE_OUTPUT_MESSAGE)
    }

    void assertHasCleanupMessage(ExecutionResult result) {
        result.assertOutputContains(DefaultBuildOutputDeleter.STALE_OUTPUT_MESSAGE)
    }

    boolean assertJarHasDescendants(String... relativePaths) {
        new JarTestFixture(jarFile).hasDescendants(relativePaths)
    }
}
