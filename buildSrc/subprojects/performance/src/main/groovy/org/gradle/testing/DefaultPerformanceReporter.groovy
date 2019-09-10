/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.testing


import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.internal.ProcessOperations
import org.gradle.api.internal.file.FileOperations
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec

import javax.inject.Inject

/**
 * A reported which generates HTML performance report based on the JUnit XML.
 */
@CompileStatic
class DefaultPerformanceReporter implements PerformanceReporter {
    String reportGeneratorClass

    FileOperations fileOperations

    ProcessOperations processOperations

    String projectName

    String githubToken

    @Inject
    DefaultPerformanceReporter(ProcessOperations processOperations, FileOperations fileOperations) {
        this.processOperations = processOperations
        this.fileOperations = fileOperations
    }

    @Override
    void report(PerformanceTest performanceTest) {
        performanceTest.generateResultsJson()

        fileOperations.delete(performanceTest.reportDir)
        ByteArrayOutputStream output = new ByteArrayOutputStream()

        ExecResult result = processOperations.javaexec(new Action<JavaExecSpec>() {
            void execute(JavaExecSpec spec) {
                spec.setMain(reportGeneratorClass)
                spec.args(performanceTest.reportDir.path, performanceTest.resultsJson.path, projectName)
                spec.systemProperties(performanceTest.databaseParameters)
                spec.systemProperty("org.gradle.performance.execution.channel", performanceTest.channel)
                spec.systemProperty("org.gradle.performance.execution.branch", performanceTest.branchName)
                spec.systemProperty("githubToken", githubToken)
                spec.setClasspath(performanceTest.classpath)

                spec.ignoreExitValue = true
                spec.setErrorOutput(output)
                spec.setStandardOutput(output)
            }
        })

        String message = output.toString().readLines().findAll { line ->
            ! [
                // WARNING: All illegal access operations will be denied in a future release
                "WARNING",
                // SLF4J: Class path contains multiple SLF4J bindings.
                "SLF4J"
            ].any { line.contains(it) }
        }.join("\n")

        if (result.exitValue != 0) {
            throw new GradleException("Performance test failed: " + message)
        } else {
            println(message)
        }
    }
}
