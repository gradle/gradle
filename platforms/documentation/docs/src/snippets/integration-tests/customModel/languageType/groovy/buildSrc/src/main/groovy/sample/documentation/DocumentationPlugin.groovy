package sample.documentation

import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.model.ModelMap
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.platform.base.*

// tag::component-registration[]
// tag::binary-registration[]
// tag::binaries-generation[]
// tag::text-tasks-generation[]
class DocumentationPlugin extends RuleSource {
// end::binary-registration[]
// end::binaries-generation[]
// end::text-tasks-generation[]
    @ComponentType
    void registerComponent(TypeBuilder<DocumentationComponent> builder) {}
// end::component-registration[]

// tag::binary-registration[]
    @ComponentType
    void registerBinary(TypeBuilder<DocumentationBinary> builder) {}
// end::binary-registration[]

// tag::text-lang-registration[]
    @ComponentType
    void registerText(TypeBuilder<TextSourceSet> builder) {}
// end::text-lang-registration[]

// tag::binaries-generation[]
    @ComponentBinaries
    void generateDocBinaries(ModelMap<DocumentationBinary> binaries, VariantComponentSpec component, @Path("buildDir") File buildDir) {
        binaries.create("exploded") { binary ->
            outputDir = new File(buildDir, "${component.name}/${binary.name}")
        }
    }
// end::binaries-generation[]

// tag::text-tasks-generation[]
    @BinaryTasks
    void generateTextTasks(ModelMap<Task> tasks, final DocumentationBinary binary) {
        binary.inputs.withType(TextSourceSet) { textSourceSet ->
            def taskName = binary.tasks.taskName("compile", textSourceSet.name)
            def outputDir = new File(binary.outputDir, textSourceSet.name)
            tasks.create(taskName, Copy) {
                from textSourceSet.source
                destinationDir = outputDir
            }
        }
    }
// tag::component-registration[]
// tag::binary-registration[]
// tag::binaries-generation[]
}
// end::component-registration[]
// end::binary-registration[]
// end::binaries-generation[]
// end::text-tasks-generation[]
