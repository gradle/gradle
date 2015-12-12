/*
 * Copyright 2015 the original author or authors.
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
package sample.documentation
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.model.ModelMap
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.platform.base.*

class DocumentationPlugin extends RuleSource {
    @ComponentType
    void register(ComponentTypeBuilder<DocumentationComponent> builder) {
    }

    @BinaryType
    void register(BinaryTypeBuilder<DocumentationBinary> builder) {
    }

    @LanguageType
    void registerText(LanguageTypeBuilder<TextSourceSet> builder) {
        builder.languageName = "text"
    }

    @ComponentBinaries
    void configureExplodedDocs(ModelMap<DocumentationBinary> binaries, DocumentationComponent component, @Path("buildDir") File buildDir) {
        binaries.create("exploded") { binary ->
            outputDir = new File(buildDir, "${component.name}/${binary.name}")
        }
    }

    @BinaryTasks
    void copyTextDocumentation(ModelMap<Task> tasks, final DocumentationBinary binary) {
        binary.inputs.withType(TextSourceSet.class) { textSourceSet ->
            def taskName = binary.tasks.taskName("compile", name)
            def outputDir = new File(binary.outputDir, name)
            tasks.create(taskName, Copy) {
                from textSourceSet.source
                destinationDir = outputDir
            }
        }
    }
}
