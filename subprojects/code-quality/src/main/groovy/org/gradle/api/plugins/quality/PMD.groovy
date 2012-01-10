/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality

import org.gradle.api.file.FileCollection

import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask
import org.gradle.api.tasks.InputFiles

/**
 * Runs a set of static code analysis rules on Java source code files and
 * generates a report of problems found.
 *
 * @see PmdPlugin
 */
class Pmd extends SourceTask implements VerificationTask {
    /**
     * The class path containing the PMD library to be used.
     */
    @InputFiles
    FileCollection pmdClassPath

    /**
     * The built-in rule sets to be used. See the <a href="http://pmd.sourceforge.net/rules/index.html">official list</a> of built-in rule sets.
     *
     * Example: ruleSets = ["basic", "braces"]
     */
    @Input
    List<String> ruleSets

    /**
     * The custom rule set files to be used. See the <a href="http://pmd.sourceforge.net/howtomakearuleset.html">official documentation</a> for
     * how to author a rule set file.
     *
     * Example: ruleSetFiles = files("config/pmd/myRuleSets.xml")
     */
    @InputFiles
    FileCollection ruleSetFiles
    
    /**
     * The file in which the XML report will be saved.
     *
     * Example: xmlReportFile = file("build/reports/pmdReport.xml")
     */
    @OutputFile
    File xmlReportFile

    /**
     * The file in which the HTML report will be saved.
     *
     * Example: htmlReportFile = file("build/reports/pmdReport.html")
     */
    @OutputFile
    File htmlReportFile

    /**
     * Whether or not to allow the build to continue if there are warnings.
     *
     * Example: ignoreFailures = true
     */
    Boolean ignoreFailures

    @TaskAction
    void run() {
        ant.taskdef(name: 'pmd', classname: 'net.sourceforge.pmd.ant.PMDTask', classpath: getPmdClassPath().asPath)
        ant.pmd(failOnRuleViolation: !getIgnoreFailures()) {
            getSource().addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
            getRuleSets().each {
                ruleset(it)
            }
            getRuleSetFiles().each {
                ruleset(it)
            }
            formatter(type: 'betterhtml', toFile: getHtmlReportFile())
            formatter(type: 'xml', toFile: getXmlReportFile())
        }
    }
}
