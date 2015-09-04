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

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Zip
import org.gradle.model.ModelMap
import org.gradle.model.Mutate
import org.gradle.model.Path
import org.gradle.model.RuleSource
import org.gradle.platform.base.*

class DocumentationPlugin extends RuleSource {
    @ComponentType
    void register(ComponentTypeBuilder<DocumentationComponent> builder) {
        builder.defaultImplementation(DefaultDocumentationComponent)
    }

    @BinaryType
    void register(BinaryTypeBuilder<DocumentationBinary> builder) {
        builder.defaultImplementation(DefaultDocumentationBinary)
    }

    @ComponentBinaries
    void createBinariesForBinaryComponent(ModelMap<DocumentationBinary> binaries, DocumentationComponent component) {
        binaries.create("${component.name}Binary")
    }

    @BinaryTasks
    void createZip(ModelMap<Task> tasks, final DocumentationBinary binary, @Path("buildDir") final File buildDir) {
        tasks.create("zip${binary.name.capitalize()}", Zip, new Action<Zip>() {
            @Override
            public void execute(Zip zipBinary) {
                binary.source.withType(DocumentationSourceSet) { source ->
                    zipBinary.into(source.name) {
                        from(source.outputDir)
                    }
                    zipBinary.dependsOn source.taskName
                }
                zipBinary.setDestinationDir(new File(buildDir, binary.name))
                zipBinary.setArchiveName(binary.name + ".zip")
            }
        });
    }
}
