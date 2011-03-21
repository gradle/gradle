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
import org.apache.tools.ant.taskdefs.Delete
import org.gradle.integtests.fixtures.ExecutionResult
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.util.AntUtil
import org.gradle.util.SystemProperties
import org.junit.Assert
import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier
import com.google.common.collect.ListMultimap
import com.google.common.collect.ArrayListMultimap

class UserGuideSamplesRunner extends Runner {
    private static final String NL = SystemProperties.lineSeparator

    Class<?> testClass
    Description description
    Map<Description, SampleRun> samples;
    GradleDistribution dist = new GradleDistribution()
    GradleDistributionExecuter executer = new GradleDistributionExecuter(dist)

    def UserGuideSamplesRunner(Class<?> testClass) {
        this.testClass = testClass
        this.description = Description.createSuiteDescription(testClass)
        samples = new LinkedHashMap()
        for (sample in getScriptsForSamples(dist.userGuideInfoDir)) {
            Description childDescription = Description.createTestDescription(testClass, sample.id)
            description.addChild(childDescription)
            samples.put(childDescription, sample)

            println "Sample $sample.id dir: $sample.subDir"
            sample.runs.each { println "    args: $it.args expect: $it.outputFile" }
        }
    }

    Description getDescription() {
        return description
    }

    void run(RunNotifier notifier) {
        for (childDescription in description.children) {
            notifier.fireTestStarted(childDescription)
            SampleRun sampleRun = samples.get(childDescription)
            try {
                cleanup(sampleRun)
                for (run in sampleRun.runs) {
                    runSample(run)
                }
            } catch (Throwable t) {
                notifier.fireTestFailure(new Failure(childDescription, t))
            }
            notifier.fireTestFinished(childDescription)
        }
    }

    private def cleanup(SampleRun run) {
        // Clean up previous runs
        File rootProjectDir = dist.samplesDir.file(run.subDir)
        if (rootProjectDir.exists()) {
            def delete = new Delete()
            delete.dir = rootProjectDir
            delete.includes = "**/.gradle/** **/build/**"
            AntUtil.execute(delete)
        }
    }

    private def runSample(GradleRun run) {
        try {
            println("Test Id: $run.id, dir: $run.subDir, args: $run.args")
            File rootProjectDir = dist.samplesDir.file(run.subDir)
            executer.inDirectory(rootProjectDir).withArguments(run.args as String[]).withEnvironmentVars(run.envs)

            ExecutionResult result = run.expectFailure ? executer.runWithFailure() : executer.run()
            if (run.outputFile) {
                String expectedResult = replaceWithPlatformNewLines(dist.userGuideOutputDir.file(run.outputFile).text)
                try {
                    compareStrings(expectedResult, result.output, run.ignoreExtraLines)
                } catch (AssertionFailedError e) {
                    println 'Expected Result:'
                    println expectedResult
                    println 'Actual Result:'
                    println result.output
                    println '---'
                    throw e
                }
            }

            run.files.each { path ->
                println "  checking file '$path' exists"
                File file = new File(rootProjectDir, path).canonicalFile
                Assert.assertTrue("Expected file '$file' does not exist.", file.exists())
                Assert.assertTrue("Expected file '$file' is not a file.", file.isFile())
            }
            run.dirs.each { path ->
                println "  checking directory '$path' exists"
                File file = new File(rootProjectDir, path).canonicalFile
                Assert.assertTrue("Expected directory '$file' does not exist.", file.exists())
                Assert.assertTrue("Expected directory '$file' is not a directory.", file.isDirectory())
            }
        } catch (Throwable e) {
            throw new AssertionError("Integration test for sample '$run.id' in dir '$run.subDir' with args $run.args failed:${NL}$e.message").initCause(e)
        }
    }

    private def compareStrings(String expected, String actual, boolean ignoreExtraLines) {
        List actualLines = normaliseOutput(actual.readLines())
        List expectedLines = expected.readLines()
        int pos = 0
        for (; pos < actualLines.size() && pos < expectedLines.size(); pos++) {
            String expectedLine = expectedLines[pos]
            String actualLine = actualLines[pos]
            boolean matches = compare(expectedLine, actualLine)
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
        if (!ignoreExtraLines && pos < actualLines.size() && pos == expectedLines.size()) {
            Assert.fail("Extra lines in actual result, starting at line ${pos + 1}.${NL}Actual: ${actualLines[pos]}${NL}Actual output:${NL}$actual${NL}---")
        }
    }

    static String replaceWithPlatformNewLines(String text) {
        StringWriter stringWriter = new StringWriter()
        new PlatformLineWriter(stringWriter).withWriter { it.write(text) }
        stringWriter.toString()
    }

    List<String> normaliseOutput(List<String> lines) {
        lines.inject(new ArrayList<String>()) { List values, String line ->
            if (line.matches('Download .+')) {
                // ignore
            } else {
                values << line
            }
            values
        }
    }

    boolean compare(String expected, String actual) {
        if (actual == expected) {
            return true
        }

        if (expected == 'Total time: 1 secs') {
            return actual.matches('Total time: .+ secs')
        }
        
        // Normalise default object toString() values
        actual = actual.replaceAll('(\\w+(\\.\\w+)*)@\\p{XDigit}+', '$1@12345')
        // Normalise $samplesDir
        actual = actual.replaceAll(java.util.regex.Pattern.quote(dist.samplesDir.absolutePath), '/home/user/gradle/samples')
        // Normalise file separators
        actual = actual.replaceAll(java.util.regex.Pattern.quote(File.separator), '/')

        return actual == expected
    }

    static Collection<SampleRun> getScriptsForSamples(File userguideInfoDir) {
        def samplesXml = new File(userguideInfoDir, 'samples.xml')
        assertSamplesGenerated(samplesXml.exists())
        Node samples = new XmlParser().parse(samplesXml)
        ListMultimap<String, GradleRun> samplesByDir = ArrayListMultimap.create()

        def children = samples.children()
        assertSamplesGenerated(!children.isEmpty())

        children.each {Node sample ->
            String id = sample.'@id'
            String dir = sample.'@dir'
            String args = sample.'@args'
            String outputFile = sample.'@outputFile'
            boolean ignoreExtraLines = Boolean.valueOf(sample.'@ignoreExtraLines')

            GradleRun run = new GradleRun(id: id)
            run.subDir = dir
            run.args = args ? args.split('\\s+') as List : []
            run.outputFile = outputFile
            run.ignoreExtraLines = ignoreExtraLines as boolean

            sample.file.each { file -> run.files << file.'@path' }
            sample.dir.each { file -> run.dirs << file.'@path' }

            samplesByDir.put(dir, run)
        }

        // Some custom values
        samplesByDir.get('userguide/tutorial/properties').each { it.envs['ORG_GRADLE_PROJECT_envProjectProp'] = 'envPropertyValue' }
        samplesByDir.get('userguide/buildlifecycle/taskExecutionEvents')*.expectFailure = true
        samplesByDir.get('userguide/buildlifecycle/buildProjectEvaluateEvents')*.expectFailure = true

        Map<String, SampleRun> samplesById = new TreeMap<String, SampleRun>()

        // Remove duplicates for a given directory.
        samplesByDir.asMap().values().collect {List<GradleRun> dirSamples ->
            Collection<GradleRun> runs = dirSamples.findAll {it.mustRun}
            if (!runs) {
                // No samples in this dir have any args, so just run gradle tasks in the dir
                def sample = dirSamples[0]
                sample.args = ['tasks']
                sample
            } else {
                return runs
            }
        }.flatten().each { GradleRun run ->
            // Collect up into sample runs
            SampleRun sampleRun = samplesById[run.id]
            if (!sampleRun) {
                sampleRun = new SampleRun(id: run.id, subDir: run.subDir)
                samplesById[run.id] = sampleRun
            }
            sampleRun.runs << run
        }

        return samplesById.values()
    }

    static void assertSamplesGenerated(boolean assertion) {
        assert assertion : """Couldn't find any samples. Most likely, samples.xml was not generated.
Please run 'gradle check devBuild' first (you can skip tests in this case)
Probably some other task can help you as well but at the moment I don't know which one :) I tried gradle docs and it didn't help. If you find out please update this message. Thanks!"""
    }
}

class SampleRun {
    String id
    String subDir
    List<GradleRun> runs = []
}

class GradleRun {
    String id
    List args = []
    String subDir
    Map envs = [:]
    String outputFile
    boolean expectFailure
    boolean ignoreExtraLines
    List files = []
    List dirs = []

    boolean getMustRun() {
        return args || files || dirs
    }
}
