package sample.markdown

import org.gradle.api.Task
import org.gradle.model.ModelMap
import org.gradle.model.RuleSource
import org.gradle.platform.base.BinaryTasks
import org.gradle.platform.base.ComponentType
import org.gradle.platform.base.TypeBuilder
import sample.documentation.DocumentationBinary

// tag::markdown-lang-registration[]
// tag::markdown-tasks-generation[]
class MarkdownPlugin extends RuleSource {
// end::markdown-tasks-generation[]
    @ComponentType
    void registerMarkdownLanguage(TypeBuilder<MarkdownSourceSet> builder) {}
// end::markdown-lang-registration[]

// tag::markdown-tasks-generation[]
    @BinaryTasks
    void processMarkdownDocumentation(ModelMap<Task> tasks, final DocumentationBinary binary) {
        binary.inputs.withType(MarkdownSourceSet) { markdownSourceSet ->
            def taskName = binary.tasks.taskName("compile", markdownSourceSet.name)
            def outputDir = new File(binary.outputDir, markdownSourceSet.name)
            tasks.create(taskName, MarkdownHtmlCompile) { compileTask ->
                compileTask.source = markdownSourceSet.source
                compileTask.destinationDir = outputDir
                compileTask.smartQuotes = markdownSourceSet.smartQuotes
                compileTask.generateIndex = markdownSourceSet.generateIndex
            }
        }
    }
// tag::markdown-lang-registration[]
}
// end::markdown-lang-registration[]
// end::markdown-tasks-generation[]
