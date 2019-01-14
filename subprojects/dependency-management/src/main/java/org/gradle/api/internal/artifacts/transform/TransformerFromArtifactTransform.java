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

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformDependencies;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.execution.TaskFingerprinter;
import org.gradle.api.internal.tasks.properties.DefaultInputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.internal.fingerprint.AbsolutePathInputNormalizer;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.Isolatable;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

public class TransformerFromArtifactTransform extends AbstractTransformer<ArtifactTransform> {
    private static final String PRIMARY_INPUT_PROPERTY_NAME = "primaryInput";
    private static final String DEPENDENCIES_PROPERTY_NAME = "dependencies";

    public TransformerFromArtifactTransform(Class<? extends ArtifactTransform> implementationClass, @Nullable Object config, Isolatable<Object[]> parameters, HashCode inputsHash, InstantiatorFactory instantiatorFactory, ImmutableAttributes fromAttributes) {
        super(implementationClass, config, parameters, inputsHash, instantiatorFactory, fromAttributes);
    }

    @Override
    public List<File> transform(File primaryInput, File outputDir, ArtifactTransformDependencies dependencies) {
        ArtifactTransform transformer = newTransformer(primaryInput, outputDir, dependencies);
        transformer.setOutputDirectory(outputDir);
        List<File> outputs = transformer.transform(primaryInput);
        return validateOutputs(primaryInput, outputDir, outputs);
    }

    @Override
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileFingerprints(TaskFingerprinter taskFingerprinter, File primaryInput, PropertyWalker propertyWalker, FileResolver fileResolver, Object owner, ArtifactTransformDependencies artifactTransformDependencies) {
        ImmutableSortedSet.Builder<InputFilePropertySpec> builder = ImmutableSortedSet.naturalOrder();
        InputFilePropertySpec primaryInputSpec = new DefaultInputFilePropertySpec(PRIMARY_INPUT_PROPERTY_NAME, AbsolutePathInputNormalizer.class, fileResolver.resolveFiles(primaryInput), true);
        builder.add(primaryInputSpec);
        if (requiresDependencies()) {
            InputFilePropertySpec dependencySpec = new DefaultInputFilePropertySpec(DEPENDENCIES_PROPERTY_NAME, AbsolutePathInputNormalizer.class, fileResolver.resolveFiles(artifactTransformDependencies.getFiles()), false);
            builder.add(dependencySpec);
        }
        return taskFingerprinter.fingerprintTaskFiles(owner.toString(), builder.build());
    }

}
