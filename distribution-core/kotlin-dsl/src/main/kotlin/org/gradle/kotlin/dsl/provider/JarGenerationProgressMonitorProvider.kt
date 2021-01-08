/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.kotlin.dsl.provider

import org.gradle.kotlin.dsl.support.ProgressMonitor

import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.progress.PercentageProgressFormatter

import java.io.File


interface JarGenerationProgressMonitorProvider {
    fun progressMonitorFor(outputJar: File, totalWork: Int): ProgressMonitor
}


internal
class StandardJarGenerationProgressMonitorProvider(
    val progressLoggerFactory: ProgressLoggerFactory
) : JarGenerationProgressMonitorProvider {

    override fun progressMonitorFor(outputJar: File, totalWork: Int): ProgressMonitor {
        val progressMessage = "Generating ${outputJar.name}"
        val progressLogger = progressLoggerFor(outputJar, progressMessage)
        val progressFormatter = PercentageProgressFormatter(progressMessage, totalWork)
        return object : ProgressMonitor {
            override fun onProgress() {
                progressLogger.progress(progressFormatter.incrementAndGetProgress())
            }

            override fun close() {
                progressLogger.completed()
            }
        }
    }

    private
    fun progressLoggerFor(outputJar: File, progressMessage: String): ProgressLogger =
        progressLoggerFactory.newOperation(JarGenerationProgressMonitorProvider::class.java).apply {
            description = "Generate ${outputJar.name}"
            started(progressMessage)
        }
}
