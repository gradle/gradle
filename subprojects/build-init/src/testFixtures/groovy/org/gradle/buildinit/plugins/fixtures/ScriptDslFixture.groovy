/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.buildinit.plugins.fixtures

import groovy.transform.CompileStatic
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JvmTestSuitePlugin
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher

import javax.annotation.Nullable

import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.GROOVY
import static org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl.KOTLIN
import static org.hamcrest.CoreMatchers.containsString

@CompileStatic
class ScriptDslFixture {

    static final List<BuildInitDsl> SCRIPT_DSLS = JavaVersion.current().isJava8Compatible() ? [GROOVY, KOTLIN] : [GROOVY]

    static final List<List<BuildInitDsl>> scriptDslCombinationsFor(int count) {
        return ([SCRIPT_DSLS] * count).combinations()
    }

    static ScriptDslFixture of(BuildInitDsl scriptDsl, TestFile rootDir, String subprojectName) {
        new ScriptDslFixture(scriptDsl, rootDir, subprojectName)
    }

    final BuildInitDsl scriptDsl
    final TestFile rootDir
    final String subprojectName

    ScriptDslFixture(BuildInitDsl scriptDsl, TestFile rootDir, String subprojectName) {
        this.scriptDsl = scriptDsl
        this.rootDir = rootDir
        this.subprojectName = subprojectName
    }

    String fileNameFor(String fileNameWithoutExtension) {
        scriptDsl.fileNameFor(fileNameWithoutExtension)
    }

    String getBuildFileName() {
        fileNameFor("build")
    }

    String getSettingsFileName() {
        fileNameFor("settings")
    }

    TestFile getBuildFile(TestFile parentFolder = rootDir) {
        if (subprojectName) {
            parentFolder.file(subprojectName).file(buildFileName)
        } else {
            parentFolder.file(buildFileName)
        }

    }

    TestFile getSettingsFile(TestFile parentFolder = rootDir) {
        parentFolder.file(settingsFileName)
    }

    TestFile scriptFile(String filePathWithoutExtension, TestFile parentFolder = rootDir) {
        def fileWithoutExtension = parentFolder.file(filePathWithoutExtension)
        new TestFile(fileWithoutExtension.parentFile, fileNameFor(fileWithoutExtension.name))
    }

    void assertGradleFilesGenerated(TestFile parentFolder = rootDir) {
        assert getBuildFile(parentFolder).exists()
        assert getSettingsFile(parentFolder).exists()
        def gradleVersion = GradleVersion.current().version
        new WrapperTestFixture(parentFolder).generated(gradleVersion)
    }

    void assertWrapperNotGenerated(TestFile parentFolder = rootDir) {
        new WrapperTestFixture(parentFolder).notGenerated()
    }

    Matcher<String> containsStringAssignment(target, string) {
        switch (scriptDsl) {
            case KOTLIN:
                return containsString("$target = \"$string\"")
            default:
                return containsString("$target = '$string'")
        }
    }

    private String blockTitleForSuite(suiteName) {
        switch (scriptDsl) {
            case KOTLIN:
                String delegateName = (suiteName == JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME) ? "getting" : "registering"
                return "val $suiteName by $delegateName(${JvmTestSuite.class.simpleName}::class)"
            case GROOVY:
                return suiteName
            default:
                throw new IllegalStateException("Unknown scriptDsl type: " + scriptDsl);
        }
    }

    @Nullable
    private String extractTopLevelBlockContents(String blockTitle, String text) {
        def allLines = text.readLines()
        def linesContainingBlock = allLines.findIndexValues { it.trim().startsWith(blockTitle) }
        if (linesContainingBlock.size() != 1) {
            return null;
        }

        int startIdx = linesContainingBlock[0] + 1;
        int endIdx = allLines.size()

        int openBraces = 1
        for (int idx = startIdx; idx < allLines.size(); idx++) {
            if (allLines[idx].contains("{")) {
                openBraces++
            }
            if (allLines[idx].contains("}")) {
                openBraces--
            }
            if (openBraces == 0) {
                endIdx = idx - 1
                break
            }
        }

        if (endIdx == allLines.size()) {
            return null
        } else {
            return allLines[startIdx..endIdx].join('\n').trim()
        }
    }

    Matcher<String> assertContainsTestSuite(String suiteName, File buildFile = getBuildFile()) {
        String testingBlock = extractTopLevelBlockContents("testing", buildFile.text)
        String suitesBlock = testingBlock ? extractTopLevelBlockContents("suites", testingBlock) : null
        String targetBlock = suitesBlock ? extractTopLevelBlockContents(blockTitleForSuite(suiteName), suitesBlock) : null

        return new BaseMatcher<String>() {
            @Override
            boolean matches(Object item) {
                return targetBlock
            }

            @Override
            void describeTo(Description description) {
                description.appendText("file ${buildFile.name} contains a test suite named: $suiteName")
            }
        }
    }

    Matcher<String> containsConfigurationDependencyNotation(String configuration, String notation, boolean useKotlinAccessors = true) {
        switch (scriptDsl) {
            case KOTLIN:
                if (useKotlinAccessors) {
                    return containsString("$configuration(\"$notation")
                } else {
                    return containsString("\"$configuration\"(\"$notation")
                }
            default:
                return containsString("$configuration '$notation")
        }
    }
}
