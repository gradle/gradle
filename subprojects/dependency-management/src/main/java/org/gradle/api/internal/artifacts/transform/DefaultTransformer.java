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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.reflect.TypeToken;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.InputArtifactDependencies;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.artifacts.transform.VariantTransformConfigurationException;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.properties.FileParameterUtils;
import org.gradle.api.internal.tasks.properties.InputParameterUtils;
import org.gradle.api.provider.Provider;
import org.gradle.api.reflect.InjectionPointQualifier;
import org.gradle.internal.Describables;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.UnitOfWork.InputFileValueSupplier;
import org.gradle.internal.execution.model.InputNormalizer;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.instantiation.InstanceFactory;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.isolated.IsolationScheme;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.model.CalculatedValueContainer;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.model.ValueCalculator;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.properties.InputFilePropertyType;
import org.gradle.internal.properties.OutputFilePropertyType;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.bean.PropertyWalker;
import org.gradle.internal.reflect.DefaultTypeValidationContext;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.Severity;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.UnknownServiceException;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.model.internal.type.ModelType;
import org.gradle.work.InputChanges;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.gradle.api.internal.tasks.properties.AbstractValidatingProperty.reportValueNotSet;

public class DefaultTransformer implements Transformer {

    private final Class<? extends TransformAction<?>> implementationClass;
    private final ImmutableAttributes fromAttributes;
    private final ImmutableAttributes toAttributes;
    private final FileNormalizer fileNormalizer;
    private final FileNormalizer dependenciesNormalizer;
    private final FileLookup fileLookup;
    private final ServiceLookup internalServices;
    private final boolean requiresDependencies;
    private final boolean requiresInputChanges;
    private final InstanceFactory<? extends TransformAction<?>> instanceFactory;
    private final boolean cacheable;
    private final CalculatedValueContainer<IsolatedParameters, IsolateTransformerParameters> isolatedParameters;
    private final DirectorySensitivity artifactDirectorySensitivity;
    private final DirectorySensitivity dependenciesDirectorySensitivity;
    private final LineEndingSensitivity artifactLineEndingSensitivity;
    private final LineEndingSensitivity dependenciesLineEndingSensitivity;

    public DefaultTransformer(
        Class<? extends TransformAction<?>> implementationClass,
        @Nullable TransformParameters parameterObject,
        ImmutableAttributes fromAttributes,
        ImmutableAttributes toAttributes,
        FileNormalizer inputArtifactNormalizer,
        FileNormalizer dependenciesNormalizer,
        boolean cacheable,
        DirectorySensitivity artifactDirectorySensitivity,
        DirectorySensitivity dependenciesDirectorySensitivity,
        LineEndingSensitivity artifactLineEndingSensitivity,
        LineEndingSensitivity dependenciesLineEndingSensitivity,
        BuildOperationExecutor buildOperationExecutor,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        IsolatableFactory isolatableFactory,
        FileCollectionFactory fileCollectionFactory,
        FileLookup fileLookup,
        PropertyWalker parameterPropertyWalker,
        InstantiationScheme actionInstantiationScheme,
        DomainObjectContext owner,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        ServiceLookup internalServices,
        DocumentationRegistry documentationRegistry
    ) {
        this.implementationClass = implementationClass;
        this.fromAttributes = fromAttributes;
        this.toAttributes = toAttributes;
        this.fileNormalizer = inputArtifactNormalizer;
        this.dependenciesNormalizer = dependenciesNormalizer;
        this.fileLookup = fileLookup;
        this.internalServices = internalServices;
        this.instanceFactory = actionInstantiationScheme.forType(implementationClass);
        this.requiresDependencies = instanceFactory.serviceInjectionTriggeredByAnnotation(InputArtifactDependencies.class);
        this.requiresInputChanges = instanceFactory.requiresService(InputChanges.class);
        this.cacheable = cacheable;
        this.artifactDirectorySensitivity = artifactDirectorySensitivity;
        this.dependenciesDirectorySensitivity = dependenciesDirectorySensitivity;
        this.artifactLineEndingSensitivity = artifactLineEndingSensitivity;
        this.dependenciesLineEndingSensitivity = dependenciesLineEndingSensitivity;
        this.isolatedParameters = calculatedValueContainerFactory.create(Describables.of("parameters of", this),
            new IsolateTransformerParameters(parameterObject, implementationClass, cacheable, owner, parameterPropertyWalker, isolatableFactory, buildOperationExecutor, classLoaderHierarchyHasher,
                fileCollectionFactory, documentationRegistry));
    }

    /**
     * Used to recreate a transformer from the configuration cache.
     */
    public DefaultTransformer(
        Class<? extends TransformAction<?>> implementationClass,
        CalculatedValueContainer<IsolatedParameters, IsolateTransformerParameters> isolatedParameters,
        ImmutableAttributes fromAttributes,
        ImmutableAttributes toAttributes,
        FileNormalizer inputArtifactNormalizer,
        FileNormalizer dependenciesNormalizer,
        boolean cacheable,
        FileLookup fileLookup,
        InstantiationScheme actionInstantiationScheme,
        ServiceLookup internalServices,
        DirectorySensitivity artifactDirectorySensitivity,
        DirectorySensitivity dependenciesDirectorySensitivity,
        LineEndingSensitivity artifactLineEndingSensitivity,
        LineEndingSensitivity dependenciesLineEndingSensitivity
    ) {
        this.implementationClass = implementationClass;
        this.fromAttributes = fromAttributes;
        this.toAttributes = toAttributes;
        this.fileNormalizer = inputArtifactNormalizer;
        this.dependenciesNormalizer = dependenciesNormalizer;
        this.fileLookup = fileLookup;
        this.internalServices = internalServices;
        this.instanceFactory = actionInstantiationScheme.forType(implementationClass);
        this.requiresDependencies = instanceFactory.serviceInjectionTriggeredByAnnotation(InputArtifactDependencies.class);
        this.requiresInputChanges = instanceFactory.requiresService(InputChanges.class);
        this.cacheable = cacheable;
        this.isolatedParameters = isolatedParameters;
        this.artifactDirectorySensitivity = artifactDirectorySensitivity;
        this.dependenciesDirectorySensitivity = dependenciesDirectorySensitivity;
        this.artifactLineEndingSensitivity = artifactLineEndingSensitivity;
        this.dependenciesLineEndingSensitivity = dependenciesLineEndingSensitivity;
    }

    public static void validateInputFileNormalizer(String propertyName, @Nullable FileNormalizer normalizer, boolean cacheable, TypeValidationContext validationContext) {
        if (cacheable) {
            if (normalizer == InputNormalizer.ABSOLUTE_PATH) {
                validationContext.visitPropertyProblem(problem ->
                    problem.withId(ValidationProblemId.CACHEABLE_TRANSFORM_CANT_USE_ABSOLUTE_SENSITIVITY)
                        .reportAs(Severity.ERROR)
                        .forProperty(propertyName)
                        .withDescription("is declared to be sensitive to absolute paths")
                        .happensBecause("This is not allowed for cacheable transforms")
                        .withLongDescription("Absolute path sensitivity does not allow sharing the transform result between different machines, although that is the goal of cacheable transforms.")
                        .addPossibleSolution("Use a different normalization strategy via @PathSensitive, @Classpath or @CompileClasspath")
                        .documentedAt("validation_problems", "cacheable_transform_cant_use_absolute_sensitivity"));
            }
        }
    }

    @Override
    public FileNormalizer getInputArtifactNormalizer() {
        return fileNormalizer;
    }

    @Override
    public FileNormalizer getInputArtifactDependenciesNormalizer() {
        return dependenciesNormalizer;
    }

    @Override
    public boolean isIsolated() {
        return isolatedParameters.getOrNull() != null;
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
    public DirectorySensitivity getInputArtifactDirectorySensitivity() {
        return artifactDirectorySensitivity;
    }

    @Override
    public DirectorySensitivity getInputArtifactDependenciesDirectorySensitivity() {
        return dependenciesDirectorySensitivity;
    }

    @Override
    public LineEndingSensitivity getInputArtifactLineEndingNormalization() {
        return artifactLineEndingSensitivity;
    }

    @Override
    public LineEndingSensitivity getInputArtifactDependenciesLineEndingNormalization() {
        return dependenciesLineEndingSensitivity;
    }

    @Override
    public HashCode getSecondaryInputHash() {
        return isolatedParameters.get().getSecondaryInputsHash();
    }

    @Override
    public TransformationResult transform(Provider<FileSystemLocation> inputArtifactProvider, File outputDir, ArtifactTransformDependencies dependencies, @Nullable InputChanges inputChanges) {
        TransformAction<?> transformAction = newTransformAction(inputArtifactProvider, dependencies, inputChanges);
        DefaultTransformOutputs transformOutputs = new DefaultTransformOutputs(inputArtifactProvider.get().getAsFile(), outputDir, fileLookup);
        transformAction.transform(transformOutputs);
        return transformOutputs.getRegisteredOutputs();
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(isolatedParameters);
    }

    @Override
    public void isolateParametersIfNotAlready() {
        isolatedParameters.finalizeIfNotAlready();
    }

    private static void fingerprintParameters(
        DocumentationRegistry documentationRegistry,
        InputFingerprinter inputFingerprinter,
        FileCollectionFactory fileCollectionFactory,
        PropertyWalker propertyWalker,
        Hasher hasher,
        Object parameterObject,
        boolean cacheable
    ) {
        DefaultTypeValidationContext validationContext = DefaultTypeValidationContext.withoutRootType(documentationRegistry, cacheable);
        InputFingerprinter.Result result = inputFingerprinter.fingerprintInputProperties(
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            ImmutableSortedMap.of(),
            visitor -> propertyWalker.visitProperties(parameterObject, validationContext, new PropertyVisitor() {
                @Override
                public void visitInputProperty(
                    String propertyName,
                    PropertyValue value,
                    boolean optional
                ) {
                    try {
                        Object preparedValue = InputParameterUtils.prepareInputParameterValue(value);

                        if (preparedValue == null && !optional) {
                            reportValueNotSet(propertyName, validationContext);
                        }
                        visitor.visitInputProperty(propertyName, () -> preparedValue);
                    } catch (Throwable e) {
                        throw new InvalidUserDataException(String.format(
                            "Error while evaluating property '%s' of %s",
                            propertyName,
                            getParameterObjectDisplayName(parameterObject)
                        ), e);
                    }
                }

                @Override
                public void visitInputFileProperty(
                    String propertyName,
                    boolean optional,
                    InputBehavior behavior,
                    DirectorySensitivity directorySensitivity,
                    LineEndingSensitivity lineEndingNormalization,
                    @Nullable FileNormalizer normalizer,
                    PropertyValue value,
                    InputFilePropertyType filePropertyType
                ) {
                    validateInputFileNormalizer(propertyName, normalizer, cacheable, validationContext);
                    visitor.visitInputFileProperty(
                        propertyName,
                        behavior,
                        new InputFileValueSupplier(
                            value,
                            normalizer == null ? InputNormalizer.ABSOLUTE_PATH : normalizer,
                            directorySensitivity,
                            lineEndingNormalization,
                            () -> FileParameterUtils.resolveInputFileValue(fileCollectionFactory, filePropertyType, value)));
                }

                @Override
                public void visitOutputFileProperty(
                    String propertyName,
                    boolean optional,
                    PropertyValue value,
                    OutputFilePropertyType filePropertyType
                ) {
                    validationContext.visitPropertyProblem(problem ->
                        problem.withId(ValidationProblemId.ARTIFACT_TRANSFORM_SHOULD_NOT_DECLARE_OUTPUT)
                            .reportAs(Severity.ERROR)
                            .forProperty(propertyName)
                            .withDescription("declares an output")
                            .happensBecause("is annotated with an output annotation")
                            .addPossibleSolution("Remove the output property and use the TransformOutputs parameter from transform(TransformOutputs) instead")
                            .documentedAt("validation_problems", "artifact_transform_should_not_declare_output")
                    );
                }
            })
        );

        ImmutableMap<String, Severity> validationMessages = validationContext.getProblems();
        if (!validationMessages.isEmpty()) {
            throw new DefaultMultiCauseException(
                String.format(validationMessages.size() == 1
                        ? "A problem was found with the configuration of the artifact transform parameter %s."
                        : "Some problems were found with the configuration of the artifact transform parameter %s.",
                    getParameterObjectDisplayName(parameterObject)),
                validationMessages.keySet().stream()
                    .sorted()
                    .map(InvalidUserDataException::new)
                    .collect(Collectors.toList())
            );
        }

        for (Map.Entry<String, ValueSnapshot> entry : result.getValueSnapshots().entrySet()) {
            hasher.putString(entry.getKey());
            entry.getValue().appendToHasher(hasher);
        }
        for (Map.Entry<String, CurrentFileCollectionFingerprint> entry : result.getFileFingerprints().entrySet()) {
            hasher.putString(entry.getKey());
            hasher.putHash(entry.getValue().getHash());
        }
    }

    private static String getParameterObjectDisplayName(Object parameterObject) {
        return ModelType.of(new DslObject(parameterObject).getDeclaredType()).getDisplayName();
    }

    private TransformAction<?> newTransformAction(Provider<FileSystemLocation> inputArtifactProvider, ArtifactTransformDependencies artifactTransformDependencies, @Nullable InputChanges inputChanges) {
        TransformParameters parameters = isolatedParameters.get().getIsolatedParameterObject().isolate();
        ServiceLookup services = new IsolationScheme<>(TransformAction.class, TransformParameters.class, TransformParameters.None.class).servicesForImplementation(parameters, internalServices);
        services = new TransformServiceLookup(inputArtifactProvider, requiresDependencies ? artifactTransformDependencies : null, inputChanges, services);
        return instanceFactory.newInstance(services);
    }

    public CalculatedValueContainer<IsolatedParameters, IsolateTransformerParameters> getIsolatedParameters() {
        return isolatedParameters;
    }

    @Override
    public ImmutableAttributes getFromAttributes() {
        return fromAttributes;
    }

    @Override
    public ImmutableAttributes getToAttributes() {
        return toAttributes;
    }

    @Override
    public Class<? extends TransformAction<?>> getImplementationClass() {
        return implementationClass;
    }

    @Override
    public String getDisplayName() {
        return implementationClass.getSimpleName();
    }

    private static class TransformServiceLookup implements ServiceLookup {
        private static final Type FILE_SYSTEM_LOCATION_PROVIDER = new TypeToken<Provider<FileSystemLocation>>() {
        }.getType();

        private final ImmutableList<InjectionPoint> injectionPoints;
        private final ServiceLookup delegate;

        public TransformServiceLookup(Provider<FileSystemLocation> inputFileProvider, @Nullable ArtifactTransformDependencies artifactTransformDependencies, @Nullable InputChanges inputChanges, ServiceLookup delegate) {
            this.delegate = delegate;
            ImmutableList.Builder<InjectionPoint> builder = ImmutableList.builder();
            builder.add(InjectionPoint.injectedByAnnotation(InputArtifact.class, FILE_SYSTEM_LOCATION_PROVIDER, () -> inputFileProvider));
            if (artifactTransformDependencies != null) {
                builder.add(InjectionPoint.injectedByAnnotation(InputArtifactDependencies.class, () -> artifactTransformDependencies.getFiles().orElseThrow(() -> new IllegalStateException("Transform does not use artifact dependencies."))));
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
            Object result = find(serviceType, null);
            if (result != null) {
                return result;
            }
            return delegate.find(serviceType);
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
            if (result != null) {
                return result;
            }
            return delegate.get(serviceType, annotatedWith);
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

    public static class IsolatedParameters {
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

    public static class IsolateTransformerParameters implements ValueCalculator<IsolatedParameters> {
        private final TransformParameters parameterObject;
        private final DomainObjectContext owner;
        private final IsolatableFactory isolatableFactory;
        private final PropertyWalker parameterPropertyWalker;
        private final BuildOperationExecutor buildOperationExecutor;
        private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
        private final FileCollectionFactory fileCollectionFactory;
        private final DocumentationRegistry documentationRegistry;
        private final boolean cacheable;
        private final Class<?> implementationClass;

        public IsolateTransformerParameters(
            @Nullable TransformParameters parameterObject,
            Class<?> implementationClass,
            boolean cacheable,
            DomainObjectContext owner,
            PropertyWalker parameterPropertyWalker,
            IsolatableFactory isolatableFactory,
            BuildOperationExecutor buildOperationExecutor,
            ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
            FileCollectionFactory fileCollectionFactory,
            DocumentationRegistry documentationRegistry
        ) {
            this.parameterObject = parameterObject;
            this.implementationClass = implementationClass;
            this.cacheable = cacheable;
            this.owner = owner;
            this.parameterPropertyWalker = parameterPropertyWalker;
            this.isolatableFactory = isolatableFactory;
            this.buildOperationExecutor = buildOperationExecutor;
            this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
            this.fileCollectionFactory = fileCollectionFactory;
            this.documentationRegistry = documentationRegistry;
        }

        @Nullable
        public TransformParameters getParameterObject() {
            return parameterObject;
        }

        public boolean isCacheable() {
            return cacheable;
        }

        public Class<?> getImplementationClass() {
            return implementationClass;
        }

        @Override
        public boolean usesMutableProjectState() {
            return owner.getProject() != null;
        }

        @Nullable
        @Override
        public ProjectInternal getOwningProject() {
            return owner.getProject();
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            if (parameterObject != null) {
                parameterPropertyWalker.visitProperties(parameterObject, TypeValidationContext.NOOP, new PropertyVisitor() {
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
                        context.add(value.getTaskDependencies());
                    }
                });
            }
        }

        @Override
        public IsolatedParameters calculateValue(NodeExecutionContext context) {
            InputFingerprinter inputFingerprinter = context.getService(InputFingerprinter.class);
            return isolateParameters(inputFingerprinter);
        }

        private IsolatedParameters isolateParameters(InputFingerprinter inputFingerprinter) {
            ModelContainer<?> model = owner.getModel();
            if (!model.hasMutableState()) {
                // This may happen when a task visits artifacts using a FileCollection instance created from a Configuration instance in a different project (not an artifact produced by a different project, these work fine)
                // There is a check in DefaultConfiguration that deprecates resolving dependencies via FileCollection instance created by a different project, however that check may not
                // necessarily be triggered. For example, the configuration may be legitimately resolved by some other task prior to the problematic task running
                // TODO - hoist this up into configuration file collection visiting (and not when visiting the upstream dependencies of a transform), and deprecate this in Gradle 7.x
                //
                // This may also happen when a transform takes upstream dependencies and the dependencies are transformed using a different transform
                // In this case, the main thread that schedules the work should isolate the transform parameters prior to scheduling the work. However, the dependencies may
                // be filtered from the result, so that the transform is not visited by the main thread, or the transform worker may start work before the main thread
                // has a chance to isolate the upstream transform
                // TODO - ensure all transform parameters required by a transform worker are isolated prior to starting the worker
                //
                // Force access to the state of the owner, regardless of whether any other thread has access. This is because attempting to acquire a lock for a project may deadlock
                // when performed from a worker thread (see DefaultBuildOperationQueue.waitForCompletion() which intentionally does not release the project locks while waiting)
                // TODO - add validation to fail eagerly when a worker attempts to lock a project
                //
                return model.forceAccessToMutableState(o -> doIsolateParameters(inputFingerprinter));
            } else {
                return doIsolateParameters(inputFingerprinter);
            }
        }

        private IsolatedParameters doIsolateParameters(InputFingerprinter inputFingerprinter) {
            try {
                return isolateParametersExclusively(inputFingerprinter);
            } catch (Exception e) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Could not isolate parameters ").appendValue(parameterObject).append(" of artifact transform ").appendType(implementationClass);
                throw new VariantTransformConfigurationException(formatter.toString(), e);
            }
        }

        private IsolatedParameters isolateParametersExclusively(InputFingerprinter inputFingerprinter) {
            Isolatable<TransformParameters> isolatedParameterObject = isolatableFactory.isolate(parameterObject);

            Hasher hasher = Hashing.newHasher();
            hasher.putString(implementationClass.getName());
            hasher.putHash(classLoaderHierarchyHasher.getClassLoaderHash(implementationClass.getClassLoader()));

            if (parameterObject != null) {
                TransformParameters isolatedTransformParameters = isolatedParameterObject.isolate();
                buildOperationExecutor.run(new RunnableBuildOperation() {
                    @Override
                    public void run(BuildOperationContext context) {
                        // TODO wolfs - schedule fingerprinting separately, it can be done without having the project lock
                        fingerprintParameters(
                            documentationRegistry,
                            inputFingerprinter,
                            fileCollectionFactory,
                            parameterPropertyWalker,
                            hasher,
                            isolatedTransformParameters,
                            cacheable
                        );
                        context.setResult(FingerprintTransformInputsOperation.Result.INSTANCE);
                    }

                    @Override
                    public BuildOperationDescriptor.Builder description() {
                        return BuildOperationDescriptor
                            .displayName("Fingerprint transformation inputs")
                            .details(FingerprintTransformInputsOperation.Details.INSTANCE);
                    }
                });
            }
            HashCode secondaryInputsHash = hasher.hash();
            return new IsolatedParameters(isolatedParameterObject, secondaryInputsHash);
        }
    }

    /*
     * This operation is only used here temporarily. Should be replaced with a more stable operation in the long term.
     */
    public interface FingerprintTransformInputsOperation extends BuildOperationType<FingerprintTransformInputsOperation.Details, FingerprintTransformInputsOperation.Result> {
        interface Details {
            Details INSTANCE = new Details() {
            };
        }

        interface Result {
            Result INSTANCE = new Result() {
            };
        }
    }
}
