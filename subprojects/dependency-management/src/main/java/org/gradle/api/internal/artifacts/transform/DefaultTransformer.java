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
import org.gradle.api.Project;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.InputArtifactDependencies;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.api.internal.tasks.properties.DefaultParameterValidationContext;
import org.gradle.api.internal.tasks.properties.FileParameterUtils;
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
import org.gradle.internal.fingerprint.AbsolutePathInputNormalizer;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.instantiation.InstanceFactory;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.instantiation.Managed;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.ParameterValidationContext;
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

public class DefaultTransformer extends AbstractTransformer<TransformAction> {

    private final Object parameterObject;
    private final Class<? extends FileNormalizer> fileNormalizer;
    private final Class<? extends FileNormalizer> dependenciesNormalizer;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final IsolatableFactory isolatableFactory;
    private final ValueSnapshotter valueSnapshotter;
    private final FileCollectionFactory fileCollectionFactory;
    private final FileCollectionFingerprinterRegistry fileCollectionFingerprinterRegistry;
    private final PropertyWalker parameterPropertyWalker;
    private final boolean requiresDependencies;
    private final InstanceFactory<? extends TransformAction> instanceFactory;
    private final DomainObjectProjectStateHandler projectStateHandler;
    private final ProjectStateRegistry.SafeExclusiveLock isolationLock;
    private final WorkNodeAction isolateAction;
    private final boolean cacheable;

    private IsolatableParameters isolatable;

    public DefaultTransformer(
        Class<? extends TransformAction> implementationClass,
        @Nullable Object parameterObject,
        ImmutableAttributes fromAttributes,
        Class<? extends FileNormalizer> inputArtifactNormalizer,
        Class<? extends FileNormalizer> dependenciesNormalizer,
        boolean cacheable,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        IsolatableFactory isolatableFactory,
        ValueSnapshotter valueSnapshotter,
        FileCollectionFactory fileCollectionFactory,
        FileCollectionFingerprinterRegistry fileCollectionFingerprinterRegistry,
        PropertyWalker parameterPropertyWalker,
        DomainObjectProjectStateHandler projectStateHandler,
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
        this.fileCollectionFingerprinterRegistry = fileCollectionFingerprinterRegistry;
        this.parameterPropertyWalker = parameterPropertyWalker;
        this.instanceFactory = actionInstantiationScheme.forType(implementationClass);
        this.requiresDependencies = instanceFactory.serviceInjectionTriggeredByAnnotation(InputArtifactDependencies.class);
        this.cacheable = cacheable;
        this.projectStateHandler = projectStateHandler;
        this.isolationLock = projectStateHandler.newExclusiveOperationLock();
        this.isolateAction = parameterObject == null ? null : new WorkNodeAction() {
            @Nullable
            @Override
            public Project getProject() {
                return projectStateHandler.maybeGetOwningProject();
            }

            @Override
            public void run() {
                isolateExclusively();
            }
        };
    }

    public static void validateInputFileNormalizer(String propertyName, @Nullable Class<? extends FileNormalizer> normalizer, boolean cacheable, ParameterValidationContext parameterValidationContext) {
        if (cacheable) {
            if (normalizer == AbsolutePathInputNormalizer.class) {
                parameterValidationContext.recordValidationMessage(null, propertyName, "is declared to be sensitive to absolute paths. This is not allowed for cacheable transforms");
            }
            if (normalizer == null) {
                parameterValidationContext.recordValidationMessage(null, propertyName, "is declared without path sensitivity. Properties of cacheable transforms must declare their path sensitivity");
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

    public boolean requiresDependencies() {
        return requiresDependencies;
    }

    @Override
    public boolean isCacheable() {
        return cacheable;
    }

    @Override
    public HashCode getSecondaryInputHash() {
        return getIsolatable().getSecondaryInputsHash();
    }

    @Override
    public ImmutableList<File> transform(File inputArtifact, File outputDir, ArtifactTransformDependencies dependencies) {
        TransformAction transformAction = newTransformAction(inputArtifact, dependencies);
        DefaultTransformOutputs transformOutputs = new DefaultTransformOutputs(inputArtifact, outputDir);
        transformAction.transform(transformOutputs);
        return transformOutputs.getRegisteredOutputs();
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        if (isolateAction != null) {
            context.add(isolateAction);
        }
        if (parameterObject != null) {
            parameterPropertyWalker.visitProperties(parameterObject, ParameterValidationContext.NOOP, new PropertyVisitor.Adapter() {
                @Override
                public void visitInputFileProperty(String propertyName, boolean optional, boolean skipWhenEmpty, @Nullable Class<? extends FileNormalizer> fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
                    context.maybeAdd(value.call());
                }
            });
        }
    }

    @Override
    public void isolateParameters() {
        if (isolatable == null) {
            if (!projectStateHandler.hasMutableProjectState()) {
                projectStateHandler.withLenientState(this::isolateExclusively);
            } else {
                isolateExclusively();
            }
        }
    }

    private void isolateExclusively() {
        isolationLock.withLock(() -> {
            if (isolatable != null) {
                return;
            }
            try {
                isolatable = doIsolateParameters();
            } catch (Exception e) {
                throw new VariantTransformConfigurationException(String.format("Cannot isolate parameters %s of artifact transform %s", parameterObject, ModelType.of(getImplementationClass()).getDisplayName()), e);
            }
        });
    }

    protected IsolatableParameters doIsolateParameters() {
        Isolatable<Object> isolatableParameterObject = isolatableFactory.isolate(parameterObject);

        Hasher hasher = Hashing.newHasher();
        appendActionImplementation(getImplementationClass(), hasher, classLoaderHierarchyHasher);

        if (parameterObject != null) {
            // TODO wolfs - schedule fingerprinting separately, it can be done without having the project lock
            fingerprintParameters(valueSnapshotter, fileCollectionFingerprinterRegistry, fileCollectionFactory, parameterPropertyWalker, hasher, isolatableParameterObject.isolate(), cacheable);
        }
        HashCode secondaryInputsHash = hasher.hash();
        return new IsolatableParameters(isolatableParameterObject, secondaryInputsHash);
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
                        validationContext.recordValidationMessage(null, propertyName, "does not have a value specified");
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
                validationContext.recordValidationMessage(null, propertyName, "is annotated with an output annotation");
            }

            @Override
            public void visitInputFileProperty(String propertyName, boolean optional, boolean skipWhenEmpty, @Nullable Class<? extends FileNormalizer> fileNormalizer, PropertyValue value, InputFilePropertyType filePropertyType) {
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
                validationMessages.stream().map(InvalidUserDataException::new).collect(Collectors.toList())
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
        Class<?> parameterClass = parameterObject instanceof Managed ? ((Managed) parameterObject).publicType() : parameterObject.getClass();
        return ModelType.of(parameterClass).getDisplayName();
    }

    private TransformAction newTransformAction(File inputFile, ArtifactTransformDependencies artifactTransformDependencies) {
        ServiceLookup services = new TransformServiceLookup(inputFile, getIsolatable().getIsolatableParameters().isolate(), requiresDependencies ? artifactTransformDependencies : null);
        return instanceFactory.newInstance(services);
    }

    private IsolatableParameters getIsolatable() {
        if (isolatable == null) {
            throw new IllegalStateException("The parameters of " + getDisplayName() + "need to be isolated first!");
        }
        return isolatable;
    }

    private static class TransformServiceLookup implements ServiceLookup {
        private final ImmutableList<InjectionPoint> injectionPoints;

        public TransformServiceLookup(File inputFile, @Nullable Object parameters, @Nullable ArtifactTransformDependencies artifactTransformDependencies) {
            ImmutableList.Builder<InjectionPoint> builder = ImmutableList.builder();
            builder.add(new InjectionPoint(InputArtifact.class, File.class, inputFile));
            if (parameters != null) {
                builder.add(new InjectionPoint(TransformParameters.class, parameters.getClass(), parameters));
            }
            if (artifactTransformDependencies != null) {
                builder.add(new InjectionPoint(InputArtifactDependencies.class, artifactTransformDependencies.getFiles()));
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

    private static class IsolatableParameters {
        private HashCode secondaryInputsHash;
        private Isolatable<?> isolatableParameters;

        public IsolatableParameters(Isolatable<?> isolatableParameters, HashCode secondaryInputsHash) {
            this.secondaryInputsHash = secondaryInputsHash;
            this.isolatableParameters = isolatableParameters;
        }

        public HashCode getSecondaryInputsHash() {
            return secondaryInputsHash;
        }

        public Isolatable<?> getIsolatableParameters() {
            return isolatableParameters;
        }
    }
}
