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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.reflect.TypeToken;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.transform.ArtifactTransformAction;
import org.gradle.api.artifacts.transform.PrimaryInput;
import org.gradle.api.artifacts.transform.PrimaryInputDependencies;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.tasks.properties.DefaultParameterValidationContext;
import org.gradle.api.internal.tasks.properties.InputFilePropertyType;
import org.gradle.api.internal.tasks.properties.InputParameterUtils;
import org.gradle.api.internal.tasks.properties.OutputFilePropertyType;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.reflect.InjectionPointQualifier;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.instantiation.InstanceFactory;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.UnknownServiceException;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.gradle.api.internal.tasks.properties.DefaultParameterValidationContext.propertyValidationMessage;

public class DefaultTransformer extends AbstractTransformer<ArtifactTransformAction, Object> {

    private final Object parameterObject;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final IsolatableFactory isolatableFactory;
    private final ValueSnapshotter valueSnapshotter;
    private final PropertyWalker propertyWalker;
    private final boolean requiresDependencies;
    private final InstanceFactory<? extends ArtifactTransformAction> instanceFactory;

    public DefaultTransformer(Class<? extends ArtifactTransformAction> implementationClass, @Nullable Object parameterObject, InstantiatorFactory instantiatorFactory, ImmutableAttributes fromAttributes, ClassLoaderHierarchyHasher classLoaderHierarchyHasher, IsolatableFactory isolatableFactory, ValueSnapshotter valueSnapshotter, PropertyWalker propertyWalker, DomainObjectContextProjectStateHandler projectStateHandler) {
        super(implementationClass, fromAttributes, projectStateHandler);
        this.parameterObject = parameterObject;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.isolatableFactory = isolatableFactory;
        this.valueSnapshotter = valueSnapshotter;
        this.propertyWalker = propertyWalker;
        this.instanceFactory = instantiatorFactory.injectScheme(ImmutableSet.of(PrimaryInput.class, PrimaryInputDependencies.class, TransformParameters.class)).forType(implementationClass);
        this.requiresDependencies = instanceFactory.serviceInjectionTriggeredByAnnotation(PrimaryInputDependencies.class);
    }

    public boolean requiresDependencies() {
        return requiresDependencies;
    }

    @Override
    public ImmutableList<File> transform(File primaryInput, File outputDir, ArtifactTransformDependencies dependencies) {
        ArtifactTransformAction transformAction = newTransformAction(primaryInput, dependencies);
        DefaultArtifactTransformOutputs transformOutputs = new DefaultArtifactTransformOutputs(outputDir);
        transformAction.transform(transformOutputs);
        ImmutableList<File> outputs = transformOutputs.getRegisteredOutputs();
        return validateOutputs(primaryInput, outputDir, outputs);
    }

    @Override
    protected IsolatableParameters<Object> doIsolateParameters() {
        Hasher hasher = Hashing.newHasher();
        appendActionImplementation(classLoaderHierarchyHasher, hasher, getImplementationClass());

        Isolatable<Object> isolatableParameterObject;
        try {
            isolatableParameterObject = isolatableFactory.isolate(parameterObject);
        } catch (Exception e) {
            throw new VariantTransformConfigurationException(String.format("Could not snapshot parameters values for transform %s: %s", ModelType.of(getImplementationClass()).getDisplayName(), parameterObject), e);
        }

        if (parameterObject != null) {
            // TODO - should snapshot later
            fingerprintParameters(valueSnapshotter, propertyWalker, hasher, isolatableParameterObject.isolate());
        }
        HashCode secondaryInputsHash = hasher.hash();
        return new IsolatableParameters<>(isolatableParameterObject, secondaryInputsHash);
    }

    private static void fingerprintParameters(
        ValueSnapshotter valueSnapshotter,
        PropertyWalker propertyWalker,
        Hasher hasher,
        Object parameterObject
    ) {
        ImmutableSortedMap.Builder<String, ValueSnapshot> inputParameterFingerprintsBuilder = ImmutableSortedMap.naturalOrder();
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

                    inputParameterFingerprintsBuilder.put(propertyName, valueSnapshotter.snapshot(preparedValue));
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

            @Override
            public void visitInputFileProperty(String propertyName, boolean optional, boolean skipWhenEmpty, Class<? extends FileNormalizer> fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
                throw new UnsupportedOperationException("File input properties are not yet supported");
            }
        });

        if (!validationMessages.isEmpty()) {
            throw new DefaultMultiCauseException(
                String.format(validationMessages.size() == 1 ? "A problem was found with the configuration of %s." : "Some problems were found with the configuration of %s.", ModelType.of(parameterObject.getClass()).getDisplayName()),
                validationMessages.stream().map(InvalidUserDataException::new).collect(Collectors.toList())
            );
        }

        for (Map.Entry<String, ValueSnapshot> entry : inputParameterFingerprintsBuilder.build().entrySet()) {
            hasher.putString(entry.getKey());
            entry.getValue().appendToHasher(hasher);
        }
    }

    private ArtifactTransformAction newTransformAction(File inputFile, ArtifactTransformDependencies artifactTransformDependencies) {
        ServiceLookup services = new TransformServiceLookup(inputFile, getIsolated().getIsolatableParameters().isolate(), requiresDependencies ? artifactTransformDependencies : null);
        return instanceFactory.newInstance(services);
    }

    private static class TransformServiceLookup implements ServiceLookup {
        private final ImmutableList<InjectionPoint> injectionPoints;

        public TransformServiceLookup(File inputFile, @Nullable Object parameters, @Nullable ArtifactTransformDependencies artifactTransformDependencies) {
            ImmutableList.Builder<InjectionPoint> builder = ImmutableList.builder();
            builder.add(new InjectionPoint(PrimaryInput.class, File.class, inputFile));
            if (parameters != null) {
                builder.add(new InjectionPoint(TransformParameters.class, parameters.getClass(), parameters));
            }
            if (artifactTransformDependencies != null) {
                builder.add(new InjectionPoint(PrimaryInputDependencies.class, artifactTransformDependencies.getFiles()));
            }
            this.injectionPoints = builder.build();
        }

        @Nullable
        private
        Object find(Type serviceType, @Nullable Class<? extends Annotation> annotatedWith) {
            TypeToken<?> serviceTypeToken = TypeToken.of(serviceType);
            for (InjectionPoint injectionPoint : injectionPoints) {
                if (annotatedWith == injectionPoint.getAnnotation() && serviceTypeToken.isSupertypeOf(injectionPoint.getInjectedType())) {
                    return injectionPoint.getValueToInject();
                }
            }
            return null;
        }

        @Nullable
        @Override
        public Object find(Type serviceType) throws ServiceLookupException {
            return find(serviceType, null);
        }

        @Override
        public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
            Object result = find(serviceType);
            if (result == null) {
                throw new UnknownServiceException(serviceType, "No service of type " + serviceType + " available.");
            }
            return result;
        }

        @Override
        public Object get(Type serviceType, Class<? extends Annotation> annotatedWith) throws UnknownServiceException, ServiceLookupException {
            Object result = find(serviceType, annotatedWith);
            if (result == null) {
                throw new UnknownServiceException(serviceType, "No service of type " + serviceType + " available.");
            }
            return result;
        }

        private static class InjectionPoint {
            private final Class<? extends Annotation> annotation;
            private final Class<?> injectedType;
            private final Object valueToInject;

            public InjectionPoint(Class<? extends Annotation> annotation, Class<?> injectedType, Object valueToInject) {
                this.annotation = annotation;
                this.injectedType = injectedType;
                this.valueToInject = valueToInject;
            }

            public InjectionPoint(Class<? extends Annotation> annotation, Object valueToInject) {
                this(annotation, determineTypeFromAnnotation(annotation), valueToInject);
            }

            private static Class<?> determineTypeFromAnnotation(Class<? extends Annotation> annotation) {
                Class<?>[] supportedTypes = annotation.getAnnotation(InjectionPointQualifier.class).supportedTypes();
                if (supportedTypes.length != 1) {
                    throw new IllegalArgumentException("Cannot determine supported type for annotation " + annotation.getName());
                }
                return supportedTypes[0];
            }

            public Class<? extends Annotation> getAnnotation() {
                return annotation;
            }

            public Class<?> getInjectedType() {
                return injectedType;
            }

            public Object getValueToInject() {
                return valueToInject;
            }
        }
    }
}
