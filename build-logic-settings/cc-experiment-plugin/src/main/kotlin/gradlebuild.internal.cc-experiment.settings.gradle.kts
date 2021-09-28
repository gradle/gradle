/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.api.internal.StartParameterInternal
import org.gradle.configurationcache.problems.ProblemsListener
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.internal.watch.vfs.BuildFinishedFileSystemWatchingBuildOperationType
import org.gradle.kotlin.dsl.support.serviceOf

plugins {
    id("com.gradle.enterprise")
}

if ((gradle.startParameter as StartParameterInternal).configurationCache.get()) {
    gradleEnterprise {
        buildScan {
            val buildOpListenerManager = gradle.serviceOf<BuildOperationListenerManager>()
            val configCacheProblems = gradle.serviceOf<ProblemsListener>()
            background {
                buildOpListenerManager.addListener(object : BuildOperationListener {

                    override fun started(op: BuildOperationDescriptor, event: OperationStartEvent) = Unit
                    override fun progress(id: OperationIdentifier, event: OperationProgressEvent) = Unit

                    override fun finished(op: BuildOperationDescriptor, event: OperationFinishEvent) {
                        when (event.result) {
                            is BuildFinishedFileSystemWatchingBuildOperationType.Result -> {
                                buildOpListenerManager.removeListener(this)
                                // TODO if (!configCacheProblems.hasProblems) return
                                value("configuration-cache:problems", "true")
                                printProblemsHelp()
                            }
                        }
                    }

                    private
                    fun printProblemsHelp() {
                        val prefix = "CC>"
                        println("$prefix The gradle/gradle build enables the configuration cache as an experiment. It seems you are using a feature of this build that is not yet supported.")
                        println("$prefix You can disable the configuration cache with `--no-configuration-cache`. You can ignore problems with `--configuration-cache-problems=warn`.")
                        println("$prefix Please see further instructions in CONTRIBUTING.md")
                    }
                })
            }
        }
    }
}
