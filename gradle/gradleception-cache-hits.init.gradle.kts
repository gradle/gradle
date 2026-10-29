import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.execution.RunRootBuildWorkBuildOperationType
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.launcher.exec.RunBuildBuildOperationType
import org.gradle.operations.execution.ExecuteWorkBuildOperationType
import java.util.concurrent.ConcurrentLinkedQueue

val buildCacheDir = File(System.getProperty("gradleception.buildCacheDir"))
val verifyCacheHits: String? = System.getProperty("gradleception.verifyCacheHits")

if (gradle.parent == null) {
    gradle.settingsEvaluated {
        buildCache {
            local {
                isEnabled = true
                directory = buildCacheDir
            }
            if (verifyCacheHits != null) {
                remote?.isEnabled = false
            }
        }
    }
    if (verifyCacheHits != null) {
        val listenerManager = gradle.serviceOf<BuildOperationListenerManager>()
        listenerManager.addListener(CacheMissReporter(listenerManager, verifyCacheHits == "fail"))
    }
}

class CacheMissReporter(
    private val listenerManager: BuildOperationListenerManager,
    private val failOnCacheMiss: Boolean,
) : BuildOperationListener {

    private val cacheMisses = ConcurrentLinkedQueue<String>()

    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent) = Unit

    override fun progress(operationIdentifier: OperationIdentifier, progressEvent: OperationProgressEvent) = Unit

    override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
        val details = buildOperation.details
        val result = finishEvent.result
        when {
            details is ExecuteTaskBuildOperationType.Details && result is ExecuteTaskBuildOperationType.Result ->
                recordIfCacheMiss(result.skipMessage, result.cachingDisabledReasonCategory) {
                    "Task ${details.taskPath} in build ${details.buildPath}: ${result.upToDateMessages.orEmpty()}"
                }

            details is ExecuteWorkBuildOperationType.Details && result is ExecuteWorkBuildOperationType.Result ->
                recordIfCacheMiss(result.skipMessage, result.cachingDisabledReasonCategory) {
                    "${details.workType} ${details.identity}: ${result.executionReasons}"
                }

            details is RunRootBuildWorkBuildOperationType.Details -> report()

            result is RunBuildBuildOperationType.Result -> listenerManager.removeListener(this)
        }
    }

    private fun recordIfCacheMiss(skipMessage: String?, cachingDisabledReasonCategory: String?, description: () -> String) {
        if (skipMessage == null && cachingDisabledReasonCategory == null) {
            cacheMisses.add(description())
        }
    }

    private fun report() {
        if (cacheMisses.isEmpty()) {
            println("Cache hit verification: every cacheable unit of work was loaded from the build cache or up-to-date")
            return
        }
        println("Cache hit verification: ${cacheMisses.size} cacheable units of work were executed instead of being loaded from the build cache:")
        cacheMisses.sorted().forEach { println("  - $it") }
        val description = "${cacheMisses.size} cacheable units of work were not loaded from the build cache in VERIFY_CACHE_HITS_WITH_BUILT_GRADLE"
        println("##teamcity[addBuildTag 'GradleceptionCacheMiss']")
        if (failOnCacheMiss) {
            println("##teamcity[buildProblem description='$description' identity='GradleceptionCacheMiss']")
        } else {
            println("##teamcity[message text='$description' status='WARNING']")
        }
    }
}
