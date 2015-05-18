/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.testing.jacoco.plugins

import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.TaskCollection
import org.gradle.internal.jacoco.JacocoAgentJar
import org.gradle.process.JavaForkOptions
import org.gradle.testing.jacoco.tasks.coverage.CoverageType
import org.gradle.testing.jacoco.tasks.coverage.JacocoCheckCoverage

import java.util.regex.Pattern

import static org.gradle.api.logging.Logging.getLogger

/**
 * Extension including common properties and methods for Jacoco.
 */
@Incubating
class JacocoPluginExtension {
    static final String TASK_EXTENSION_NAME = 'jacoco'

    // For convenience in build scripts.
    public static final CoverageType BRANCH = CoverageType.BRANCH
    public static final CoverageType CLASS = CoverageType.CLASS
    public static final CoverageType COMPLEXITY = CoverageType.COMPLEXITY
    public static final CoverageType INSTRUCTION = CoverageType.INSTRUCTION
    public static final CoverageType LINE = CoverageType.LINE
    public static final CoverageType METHOD = CoverageType.METHOD

    Logger logger = getLogger(getClass())

    /** The coverage threshold rules to be enforced by {@link JacocoCheckCoverage}. */
    public final List<Closure<Double>> coverageRules = new ArrayList<>()

    /**
     * Version of Jacoco JARs to use.
     */
    String toolVersion = '0.7.1.201405082137'

    protected final Project project

    /**
     * The directory where reports will be generated.
     */
    File reportsDir

    private final JacocoAgentJar agent

    /**
     * Creates a Jacoco plugin extension.
     * @param project the project the extension is attached to
     * @param agent the agent JAR to be used by Jacoco
     */
    JacocoPluginExtension(Project project, JacocoAgentJar agent) {
        this.project = project
        this.agent = agent
    }

    /**
     * Applies Jacoco to the given task. Configuration options will be
     * provided on a task extension named {@link #TASK_EXTENSION_NAME}.
     * Jacoco will be run as an agent during the execution of the task.
     * @param task the task to apply Jacoco to.
     */
    void applyTo(JavaForkOptions task) {
        logger.debug "Applying Jacoco to $task.name"
        JacocoTaskExtension extension = task.extensions.create(TASK_EXTENSION_NAME, JacocoTaskExtension, agent, task)
        task.jacoco.conventionMapping.destinationFile = { project.file("${project.buildDir}/jacoco/${task.name}.exec") }
        task.doFirst {
            //add agent
            if (extension.enabled) {
                task.jvmArgs extension.getAsJvmArg()
            }
        }
    }

    /**
     * Applies Jacoco to all of the given tasks.
     * @param tasks the tasks to apply Jacoco to
     * @see #applyTo(JavaForkOptions)
     */
    void applyTo(TaskCollection tasks) {
        tasks.withType(JavaForkOptions) {
            applyTo(it)
        }
    }

    /**
     * Adds a minimum coverage threshold for all files and coverage types.
     * @param value The minimum required threshold, a number in [0,1].
     */
    void threshold(double value) {
        coverageRules.add({
            CoverageType ct, String str -> value
        })
    }

    /**
     * Adds a minimum coverage threshold for the given coverage type and for all files.
     * @param value The minimum required threshold, a number in [0,1].
     * @param coverageType The type of JaCoCo coverage this threshold applies to.
     */
    void threshold(double value, CoverageType coverageType) {
        threshold(value, coverageType, ~/.*/)
    }

    /**
     * Adds a minimum coverage threshold for the given coverage type and files with the given filename (in any directory).
     * @param value The minimum required threshold, a number in [0,1].
     * @param coverageType The type of JaCoCo coverage this rule applies to.
     * @param filename The name of the file(s) that this threshold applies to.
     */
    void threshold(double value, CoverageType coverageType, String filename) {
        coverageRules.add({ CoverageType ct, String str ->
            if (coverageType == ct && str.equals(filename)) {
                return value
            }
            return 2.0 // Threshold >1.0 are filtered out before determining violation; thus, returning 2.0 results in this check being ignored.
        })
    }

    /**
     * Adds a minimum coverage threshold for the given coverage type and files (in any directory) matching the given pattern.
     * @param value The minimum required threshold, a number in [0,1].
     * @param coverageType The type of JaCoCo coverage this rule applies to.
     * @param fileNamePattern A regular expression specifying the name of the file(s) that this threshold applies to.
     */
    void threshold(double value, CoverageType coverageType, Pattern fileNamePattern) {
        coverageRules.add({ CoverageType ct, String str ->
            if (coverageType == ct && str =~ fileNamePattern) {
                return value
            }
            return 2.0 // Threshold >1.0 are filtered out before determining violation; thus, returning 2.0 results in this check being ignored.
        })
    }
}
