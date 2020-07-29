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
