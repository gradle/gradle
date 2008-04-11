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
package org.gradle.build.samples

import org.gradle.build.integtests.Executer;

/**
 * @author Hans Dockter
 */
class Userguide {
    static String NL = System.properties['line.separator']

    static void createOutput(File outputFile, String gradleHome, File projectDir, List tasks, String gradlefile, int outputType) {
        AntBuilder ant = new AntBuilder()
        ant.delete(dir: new File(projectDir, 'build'))
        Map result = Executer.execute(gradleHome, projectDir.absolutePath, tasks, gradlefile, outputType)
        ant.delete(dir: new File(projectDir, 'build'))
        result.output = ">$result.command$NL" + result.output
        outputFile.write(result.output)
    }

    static void createNonTutorialOutput(File tutorialOutputDir, File explodedDistDir, File explodedDistSamplesDir) {
        createOutput(new File(tutorialOutputDir, 'buildlifecycle.out'), explodedDistDir.absolutePath,
            new File("$explodedDistSamplesDir/userguide/buildlifecycle"),
            ['test'], '', Executer.INFO)
        createOutput(new File(tutorialOutputDir, 'multiprojectFirstExample.out'), explodedDistDir.absolutePath,
            new File("$explodedDistSamplesDir/userguide/multiproject/firstExample/water"),
            ['hello'], '', Executer.QUIET)
    }

    static void checkGroovyScripts(File tutorialDir, File tutorialOutputDir) {
        String localScope1 = 'localScope1'
        String localScope2 = 'localScope2'
        String scriptScope = 'scriptScope'
        String notAvailable = 'NotAvailable'

        assert runScript(new File(tutorialDir, 'scope.groovy'), new File(tutorialOutputDir, 'scope.out')) ==
                "$localScope1$NL$localScope2$NL$scriptScope$NL$localScope1$NL$localScope2$NL$scriptScope$NL$localScope1$notAvailable$NL$localScope2$notAvailable$NL$scriptScope$NL"
    }

    static String runScript(File script, File outputFile) {
        StringWriter stringWriter = new StringWriter()
        PrintWriter printWriter = new PrintWriter(stringWriter)
        new GroovyShell(new Binding(out: printWriter)).evaluate(script)
        println "Evaluating Groovy script: $script.absolutePath"
        outputFile.write(">groovy $script.name$NL$stringWriter")
        stringWriter
    }
}
