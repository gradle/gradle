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
package org.gradle.api.internal.resolve;

import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.jvm.JarBinarySpec;
import org.gradle.language.base.internal.DependentSourceSetInternal;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData;
import org.gradle.language.base.internal.model.VariantDimensionSelectorFactory;
import org.gradle.language.base.internal.model.VariantsMetaData;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.DependencySpec;

import java.util.Collections;
import java.util.List;

public class JvmLocalLibraryDependencyResolver extends AbstractLocalLibraryDependencyResolver<JarBinarySpec> {

    public JvmLocalLibraryDependencyResolver(ProjectModelResolver projectModelResolver, VariantsMetaData variantsMetaData, List<VariantDimensionSelectorFactory> selectorFactories, ModelSchemaStore schemaStore) {
        super(JarBinarySpec.class, projectModelResolver, selectorFactories, variantsMetaData, new JvmLibraryResolutionErrorMessageBuilder(variantsMetaData, schemaStore), schemaStore);
    }

    protected DefaultLibraryLocalComponentMetaData createLocalComponentMetaData(BinarySpec selectedBinary, TaskDependency buildDependencies, String projectPath) {
        JarBinarySpec jarBinarySpec = (JarBinarySpec) selectedBinary;
        DefaultLibraryLocalComponentMetaData metaData = DefaultLibraryLocalComponentMetaData.newMetaData(jarBinarySpec.getId(), buildDependencies);
        LibraryPublishArtifact jarBinary = new LibraryPublishArtifact("jar", jarBinarySpec.getApiJarFile());
        metaData.addArtifacts(DefaultLibraryBinaryIdentifier.CONFIGURATION_API, Collections.singleton(jarBinary));
        addExportedDependencies(selectedBinary, metaData, projectPath);
        return metaData;
    }

    private void addExportedDependencies(BinarySpec selectedBinary, DefaultLibraryLocalComponentMetaData metaData, String selectorProjectPath) {
        for (DependentSourceSetInternal sourceSet : selectedBinary.getInputs().withType(DependentSourceSetInternal.class)) {
            for (DependencySpec dependency : sourceSet.getDependencies().getDependencies()) {
                if (dependency.isExported()) {
                    metaData.addDependency(dependency, selectorProjectPath);
                }
            }
        }
    }

}
