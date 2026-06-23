import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.flow.BuildWorkResult
import javax.inject.Inject

// tag::flow-action[]
abstract class PrintBuildResultPlugin : Plugin<Project> {

    @get:Inject
    abstract val flowScope: FlowScope

    @get:Inject
    abstract val flowProviders: FlowProviders

    override fun apply(target: Project) {
        flowScope.always(BuildResultPrinter::class.java) {
            parameters.buildResult.set(flowProviders.buildWorkResult)
        }
    }
}

abstract class BuildResultPrinter : FlowAction<BuildResultPrinter.Parameters> {

    interface Parameters : FlowParameters {
        @get:Input
        val buildResult: Property<BuildWorkResult>
    }

    override fun execute(parameters: Parameters) {
        val result = parameters.buildResult.get()
        if (result.failure.isPresent) {
            println("Build failed: ${result.failure.get().message}")
        } else {
            println("Build succeeded")
        }
    }
}
// end::flow-action[]

apply<PrintBuildResultPlugin>()
