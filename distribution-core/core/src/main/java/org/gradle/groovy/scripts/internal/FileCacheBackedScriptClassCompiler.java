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
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.Pair;
import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.ClasspathEntryVisitor;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.hash.PrimitiveHasher;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.model.dsl.internal.transform.RuleVisitor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.URI;

import static org.gradle.internal.classpath.CachedClasspathTransformer.StandardTransform.BuildLogic;

/**
 * A {@link ScriptClassCompiler} which compiles scripts to a cache directory, and loads them from there.
 */
public class FileCacheBackedScriptClassCompiler implements ScriptClassCompiler, Closeable {
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    private final ScriptCompilationHandler scriptCompilationHandler;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final CacheRepository cacheRepository;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final CachedClasspathTransformer classpathTransformer;

    public FileCacheBackedScriptClassCompiler(CacheRepository cacheRepository, ScriptCompilationHandler scriptCompilationHandler,
                                              ProgressLoggerFactory progressLoggerFactory, ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
                                              CachedClasspathTransformer classpathTransformer) {
        this.cacheRepository = cacheRepository;
        this.scriptCompilationHandler = scriptCompilationHandler;
        this.progressLoggerFactory = progressLoggerFactory;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.classpathTransformer = classpathTransformer;
    }

    @Override
    public <T extends Script, M> CompiledScript<T, M> compile(final ScriptSource source,
                                                              final ClassLoaderScope targetScope,
                                                              final CompileOperation<M> operation,
                                                              final Class<T> scriptBaseClass,
                                                              final Action<? super ClassNode> verifier) {
        assert source.getResource().isContentCached();
        if (source.getResource().getHasEmptyContent()) {
            return emptyCompiledScript(operation);
        }

        ClassLoader classLoader = targetScope.getExportClassLoader();
        HashCode sourceHashCode = source.getResource().getContentHash();
        final String dslId = operation.getId();
        HashCode classLoaderHash = classLoaderHierarchyHasher.getClassLoaderHash(classLoader);
        if (classLoaderHash == null) {
            throw new IllegalArgumentException("Unknown classloader: " + classLoader);
        }
        final RemappingScriptSource remapped = new RemappingScriptSource(source);

        PrimitiveHasher hasher = Hashing.newPrimitiveHasher();
        hasher.putString(dslId);
        hasher.putHash(sourceHashCode);
        hasher.putHash(classLoaderHash);
        String key = HashUtil.compactStringFor(hasher.hash().toByteArray());

        // Caching involves 2 distinct caches, so that 2 scripts with the same (hash, classpath) do not get compiled twice
        // 1. First, we look for a cache script which (path, hash) matches. This cache is invalidated when the compile classpath of the script changes
        // 2. Then we look into the 2d cache for a "generic script" with the same hash, that will be remapped to the script class name
        // Both caches can be closed directly after use because:
        // For 1, if the script changes or its compile classpath changes, a different directory will be used
        // For 2, if the script changes, a different cache is used. If the classpath changes, the cache is invalidated, but classes are remapped to 1. anyway so never directly used
        final PersistentCache cache = cacheRepository.cache("scripts/" + key)
            .withDisplayName(dslId + " generic class cache for " + source.getDisplayName())
            .withInitializer(new ProgressReportingInitializer(
                progressLoggerFactory,
                new CompileToCrossBuildCacheAction(remapped, classLoader, operation, verifier, scriptBaseClass),
                "Compiling " + source.getShortDisplayName()))
            .open();
        try {
            File genericClassesDir = classesDir(cache, operation);
            File metadataDir = metadataDir(cache);
            ClassPath remappedClasses = remapClasses(genericClassesDir, remapped);
            return scriptCompilationHandler.loadFromDir(source, sourceHashCode, targetScope, remappedClasses, metadataDir, operation, scriptBaseClass);
        } finally {
            cache.close();
        }
    }

    private <T extends Script, M> CompiledScript<T, M> emptyCompiledScript(CompileOperation<M> operation) {
        return new EmptyCompiledScript<>(operation);
    }

    private ClassPath remapClasses(File genericClassesDir, RemappingScriptSource source) {
        ScriptSource origin = source.getSource();
        String className = origin.getClassName();
        return classpathTransformer.transform(DefaultClassPath.of(genericClassesDir), BuildLogic, new CachedClasspathTransformer.Transform() {
            @Override
            public void applyConfigurationTo(Hasher hasher) {
                hasher.putString(FileCacheBackedScriptClassCompiler.class.getSimpleName());
                hasher.putInt(1); // transformation version
                hasher.putString(className);
            }

            @Override
            public Pair<RelativePath, ClassVisitor> apply(ClasspathEntryVisitor.Entry entry, ClassVisitor visitor) throws IOException {
                String renamed = entry.getPath().getLastName();
                if (renamed.startsWith(RemappingScriptSource.MAPPED_SCRIPT)) {
                    renamed = className + renamed.substring(RemappingScriptSource.MAPPED_SCRIPT.length());
                }
                byte[] content = entry.getContent();
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

    private File classesDir(PersistentCache cache, CompileOperation<?> operation) {
        return new File(cache.getBaseDir(), operation.getId());
    }

    private File metadataDir(PersistentCache cache) {
        return new File(cache.getBaseDir(), "metadata");
    }

    private class CompileToCrossBuildCacheAction implements Action<PersistentCache> {
        private final Action<? super ClassNode> verifier;
        private final Class<? extends Script> scriptBaseClass;
        private final ClassLoader classLoader;
        private final CompileOperation<?> operation;
        private final ScriptSource source;

        public <T extends Script> CompileToCrossBuildCacheAction(ScriptSource source, ClassLoader classLoader, CompileOperation<?> operation,
                                                                 Action<? super ClassNode> verifier, Class<T> scriptBaseClass) {
            this.source = source;
            this.classLoader = classLoader;
            this.operation = operation;
            this.verifier = verifier;
            this.scriptBaseClass = scriptBaseClass;
        }

        @Override
        public void execute(PersistentCache cache) {
            File classesDir = classesDir(cache, operation);
            File metadataDir = metadataDir(cache);
            scriptCompilationHandler.compileToDir(source, classLoader, classesDir, metadataDir, operation, scriptBaseClass, verifier);
        }
    }

    static class ProgressReportingInitializer implements Action<PersistentCache> {
        private final ProgressLoggerFactory progressLoggerFactory;
        private final Action<? super PersistentCache> delegate;
        private final String shortDescription;

        public ProgressReportingInitializer(ProgressLoggerFactory progressLoggerFactory,
                                            Action<PersistentCache> delegate,
                                            String shortDescription) {
            this.progressLoggerFactory = progressLoggerFactory;
            this.delegate = delegate;
            this.shortDescription = shortDescription;
        }

        @Override
        public void execute(PersistentCache cache) {
            ProgressLogger op = progressLoggerFactory.newOperation(FileCacheBackedScriptClassCompiler.class).start(shortDescription, shortDescription);
            try {
                delegate.execute(cache);
            } finally {
                op.completed();
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
            return name.replaceAll(RemappingScriptSource.MAPPED_SCRIPT, scriptSource.getClassName());
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
