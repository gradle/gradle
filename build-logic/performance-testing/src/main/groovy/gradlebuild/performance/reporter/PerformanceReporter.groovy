/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.performance.reporter

import gradlebuild.basics.Gradle9PropertyUpgradeSupport
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.JavaExecSpec

import javax.inject.Inject

/**
 * A reported which generates HTML performance report based on the JUnit XML.
 */
@CompileStatic
class PerformanceReporter {
    private final FileSystemOperations fileOperations
    private final ExecOperations execOperations

    @Inject
    PerformanceReporter(ExecOperations execOperations, FileSystemOperations fileOperations) {
        this.execOperations = execOperations
        this.fileOperations = fileOperations
    }

    void report(
        String reportGeneratorClass,
        File reportDir,
        Iterable<File> resultJsons,
        Map<String, String> databaseParameters,
        String channel,
        Set<String> channelPatterns,
        String branchName,
        String commitId,
        FileCollection classpath,
        String projectName,
        String dependencyBuildIds,
        boolean debugReportGeneration
    ) {
        fileOperations.delete {
           it.delete(reportDir)
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream()

        ExecResult result = execOperations.javaexec(new Action<JavaExecSpec>() {
            void execute(JavaExecSpec spec) {
                spec.getMainClass().set(reportGeneratorClass)
                spec.args(reportDir.path, projectName)
                spec.args(resultJsons*.path)
                spec.systemProperties(databaseParameters)
                spec.debugOptions.enabled.set(debugReportGeneration)
                spec.systemProperty("org.gradle.performance.execution.channel", channel)
                spec.systemProperty("org.gradle.performance.execution.channel.patterns", channelPatterns.join(","))
                spec.systemProperty("org.gradle.performance.execution.branch", branchName)
                spec.systemProperty("org.gradle.performance.dependencyBuildIds", dependencyBuildIds)

                // For org.gradle.performance.util.Git
                spec.systemProperty("gradleBuildBranch", branchName)
                spec.systemProperty("gradleBuildCommitId", commitId)

                spec.setClasspath(classpath)

                Gradle9PropertyUpgradeSupport.setProperty(spec, "setIgnoreExitValue", true)
                Gradle9PropertyUpgradeSupport.setProperty(spec, "setErrorOutput", output)
                Gradle9PropertyUpgradeSupport.setProperty(spec, "setStandardOutput", output)
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

        println(message)
        if (result.exitValue != 0) {
            throw new GradleException("Performance test failed: " + message)
        }
    }
}
