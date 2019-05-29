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
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.reflect.TypeToken;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.InputArtifactDependencies;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.properties.DefaultParameterValidationContext;
import org.gradle.api.internal.tasks.properties.FileParameterUtils;
import org.gradle.api.internal.tasks.properties.InputFilePropertyType;
import org.gradle.api.internal.tasks.properties.InputParameterUtils;
import org.gradle.api.internal.tasks.properties.OutputFilePropertyType;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.provider.Provider;
import org.gradle.api.reflect.InjectionPointQualifier;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.fingerprint.AbsolutePathInputNormalizer;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.instantiation.InstanceFactory;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.ParameterValidationContext;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.UnknownServiceException;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.DeprecationLogger;
import org.gradle.work.InputChanges;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DefaultTransformer extends AbstractTransformer<TransformAction> {

    private final TransformParameters parameterObject;
    private final Class<? extends FileNormalizer> fileNormalizer;
    private final Class<? extends FileNormalizer> dependenciesNormalizer;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final IsolatableFactory isolatableFactory;
    private final ValueSnapshotter valueSnapshotter;
    private final FileCollectionFactory fileCollectionFactory;
    private final PropertyWalker parameterPropertyWalker;
    private final boolean requiresDependencies;
    private final boolean requiresInputChanges;
    private final InstanceFactory<? extends TransformAction> instanceFactory;
    private final boolean cacheable;

    private IsolatedParameters isolatedParameters;

    public DefaultTransformer(
        Class<? extends TransformAction> implementationClass,
        @Nullable TransformParameters parameterObject,
        ImmutableAttributes fromAttributes,
        Class<? extends FileNormalizer> inputArtifactNormalizer,
        Class<? extends FileNormalizer> dependenciesNormalizer,
        boolean cacheable,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        IsolatableFactory isolatableFactory,
        ValueSnapshotter valueSnapshotter,
        FileCollectionFactory fileCollectionFactory,
        PropertyWalker parameterPropertyWalker,
        InstantiationScheme actionInstantiationScheme
    ) {
        super(implementationClass, fromAttributes);
        this.parameterObject = parameterObject;
        this.fileNormalizer = inputArtifactNormalizer;
        this.dependenciesNormalizer = dependenciesNormalizer;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.isolatableFactory = isolatableFactory;
        this.valueSnapshotter = valueSnapshotter;
        this.fileCollectionFactory = fileCollectionFactory;
        this.parameterPropertyWalker = parameterPropertyWalker;
        this.instanceFactory = actionInstantiationScheme.forType(implementationClass);
        this.requiresDependencies = instanceFactory.serviceInjectionTriggeredByAnnotation(InputArtifactDependencies.class);
        this.requiresInputChanges = instanceFactory.requiresService(InputChanges.class);
        this.cacheable = cacheable;
    }

    public static void validateInputFileNormalizer(String propertyName, @Nullable Class<? extends FileNormalizer> normalizer, boolean cacheable, ParameterValidationContext parameterValidationContext) {
        if (cacheable) {
            if (normalizer == AbsolutePathInputNormalizer.class) {
                parameterValidationContext.visitError(null, propertyName, "is declared to be sensitive to absolute paths. This is not allowed for cacheable transforms");
            }
            if (normalizer == null) {
                parameterValidationContext.visitError(null, propertyName, "has no normalization specified. Properties of cacheable transforms must declare their normalization via @PathSensitive, @Classpath or @CompileClasspath");
            }
        }
    }

    @Override
    public Class<? extends FileNormalizer> getInputArtifactNormalizer() {
        return fileNormalizer;
    }

    @Override
    public Class<? extends FileNormalizer> getInputArtifactDependenciesNormalizer() {
        return dependenciesNormalizer;
    }

    @Override
    public boolean isIsolated() {
        return isolatedParameters != null;
    }

    @Override
    public boolean requiresDependencies() {
        return requiresDependencies;
    }

    @Override
    public boolean requiresInputChanges() {
        return requiresInputChanges;
    }

    @Override
    public boolean isCacheable() {
        return cacheable;
    }

    @Override
    public HashCode getSecondaryInputHash() {
        return getIsolatedParameters().getSecondaryInputsHash();
    }

    @Override
    public ImmutableList<File> transform(Provider<FileSystemLocation> inputArtifactProvider, File outputDir, ArtifactTransformDependencies dependencies, @Nullable InputChanges inputChanges) {
        TransformAction transformAction = newTransformAction(inputArtifactProvider, dependencies, inputChanges);
        DefaultTransformOutputs transformOutputs = new DefaultTransformOutputs(inputArtifactProvider.get().getAsFile(), outputDir);
        transformAction.transform(transformOutputs);
        return transformOutputs.getRegisteredOutputs();
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        if (parameterObject != null) {
            parameterPropertyWalker.visitProperties(parameterObject, ParameterValidationContext.NOOP, new PropertyVisitor.Adapter() {
                @Override
                public void visitInputFileProperty(String propertyName, boolean optional, boolean skipWhenEmpty, boolean incremental, @Nullable Class<? extends FileNormalizer> fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
                    context.add(value.getTaskDependencies());
                }
            });
        }
    }

    @Override
    public void isolateParameters(FileCollectionFingerprinterRegistry fingerprinterRegistry) {
        try {
            isolatedParameters = doIsolateParameters(fingerprinterRegistry);
        } catch (Exception e) {
            throw new VariantTransformConfigurationException(String.format("Cannot isolate parameters %s of artifact transform %s", parameterObject, ModelType.of(getImplementationClass()).getDisplayName()), e);
        }
    }

    protected IsolatedParameters doIsolateParameters(FileCollectionFingerprinterRegistry fingerprinterRegistry) {
        Isolatable<TransformParameters> isolatedParameterObject = isolatableFactory.isolate(parameterObject);

        Hasher hasher = Hashing.newHasher();
        appendActionImplementation(getImplementationClass(), hasher, classLoaderHierarchyHasher);

        if (parameterObject != null) {
            // TODO wolfs - schedule fingerprinting separately, it can be done without having the project lock
            fingerprintParameters(valueSnapshotter, fingerprinterRegistry, fileCollectionFactory, parameterPropertyWalker, hasher, isolatedParameterObject.isolate(), cacheable);
        }
        HashCode secondaryInputsHash = hasher.hash();
        return new IsolatedParameters(isolatedParameterObject, secondaryInputsHash);
    }

    private static void fingerprintParameters(
        ValueSnapshotter valueSnapshotter,
        FileCollectionFingerprinterRegistry fingerprinterRegistry,
        FileCollectionFactory fileCollectionFactory,
        PropertyWalker propertyWalker,
        Hasher hasher,
        Object parameterObject,
        boolean cacheable
    ) {
        ImmutableSortedMap.Builder<String, ValueSnapshot> inputParameterFingerprintsBuilder = ImmutableSortedMap.naturalOrder();
        ImmutableSortedMap.Builder<String, CurrentFileCollectionFingerprint> inputFileParameterFingerprintsBuilder = ImmutableSortedMap.naturalOrder();
        List<String> validationMessages = new ArrayList<>();
        DefaultParameterValidationContext validationContext = new DefaultParameterValidationContext(validationMessages);
        propertyWalker.visitProperties(parameterObject, validationContext, new PropertyVisitor.Adapter() {
            @Override
            public void visitInputProperty(String propertyName, PropertyValue value, boolean optional) {
                try {
                    Object preparedValue = InputParameterUtils.prepareInputParameterValue(value);

                    if (preparedValue == null && !optional) {
                        validationContext.visitError(null, propertyName, "does not have a value specified");
                    }

                    inputParameterFingerprintsBuilder.put(propertyName, valueSnapshotter.snapshot(preparedValue));
                } catch (Throwable e) {
                    throw new InvalidUserDataException(String.format(
                        "Error while evaluating property '%s' of %s",
                        propertyName,
                        getParameterObjectDisplayName(parameterObject)
                    ), e);
                }
            }

            @Override
            public void visitOutputFileProperty(String propertyName, boolean optional, PropertyValue value, OutputFilePropertyType filePropertyType) {
                validationContext.visitError(null, propertyName, "is annotated with an output annotation");
            }

            @Override
            public void visitInputFileProperty(String propertyName, boolean optional, boolean skipWhenEmpty, boolean incremental, @Nullable Class<? extends FileNormalizer> fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
                validateInputFileNormalizer(propertyName, fileNormalizer, cacheable, validationContext);
                FileCollectionFingerprinter fingerprinter = fingerprinterRegistry.getFingerprinter(FileParameterUtils.normalizerOrDefault(fileNormalizer));
                FileCollection inputFileValue = FileParameterUtils.resolveInputFileValue(fileCollectionFactory, filePropertyType, value);
                CurrentFileCollectionFingerprint fingerprint = fingerprinter.fingerprint(inputFileValue);
                inputFileParameterFingerprintsBuilder.put(propertyName, fingerprint);
            }
        });

        if (!validationMessages.isEmpty()) {
            throw new DefaultMultiCauseException(
                String.format(validationMessages.size() == 1 ? "A problem was found with the configuration of the artifact transform parameter %s." : "Some problems were found with the configuration of the artifact transform parameter %s.", getParameterObjectDisplayName(parameterObject)),
                validationMessages.stream().sorted().map(InvalidUserDataException::new).collect(Collectors.toList())
            );
        }

        for (Map.Entry<String, ValueSnapshot> entry : inputParameterFingerprintsBuilder.build().entrySet()) {
            hasher.putString(entry.getKey());
            entry.getValue().appendToHasher(hasher);
        }
        for (Map.Entry<String, CurrentFileCollectionFingerprint> entry : inputFileParameterFingerprintsBuilder.build().entrySet()) {
            hasher.putString(entry.getKey());
            hasher.putHash(entry.getValue().getHash());
        }
    }

    private static String getParameterObjectDisplayName(Object parameterObject) {
        return ModelType.of(new DslObject(parameterObject).getDeclaredType()).getDisplayName();
    }

    private TransformAction newTransformAction(Provider<FileSystemLocation> inputArtifactProvider, ArtifactTransformDependencies artifactTransformDependencies, @Nullable InputChanges inputChanges) {
        ServiceLookup services = new TransformServiceLookup(inputArtifactProvider, getIsolatedParameters().getIsolatedParameterObject().isolate(), requiresDependencies ? artifactTransformDependencies : null, inputChanges);
        return instanceFactory.newInstance(services);
    }

    private IsolatedParameters getIsolatedParameters() {
        if (isolatedParameters == null) {
            throw new IllegalStateException("The parameters of " + getDisplayName() + "need to be isolated first!");
        }
        return isolatedParameters;
    }

    private static class TransformServiceLookup implements ServiceLookup {
        private static final Type FILE_SYSTEM_LOCATION_PROVIDER = new TypeToken<Provider<FileSystemLocation>>() {}.getType();

        private final ImmutableList<InjectionPoint> injectionPoints;

        public TransformServiceLookup(Provider<FileSystemLocation> inputFileProvider, @Nullable TransformParameters parameters, @Nullable ArtifactTransformDependencies artifactTransformDependencies, @Nullable InputChanges inputChanges) {
            ImmutableList.Builder<InjectionPoint> builder = ImmutableList.builder();
            builder.add(InjectionPoint.injectedByAnnotation(InputArtifact.class, File.class, () -> {
                DeprecationLogger.nagUserOfDeprecated("Injecting the input artifact of a transform as a File", "Declare the input artifact as Provider<FileSystemLocation> instead.");
                return inputFileProvider.get().getAsFile();
            }));
            builder.add(InjectionPoint.injectedByAnnotation(InputArtifact.class, FILE_SYSTEM_LOCATION_PROVIDER, () -> inputFileProvider));
            if (parameters != null) {
                builder.add(InjectionPoint.injectedByType(parameters.getClass(), () -> parameters));
            } else {
                builder.add(InjectionPoint.injectedByType(TransformParameters.None.class, () -> {
                    throw new UnknownServiceException(TransformParameters.None.class, "Cannot query parameters for artifact transform without parameters.");
                }));
            }
            if (artifactTransformDependencies != null) {
                builder.add(InjectionPoint.injectedByAnnotation(InputArtifactDependencies.class, () -> artifactTransformDependencies.getFiles()));
            }
            if (inputChanges != null) {
                builder.add(InjectionPoint.injectedByType(InputChanges.class, () -> inputChanges));
            }
            this.injectionPoints = builder.build();
        }

        @Nullable
        private Object find(Type serviceType, @Nullable Class<? extends Annotation> annotatedWith) {
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
            private final Type injectedType;
            private final Supplier<Object> valueToInject;

            public static InjectionPoint injectedByAnnotation(Class<? extends Annotation> annotation, Supplier<Object> valueToInject) {
                return new InjectionPoint(annotation, determineTypeFromAnnotation(annotation), valueToInject);
            }

            public static InjectionPoint injectedByAnnotation(Class<? extends Annotation> annotation, Type injectedType, Supplier<Object> valueToInject) {
                return new InjectionPoint(annotation, injectedType, valueToInject);
            }

            public static InjectionPoint injectedByType(Class<?> injectedType, Supplier<Object> valueToInject) {
                return new InjectionPoint(null, injectedType, valueToInject);
            }

            private InjectionPoint(@Nullable Class<? extends Annotation> annotation, Type injectedType, Supplier<Object> valueToInject) {
                this.annotation = annotation;
                this.injectedType = injectedType;
                this.valueToInject = valueToInject;
            }

            private static Class<?> determineTypeFromAnnotation(Class<? extends Annotation> annotation) {
                Class<?>[] supportedTypes = annotation.getAnnotation(InjectionPointQualifier.class).supportedTypes();
                if (supportedTypes.length != 1) {
                    throw new IllegalArgumentException("Cannot determine supported type for annotation " + annotation.getName());
                }
                return supportedTypes[0];
            }

            @Nullable
            public Class<? extends Annotation> getAnnotation() {
                return annotation;
            }

            public Type getInjectedType() {
                return injectedType;
            }

            public Object getValueToInject() {
                return valueToInject.get();
            }
        }
    }

    private static class IsolatedParameters {
        private final HashCode secondaryInputsHash;
        private final Isolatable<? extends TransformParameters> isolatedParameterObject;

        public IsolatedParameters(Isolatable<? extends TransformParameters> isolatedParameterObject, HashCode secondaryInputsHash) {
            this.secondaryInputsHash = secondaryInputsHash;
            this.isolatedParameterObject = isolatedParameterObject;
        }

        public HashCode getSecondaryInputsHash() {
            return secondaryInputsHash;
        }

        public Isolatable<? extends TransformParameters> getIsolatedParameterObject() {
            return isolatedParameterObject;
        }
    }
}
