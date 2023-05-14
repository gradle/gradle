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

package org.gradle.api.internal.artifacts.transform

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.provider.Provider
import org.gradle.caching.internal.controller.BuildCacheController
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.internal.Try
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.enterprise.core.GradleEnterprisePluginManager
import org.gradle.internal.execution.BuildOutputCleanupRegistry
import org.gradle.internal.execution.InputFingerprinter
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.execution.TestExecutionHistoryStore
import org.gradle.internal.execution.WorkInputListeners
import org.gradle.internal.execution.history.OutputFilesRepository
import org.gradle.internal.execution.history.changes.DefaultExecutionStateChangeDetector
import org.gradle.internal.execution.history.impl.DefaultOverlappingOutputDetector
import org.gradle.internal.execution.impl.DefaultFileCollectionFingerprinterRegistry
import org.gradle.internal.execution.impl.DefaultInputFingerprinter
import org.gradle.internal.execution.impl.DefaultOutputSnapshotter
import org.gradle.internal.execution.impl.FingerprinterRegistration
import org.gradle.internal.execution.model.InputNormalizer
import org.gradle.internal.execution.steps.ValidateStep
import org.gradle.internal.execution.timeout.TimeoutHandler
import org.gradle.internal.fingerprint.DirectorySensitivity
import org.gradle.internal.fingerprint.FileNormalizer
import org.gradle.internal.fingerprint.LineEndingSensitivity
import org.gradle.internal.fingerprint.hashing.FileSystemLocationSnapshotHasher
import org.gradle.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.id.UniqueId
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.scopeids.id.BuildInvocationScopeId
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.ExecutionGradleServices
import org.gradle.internal.snapshot.impl.DefaultValueSnapshotter
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.Path
import org.gradle.work.InputChanges

import java.util.function.BiFunction

class DefaultTransformerInvocationFactoryTest extends AbstractProjectBuilderSpec {
    private DocumentationRegistry documentationRegistry = new DocumentationRegistry()

    def immutableTransformsStoreDirectory = temporaryFolder.file("output")
    def mutableTransformsStoreDirectory = temporaryFolder.file("child/build/transforms")

    def classloaderHasher = Stub(ClassLoaderHierarchyHasher) {
        getClassLoaderHash(_ as ClassLoader) >> TestHashCodes.hashCodeFrom(1234)
    }
    def valueSnapshotter = new DefaultValueSnapshotter([], classloaderHasher)

    def executionHistoryStore = new TestExecutionHistoryStore()
    def virtualFileSystem = TestFiles.virtualFileSystem()
    def fileSystemAccess = TestFiles.fileSystemAccess(virtualFileSystem)
    def fileCollectionSnapshotter = new DefaultFileCollectionSnapshotter(fileSystemAccess, TestFiles.fileSystem())

    def transformationWorkspaceServices = new TestTransformationWorkspaceServices(immutableTransformsStoreDirectory, executionHistoryStore)

    def fileCollectionFactory = TestFiles.fileCollectionFactory()
    def artifactTransformListener = Mock(ArtifactTransformListener)

    def dependencyFingerprinter = new AbsolutePathFileCollectionFingerprinter(DirectorySensitivity.DEFAULT, fileCollectionSnapshotter, FileSystemLocationSnapshotHasher.DEFAULT)
    def fileCollectionFingerprinterRegistry = new DefaultFileCollectionFingerprinterRegistry([FingerprinterRegistration.registration(DirectorySensitivity.DEFAULT, LineEndingSensitivity.DEFAULT, dependencyFingerprinter)])
    def inputFingerprinter = new DefaultInputFingerprinter(fileCollectionSnapshotter, fileCollectionFingerprinterRegistry, valueSnapshotter)

    def projectServiceRegistry = Stub(ServiceRegistry) {
        get(TransformationWorkspaceServices) >> new TestTransformationWorkspaceServices(mutableTransformsStoreDirectory, executionHistoryStore)
    }

    def childProject = Stub(ProjectInternal) {
        getServices() >> projectServiceRegistry
    }

    def projectStateRegistry = Stub(ProjectStateRegistry) {
        stateFor(_ as ProjectComponentIdentifier) >> Stub(ProjectState) {
            getMutableModel() >> childProject
        }
    }

    def dependencies = Stub(ArtifactTransformDependencies) {
        getFiles() >> Optional.empty()
    }

    def buildOperationExecutor = new TestBuildOperationExecutor()

    def buildCacheController = Stub(BuildCacheController)
    def buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate())
    def cancellationToken = new DefaultBuildCancellationToken()
    def outputChangeListener = { affectedOutputPaths -> fileSystemAccess.write(affectedOutputPaths, {}) } as OutputChangeListener
    def outputFilesRepository = Stub(OutputFilesRepository) {
        isGeneratedByGradle(_ as File) >> true
    }
    def workInputListeners = Stub(WorkInputListeners)
    def buildOutputCleanupRegistry = Mock(BuildOutputCleanupRegistry)
    def outputSnapshotter = new DefaultOutputSnapshotter(fileCollectionSnapshotter)
    def deleter = TestFiles.deleter()
    def validationWarningRecorder = Mock(ValidateStep.ValidationWarningRecorder)
    def executionEngine = new ExecutionGradleServices().createExecutionEngine(
        buildCacheController,
        cancellationToken,
        buildInvocationScopeId,
        buildOperationExecutor,
        buildOutputCleanupRegistry,
        new GradleEnterprisePluginManager(),
        classloaderHasher,
        new CurrentBuildOperationRef(),
        deleter,
        new DefaultExecutionStateChangeDetector(),
        outputChangeListener,
        workInputListeners,
        outputFilesRepository,
        outputSnapshotter,
        new DefaultOverlappingOutputDetector(),
        Mock(TimeoutHandler),
        validationWarningRecorder,
        virtualFileSystem,
        documentationRegistry
    )

    def invoker = new DefaultTransformerInvocationFactory(
        executionEngine,
        fileSystemAccess,
        artifactTransformListener,
        transformationWorkspaceServices,
        fileCollectionFactory,
        projectStateRegistry,
        buildOperationExecutor
    )

    private static class TestTransformer implements Transformer {
        private final HashCode secondaryInputsHash
        private final BiFunction<File, File, List<File>> transformationAction

        static TestTransformer create(HashCode secondaryInputsHash = TestHashCodes.hashCodeFrom(1234), BiFunction<File, File, List<File>> transformationAction) {
            return new TestTransformer(secondaryInputsHash, transformationAction)
        }

        TestTransformer(HashCode secondaryInputsHash, BiFunction<File, File, List<File>> transformationAction) {
            this.transformationAction = transformationAction
            this.secondaryInputsHash = secondaryInputsHash
        }

        @Override
        Class<?> getImplementationClass() {
            return TransformAction.class
        }

        @Override
        ImmutableAttributes getFromAttributes() {
            return ImmutableAttributes.EMPTY
        }

        @Override
        ImmutableAttributes getToAttributes() {
            return ImmutableAttributes.EMPTY
        }

        @Override
        boolean requiresDependencies() {
            return false
        }

        @Override
        boolean requiresInputChanges() {
            return false
        }

        @Override
        boolean isCacheable() {
            return false
        }

        @Override
        TransformationResult transform(Provider<FileSystemLocation> inputArtifactProvider, File outputDir, ArtifactTransformDependencies dependencies, InputChanges inputChanges) {
            def builder = TransformationResult.builderFor(inputArtifactProvider.get().asFile, outputDir)
            transformationAction.apply(inputArtifactProvider.get().asFile, outputDir).each {
                builder.addOutput(it) {}
            }
            return builder.build()
        }

        @Override
        HashCode getSecondaryInputHash() {
            return secondaryInputsHash
        }

        @Override
        FileNormalizer getInputArtifactNormalizer() {
            return InputNormalizer.ABSOLUTE_PATH
        }

        @Override
        FileNormalizer getInputArtifactDependenciesNormalizer() {
            return InputNormalizer.ABSOLUTE_PATH
        }

        @Override
        boolean isIsolated() {
            return true
        }

        @Override
        void isolateParametersIfNotAlready() {
        }

        @Override
        String getDisplayName() {
            return "Test transformer"
        }

        @Override
        void visitDependencies(TaskDependencyResolveContext context) {
        }

        @Override
        DirectorySensitivity getInputArtifactDirectorySensitivity() {
            return DirectorySensitivity.DEFAULT
        }

        @Override
        DirectorySensitivity getInputArtifactDependenciesDirectorySensitivity() {
            return DirectorySensitivity.DEFAULT
        }

        @Override
        LineEndingSensitivity getInputArtifactLineEndingNormalization() {
            return LineEndingSensitivity.DEFAULT
        }

        @Override
        LineEndingSensitivity getInputArtifactDependenciesLineEndingNormalization() {
            return LineEndingSensitivity.DEFAULT
        }
    }

    def "executes transformations in workspace (#transformationType)"(TransformationType transformationType) {
        def inputArtifact = temporaryFolder.file("input")
        inputArtifact.text = "my input"
        def transformer = TestTransformer.create { input, outputDir ->
            def outputFile = new File(outputDir, input.name)
            outputFile.text = input.text + "transformed"
            return [outputFile]
        }

        when:
        def result = invoke(transformer, inputArtifact, dependencies, dependency(transformationType, inputArtifact), inputFingerprinter)

        then:
        result.get().size() == 1
        def transformedFile = result.get()[0]
        transformedFile.parentFile.parentFile.parentFile == workspaceDirectory(transformationType)

        where:
        transformationType << TransformationType.values()
    }

    def "up-to-date on second run"() {
        def inputArtifact = temporaryFolder.file("input")
        inputArtifact.text = "my input"
        int transformerInvocations = 0
        def transformer = TestTransformer.create { input, outputDir ->
            transformerInvocations++
            def outputFile = new File(outputDir, input.name)
            outputFile.text = input.text + "transformed"
            return [outputFile]
        }

        when:
        invoke(transformer, inputArtifact, dependencies, immutableDependency(inputArtifact), inputFingerprinter)

        then:
        transformerInvocations == 1
        1 * artifactTransformListener.beforeTransformerInvocation(_, _)
        1 * artifactTransformListener.afterTransformerInvocation(_, _)

        when:
        invoke(transformer, inputArtifact, dependencies, immutableDependency(inputArtifact), inputFingerprinter)
        then:
        transformerInvocations == 1
        0 * _
    }

    def "re-runs transform when previous execution failed"() {
        def inputArtifact = temporaryFolder.file("input")
        inputArtifact.text = "my input"
        def failure = new RuntimeException("broken")
        int transformerInvocations = 0
        def transformer = TestTransformer.create { input, outputDir ->
            transformerInvocations++
            def outputFile = new File(outputDir, input.name)
            assert !outputFile.exists()
            outputFile.text = input.text + "transformed"
            if (transformerInvocations == 1) {
                throw failure
            }
            return [outputFile]
        }

        when:
        def result = invoke(transformer, inputArtifact, dependencies, immutableDependency(inputArtifact), inputFingerprinter)

        then:
        transformerInvocations == 1
        1 * artifactTransformListener.beforeTransformerInvocation(_, _)
        1 * artifactTransformListener.afterTransformerInvocation(_, _)
        def wrappedFailure = result.failure.get()
        wrappedFailure.cause == failure

        when:
        invoke(transformer, inputArtifact, dependencies, immutableDependency(inputArtifact), inputFingerprinter)
        then:
        transformerInvocations == 2
        1 * artifactTransformListener.beforeTransformerInvocation(_, _)
        1 * artifactTransformListener.afterTransformerInvocation(_, _)
    }

    def "re-runs transform when output has been modified"() {
        def inputArtifact = temporaryFolder.file("input")
        inputArtifact.text = "my input"
        File outputFile = null
        int transformerInvocations = 0
        def transformer = TestTransformer.create { input, outputDir ->
            transformerInvocations++
            outputFile = new File(outputDir, input.name)
            assert !outputFile.exists()
            outputFile.text = input.text + " transformed"
            return [outputFile]
        }

        when:
        invoke(transformer, inputArtifact, dependencies, immutableDependency(inputArtifact), inputFingerprinter)
        then:
        transformerInvocations == 1
        outputFile?.isFile()

        when:
        fileSystemAccess.write([outputFile.absolutePath], { -> outputFile.text = "changed" })

        invoke(transformer, inputArtifact, dependencies, immutableDependency(inputArtifact), inputFingerprinter)
        then:
        transformerInvocations == 2
    }

    def "different workspace for different secondary inputs (#transformationType)"(TransformationType transformationType) {
        def inputArtifact = temporaryFolder.file("input")
        inputArtifact.text = "my input"
        def workspaces = new HashSet<File>()
        def transformationAction = { File input, File workspace ->
            workspaces.add(workspace)
            def outputFile = new File(workspace, input.name)
            outputFile.text = input.text + " transformed"
            return ImmutableList.of(outputFile)
        }
        def transformer1 = TestTransformer.create(TestHashCodes.hashCodeFrom(1234), transformationAction)
        def transformer2 = TestTransformer.create(TestHashCodes.hashCodeFrom(4321), transformationAction)

        def subject = dependency(transformationType, inputArtifact)
        when:
        invoke(transformer1, inputArtifact, dependencies, subject, inputFingerprinter)
        invoke(transformer2, inputArtifact, dependencies, subject, inputFingerprinter)

        then:
        workspaces.size() == 2

        where:
        transformationType << TransformationType.values()
    }

    def "different workspace for different input artifact paths (#transformationType)"(TransformationType transformationType) {
        def inputArtifact1 = temporaryFolder.file("input1")
        inputArtifact1.text = "my input"
        def inputArtifact2 = temporaryFolder.file("input2")
        inputArtifact1.text = "my input"
        def workspaces = new HashSet<File>()
        def transformationAction = { File input, File workspace ->
            workspaces.add(workspace)
            def outputFile = new File(workspace, input.name)
            outputFile.text = input.text + " transformed"
            return ImmutableList.of(outputFile)
        }
        def transformer = TestTransformer.create(TestHashCodes.hashCodeFrom(1234), transformationAction)
        when:
        invoke(transformer, inputArtifact1, dependencies, dependency(transformationType, inputArtifact1), inputFingerprinter)
        then:
        workspaces.size() == 1

        when:
        fileSystemAccess.write([inputArtifact1.absolutePath], { -> inputArtifact1.text = "changed" })
        invoke(transformer, inputArtifact2, dependencies, dependency(transformationType, inputArtifact2), inputFingerprinter)

        then:
        workspaces.size() == 2

        where:
        transformationType << TransformationType.values()
    }

    def "different workspace for different immutable input artifacts"() {
        def inputArtifact = temporaryFolder.file("input")
        inputArtifact.text = "my input"
        def workspaces = new HashSet<File>()
        def transformationAction = { File input, File workspace ->
            workspaces.add(workspace)
            def outputFile = new File(workspace, input.name)
            outputFile.text = input.text + " transformed"
            return ImmutableList.of(outputFile)
        }
        def transformer = TestTransformer.create(TestHashCodes.hashCodeFrom(1234), transformationAction)
        def subject = immutableDependency(inputArtifact)

        when:
        invoke(transformer, inputArtifact, dependencies, subject, inputFingerprinter)
        then:
        workspaces.size() == 1

        when:
        fileSystemAccess.write([inputArtifact.absolutePath], { -> inputArtifact.text = "changed" })
        invoke(transformer, inputArtifact, dependencies, subject, inputFingerprinter)

        then:
        workspaces.size() == 2
    }

    def "same workspace for different mutable input artifacts"() {
        def inputArtifact = temporaryFolder.file("input")
        inputArtifact.text = "my input"
        def workspaces = new HashSet<File>()
        def transformationAction = { File input, File workspace ->
            workspaces.add(workspace)
            def outputFile = new File(workspace, input.name)
            outputFile.text = input.text + " transformed"
            return ImmutableList.of(outputFile)
        }
        def transformer = TestTransformer.create(TestHashCodes.hashCodeFrom(1234), transformationAction)
        def subject = mutableDependency(inputArtifact)

        when:
        invoke(transformer, inputArtifact, dependencies, subject, inputFingerprinter)
        then:
        workspaces.size() == 1

        when:
        fileSystemAccess.write([inputArtifact.absolutePath], { -> inputArtifact.text = "changed" })
        invoke(transformer, inputArtifact, dependencies, subject, inputFingerprinter)

        then:
        workspaces.size() == 1
    }

    enum TransformationType {
        MUTABLE, IMMUTABLE
    }

    private dependency(TransformationType type, File file) {
        return type == TransformationType.MUTABLE ? mutableDependency(file) : immutableDependency(file)
    }

    private workspaceDirectory(TransformationType type) {
        return type == TransformationType.MUTABLE ? mutableTransformsStoreDirectory : immutableTransformsStoreDirectory
    }

    private TransformationSubject immutableDependency(File file) {
        return TransformationSubject.initial(artifact(Stub(ComponentArtifactIdentifier), file))
    }

    private TransformationSubject mutableDependency(File file) {
        def artifactIdentifier = new ComponentFileArtifactIdentifier(
            new DefaultProjectComponentIdentifier(
                DefaultBuildIdentifier.ROOT,
                Path.path(":child"),
                Path.path(":child"),
                "child"
            ), file.getName())
        return TransformationSubject.initial(artifact(artifactIdentifier, file))
    }

    private ResolvableArtifact artifact(ComponentArtifactIdentifier id, File file) {
        return Stub(ResolvableArtifact) {
            getId() >> id
        }
    }

    private Try<ImmutableList<File>> invoke(
        Transformer transformer,
        File inputArtifact,
        ArtifactTransformDependencies dependencies,
        TransformationSubject subject,
        InputFingerprinter inputFingerprinter
    ) {
        return invoker.createInvocation(transformer, inputArtifact, dependencies, subject, inputFingerprinter).completeAndGet()
    }
}
