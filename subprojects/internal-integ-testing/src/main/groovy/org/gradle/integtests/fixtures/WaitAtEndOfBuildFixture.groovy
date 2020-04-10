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

package org.gradle.integtests.fixtures

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.execution.RunRootBuildWorkBuildOperationType
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.util.GradleVersion


class WaitAtEndOfBuildFixture {

    private static final GradleVersion SUPPORTS_BUILD_SERVICES = GradleVersion.version("6.1")

    /**
     * Generates build logic to wait a given amount of time at the end of the build.
     */
    static String buildLogicForEndOfBuildWait(long waitTimeMillis, GradleVersion gradleVersion = GradleVersion.current()) {
        if (gradleVersion < SUPPORTS_BUILD_SERVICES) {
            return """
                gradle.buildFinished {
                    Thread.sleep(${waitTimeMillis})
                }
            """
        }
        return """
            interface EndOfBuildWaitParams extends ${BuildServiceParameters.name} {
                Property<Long> getWaitTimeMillis()
            }

            abstract class EndOfBuildWait implements ${BuildService.name}<EndOfBuildWaitParams>, ${BuildOperationListener.name} {

                @Override
                void started(${BuildOperationDescriptor.name} buildOperation, ${OperationStartEvent.name} startEvent) {}

                @Override
                void progress(${OperationIdentifier.name} operationIdentifier, ${OperationProgressEvent.name} progressEvent) {}

                @Override
                void finished(${BuildOperationDescriptor.name} buildOperation, ${OperationFinishEvent.name} finishEvent) {
                    if (buildOperation.details instanceof ${RunRootBuildWorkBuildOperationType.Details.name}) {
                        Thread.sleep(parameters.waitTimeMillis.get())
                    }
                }
            }
            def endOfBuildWait = gradle.sharedServices.registerIfAbsent("endOfBuildWait", EndOfBuildWait) {
                parameters.waitTimeMillis.set(${waitTimeMillis}L)
            }
            gradle.services.get(${BuildEventListenerRegistryInternal.name}).onOperationCompletion(endOfBuildWait)
        """.stripIndent()
    }

    /**
     * Generates build logic to ensure builds last for a given minimum time.
     *
     * This is used to ensure that the lastModified() timestamps actually change in between builds.
     * If the build is very fast, the timestamp of the file will not change and the JDK file watch service won't see the change.
     */
    static String buildLogicForMinimumBuildTime(long minimumBuildTimeMillis, GradleVersion gradleVersion = GradleVersion.current()) {
        if (gradleVersion < SUPPORTS_BUILD_SERVICES) {
            return """
                def startAt = System.nanoTime()
                gradle.buildFinished {
                    long sinceStart = (System.nanoTime() - startAt) / 1000000L
                    if (sinceStart > 0 && sinceStart < $minimumBuildTimeMillis) {
                      Thread.sleep(($minimumBuildTimeMillis - sinceStart) as Long)
                    }
                }
            """
        }
        return """
            def startAt = System.nanoTime()

            interface MinimumBuildTimeParams extends ${BuildServiceParameters.name} {
                Property<Long> getStartAt()
                Property<Long> getMinimumBuildTimeMillis()
            }

            abstract class MinimumBuildTime implements ${BuildService.name}<MinimumBuildTimeParams>, ${BuildOperationListener.name} {

                @Override
                void started(${BuildOperationDescriptor.name} buildOperation, ${OperationStartEvent.name} startEvent) {}

                @Override
                void progress(${OperationIdentifier.name} operationIdentifier, ${OperationProgressEvent.name} progressEvent) {}

                @Override
                void finished(${BuildOperationDescriptor.name} buildOperation, ${OperationFinishEvent.name} finishEvent) {
                    if (buildOperation.details instanceof ${RunRootBuildWorkBuildOperationType.Details.name}) {
                        long sinceStart = (System.nanoTime() - parameters.startAt.get()) / 1000000L
                        if (sinceStart > 0 && sinceStart < parameters.minimumBuildTimeMillis.get()) {
                          Thread.sleep((parameters.minimumBuildTimeMillis.get() - sinceStart) as Long)
                        }
                    }
                }
            }
            def minimumBuildTime = gradle.sharedServices.registerIfAbsent("minimumBuildTime", MinimumBuildTime) {
                parameters.startAt.set(startAt)
                parameters.minimumBuildTimeMillis.set(${minimumBuildTimeMillis}L)
            }
            gradle.services.get(${BuildEventListenerRegistryInternal.name}).onOperationCompletion(minimumBuildTime)
        """.stripIndent()
    }
}
