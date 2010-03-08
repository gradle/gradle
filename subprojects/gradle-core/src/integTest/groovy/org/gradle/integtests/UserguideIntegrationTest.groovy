/*
 * Copyright 2010 the original author or authors.
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

import groovy.io.PlatformLineWriter
import junit.framework.AssertionFailedError
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.StartParameter
import org.gradle.integtests.QuickGradleExecuter.StartParameterModifier
import org.gradle.util.TestFile

/**
 * @author Hans Dockter
 */
@RunWith (DistributionIntegrationTestRunner.class)
class UserguideIntegrationTest {

    private static Logger logger = LoggerFactory.getLogger(UserguideIntegrationTest)
    static final String NL = System.properties['line.separator']

    // Injected by test runner
    private GradleDistribution dist;
    private GradleExecuter executer;

    @Test
    public void apiLinks() {
        TestFile gradleHome = dist.gradleHomeDir         
        TestFile userguideInfoDir = dist.userGuideInfoDir
        Node links = new XmlParser().parse(new File(userguideInfoDir, 'links.xml'))
        links.children().each {Node link ->
            String classname = link.'@className'
            String lang = link.'@lang'
            File classDocFile = new File(gradleHome, "docs/${lang}doc/${classname.replace('.', '/')}.html")
            Assert.assertTrue("Could not find javadoc for class '$classname' referenced in userguide: $classDocFile does not exist.", classDocFile.isFile())
        }
    }

    @Test
    public void userGuideSamples() {
        TestFile samplesDir = dist.samplesDir
        TestFile userguideOutputDir = dist.userGuideOutputDir
        TestFile userguideInfoDir = dist.userGuideInfoDir

        Collection testRuns = getScriptsForSamples(userguideInfoDir)
        testRuns.each {GradleRun run ->
            try {
                logger.info("Test Id: $run.id, dir: $run.subDir, args: $run.execute")
                File rootProjectDir = new File(samplesDir, run.subDir)
                executer.inDirectory(rootProjectDir).withArguments(run.execute as String[]).withEnvironmentVars(run.envs)
                if (executer instanceof QuickGradleExecuter) {
                    ((QuickGradleExecuter) executer).setInProcessStartParameterModifier(createModifier(rootProjectDir))
                }
                ExecutionResult result = run.expectFailure ? executer.runWithFailure() : executer.run()
                if (run.outputFile) {
                    String expectedResult = replaceWithPlatformNewLines(new File(userguideOutputDir, run.outputFile).text)
                    try {
                        compareStrings(expectedResult, result.output)
                    } catch (AssertionFailedError e) {
                        println 'Expected Result:'
                        println expectedResult
                        println 'Actual Result:'
                        println result.output
                        println '---'
                        throw e
                    }
                }
            } catch (Throwable e) {
                throw new AssertionError("Integration test for sample '$run.id' in dir '$run.subDir' failed: $e.message").initCause(e)
            }
        }
    }

    private static def compareStrings(String expected, String actual) {
        List actualLines = removePotentialBuildSuccessfulMessages(actual.readLines())
        List expectedLines = removePotentialBuildSuccessfulMessages(expected.readLines())
        int pos = 0
        for (; pos < actualLines.size() && pos < expectedLines.size(); pos++) {
            String expectedLine = expectedLines[pos]
            String actualLine = actualLines[pos]
            String normalisedActual = actualLine.replaceAll(java.util.regex.Pattern.quote(File.separator), '/')
            boolean matches = normalisedActual == expectedLine
            if (!matches) {
                if (expectedLine.contains(actualLine)) {
                    Assert.fail("Missing text at line ${pos + 1}.${NL}Expected: ${expectedLine}${NL}Actual: ${actualLine}${NL}---${NL}Actual output:${NL}$actual${NL}---")
                }
                if (actualLine.contains(expectedLine)) {
                    Assert.fail("Extra text at line ${pos + 1}.${NL}Expected: ${expectedLine}${NL}Actual: ${actualLine}${NL}---${NL}Actual output:${NL}$actual${NL}---")
                }
                Assert.fail("Unexpected value at line ${pos + 1}.${NL}Expected: ${expectedLine}${NL}Actual: ${actualLine}${NL}---${NL}Actual output:${NL}$actual${NL}---")
            }
        }
        if (pos == actualLines.size() && pos < expectedLines.size()) {
            Assert.fail("Lines missing from actual result, starting at line ${pos + 1}.${NL}Expected: ${expectedLines[pos]}${NL}Actual output:${NL}$actual${NL}---")
        }
        if (pos < actualLines.size() && pos == expectedLines.size()) {
            Assert.fail("Extra lines in actual result, starting at line ${pos + 1}.${NL}Actual: ${actualLines[pos]}${NL}Actual output:${NL}$actual${NL}---")
        }
    }

    static String replaceWithPlatformNewLines(String text) {
        StringWriter stringWriter = new StringWriter()
        new PlatformLineWriter(stringWriter).withWriter { it.write(text) }
        stringWriter.toString()
    }

    static Collection getScriptsForSamples(File userguideInfoDir) {
        Node samples = new XmlParser().parse(new File(userguideInfoDir, 'samples.xml'))
        Map samplesByDir = new LinkedHashMap()

        samples.children().each {Node sample ->
            String id = sample.'@id'
            String dir = sample.'@dir'
            String args = sample.'@args'
            String outputFile = sample.'@outputFile'
            if (!samplesByDir[dir]) {
                samplesByDir[dir] = []
            }
            samplesByDir[dir] << [id: id, dir: dir, args: args, envs: [:], expectFailure: false, outputFile: outputFile]
        }

        // Some custom values
        samplesByDir['userguide/tutorial/properties'].each { it.envs['ORG_GRADLE_PROJECT_envProjectProp'] = 'envPropertyValue' }
        samplesByDir['userguide/buildlifecycle/taskExecutionEvents']*.expectFailure = true
        samplesByDir['userguide/buildlifecycle/buildProjectEvaluateEvents']*.expectFailure = true

        return samplesByDir.values().collect {List dirSamples ->
            List runs = dirSamples.findAll {it.args}
            if (!runs) {
                def sample = dirSamples[0]
                return new GradleRun(id: sample.id, subDir: sample.dir, execute: ['-t'], outputFile: null, envs: sample.envs, expectFailure: sample.expectFailure)
            }
            else {
                return runs.collect {sample ->
                    new GradleRun(id: sample.id, subDir: sample.dir, execute: sample.args.split('\\s+'), outputFile: sample.getOutputFile, envs: sample.envs, expectFailure: sample.expectFailure)
                }
            }
        }.flatten()
    }

    static List removePotentialBuildSuccessfulMessages(List lines) {
        if (lines.isEmpty()) {
            return lines
        }
        List normalizedLines = null
        if (lines.last().matches('Total time: .+ secs')) {
            normalizedLines = lines[0..lines.size() - 5]
        }
        return normalizedLines ?: lines;
    }

    static org.gradle.integtests.QuickGradleExecuter.StartParameterModifier createModifier(File rootProjectDir) {
        {StartParameter parameter ->
            if (parameter.getBuildFile() != null) {
                parameter.setBuildFile(normalizedPath(parameter.getBuildFile(), rootProjectDir));
            }
            if (parameter.getCurrentDir() != null) {
                parameter.setCurrentDir(normalizedPath(parameter.getCurrentDir(), rootProjectDir));
            }
            List<File> initScripts = new ArrayList<File>();
            for (File initScript: parameter.getInitScripts()) {
                initScripts.add(normalizedPath(initScript, rootProjectDir));
            }
            parameter.setInitScripts(initScripts);
        } as StartParameterModifier
    }

    static File normalizedPath(File path, File rootProjectDir) {
        String pathName = path.getAbsolutePath();
        if (!pathName.startsWith(rootProjectDir.getAbsolutePath())) {
            String currentDirName = new File("").getAbsolutePath();
            if (!pathName.startsWith(currentDirName)) {
                throw new RuntimeException("Path " + path + " is neither subdir of Gradle home nor of the root project!.")
            }
            pathName = new File(rootProjectDir, pathName.substring(currentDirName.length() + 1)).getAbsolutePath();
        }
        return new File(pathName);
    }
}

class GradleRun {
    String id
    List execute = []
    String subDir
    Map envs = [:]
    String outputFile
    boolean expectFailure
}