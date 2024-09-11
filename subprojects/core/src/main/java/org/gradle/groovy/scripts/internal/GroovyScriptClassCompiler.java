/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.groovy.scripts.internal;

import groovy.lang.Script;
import org.codehaus.groovy.ast.ClassNode;
import org.gradle.api.Action;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.internal.GroovyScriptClassCompiler.GroovyScriptCompilationAndInstrumentation.GroovyScriptCompilationOutput;
import org.gradle.internal.Pair;
import org.gradle.model.internal.asm.AsmConstants;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassData;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.ClasspathEntryVisitor;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.classpath.transforms.ClassTransform;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForLegacy;
import org.gradle.internal.classpath.types.GradleCoreInstrumentationTypeRegistry;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.caching.CachingDisabledReason;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.instrumentation.reporting.PropertyUpgradeReportConfig;
import org.gradle.internal.scripts.BuildScriptCompilationAndInstrumentation;
import org.gradle.model.dsl.internal.transform.RuleVisitor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.net.URI;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link ScriptClassCompiler} which compiles scripts to a cache directory, and loads them from there.
 */
public class GroovyScriptClassCompiler implements ScriptClassCompiler, Closeable {
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    private static final String CLASSPATH_PROPERTY_NAME = "classpath";
    private static final String TEMPLATE_ID_PROPERTY_NAME = "templateId";
    private static final String SOURCE_HASH_PROPERTY_NAME = "sourceHash";
    private final ScriptCompilationHandler scriptCompilationHandler;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final CachedClasspathTransformer classpathTransformer;
    private final ExecutionEngine earlyExecutionEngine;
    private final FileCollectionFactory fileCollectionFactory;
    private final InputFingerprinter inputFingerprinter;
    private final ImmutableWorkspaceProvider workspaceProvider;
    private final ClasspathElementTransformFactoryForLegacy transformFactoryForLegacy;
    private final GradleCoreInstrumentationTypeRegistry gradleCoreTypeRegistry;
    private final PropertyUpgradeReportConfig propertyUpgradeReportConfig;

    public GroovyScriptClassCompiler(
        ScriptCompilationHandler scriptCompilationHandler,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        CachedClasspathTransformer classpathTransformer,
        ExecutionEngine earlyExecutionEngine,
        FileCollectionFactory fileCollectionFactory,
        InputFingerprinter inputFingerprinter,
        ImmutableWorkspaceProvider workspaceProvider,
        ClasspathElementTransformFactoryForLegacy transformFactoryForLegacy,
        GradleCoreInstrumentationTypeRegistry gradleCoreTypeRegistry,
        PropertyUpgradeReportConfig propertyUpgradeReportConfig
    ) {
        this.scriptCompilationHandler = scriptCompilationHandler;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.classpathTransformer = classpathTransformer;
        this.earlyExecutionEngine = earlyExecutionEngine;
        this.fileCollectionFactory = fileCollectionFactory;
        this.inputFingerprinter = inputFingerprinter;
        this.workspaceProvider = workspaceProvider;
        this.transformFactoryForLegacy = transformFactoryForLegacy;
        this.gradleCoreTypeRegistry = gradleCoreTypeRegistry;
        this.propertyUpgradeReportConfig = propertyUpgradeReportConfig;
    }

    @Override
    public <T extends Script, M> CompiledScript<T, M> compile(
        final ScriptSource source, final Class<T> scriptBaseClass, final Object target,
        final ClassLoaderScope targetScope,
        final CompileOperation<M> operation,
        final Action<? super ClassNode> verifier
    ) {
        assert source.getResource().isContentCached();
        if (source.getResource().getHasEmptyContent()) {
            return new EmptyCompiledScript<>(operation);
        }

        String templateId = operation.getId();
        // TODO: Figure if execution engine should calculate the source hash on its own
        HashCode sourceHashCode = source.getResource().getContentHash();
        RemappingScriptSource remapped = new RemappingScriptSource(source);
        ClassLoader classLoader = targetScope.getExportClassLoader();
        GroovyScriptCompilationOutput output = doCompile(target, templateId, sourceHashCode, remapped, classLoader, operation, verifier, scriptBaseClass);

        File instrumentedOutput = output.getInstrumentedOutput();
        File metadataDir = output.getMetadataDir();
        // TODO: Remove the remapping or move remapping to an uncached unit of work?
        ClassPath remappedClasses = remapClasses(instrumentedOutput, remapped);
        return scriptCompilationHandler.loadFromDir(source, sourceHashCode, targetScope, remappedClasses, metadataDir, operation, scriptBaseClass);
    }

    private <T extends Script> GroovyScriptCompilationOutput doCompile(
        Object target,
        String templateId,
        HashCode sourceHashCode,
        RemappingScriptSource remappedSource,
        ClassLoader classLoader,
        CompileOperation<?> operation,
        Action<? super ClassNode> verifier,
        Class<T> scriptBaseClass
    ) {
        UnitOfWork unitOfWork = new GroovyScriptCompilationAndInstrumentation(
            templateId,
            sourceHashCode,
            classLoader,
            remappedSource,
            operation,
            verifier,
            scriptBaseClass,
            classLoaderHierarchyHasher,
            workspaceProvider,
            fileCollectionFactory,
            inputFingerprinter,
            transformFactoryForLegacy,
            scriptCompilationHandler,
            gradleCoreTypeRegistry,
            propertyUpgradeReportConfig
        );
        return getExecutionEngine(target)
            .createRequest(unitOfWork)
            .execute()
            .getOutputAs(GroovyScriptCompilationOutput.class)
            .get();
    }

    /**
     * We want to use build cache for script compilation, but build cache might not be available yet with early execution engine.
     * Thus settings and init scripts are not using build cache for now.
     * <p>
     * When we compile project build scripts, build cache is available, but we need to query execution engine with build cache support
     * from the project services directly to use it.
     * <p>
     * TODO: Remove this and just inject execution engine once we unify execution engines in https://github.com/gradle/gradle/issues/27249
     */
    private ExecutionEngine getExecutionEngine(Object target) {
        if (target instanceof ProjectInternal) {
            return ((ProjectInternal) target).getServices().get(ExecutionEngine.class);
        }
        return earlyExecutionEngine;
    }

    private ClassPath remapClasses(File genericClassesDir, RemappingScriptSource source) {
        ScriptSource origin = source.getSource();
        String className = origin.getClassName();
        return classpathTransformer.transform(DefaultClassPath.of(genericClassesDir), new ClassTransform() {
            @Override
            public void applyConfigurationTo(Hasher hasher) {
                hasher.putString(GroovyScriptClassCompiler.class.getSimpleName());
                hasher.putInt(1); // transformation version
                hasher.putString(className);
            }

            @Override
            public Pair<RelativePath, ClassVisitor> apply(ClasspathEntryVisitor.Entry entry, ClassVisitor visitor, ClassData classData) {
                String renamed = entry.getPath().getLastName();
                if (renamed.startsWith(RemappingScriptSource.MAPPED_SCRIPT)) {
                    renamed = className + renamed.substring(RemappingScriptSource.MAPPED_SCRIPT.length());
                }
                byte[] content = classData.getClassContent();
                ClassReader cr = new ClassReader(content);
                String originalClassName = cr.getClassName();
                String contentHash = Hashing.hashBytes(content).toString();
                BuildScriptRemapper remapper = new BuildScriptRemapper(visitor, origin, originalClassName, contentHash);
                return Pair.of(entry.getPath().getParent().append(true, renamed), remapper);
            }
        });
    }

    @Override
    public void close() {
    }

    static class GroovyScriptCompilationAndInstrumentation extends BuildScriptCompilationAndInstrumentation {

        private final String templateId;
        private final HashCode sourceHashCode;
        private final ClassLoader classLoader;
        private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
        private final RemappingScriptSource source;
        private final CompileOperation<?> operation;
        private final Class<? extends Script> scriptBaseClass;
        private final ScriptCompilationHandler scriptCompilationHandler;
        private final Action<? super ClassNode> verifier;

        public GroovyScriptCompilationAndInstrumentation(
            String templateId,
            HashCode sourceHashCode,
            ClassLoader classLoader,
            RemappingScriptSource remappedSource,
            CompileOperation<?> operation,
            Action<? super ClassNode> verifier,
            Class<? extends Script> scriptBaseClass,
            ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
            ImmutableWorkspaceProvider workspaceProvider,
            FileCollectionFactory fileCollectionFactory,
            InputFingerprinter inputFingerprinter,
            ClasspathElementTransformFactoryForLegacy transformFactoryForLegacy,
            ScriptCompilationHandler scriptCompilationHandler,
            GradleCoreInstrumentationTypeRegistry gradleCoreTypeRegistry,
            PropertyUpgradeReportConfig propertyUpgradeReportConfig
        ) {
            super(remappedSource.getSource(), workspaceProvider, fileCollectionFactory, inputFingerprinter, transformFactoryForLegacy, gradleCoreTypeRegistry, propertyUpgradeReportConfig);
            this.templateId = templateId;
            this.sourceHashCode = sourceHashCode;
            this.classLoader = classLoader;
            this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
            this.source = remappedSource;
            this.operation = operation;
            this.verifier = verifier;
            this.scriptBaseClass = scriptBaseClass;
            this.scriptCompilationHandler = scriptCompilationHandler;
        }

        @Override
        public Optional<CachingDisabledReason> shouldDisableCaching(@Nullable OverlappingOutputs detectedOverlappingOutputs) {
            // Disabled since enabling it introduced negative savings to Groovy script compilation.
            // It's not disabled for Kotlin since Kotlin has better compile avoidance, additionally
            // Kotlin has build cache from the beginning and there was no report of a problem with it.
            return Optional.of(NOT_WORTH_CACHING);
        }

        @Override
        public void visitIdentityInputs(InputVisitor visitor) {
            super.visitIdentityInputs(visitor);
            visitor.visitInputProperty(TEMPLATE_ID_PROPERTY_NAME, () -> templateId);
            visitor.visitInputProperty(SOURCE_HASH_PROPERTY_NAME, () -> sourceHashCode);
            visitor.visitInputProperty(CLASSPATH_PROPERTY_NAME, () -> classLoaderHierarchyHasher.getClassLoaderHash(classLoader));
        }

        @Override
        public void visitOutputs(File workspace, OutputVisitor visitor) {
            super.visitOutputs(workspace, visitor);
            File metadataDir = metadataDir(workspace);
            OutputFileValueSupplier metadataDirValue = OutputFileValueSupplier.fromStatic(metadataDir, fileCollectionFactory.fixed(metadataDir));
            visitor.visitOutputProperty("metadataDir", TreeType.DIRECTORY, metadataDirValue);
        }

        @Override
        public Object loadAlreadyProducedOutput(File workspace) {
            Output output = (Output) super.loadAlreadyProducedOutput(workspace);
            File instrumentedJar = checkNotNull(output).getInstrumentedOutput();
            File metadataDir = metadataDir(workspace);
            return new GroovyScriptCompilationOutput(instrumentedJar, metadataDir);
        }

        @Override
        public File compile(File workspace) {
            File classesDir = classesDir(workspace);
            scriptCompilationHandler.compileToDir(source, classLoader, classesDir, metadataDir(workspace), operation, scriptBaseClass, verifier);
            return classesDir;
        }

        @Override
        public File instrumentedOutput(File workspace) {
            return new File(workspace, "instrumented/" + operation.getId());
        }

        private File classesDir(File workspace) {
            return new File(workspace, "classes/" + operation.getId());
        }

        private static File metadataDir(File workspace) {
            return new File(workspace, "metadata");
        }

        @Override
        public String getDisplayName() {
            return "Groovy DSL script compilation (" + templateId + ")";
        }

        static class GroovyScriptCompilationOutput {

            private final File instrumentedOutput;
            private final File metadataDir;

            public GroovyScriptCompilationOutput(File instrumentedOutput, File metadataDir) {
                this.instrumentedOutput = instrumentedOutput;
                this.metadataDir = metadataDir;
            }

            public File getInstrumentedOutput() {
                return instrumentedOutput;
            }

            public File getMetadataDir() {
                return metadataDir;
            }
        }
    }

    private static class EmptyCompiledScript<T extends Script, M> implements CompiledScript<T, M> {
        private final M data;

        public EmptyCompiledScript(CompileOperation<M> operation) {
            this.data = operation.getExtractedData();
        }

        @Override
        public boolean getRunDoesSomething() {
            return false;
        }

        @Override
        public boolean getHasMethods() {
            return false;
        }

        @Override
        public void onReuse() {
            // Ignore
        }

        @Override
        public Class<? extends T> loadClass() {
            throw new UnsupportedOperationException("Cannot load a script that does nothing.");
        }

        @Override
        public M getData() {
            return data;
        }
    }

    private static class BuildScriptRemapper extends ClassVisitor implements Opcodes {
        private static final String SCRIPT_ORIGIN = "org/gradle/internal/scripts/ScriptOrigin";
        private final ScriptSource scriptSource;
        private final String originalClassName;
        private final String contentHash;

        public BuildScriptRemapper(ClassVisitor cv, ScriptSource source, String originalClassName, String contentHash) {
            super(AsmConstants.ASM_LEVEL, cv);
            this.scriptSource = source;
            this.originalClassName = originalClassName;
            this.contentHash = contentHash;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            String owner = remap(name);
            boolean shouldAddScriptOrigin = shouldAddScriptOrigin(access);
            cv.visit(version, access, owner, remap(signature), remap(superName), remapAndAddInterfaces(interfaces, shouldAddScriptOrigin));
            if (shouldAddScriptOrigin) {
                addOriginalClassName(cv, owner, originalClassName);
                addContentHash(cv, owner, contentHash);
            }
        }

        private static boolean shouldAddScriptOrigin(int access) {
            return ((access & ACC_INTERFACE) == 0) && ((access & ACC_ANNOTATION) == 0);
        }

        private static void addOriginalClassName(ClassVisitor cv, String owner, String originalClassName) {
            cv.visitField(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC | ACC_FINAL, "__originalClassName", Type.getDescriptor(String.class), "", originalClassName);
            MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "getOriginalClassName", Type.getMethodDescriptor(Type.getType(String.class)), null, null);
            mv.visitCode();
            mv.visitFieldInsn(GETSTATIC, owner, "__originalClassName", Type.getDescriptor(String.class));
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        private static void addContentHash(ClassVisitor cv, String owner, String contentHash) {
            cv.visitField(ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC | ACC_FINAL, "__signature", Type.getDescriptor(String.class), "", contentHash);
            MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "getContentHash", Type.getMethodDescriptor(Type.getType(String.class)), null, null);
            mv.visitCode();
            mv.visitFieldInsn(GETSTATIC, owner, "__signature", Type.getDescriptor(String.class));
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        @Override
        public void visitSource(String source, String debug) {
            cv.visitSource(scriptSource.getFileName(), debug);
        }

        private String[] remapAndAddInterfaces(String[] interfaces, boolean shouldAddScriptOrigin) {
            if (!shouldAddScriptOrigin) {
                return remap(interfaces);
            }
            if (interfaces == null) {
                return new String[]{SCRIPT_ORIGIN};
            }
            String[] remapped = new String[interfaces.length + 1];
            for (int i = 0; i < interfaces.length; i++) {
                remapped[i] = remap(interfaces[i]);
            }
            remapped[remapped.length - 1] = SCRIPT_ORIGIN;
            return remapped;
        }

        private String[] remap(String[] names) {
            if (names == null) {
                return null;
            }
            String[] remapped = new String[names.length];
            for (int i = 0; i < names.length; i++) {
                remapped[i] = remap(names[i]);
            }
            return remapped;
        }

        private String remap(String name) {
            if (name == null) {
                return null;
            }
            if (RuleVisitor.SOURCE_URI_TOKEN.equals(name)) {
                URI uri = scriptSource.getResource().getLocation().getURI();
                return uri == null ? null : uri.toString();
            }
            if (RuleVisitor.SOURCE_DESC_TOKEN.equals(name)) {
                return scriptSource.getDisplayName();
            }
            return name.replace(RemappingScriptSource.MAPPED_SCRIPT, scriptSource.getClassName());
        }

        private Object remap(Object o) {
            if (o instanceof Type) {
                return Type.getType(remap(((Type) o).getDescriptor()));
            }
            if (o instanceof String) {
                return remap((String) o);
            }
            return o;
        }

        private Object[] remap(int count, Object[] original) {
            if (count == 0) {
                return EMPTY_OBJECT_ARRAY;
            }
            Object[] remapped = new Object[count];
            for (int idx = 0; idx < count; idx++) {
                remapped[idx] = remap(original[idx]);
            }
            return remapped;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor mv = cv.visitMethod(access, name, remap(desc), remap(signature), remap(exceptions));
            if (mv != null && (access & ACC_ABSTRACT) == 0) {
                mv = new MethodRenamer(mv);
            }
            return mv;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
            return super.visitField(access, name, remap(desc), remap(signature), remap(value));
        }

        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            super.visitInnerClass(remap(name), remap(outerName), remap(innerName), access);
        }

        @Override
        public void visitOuterClass(String owner, String name, String desc) {
            super.visitOuterClass(remap(owner), remap(name), remap(desc));
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            return super.visitAnnotation(remap(desc), visible);
        }

        class MethodRenamer extends MethodVisitor {

            public MethodRenamer(final MethodVisitor mv) {
                super(AsmConstants.ASM_LEVEL, mv);
            }

            @Override
            public void visitTypeInsn(int i, String name) {
                mv.visitTypeInsn(i, remap(name));
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                mv.visitFieldInsn(opcode, remap(owner), name, remap(desc));
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean intf) {
                mv.visitMethodInsn(opcode, remap(owner), name, remap(desc), intf);
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                for (int i = 0; i < bootstrapMethodArguments.length; i++) {
                    bootstrapMethodArguments[i] = remapIfHandle(bootstrapMethodArguments[i]);
                }
                mv.visitInvokeDynamicInsn(remap(name), remap(descriptor), remapHandle(bootstrapMethodHandle), bootstrapMethodArguments);
            }

            private Object remapIfHandle(Object bootstrapArgument) {
                if (bootstrapArgument instanceof Handle) {
                    Handle handle = (Handle) bootstrapArgument;
                    return remapHandle(handle);
                }
                return bootstrapArgument;
            }

            private Handle remapHandle(Handle handle) {
                return new Handle(handle.getTag(),
                    remap(handle.getOwner()),
                    handle.getName(),
                    remap(handle.getDesc()),
                    handle.isInterface()
                );
            }

            @Override
            public void visitLdcInsn(Object cst) {
                super.visitLdcInsn(remap(cst));
            }

            @Override
            public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
                super.visitLocalVariable(name, remap(desc), remap(signature), start, end, index);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                return super.visitAnnotation(remap(desc), visible);
            }

            @Override
            public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
                super.visitFrame(type, nLocal, remap(nLocal, local), nStack, remap(nStack, stack));
            }
        }
    }
}
