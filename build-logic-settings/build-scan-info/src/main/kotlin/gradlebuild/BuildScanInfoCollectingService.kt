/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild

import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import com.gradle.develocity.agent.gradle.scan.BuildScanConfiguration
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
// Using star import to workaround https://youtrack.jetbrains.com/issue/KTIJ-24390
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf

/**
 * The outcome of a task, as far as the Build Scan info collectors care about it.
 */
enum class TaskOutcome {
    /**
     * The task ran its actions: it was neither up-to-date nor loaded from the build cache.
     */
    EXECUTED,

    /**
     * The task ran its actions and failed.
     */
    FAILED,

    /**
     * The task did not run its actions because it was up-to-date.
     */
    UP_TO_DATE,

    /**
     * The task did not run its actions because its outputs were loaded from the build cache.
     */
    FROM_CACHE,

    /**
     * The task did not run its actions because it was skipped or had no source.
     */
    SKIPPED
}

/**
 * A build service which observes the execution of every task in the build tree - the tasks of the main build
 * as well as the tasks of included builds like `build-logic` - and collects information for the Build Scan.
 *
 * It is currently implemented by two use cases:
 * 1. Collect cache misses for compilation tasks and publish a `CACHE_MISS` tag for a Build Scan.
 * 2. Collect failed task paths and display a link which points to the corresponding task in the Build Scan,
 *    like `https://ge.gradle.org/s/xxx/console-log?task=yyy`.
 *
 * Task types are matched on the finished build operation rather than on the task graph, so that no task has to be
 * looked up during configuration. This keeps the collectors compatible with Isolated Projects, which does not allow
 * a project to observe the tasks of other projects.
 */
abstract class AbstractBuildScanInfoCollectingService<T : BuildServiceParameters> : BuildService<T>, BuildOperationListener {

    /**
     * Which tasks do we need to monitor? For example, cache-miss-monitor monitors `AbstractCompile` tasks.
     *
     * Called from arbitrary threads.
     */
    abstract fun isMonitoredTask(taskClass: Class<*>): Boolean

    /**
     * Collects information from the result of a finished task of a monitored type.
     *
     * Called from arbitrary threads.
     */
    abstract fun action(taskPath: String, outcome: TaskOutcome)

    final override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent) = Unit

    final override fun progress(operationIdentifier: OperationIdentifier, progressEvent: OperationProgressEvent) = Unit

    final override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
        val details = buildOperation.details as? ExecuteTaskBuildOperationType.Details ?: return
        if (isMonitoredTask(details.taskClass)) {
            action(details.taskIdentityPath, finishEvent.taskOutcome)
        }
    }
}

/**
 * The path of the task within the build tree, e.g. `:core:compileJava` or `:build-logic:basics:compileKotlin`.
 */
private
val ExecuteTaskBuildOperationType.Details.taskIdentityPath: String
    get() = if (buildPath == ":") taskPath else buildPath + taskPath

private
val OperationFinishEvent.taskOutcome: TaskOutcome
    get() {
        if (failure != null) {
            return TaskOutcome.FAILED
        }
        val result = result as? ExecuteTaskBuildOperationType.Result ?: return TaskOutcome.SKIPPED
        // The skip message is `null` if and only if the task actually ran its actions,
        // i.e. it was not UP-TO-DATE, FROM-CACHE, SKIPPED or NO-SOURCE. Otherwise it is the message of the
        // corresponding `TaskExecutionOutcome`, which is what the console and the Tooling API report.
        return when (result.skipMessage) {
            null -> TaskOutcome.EXECUTED
            "UP-TO-DATE" -> TaskOutcome.UP_TO_DATE
            "FROM-CACHE" -> TaskOutcome.FROM_CACHE
            else -> TaskOutcome.SKIPPED
        }
    }

/**
 * Is this class the given class, or a subtype of it?
 *
 * Task types contributed by third-party plugins are loaded by the plugin class loader of the build that applies them,
 * which is not visible from a settings plugin, so those have to be matched by name.
 */
fun Class<*>.isSubtypeOf(className: String): Boolean = generateSequence(this) { it.superclass }.any { it.name == className }

/**
 * Registers a build service that observes the execution of every task in the build tree.
 *
 * Returns `null` when not running on TeamCity, where none of the collected information is consumed.
 */
fun <P : BuildServiceParameters, T : AbstractBuildScanInfoCollectingService<P>> Settings.registerTaskExecutionObserver(
    /* the implementation class to collect information from task execution results */
    klass: Class<T>,
    /* configures the parameters of the service */
    configureParameters: P.() -> Unit = {}
): Provider<T>? {
    if (System.getenv("TEAMCITY_VERSION") == null) {
        return null
    }
    val service: Provider<T> = gradle.sharedServices.registerIfAbsent(klass.simpleName, klass) { parameters.configureParameters() }
    gradle.serviceOf<BuildEventListenerRegistryInternal>().onOperationCompletion(service)
    return service
}

/**
 * Registers a build service that collects information about the tasks of the whole build tree and hands it to the
 * Build Scan. Only does anything on TeamCity, where the collected information is consumed.
 */
fun <T : AbstractBuildScanInfoCollectingService<BuildServiceParameters.None>> Settings.registerBuildScanInfoCollectingService(
    /* the implementation class to collect information from task execution results */
    klass: Class<T>,
    /* pass the collected information to the Build Scan */
    buildScanAction: BuildScanConfiguration.(collectedInfo: Provider<T>) -> Unit
) {
    val buildScan = extensions.findByType<DevelocityConfiguration>()?.buildScan ?: return
    val service = registerTaskExecutionObserver(klass) ?: return
    buildScan.buildScanAction(service)
}
