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

import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.jvm.internal.JarBinarySpecInternal;
import org.gradle.jvm.internal.JarFile;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData;
import org.gradle.platform.base.BinarySpec;

import java.util.Arrays;
import java.util.Collections;

import static org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier.CONFIGURATION_API;
import static org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier.CONFIGURATION_RUNTIME;
import static org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetaData.newResolvedLibraryMetadata;

public class JvmLocalLibraryMetaDataAdapter implements LocalLibraryMetaDataAdapter {

    @Override
    public DefaultLibraryLocalComponentMetaData createLocalComponentMetaData(BinarySpec selectedBinary, String usage, String projectPath) {
        JarBinarySpecInternal jarBinarySpec = (JarBinarySpecInternal) selectedBinary;
        DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
        LibraryPublishArtifact jarBinary;
        if (CONFIGURATION_API.equals(usage)) {
            JarFile apiJar = jarBinarySpec.getApiJar();
            buildDependencies.add(apiJar);
            jarBinary = new LibraryPublishArtifact("jar", apiJar.getFile());
        } else if (CONFIGURATION_RUNTIME.equals(usage)) {
            JarFile runtimeJar = jarBinarySpec.getRuntimeJar();
            buildDependencies.add(runtimeJar);
            jarBinary = new LibraryPublishArtifact("jar", runtimeJar.getFile());
        } else {
            throw new GradleException("Unrecognized usage found: '" + usage + "'. Should be one of " + Arrays.asList(CONFIGURATION_API, CONFIGURATION_RUNTIME));
        }
        DefaultLibraryLocalComponentMetaData metadata = newResolvedLibraryMetadata(jarBinarySpec.getId(), usage, buildDependencies, jarBinarySpec.getApiDependencies(), projectPath);
        metadata.addArtifacts(usage, Collections.singleton(jarBinary));
        return metadata;
    }

}
