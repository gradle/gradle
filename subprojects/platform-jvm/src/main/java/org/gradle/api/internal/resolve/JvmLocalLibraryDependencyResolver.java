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
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData;
import org.gradle.language.base.internal.model.VariantDimensionSelectorFactory;
import org.gradle.language.base.internal.model.VariantsMetaData;
import org.gradle.platform.base.BinarySpec;

import java.util.Collections;
import java.util.List;

public class JvmLocalLibraryDependencyResolver extends AbstractLocalLibraryDependencyResolver<JarBinarySpec> {

    public JvmLocalLibraryDependencyResolver(ProjectModelResolver projectModelResolver, VariantsMetaData variantsMetaData, List<VariantDimensionSelectorFactory> selectorFactories) {
        super(JarBinarySpec.class, projectModelResolver, selectorFactories, variantsMetaData, new JvmLibraryResolutionErrorMessageBuilder(variantsMetaData));
    }

    protected DefaultLibraryLocalComponentMetaData createLocalComponentMetaData(BinarySpec selectedBinary, TaskDependency buildDependencies) {
        JarBinarySpec jarBinarySpec = (JarBinarySpec) selectedBinary;
        DefaultLibraryLocalComponentMetaData metaData = DefaultLibraryLocalComponentMetaData.newMetaData(jarBinarySpec.getId(), buildDependencies);
        LibraryPublishArtifact jarBinary = new LibraryPublishArtifact("jar", jarBinarySpec.getJarFile());
        metaData.addArtifacts(DefaultLibraryBinaryIdentifier.CONFIGURATION_NAME, Collections.singleton(jarBinary));
        return metaData;
    }

}
