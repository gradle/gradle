/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformAction;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.internal.artifacts.ArtifactTransformRegistration;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.tasks.properties.DefaultParameterValidationContext;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.internal.tasks.properties.TypeMetadata;
import org.gradle.api.internal.tasks.properties.TypeMetadataStore;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.IgnoredPathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.NameOnlyFingerprintingStrategy;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.PropertyMetadata;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultTransformationRegistrationFactory implements TransformationRegistrationFactory {

    private final IsolatableFactory isolatableFactory;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final TransformerInvoker transformerInvoker;
    private final ValueSnapshotter valueSnapshotter;
    private final PropertyWalker parametersPropertyWalker;
    private final DomainObjectProjectStateHandler domainObjectProjectStateHandler;
    private final TypeMetadataStore actionMetadataStore;
    private final InstantiationScheme actionInstantiationScheme;
    private final InstantiationScheme legacyActionInstantiationScheme;

    public DefaultTransformationRegistrationFactory(
        IsolatableFactory isolatableFactory,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        TransformerInvoker transformerInvoker,
        ValueSnapshotter valueSnapshotter,
        DomainObjectProjectStateHandler domainObjectProjectStateHandler,
        ArtifactTransformParameterScheme parameterScheme,
        ArtifactTransformActionScheme actionScheme
    ) {
        this.isolatableFactory = isolatableFactory;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.transformerInvoker = transformerInvoker;
        this.valueSnapshotter = valueSnapshotter;
        this.actionInstantiationScheme = actionScheme.getInstantiationScheme();
        this.actionMetadataStore = actionScheme.getInspectionScheme().getMetadataStore();
        this.legacyActionInstantiationScheme = actionScheme.getLegacyInstantiationScheme();
        this.parametersPropertyWalker = parameterScheme.getInspectionScheme().getPropertyWalker();
        this.domainObjectProjectStateHandler = domainObjectProjectStateHandler;
    }

    @Override
    public ArtifactTransformRegistration create(ImmutableAttributes from, ImmutableAttributes to, Class<? extends ArtifactTransformAction> implementation, @Nullable Object parameterObject) {
        List<String> validationMessages = new ArrayList<>();
        TypeMetadata actionMetadata = actionMetadataStore.getTypeMetadata(implementation);
        actionMetadata.collectValidationFailures(null, new DefaultParameterValidationContext(validationMessages));
        if (!validationMessages.isEmpty()) {
            throw new DefaultMultiCauseException(
                String.format(validationMessages.size() == 1 ? "A problem was found with the configuration of %s." : "Some problems were found with the configuration of %s.", ModelType.of(implementation).getDisplayName()),
                validationMessages.stream().map(InvalidUserDataException::new).collect(Collectors.toList()));
        }
        PathSensitivity pathSensitivity = PathSensitivity.ABSOLUTE;
        for (PropertyMetadata propertyMetadata : actionMetadata.getPropertiesMetadata()) {
            if (propertyMetadata.getPropertyType().equals(InputArtifact.class)) {
                // Should ask the annotation handler to figure this out instead
                PathSensitive annotation = propertyMetadata.getAnnotation(PathSensitive.class);
                if (annotation != null) {
                    pathSensitivity = annotation.value();
                }
                break;
            }
        }
        // Should reuse the registry to make this decision
        // Should retain this on the metadata rather than calculate on each invocation
        FingerprintingStrategy fingerprintingStrategy;
        switch (pathSensitivity) {
            case NONE:
                fingerprintingStrategy = IgnoredPathFingerprintingStrategy.INSTANCE;
                break;
            case NAME_ONLY:
                fingerprintingStrategy = NameOnlyFingerprintingStrategy.INSTANCE;
                break;
            case RELATIVE:
            case ABSOLUTE:
                fingerprintingStrategy = AbsolutePathFingerprintingStrategy.INCLUDE_MISSING;
                break;
            default:
                throw new IllegalArgumentException();
        }

        Transformer transformer = new DefaultTransformer(
            implementation,
            parameterObject,
            from,
            fingerprintingStrategy,
            classLoaderHierarchyHasher,
            isolatableFactory,
            valueSnapshotter,
            parametersPropertyWalker,
            domainObjectProjectStateHandler,
            actionInstantiationScheme);

        return new DefaultArtifactTransformRegistration(from, to, new TransformationStep(transformer, transformerInvoker));
    }

    @Override
    public ArtifactTransformRegistration create(ImmutableAttributes from, ImmutableAttributes to, Class<? extends ArtifactTransform> implementation, Object[] params) {
        Transformer transformer = new LegacyTransformer(implementation, params, legacyActionInstantiationScheme, from, classLoaderHierarchyHasher, isolatableFactory);
        return new DefaultArtifactTransformRegistration(from, to, new TransformationStep(transformer, transformerInvoker));
    }

    private static class DefaultArtifactTransformRegistration implements ArtifactTransformRegistration {
        private final ImmutableAttributes from;
        private final ImmutableAttributes to;
        private final TransformationStep transformationStep;

        public DefaultArtifactTransformRegistration(ImmutableAttributes from, ImmutableAttributes to, TransformationStep transformationStep) {
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
}
