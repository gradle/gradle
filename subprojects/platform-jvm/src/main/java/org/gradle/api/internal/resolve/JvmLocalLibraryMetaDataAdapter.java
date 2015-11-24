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

import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.jvm.internal.JarBinarySpecInternal;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData;
import org.gradle.platform.base.BinarySpec;

import java.util.Collections;

import static org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier.CONFIGURATION_API;
import static org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData.newDefaultLibraryLocalComponentMetadata;

public class JvmLocalLibraryMetaDataAdapter implements LocalLibraryMetaDataAdapter {

    @Override
    public DefaultLibraryLocalComponentMetaData createLocalComponentMetaData(BinarySpec selectedBinary, String projectPath) {
        JarBinarySpecInternal jarBinarySpec = (JarBinarySpecInternal) selectedBinary;
        DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
        buildDependencies.add(jarBinarySpec.getApiJar());
        DefaultLibraryLocalComponentMetaData metadata = newDefaultLibraryLocalComponentMetadata(jarBinarySpec.getId(), buildDependencies, jarBinarySpec.getApiDependencies(), projectPath);
        LibraryPublishArtifact jarBinary = new LibraryPublishArtifact("jar", jarBinarySpec.getApiJar().getFile());
        metadata.addArtifacts(CONFIGURATION_API, Collections.singleton(jarBinary));
        return metadata;
    }

}
