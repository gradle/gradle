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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.tasks.properties.DefaultParameterValidationContext;
import org.gradle.api.internal.tasks.properties.InputParameterUtils;
import org.gradle.api.internal.tasks.properties.OutputFilePropertyType;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.gradle.api.internal.tasks.properties.DefaultParameterValidationContext.propertyValidationMessage;

public class DefaultTransformationRegistration implements VariantTransformRegistry.Registration {

    private final ImmutableAttributes from;
    private final ImmutableAttributes to;
    private final TransformationStep transformationStep;

    public static VariantTransformRegistry.Registration create(ImmutableAttributes from, ImmutableAttributes to, Class<? extends ArtifactTransform> implementation, @Nullable Object parameterObject, Object[] params, IsolatableFactory isolatableFactory, ClassLoaderHierarchyHasher classLoaderHierarchyHasher, InstantiatorFactory instantiatorFactory, TransformerInvoker transformerInvoker, ValueSnapshotter valueSnapshotter, PropertyWalker propertyWalker) {
        Hasher hasher = Hashing.newHasher();
        hasher.putString(implementation.getName());
        hasher.putHash(classLoaderHierarchyHasher.getClassLoaderHash(implementation.getClassLoader()));

        // TODO - should snapshot later
        Isolatable<Object[]> paramsSnapshot;
        try {
            paramsSnapshot = isolatableFactory.isolate(params);
        } catch (Exception e) {
            throw new VariantTransformConfigurationException(String.format("Could not snapshot parameters values for transform %s: %s", ModelType.of(implementation).getDisplayName(), Arrays.asList(params)), e);
        }
        Isolatable<?> isolatedParameterObject = isolatableFactory.isolate(parameterObject);

        paramsSnapshot.appendToHasher(hasher);
        if (parameterObject != null) {
            fingerprintParameters(valueSnapshotter, propertyWalker, hasher, isolatedParameterObject);
        }

        Transformer transformer = new DefaultTransformer(implementation, isolatedParameterObject, paramsSnapshot, hasher.hash(), instantiatorFactory, from);
        return new DefaultTransformationRegistration(from, to, new TransformationStep(transformer, transformerInvoker));
    }

    private static void fingerprintParameters(
        ValueSnapshotter valueSnapshotter,
        PropertyWalker propertyWalker,
        Hasher hasher,
        Isolatable<?> isolatableParameterObject
    ) {
        ImmutableSortedMap.Builder<String, ValueSnapshot> inputPropertyFingerprintsBuilder = ImmutableSortedMap.naturalOrder();
        Object parameterObject = isolatableParameterObject.isolate();
        List<String> validationMessages = new ArrayList<>();
        DefaultParameterValidationContext validationContext = new DefaultParameterValidationContext(validationMessages);
        propertyWalker.visitProperties(parameterObject, validationContext, new PropertyVisitor.Adapter() {
            @Override
            public void visitInputProperty(String propertyName, PropertyValue value, boolean optional) {
                try {
                    Object preparedValue = InputParameterUtils.prepareInputParameterValue(value);

                    if (preparedValue == null && !optional) {
                        validationContext.recordValidationMessage(propertyValidationMessage(propertyName, "does not have a value specified"));
                    }

                    inputPropertyFingerprintsBuilder.put(propertyName, valueSnapshotter.snapshot(preparedValue));
                } catch (Throwable e) {
                    throw new InvalidUserDataException(String.format(
                        "Error while evaluating property '%s' of %s",
                        propertyName,
                        ModelType.of(parameterObject.getClass()).getDisplayName()
                    ), e);
                }
            }

            @Override
            public void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType) {
                validationContext.recordValidationMessage(propertyValidationMessage(propertyName, "is annotated with an output annotation"));
            }
        });

        if (!validationMessages.isEmpty()) {
            throw new DefaultMultiCauseException(
                String.format(validationMessages.size() == 1 ? "A problem was found with the configuration of %s." : "Some problems were found with the configuration of %s.", ModelType.of(parameterObject.getClass()).getDisplayName()),
                validationMessages.stream().map(InvalidUserDataException::new).collect(Collectors.toList())
            );
        }

        for (Map.Entry<String, ValueSnapshot> entry : inputPropertyFingerprintsBuilder.build().entrySet()) {
            hasher.putString(entry.getKey());
            entry.getValue().appendToHasher(hasher);
        }
    }

    public DefaultTransformationRegistration(ImmutableAttributes from, ImmutableAttributes to, TransformationStep transformationStep) {
        this.from = from;
        this.to = to;
        this.transformationStep = transformationStep;
    }

    @Override
    public AttributeContainerInternal getFrom() {
        return from;
    }

    @Override
    public AttributeContainerInternal getTo() {
        return to;
    }

    @Override
    public TransformationStep getTransformationStep() {
        return transformationStep;
    }
}
