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

import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.InputArtifactDependencies;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.TransformRegistration;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.tasks.properties.FileParameterUtils;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.properties.InputFilePropertyType;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.properties.annotations.TypeMetadataStore;
import org.gradle.internal.properties.bean.PropertyWalker;
import org.gradle.internal.reflect.DefaultTypeValidationContext;
import org.gradle.internal.service.ServiceLookup;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;

public class DefaultTransformRegistrationFactory implements TransformRegistrationFactory {

    private final BuildOperationExecutor buildOperationExecutor;
    private final IsolatableFactory isolatableFactory;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final TransformInvocationFactory transformInvocationFactory;
    private final PropertyWalker parametersPropertyWalker;
    private final ServiceLookup internalServices;
    private final TypeMetadataStore actionMetadataStore;
    private final FileCollectionFactory fileCollectionFactory;
    private final FileLookup fileLookup;
    private final InputFingerprinter inputFingerprinter;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final DomainObjectContext owner;
    private final InstantiationScheme actionInstantiationScheme;

    public DefaultTransformRegistrationFactory(
        BuildOperationExecutor buildOperationExecutor,
        IsolatableFactory isolatableFactory,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        TransformInvocationFactory transformInvocationFactory,
        FileCollectionFactory fileCollectionFactory,
        FileLookup fileLookup,
        InputFingerprinter inputFingerprinter,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        DomainObjectContext owner,
        TransformParameterScheme parameterScheme,
        TransformActionScheme actionScheme,
        ServiceLookup internalServices
    ) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.isolatableFactory = isolatableFactory;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.transformInvocationFactory = transformInvocationFactory;
        this.fileCollectionFactory = fileCollectionFactory;
        this.fileLookup = fileLookup;
        this.inputFingerprinter = inputFingerprinter;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.owner = owner;
        this.actionInstantiationScheme = actionScheme.getInstantiationScheme();
        this.actionMetadataStore = actionScheme.getInspectionScheme().getMetadataStore();
        this.parametersPropertyWalker = parameterScheme.getInspectionScheme().getPropertyWalker();
        this.internalServices = internalServices;
    }

    @Override
    public TransformRegistration create(ImmutableAttributes from, ImmutableAttributes to, Class<? extends TransformAction<?>> implementation, @Nullable TransformParameters parameterObject) {
        TypeMetadata actionMetadata = actionMetadataStore.getTypeMetadata(implementation);
        boolean cacheable = implementation.isAnnotationPresent(CacheableTransform.class);
        DefaultTypeValidationContext validationContext = DefaultTypeValidationContext.withoutRootType(cacheable);
        actionMetadata.visitValidationFailures(null, validationContext);

        // Should retain this on the metadata rather than calculate on each invocation
        FileNormalizer inputArtifactNormalizer = null;
        FileNormalizer dependenciesNormalizer = null;
        DirectorySensitivity artifactDirectorySensitivity = DirectorySensitivity.DEFAULT;
        DirectorySensitivity dependenciesDirectorySensitivity = DirectorySensitivity.DEFAULT;
        LineEndingSensitivity artifactLineEndingSensitivity = LineEndingSensitivity.DEFAULT;
        LineEndingSensitivity dependenciesLineEndingSensitivity = LineEndingSensitivity.DEFAULT;
        for (PropertyMetadata propertyMetadata : actionMetadata.getPropertiesMetadata()) {
            // Should ask the annotation handler to figure this out instead
            Class<? extends Annotation> propertyType = propertyMetadata.getPropertyType();
            NormalizerCollectingVisitor visitor = new NormalizerCollectingVisitor();
            if (propertyType.equals(InputArtifact.class)) {
                actionMetadata.getAnnotationHandlerFor(propertyMetadata).visitPropertyValue(propertyMetadata.getPropertyName(), PropertyValue.ABSENT, propertyMetadata, visitor);
                inputArtifactNormalizer = visitor.normalizer;
                artifactDirectorySensitivity = visitor.directorySensitivity;
                artifactLineEndingSensitivity = visitor.lineEndingSensitivity;
                DefaultTransform.validateInputFileNormalizer(propertyMetadata.getPropertyName(), inputArtifactNormalizer, cacheable, validationContext);
            } else if (propertyType.equals(InputArtifactDependencies.class)) {
                actionMetadata.getAnnotationHandlerFor(propertyMetadata).visitPropertyValue(propertyMetadata.getPropertyName(), PropertyValue.ABSENT, propertyMetadata, visitor);
                dependenciesNormalizer = visitor.normalizer;
                dependenciesDirectorySensitivity = visitor.directorySensitivity;
                dependenciesLineEndingSensitivity = visitor.lineEndingSensitivity;
                DefaultTransform.validateInputFileNormalizer(propertyMetadata.getPropertyName(), dependenciesNormalizer, cacheable, validationContext);
            }
        }
        DefaultTypeValidationContext.throwOnProblemsOf(implementation, validationContext.getProblems());
        Transform transform = new DefaultTransform(
            implementation,
            parameterObject,
            from,
            to,
            FileParameterUtils.normalizerOrDefault(inputArtifactNormalizer),
            FileParameterUtils.normalizerOrDefault(dependenciesNormalizer),
            cacheable,
            artifactDirectorySensitivity,
            dependenciesDirectorySensitivity,
            artifactLineEndingSensitivity,
            dependenciesLineEndingSensitivity,
            buildOperationExecutor,
            classLoaderHierarchyHasher,
            isolatableFactory,
            fileCollectionFactory,
            fileLookup,
            parametersPropertyWalker,
            actionInstantiationScheme,
            owner,
            calculatedValueContainerFactory,
            internalServices
        );

        return new DefaultTransformRegistration(from, to, new TransformStep(transform, transformInvocationFactory, owner, inputFingerprinter));
    }

    private static class DefaultTransformRegistration implements TransformRegistration {
        private final ImmutableAttributes from;
        private final ImmutableAttributes to;
        private final TransformStep transformStep;

        public DefaultTransformRegistration(ImmutableAttributes from, ImmutableAttributes to, TransformStep transformStep) {
            this.from = from;
            this.to = to;
            this.transformStep = transformStep;
        }

        @Override
        public ImmutableAttributes getFrom() {
            return from;
        }

        @Override
        public ImmutableAttributes getTo() {
            return to;
        }

        @Override
        public TransformStep getTransformStep() {
            return transformStep;
        }

        @Override
        public String toString() {
            return transformStep + " transform from " + from + " to " + to;
        }
    }

    private static class NormalizerCollectingVisitor implements PropertyVisitor {
        private FileNormalizer normalizer;
        private DirectorySensitivity directorySensitivity = DirectorySensitivity.DEFAULT;
        private LineEndingSensitivity lineEndingSensitivity = LineEndingSensitivity.DEFAULT;

        @Override
        public void visitInputFileProperty(
            String propertyName,
            boolean optional,
            InputBehavior behavior,
            DirectorySensitivity directorySensitivity,
            LineEndingSensitivity lineEndingSensitivity,
            @Nullable FileNormalizer fileNormalizer,
            PropertyValue value,
            InputFilePropertyType filePropertyType
        ) {
            this.normalizer = fileNormalizer;
            this.directorySensitivity = directorySensitivity;
            this.lineEndingSensitivity = lineEndingSensitivity;
        }
    }
}
