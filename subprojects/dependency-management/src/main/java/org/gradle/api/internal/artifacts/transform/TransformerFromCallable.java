/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.artifacts.transform.ArtifactTransformDependencies;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.execution.TaskFingerprinter;
import org.gradle.api.internal.tasks.properties.DefaultUnitOfWorkProperties;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.internal.tasks.properties.UnitOfWorkProperties;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.Isolatable;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

public class TransformerFromCallable extends AbstractTransformer<Callable<List<File>>> {

    public TransformerFromCallable(Class<? extends Callable<List<File>>> implementationClass, Isolatable<Object> configSnapshot, Isolatable<Object[]> paramsSnapshot, HashCode secondaryInputsHash, InstantiatorFactory instantiatorFactory, ImmutableAttributes from) {
        super(implementationClass, configSnapshot, paramsSnapshot, secondaryInputsHash, instantiatorFactory, from);
    }

    @Override
    public List<File> transform(File primaryInput, File outputDir, ArtifactTransformDependencies dependencies) {
        Callable<List<File>> transformer = newTransformer(primaryInput, outputDir, dependencies);
        try {
            List<File> result = transformer.call();
            validateOutputs(primaryInput, outputDir, result);
            return result;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileFingerprints(TaskFingerprinter taskFingerprinter, File primaryInput, PropertyWalker propertyWalker, FileResolver pathToFileResolver, Object owner, ArtifactTransformDependencies artifactTransformDependencies) {
        Callable<List<File>> transformerInstance = newTransformer(primaryInput, null, artifactTransformDependencies);

        UnitOfWorkProperties properties = DefaultUnitOfWorkProperties.resolve(propertyWalker, pathToFileResolver, owner.toString(), transformerInstance);
        return taskFingerprinter.fingerprintTaskFiles(owner.toString(), properties.getInputFileProperties());
    }
}
