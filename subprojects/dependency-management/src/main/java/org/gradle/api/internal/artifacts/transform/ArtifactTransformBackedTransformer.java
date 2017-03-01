/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.hash.HashCode;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.GenericFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy;
import org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.model.internal.type.ModelType;

import java.io.File;
import java.util.List;

class ArtifactTransformBackedTransformer implements Transformer<List<File>, File> {
    private final Class<? extends ArtifactTransform> type;
    private final Object[] parameters;
    private final ArtifactCacheMetaData artifactCacheMetaData;
    private final HashCode inputsHash;
    private final GenericFileCollectionSnapshotter fileCollectionSnapshotter;

    ArtifactTransformBackedTransformer(Class<? extends ArtifactTransform> type, Object[] parameters, ArtifactCacheMetaData artifactCacheMetaData, HashCode inputsHash, GenericFileCollectionSnapshotter fileCollectionSnapshotter) {
        this.type = type;
        this.parameters = parameters;
        this.artifactCacheMetaData = artifactCacheMetaData;
        this.inputsHash = inputsHash;
        this.fileCollectionSnapshotter = fileCollectionSnapshotter;
    }

    @Override
    public List<File> transform(File file) {
        // Snapshot the input files
        file = file.getAbsoluteFile();
        FileCollectionSnapshot snapshot = fileCollectionSnapshotter.snapshot(new SimpleFileCollection(file), TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE);

        ArtifactTransform artifactTransform = create();

        DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
        hasher.putBytes(inputsHash.asBytes());
        snapshot.appendToHasher(hasher);

        File outputDir = new File(artifactCacheMetaData.getTransformsStoreDirectory(), file.getName() + "/" + hasher.hash());
        outputDir.mkdirs();

        artifactTransform.setOutputDirectory(outputDir);
        List<File> outputs = artifactTransform.transform(file);
        if (outputs == null) {
            throw new InvalidUserDataException("Illegal null output from ArtifactTransform");
        }
        for (File output : outputs) {
            if (!output.exists()) {
                throw new InvalidUserDataException("ArtifactTransform output '" + output.getPath() + "' does not exist");
            }
        }
        return outputs;
    }

    private ArtifactTransform create() {
        try {
            return DirectInstantiator.INSTANCE.newInstance(type, parameters);
        } catch (ObjectInstantiationException e) {
            throw new VariantTransformConfigurationException("Could not create instance of " + ModelType.of(type).getDisplayName() + ".", e.getCause());
        } catch (RuntimeException e) {
            throw new VariantTransformConfigurationException("Could not create instance of " + ModelType.of(type).getDisplayName() + ".", e);
        }
    }

}
