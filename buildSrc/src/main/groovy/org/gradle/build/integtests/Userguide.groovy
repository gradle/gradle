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

    static void execute(String gradleHome, String samplesDirName, String userguideOutputDir) {
        File userguideDir = new File(samplesDirName, 'userguide')
        getScripts().each {GradleRun run ->
            logger.info("Test Id: $run.id")
            Map result
            if (run.groovyScript) {
                result = runGroovyScript(new File(userguideDir, "$run.subDir/$run.file"))
            } else {
                result = Executer.execute(gradleHome, new File(userguideDir, run.subDir).absolutePath, run.execute ? [run.execute] : [], run.envs, run.file,
                        run.debugLevel)
            }
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
                Assert.fail("Unexpected value at line ${pos+1}.${NL}Expected: ${expectedLine}${NL}Actual: ${actualLine}")
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

    static void main(String[] args) {
        execute(args[0], args[1], args[2])
    }

    static List getScripts() {
        [
                new GradleRun(subDir: 'tutorial', id: 'scope', file: 'scope.groovy', groovyScript: true),
                runMp('firstExample/water', 'FirstExample', 'hello'),
                runMp('addKrill/water', 'AddKrill', 'hello'),
                runMp('useSubprojects/water', 'UseSubprojects', 'hello'),
                runMp('subprojectsAddFromTop/water', 'SubprojectsAddFromTop', 'hello'),
                runMp('spreadSpecifics/water', 'SpreadSpecifics', 'hello'),
                runMp('addTropical/water', 'AddTropical', 'hello'),
                runMp('tropicalWithProperties/water', 'TropicalWithProperties', 'hello'),
                runMp('tropicalWithProperties/water/bluewhale', 'SubBuild', 'hello'),
                runMp('partialTasks/water', 'PartialTasks', 'distanceToIceberg'),
//                runMp('partialTasks/water', 'PartialTasksNotQuiet', 'distanceToIceberg', Executer.LIFECYCLE),
                runMp('partialTasks/water/tropicalFish', 'AbsoluteTaskPaths', ':hello :krill:hello hello'),
                runMp('dependencies/firstMessages/messages', 'FirstMessages', 'action'),
                runMp('dependencies/messagesHack/messages', 'MessagesHack', 'action'),
                runMp('dependencies/messagesHack/messages/consumer', 'MessagesHackBroken', 'action'),
                runMp('dependencies/messagesWithDependencies/messages', 'MessagesDependencies', 'action'),
                runMp('dependencies/messagesWithDependencies/messages/consumer', 'MessagesDependenciesSubBuild', 'action'),
                runMp('dependencies/messagesDifferentTaskNames/messages/consumer', 'MessagesDifferentTaskNames', 'consume'),
                runMp('dependencies/messagesTaskDependencies/messages/consumer', 'MessagesTaskDependencies', 'consume'),
                runMp('dependencies/messagesConfigDependenciesBroken/messages/consumer', 'MessagesConfigDependenciesBroken', 'consume'),
                runMp('dependencies/messagesConfigDependencies/messages/consumer', 'MessagesConfigDependencies', 'consume'),
                runMp('dependencies/messagesConfigDependenciesAltSolution/messages/consumer', 'MessagesConfigDependenciesAltSolution', 'consume'),
                runMp('flat/master', 'Flat', 'hello'),
                runMp('flat/shark', 'FlatPartial', 'hello'),
                new GradleRun(subDir: 'tutorial/properties', id: 'properties', debugLevel: Executer.QUIET, envs: ['ORG_GRADLE_PROJECT_envProjectProp=envPropertyValue'],
                                        execute: '-PcommandLineProjectProp=commandLineProjectPropValue -Dorg.gradle.project.systemProjectProp=systemPropertyValue printProps'),
                run('tutorial', 'antChecksum', 'checksum'),
                run('tutorial', 'antChecksumWithMethod', 'checksum'),
                run('tutorial', 'autoskip', '-Dskip.autoskip autoskip'),
                run('tutorial', 'autoskipDepends', '-Dskip.autoskip depends'),
                run('tutorial', 'configByDag', 'release'),
                run('tutorial', 'configureObject', 'configure'),
                run('tutorial', 'count', 'count'),
                run('tutorial', 'defaultTasks', ''),
                run('tutorial', 'directoryTask', 'otherResources'),
                run('tutorial', 'disableTask', 'disableMe'),
                run('tutorial', 'dynamic', 'task_1'),
                run('tutorial', 'dynamicDepends', 'task_0'),
                run('tutorial', 'dynamicProperties', 'showProps'),
                run('tutorial', 'hello', 'hello'),
                run('tutorial', 'helloEnhanced', 'hello'),
                run('tutorial', 'helloWithShortCut', 'hello'),
                run('tutorial', 'intro', 'intro'),
                run('tutorial', 'lazyDependsOn', 'taskX'),
                run('tutorial', 'makeDirectory', 'compile'),
                run('tutorial', 'mkdirTrap', 'compile'),
                run('tutorial', 'multipleTasksFromCommandLine', 'libs test'),
                run('tutorial', 'pluginConfig', 'check'),
                run('tutorial', 'pluginConvention', 'check'),
                run('tutorial', 'pluginIntro', 'check'),
                run('tutorial', 'projectApi', 'check'),
                run('tutorial', 'projectReports', '--tasks').withOutputFile('taskListReport.out'),
                run('tutorial', 'projectReports', '--dependencies').withOutputFile('dependencyListReport.out'),
//                run('tutorial', 'projectReports', '--properties').withOutputFile('propertyListReport.out'),
                run('tutorial', 'replaceTask', 'resources'),
                run('tutorial', 'skipProperties', '-DmySkipProperty skipMe'),
                run('tutorial', 'stopExecutionException', 'myTask'),
                run('tutorial', 'upper', 'upper'),
                run('tutorial', 'zip', 'init'),
                run('tutorial', 'zipWithCustomName', 'init'),
                run('tutorial', 'zipWithArguments', 'init'),
                run('buildlifecycle', 'test').withLoggingLevel(Executer.LIFECYCLE),
                run('tasks', 'addDependencyUsingTask', 'taskX'),
                run('tasks', 'addDependencyUsingPath', 'taskX'),
                run('tasks', 'addDependencyUsingClosure', 'taskX')
        ]
    }

    static GradleRun run(String id, String execute, int debugLevel = Executer.QUIET) {
        new GradleRun(subDir: id, id: id, execute: execute, debugLevel: debugLevel)
    }
    
    static GradleRun run(String subDir, String id, String execute, int debugLevel = Executer.QUIET) {
        new GradleRun(subDir: "$subDir/$id", id: id, execute: execute, debugLevel: debugLevel)
    }

    static GradleRun runEnv(String subDir, String id, String execute, List envs, int debugLevel = Executer.QUIET) {
        new GradleRun(subDir: subDir, id: id, execute: execute, debugLevel: debugLevel, file: id + '.gradle', envs: envs)
    }

    static GradleRun runMp(String subDir, String id, String execute, int debugLevel = Executer.QUIET) {
        new GradleRun(subDir: "multiproject/" + subDir, id: 'multiproject' + id, execute: execute, debugLevel: debugLevel)
    }

    static Map runGroovyScript(File script) {
        StringWriter stringWriter = new StringWriter()
        PrintWriter printWriter = new PrintWriter(stringWriter)
        logger.info("Evaluating Groovy script: $script.absolutePath")
        new GroovyShell(new Binding(out: printWriter)).evaluate(script)
        [output: stringWriter, command: "groovy $script.name", unixCommand: "groovy $script.name", windowsCommand: "groovy $script.name"]
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

    def getOutputFile() {
        return outputFile ? outputFile : id + '.out'
    }

    def withOutputFile(outputFile) {
        this.outputFile = outputFile
        this
    }
    
    def withLoggingLevel(int debugLevel) {
        this.debugLevel = debugLevel
        this
    }
}