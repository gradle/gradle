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

import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.plugins.quality.internal.PmdReportsImpl
import org.gradle.api.reporting.Reporting
import org.gradle.api.tasks.*
import org.gradle.internal.reflect.Instantiator
import org.gradle.logging.ConsoleRenderer

import javax.inject.Inject

/**
 * Runs a set of static code analysis rules on Java source code files and
 * generates a report of problems found.
 *
 * @see PmdPlugin
 */
class Pmd extends SourceTask implements VerificationTask, Reporting<PmdReports> {
    /**
     * The class path containing the PMD library to be used.
     */
    @InputFiles
    FileCollection pmdClasspath

    /**
     * The built-in rule sets to be used. See the <a href="http://pmd.sourceforge.net/rules/index.html">official list</a> of built-in rule sets.
     *
     * Example: ruleSets = ["basic", "braces"]
     */
    @Input
    List<String> ruleSets

    /**
     * The target jdk to use with pmd
     */
    @Input
    String targetJdk

    /**
     * The custom rule set files to be used. See the <a href="http://pmd.sourceforge.net/howtomakearuleset.html">official documentation</a> for
     * how to author a rule set file.
     *
     * Example: ruleSetFiles = files("config/pmd/myRuleSets.xml")
     */
    @InputFiles
    FileCollection ruleSetFiles

    @Nested
    private final PmdReportsImpl reports

    private final IsolatedAntBuilder antBuilder

    /**
     * Whether or not to allow the build to continue if there are warnings.
     *
     * Example: ignoreFailures = true
     */
    boolean ignoreFailures

    @Inject Pmd(Instantiator instantiator, IsolatedAntBuilder antBuilder) {
        reports = instantiator.newInstance(PmdReportsImpl, this)
        this.antBuilder = antBuilder
    }

    @TaskAction
    void run() {
        boolean oldBranch = getPmdClasspath().find {
            it.name ==~ /pmd-([1-4]\.[0-9\.]+)\.jar/
        }
        antBuilder.withClasspath(getPmdClasspath()).execute {
            ant.taskdef(name: 'pmd', classname: 'net.sourceforge.pmd.ant.PMDTask')
            if (oldBranch) {
                ant.pmd(failOnRuleViolation: false, failuresPropertyName: "pmdFailureCount", targetjdk: targetJdk) {
                    getSource().addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
                    if (getRuleSets().isEmpty()) {
                        setRuleSets(["basic"])
                    }
                    getRuleSets().each {
                        ruleset(it)
                    }
                    getRuleSetFiles().each {
                        ruleset(it)
                    }

                    if (reports.html.enabled) {
                        assert reports.html.destination.parentFile.exists()
                        formatter(type: 'betterhtml', toFile: reports.html.destination)
                    }
                    if (reports.xml.enabled) {
                        formatter(type: 'xml', toFile: reports.xml.destination)
                    }
                }
            } else {
                ant.pmd(failOnRuleViolation: false, failuresPropertyName: "pmdFailureCount") {
                    getSource().addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
                    if (getRuleSets().isEmpty()) {
                        setRuleSets(["java-basic"])
                    }
                    getRuleSets().each {
                        ruleset(it)
                    }
                    getRuleSetFiles().each {
                        ruleset(it)
                    }

                    if (reports.html.enabled) {
                        assert reports.html.destination.parentFile.exists()
                        formatter(type: 'html', toFile: reports.html.destination)
                    }
                    if (reports.xml.enabled) {
                        formatter(type: 'xml', toFile: reports.xml.destination)
                    }
                }
            }
            def failureCount = ant.project.properties["pmdFailureCount"]
            if (failureCount) {
                def message = "$failureCount PMD rule violations were found."
                def report = reports.firstEnabled
                if (report) {
                    def reportUrl = new ConsoleRenderer().asClickableFileUrl(report.destination)
                    message += " See the report at: $reportUrl"
                }
                if (getIgnoreFailures()) {
                    logger.warn(message)
                } else {
                    throw new GradleException(message)
                }
            }
        }
    }

    /**
     * Configures the reports to be generated by this task.
     */
    PmdReports reports(Closure closure) {
        reports.configure(closure)
    }

    /**
     * Returns the reports to be generated by this task.
     */
    PmdReports getReports() {
        reports
    }
}
