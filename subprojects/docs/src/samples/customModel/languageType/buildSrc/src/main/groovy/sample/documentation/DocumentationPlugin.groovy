
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

import org.gradle.model.Mutate
import org.gradle.model.RuleSource
import org.gradle.platform.base.BinaryTasks
import org.gradle.platform.base.BinaryType
import org.gradle.platform.base.ComponentBinaries
import org.gradle.platform.base.ComponentType
import org.gradle.model.collection.CollectionBuilder
import org.gradle.platform.base.BinaryTypeBuilder
import org.gradle.api.Task
import org.gradle.api.tasks.bundling.Zip
import org.gradle.platform.base.*
import org.gradle.api.Action

import org.gradle.model.Path

class DocumentationPlugin extends RuleSource {
    @ComponentType
    void register(ComponentTypeBuilder<DocumentationComponent> builder) {
        builder.defaultImplementation(DefaultDocumentationComponent)
    }

    @Mutate
    void createSampleComponentComponents(CollectionBuilder<DocumentationComponent> componentSpecs) {
        componentSpecs.create("docs")
    }

    @BinaryType
    void register(BinaryTypeBuilder<DocumentationBinary> builder) {
        builder.defaultImplementation(DefaultDocumentationBinary)
    }

    @ComponentBinaries
    void createBinariesForBinaryComponent(CollectionBuilder<DocumentationBinary> binaries, DocumentationComponent component) {
        binaries.create("${component.name}Binary")
    }

    @BinaryTasks
    void createZip(CollectionBuilder<Task> tasks, final DocumentationBinary binary, @Path("buildDir") final File buildDir) {
        tasks.create("zip${binary.name}", Zip, new Action<Zip>() {
            @Override
            public void execute(Zip zipBinary) {
                binary.content.each { target, content ->
                    zipBinary.into(target) {
                        from(content)
                    }
                }
                zipBinary.setDestinationDir(new File(buildDir, binary.name))
                zipBinary.setArchiveName(binary.name + ".zip")
            }
        });
    }
}