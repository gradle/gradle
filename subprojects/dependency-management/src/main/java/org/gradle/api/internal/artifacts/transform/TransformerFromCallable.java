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
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.artifacts.transform.ArtifactTransformDependencies;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.execution.TaskFingerprinter;
import org.gradle.api.internal.tasks.properties.DefaultInputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.InputFilePropertySpec;
import org.gradle.api.internal.tasks.properties.InputFilePropertyType;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.Isolatable;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

public class TransformerFromCallable extends AbstractTransformer<Callable<List<File>>> {

    public TransformerFromCallable(Class<? extends Callable<List<File>>> implementationClass, @Nullable Object config, Isolatable<Object[]> paramsSnapshot, HashCode secondaryInputsHash, InstantiatorFactory instantiatorFactory, ImmutableAttributes from) {
        super(implementationClass, config, paramsSnapshot, secondaryInputsHash, instantiatorFactory, from);
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
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileFingerprints(TaskFingerprinter taskFingerprinter, File primaryInput, PropertyWalker propertyWalker, FileResolver fileResolver, Object owner, ArtifactTransformDependencies artifactTransformDependencies) {
        Callable<List<File>> transformerInstance = newTransformer(primaryInput, null, artifactTransformDependencies);

        ImmutableSortedSet.Builder<InputFilePropertySpec> builder = ImmutableSortedSet.naturalOrder();

        propertyWalker.visitProperties(new InputFilePropertySpecsVisitor(fileResolver, builder, "action."), transformerInstance);
        Object config = getConfig();
        if (config != null) {
            propertyWalker.visitProperties(new InputFilePropertySpecsVisitor(fileResolver, builder, "configuration."), config);
        }
        return taskFingerprinter.fingerprintTaskFiles(owner.toString(), builder.build());
    }

    private static class InputFilePropertySpecsVisitor extends PropertyVisitor.Adapter {

        private final FileResolver fileResolver;
        private final ImmutableSortedSet.Builder<InputFilePropertySpec> builder;
        @Nullable
        private final String prefix;

        public InputFilePropertySpecsVisitor(FileResolver fileResolver, ImmutableSortedSet.Builder<InputFilePropertySpec> builder, String prefix) {
            this.fileResolver = fileResolver;
            this.builder = builder;
            this.prefix = prefix;
        }

        @Override
        public void visitInputFileProperty(String propertyName, boolean optional, boolean skipWhenEmpty, Class<? extends FileNormalizer> fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
            FileCollection files = filePropertyType == InputFilePropertyType.DIRECTORY ? fileResolver.resolveFilesAsTree(value) : fileResolver.resolveFiles(value);
            builder.add(new DefaultInputFilePropertySpec(prefix + propertyName, fileNormalizer, files, skipWhenEmpty));
        }
    }
}
