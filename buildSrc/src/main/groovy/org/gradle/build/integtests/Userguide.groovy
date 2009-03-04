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

import groovy.io.PlatformLineWriter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.junit.Assert

/**
 * @author Hans Dockter
 */
class Userguide {

    private static Logger logger = LoggerFactory.getLogger(Userguide)
    static String NL = System.properties['line.separator']

    static void execute(File gradleHome, File samplesDir, File userguideOutputDir, File userguideInfoDir) {
        getScriptsForSamples(userguideInfoDir).each {GradleRun run ->
            logger.info("Test Id: $run.id dir: $run.subDir execute: $run.execute")
            Map result
            if (run.groovyScript) {
                result = runGroovyScript(new File(samplesDir, "userguide/$run.subDir/$run.file"))
            } else {
                result = Executer.execute(gradleHome.absolutePath, new File(samplesDir, run.subDir).absolutePath, run.execute ? [run.execute] : [], run.envs, run.file,
                        run.debugLevel)
            }
            if (run.outputFile) {
                result.output = ">$result.unixCommand$NL" + result.output
                String expectedResult = replaceWithPlatformNewLines(new File(userguideOutputDir, run.outputFile).text)
                try {
                    compareStrings(expectedResult, result.output)
                } catch (AssertionError e) {
                    println 'Expected Result:'
                    println expectedResult
                    println 'Actual Result:'
                    println result.output
                    throw e
                }
            }

        }
    }

    private static def compareStrings(String expected, String actual) {
        List actualLines = actual.readLines()
        List expectedLines = expected.readLines()
        int pos = 0
        for (; pos < actualLines.size() && pos < expectedLines.size(); pos++) {
            String expectedLine = expectedLines[pos]
            String actualLine = actualLines[pos]
            boolean matches = actualLine == expectedLine ||
                    expectedLine.matches('Total time: .+ secs') && actualLine.matches('Total time: .+ secs')
            if (!matches) {
                if (expectedLine.contains(actualLine)) {
                    Assert.fail("Missing text at line ${pos + 1}.${NL}Expected: ${expectedLine}${NL}Actual: ${actualLine}")
                }
                if (actualLine.contains(expectedLine)) {
                    Assert.fail("Extra text at line ${pos + 1}.${NL}Expected: ${expectedLine}${NL}Actual: ${actualLine}")
                }
                Assert.fail("Unexpected value at line ${pos + 1}.${NL}Expected: ${expectedLine}${NL}Actual: ${actualLine}")
            }
        }
        if (pos == actualLines.size() && pos < expectedLines.size()) {
            Assert.fail("Lines missing from actual result, starting at line ${pos + 1}.${NL}Expected: ${expectedLines[pos]}")
        }
        if (pos < actualLines.size() && pos == expectedLines.size()) {
            Assert.fail("Extra lines in actual result, starting at line ${pos + 1}.${NL}Actual: ${actualLines[pos]}")
        }
    }

    static String replaceWithPlatformNewLines(String text) {
        StringWriter stringWriter = new StringWriter()
        new PlatformLineWriter(stringWriter).withWriter { it.write(text) }
        stringWriter.toString()
    }

    static Map runGroovyScript(File script) {
        StringWriter stringWriter = new StringWriter()
        PrintWriter printWriter = new PrintWriter(stringWriter)
        logger.info("Evaluating Groovy script: $script.absolutePath")
        new GroovyShell(new Binding(out: printWriter)).evaluate(script)
        [output: stringWriter, command: "groovy $script.name", unixCommand: "groovy $script.name", windowsCommand: "groovy $script.name"]
    }

    static Collection getScriptsForSamples(File userguideInfoDir) {
        Node samples = new XmlParser().parse(new File(userguideInfoDir, 'samples.xml'))
        Map samplesById = new LinkedHashMap()

        samples.children().each {Node sample ->
            String id = sample.'@id'
            String dir = sample.'@dir'
            String args = sample.'@args'
            if (samplesById[id]) {
                if (samplesById[id].dir != dir || (samplesById[id].args && args)) {
                    throw new RuntimeException("Duplicate sample with id '$id'.")
                }
                if (!args) {
                    return
                }
            }
            samplesById[id] = [id: id, dir: dir, args: args, envs: []]
        }

        // Some custom values
        samplesById['properties'].envs = ['ORG_GRADLE_PROJECT_envProjectProp=envPropertyValue']

        return samplesById.values().collect {sample ->
            String id = sample.id
            String dir = sample.dir
            String args = sample.args == null ? '-t' : sample.args
            String outputFile = sample.args != null ? "${id}.out" : null
            new GradleRun(id: id, subDir: dir, execute: args, outputFile: outputFile, debugLevel: Executer.LIFECYCLE, envs: sample.envs)
        }
    }
}

class GradleRun {
    String id
    String execute
    int debugLevel
    String file
    String subDir
    boolean groovyScript = false
    List envs = []
    String outputFile

    def withLoggingLevel(int debugLevel) {
        this.debugLevel = debugLevel
        this
    }
}