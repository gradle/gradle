/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.api.internal.file.archive.ZipInput;
import org.gradle.api.internal.file.archive.impl.FileZipInput;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.internal.Pair;
import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.internal.classpath.types.GradleCoreInstrumentingTypeRegistry;
import org.gradle.internal.classpath.types.InstrumentingTypeRegistry;
import org.gradle.internal.file.FileException;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.util.internal.GFileUtils;
import org.gradle.util.internal.JarUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.OptionalInt;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static java.lang.String.format;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class InstrumentingClasspathFileTransformer implements ClasspathFileTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentingClasspathFileTransformer.class);
    private static final int CACHE_FORMAT = 6;
    private static final int AGENT_INSTRUMENTATION_VERSION = 2;

    private final FileLockManager fileLockManager;
    private final ClasspathWalker classpathWalker;
    private final ClasspathBuilder classpathBuilder;
    private final CurrentJavaVersionProvider javaVersionProvider;
    private final ClasspathFileHasher fileHasher;
    private final Policy policy;
    private final CachedClasspathTransformer.Transform transform;

    /**
     * Instrumentation policy. There are some differences when instrumenting classes to be loaded by the instrumenting agent, this interface encapsulates them.
     */
    public interface Policy {
        /**
         * Modifies JAR content hash according to the algorithm implemented by this policy.
         *
         * @param hasher the hasher to modify
         */
        void applyConfigurationTo(Hasher hasher);

        /**
         * Returns the transformation to be applied to the given file/directory.
         *
         * @param owner the owner for the returned transformation
         * @param file the file/directory to transform
         * @return the transformation that will transform the file upon request.
         */
        Transformation createTransformer(InstrumentingClasspathFileTransformer owner, File file, InstrumentingTypeRegistry typeRegistry);
    }

    /**
     * A "pending" transformation of the original file/directory.
     */
    private interface Transformation {
        /**
         * Transform the file/directory into destination. The destination should be a JAR file name.
         *
         * @param destination the destination file
         */
        void transform(File destination);
    }

    public InstrumentingClasspathFileTransformer(
        FileLockManager fileLockManager,
        ClasspathWalker classpathWalker,
        ClasspathBuilder classpathBuilder,
        ClasspathFileHasher classpathFileHasher,
        Policy policy,
        CachedClasspathTransformer.Transform transform,
        GradleCoreInstrumentingTypeRegistry gradleCoreInstrumentingTypeRegistry,
        CurrentJavaVersionProvider javaVersionProvider
    ) {
        this.fileLockManager = fileLockManager;
        this.classpathWalker = classpathWalker;
        this.classpathBuilder = classpathBuilder;
        this.javaVersionProvider = javaVersionProvider;

        HashCode configHash = configHashFor(policy, transform, gradleCoreInstrumentingTypeRegistry);
        this.fileHasher = sourceSnapshot -> {
            Hasher hasher = Hashing.defaultFunction().newHasher();
            hasher.putHash(configHash);
            hasher.putHash(classpathFileHasher.hashOf(sourceSnapshot));
            return hasher.hash();
        };

        this.policy = policy;
        this.transform = transform;
    }

    private static HashCode configHashFor(Policy policy, CachedClasspathTransformer.Transform transform, GradleCoreInstrumentingTypeRegistry gradleCoreInstrumentingTypeRegistry) {
        Hasher hasher = Hashing.defaultFunction().newHasher();
        hasher.putInt(CACHE_FORMAT);
        hasher.putInt(AsmConstants.MAX_SUPPORTED_JAVA_VERSION);
        gradleCoreInstrumentingTypeRegistry.getInstrumentedFileHash().ifPresent(hasher::putHash);
        policy.applyConfigurationTo(hasher);
        transform.applyConfigurationTo(hasher);
        return hasher.hash();
    }

    @Override
    public File transform(File source, FileSystemLocationSnapshot sourceSnapshot, File cacheDir, InstrumentingTypeRegistry typeRegistry) {
        String destDirName = hashOf(sourceSnapshot);
        File destDir = new File(cacheDir, destDirName);
        String destFileName = sourceSnapshot.getType() == FileType.Directory ? source.getName() + ".jar" : source.getName();
        File receipt = new File(destDir, destFileName + ".receipt");
        File transformed = new File(destDir, destFileName);

        // Avoid file locking overhead by checking for the receipt first.
        if (receipt.isFile()) {
            return transformed;
        }

        final File lockFile = new File(destDir, destFileName + ".lock");
        final FileLock fileLock = exclusiveLockFor(lockFile);
        try {
            if (receipt.isFile()) {
                // Lock was acquired after a concurrent writer had already finished.
                return transformed;
            }
            transform(source, transformed, typeRegistry);
            try {
                receipt.createNewFile();
            } catch (IOException e) {
                throw new UncheckedIOException(
                    format("Failed to create receipt for instrumented classpath file '%s/%s'.", destDirName, destFileName),
                    e
                );
            }
            return transformed;
        } finally {
            fileLock.close();
        }
    }

    @Override
    public ClasspathFileHasher getFileHasher() {
        return fileHasher;
    }

    private FileLock exclusiveLockFor(File file) {
        return fileLockManager.lock(
            file,
            mode(FileLockManager.LockMode.Exclusive).useCrossVersionImplementation(),
            "instrumented jar cache"
        );
    }

    private String hashOf(FileSystemLocationSnapshot sourceSnapshot) {
        return fileHasher.hashOf(sourceSnapshot).toString();
    }

    private void transform(File source, File dest, InstrumentingTypeRegistry typeRegistry) {
        policy.createTransformer(this, source, typeRegistry).transform(dest);
    }

    /**
     * A no-op transformation that copies the original file verbatim. Can be used if the original cannot be instrumented under policy.
     */
    private static class SkipTransformation implements Transformation {
        private final File source;

        public SkipTransformation(File source) {
            this.source = source;
        }

        @Override
        public void transform(File destination) {
            LOGGER.debug("Archive '{}' rejected by policy. Skipping instrumentation.", source.getName());
            GFileUtils.copyFile(source, destination);
        }
    }

    /**
     * Base class for the transformations. Note that the order in which entries are visited is not defined.
     */
    private class BaseTransformation implements Transformation {
        protected final File source;
        private final InstrumentingTypeRegistry typeRegistry;

        public BaseTransformation(File source, InstrumentingTypeRegistry typeRegistry) {
            this.source = source;
            this.typeRegistry = typeRegistry;
        }

        @Override
        public final void transform(File destination) {
            classpathBuilder.jar(destination, builder -> {
                try {
                    visitEntries(builder);
                } catch (FileException e) {
                    // Badly formed archive, so discard the contents and produce an empty JAR
                    LOGGER.debug("Malformed archive '{}'. Discarding contents.", source.getName(), e);
                }
            });
        }

        private void visitEntries(ClasspathBuilder.EntryBuilder builder) throws IOException, FileException {
            classpathWalker.visit(source, entry -> {
                visitEntry(builder, entry);
            });
            finishProcessing();
        }

        private void visitEntry(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry entry) throws IOException {
            try {
                if (isClassFile(entry)) {
                    processClassFile(builder, entry);
                } else if (isManifest(entry)) {
                    processManifest(builder, entry);
                } else {
                    processResource(builder, entry);
                }
            } catch (Throwable e) {
                throw new IOException("Failed to process the entry '" + entry.getName() + "' from '" + source + "'", e);
            }
        }

        /**
         * Processes a class file. The type of file is determined solely by name, so it may not be a well-formed class file.
         * Base class implementation applies the {@link InstrumentingClasspathFileTransformer#transform} to the code.
         *
         * @param builder the builder for the transformed output
         * @param classEntry the entry to process
         * @throws IOException if reading or writing entry fails
         */
        protected void processClassFile(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry classEntry) throws IOException {
            ClassReader reader = new ClassReader(classEntry.getContent());
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            Pair<RelativePath, ClassVisitor> chain = transform.apply(classEntry, classWriter, new ClassData(reader, typeRegistry));
            reader.accept(chain.right, 0);
            byte[] bytes = classWriter.toByteArray();
            builder.put(chain.left.getPathString(), bytes, classEntry.getCompressionMethod());
        }

        /**
         * Processes a JAR Manifest. Base class implementation copies the manifest unchanged.
         *
         * @param builder the builder for the transformed output
         * @param manifestEntry the entry to process
         * @throws IOException if reading or writing entry fails
         */
        protected void processManifest(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry manifestEntry) throws IOException {
            processResource(builder, manifestEntry);
        }

        /**
         * Processes a resource entry. Base class implementation copies the resource unchanged.
         *
         * @param builder the builder for the transformed output
         * @param resourceEntry the entry to process
         * @throws IOException if reading or writing entry fails
         */
        protected void processResource(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry resourceEntry) throws IOException {
            builder.put(resourceEntry.getName(), resourceEntry.getContent(), resourceEntry.getCompressionMethod());
        }

        /**
         * Processing is complete.
         * @throws IOException if finishing touches encountered I/O problem
         */
        protected void finishProcessing() throws IOException {}

        /**
         * Checks if the entry is supported for instrumentation and loading.
         * Method returns {@code true} if the entry is eligible for instrumentation and {@code false} otherwise.
         * If the entry is loadable on the current JVM, but not eligible for instrumentation, {@link #processLoadableUnsupportedVersionedEntry(int)} method is called.
         * If that method throws, exception is propagated.
         * <p>
         * This method only relies on entry name to extract the target version, i.e. it assumes that the current JAR is a multi-release JAR.
         *
         * @param entry the entry to check
         * @return {@code true} if the entry is eligible for instrumentation and {@code false} otherwise
         * @throws UnsupportedBytecodeVersionException (optional) to abort JAR transformation if the entry is not instrumentable, but loadable on the current JVM
         */
        protected final boolean checkEntryInstrumentable(ClasspathEntryVisitor.Entry entry) {
            OptionalInt version = JarUtil.getVersionedDirectoryMajorVersion(entry.getName());
            if (!version.isPresent() || version.getAsInt() <= AsmConstants.MAX_SUPPORTED_JAVA_VERSION) {
                // Non-versioned entry or for version that can be processed.
                return true;
            }
            if (version.getAsInt() <= javaVersionProvider.getJavaVersion()) {
                // Unsupported entry for the current JVM, this JAR should not be loaded.
                processLoadableUnsupportedVersionedEntry(version.getAsInt());
            }
            // Unsupported entry for a newer JVM, should not be included.
            return false;
        }

        /**
         * Processes the entry in the versioned directory.
         * Implementation may throw UnsupportedBytecodeVersionException to abort the transformation.
         * This method is only called by {@link #checkEntryInstrumentable(ClasspathEntryVisitor.Entry)}.
         *
         * @param entryVersion the version of the entry.
         */
        protected void processLoadableUnsupportedVersionedEntry(int entryVersion) {}

        private boolean isClassFile(ClasspathEntryVisitor.Entry entry) {
            return entry.getName().endsWith(".class");
        }

        private boolean isManifest(ClasspathEntryVisitor.Entry entry) {
            return JarUtil.isManifestName(entry.getName());
        }
    }

    public static Policy instrumentForLoadingWithClassLoader() {
        return new Policy() {
            @Override
            public void applyConfigurationTo(Hasher hasher) {
                // Do nothing, this is compatible with the old instrumentation
            }

            @Override
            public Transformation createTransformer(InstrumentingClasspathFileTransformer owner, File source, InstrumentingTypeRegistry typeRegistry) {
                Boolean isMultiReleaseJar = null;

                if (source.isFile()) {
                    // Walk a file to figure out if it is signed and if it is a multi-release JAR.
                    try (ZipInput entries = FileZipInput.create(source)) {
                        for (ZipEntry entry : entries) {
                            String entryName = entry.getName();
                            if (isJarSignatureFile(entryName)) {
                                // TODO(mlopatkin) Manifest of the signed JAR contains signature information and must be the first entry in the JAR.
                                //  Looking into the manifest here should be more effective.
                                // This policy doesn't transform signed JARs so no further checks are necessary.
                                return new SkipTransformation(source);
                            }
                            if (isMultiReleaseJar == null && JarUtil.isManifestName(entryName)) {
                                isMultiReleaseJar = JarUtil.isMultiReleaseJarManifest(JarUtil.readManifest(entry.getContent()));
                            }
                        }
                    } catch (FileException e) {
                        // Ignore malformed archive, let the transformation handle it.
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
                if (isMultiReleaseJar != null && isMultiReleaseJar) {
                    return owner.new MultiReleaseTransformationForLegacy(source, typeRegistry);
                }
                return owner.new BaseTransformation(source, typeRegistry);
            }

            private boolean isJarSignatureFile(String entryName) {
                return entryName.startsWith("META-INF/") && entryName.endsWith(".SF");
            }

            @Override
            public String toString() {
                return "Policy(legacy)";
            }
        };
    }

    /**
     * Transformation for legacy instrumentation when transformed JARs are part of the classpath.
     * This is still used when TestKit and TAPI run Gradle in embedded or debug mode.
     * <p>
     * This transformation filters out not yet supported versioned directories of the multi-release JARs.
     */
    private class MultiReleaseTransformationForLegacy extends BaseTransformation {
        public MultiReleaseTransformationForLegacy(File source, InstrumentingTypeRegistry typeRegistry) {
            super(source, typeRegistry);
        }

        @Override
        protected void processClassFile(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry classEntry) throws IOException {
            if (checkEntryInstrumentable(classEntry)) {
                super.processClassFile(builder, classEntry);
            }
        }

        @Override
        protected void processResource(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry resourceEntry) throws IOException {
            // Even versioned resources for unsupported versions should not fail the transformation.
            // The processClassFile will abort if there's also classes, but without classes the JAR is fine.
            super.processResource(builder, resourceEntry);
        }

        @Override
        protected void processLoadableUnsupportedVersionedEntry(int entryVersion) {
            throw unsupportedJar(source);
        }
    }

    public static Policy instrumentForLoadingWithAgent() {
        return new Policy() {
            @Override
            public void applyConfigurationTo(Hasher hasher) {
                hasher.putInt(AGENT_INSTRUMENTATION_VERSION);
            }

            @Override
            public Transformation createTransformer(InstrumentingClasspathFileTransformer owner, File file, InstrumentingTypeRegistry typeRegistry) {
                return owner.new TransformationForAgent(file, typeRegistry);
            }

            @Override
            public String toString() {
                return "Policy(agent)";
            }
        };
    }

    /**
     * Transformation for agent-based instrumentation.
     */
    private class TransformationForAgent extends BaseTransformation {
        private boolean isMultiReleaseJar;
        private boolean hasUnsupportedLoadableClasses;

        public TransformationForAgent(File source, InstrumentingTypeRegistry typeRegistry) {
            super(source, typeRegistry);
        }

        @Override
        protected void processClassFile(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry classEntry) throws IOException {
            // We can filter out "unsupported" classes without checking the manifest beforehand.
            // Even if this JAR isn't multi-release per manifest, classes in META-INF/ cannot be loaded, so they are just weird resources.
            // The agent-based instrumentation doesn't load resources from the instrumented JAR.
            if (checkEntryInstrumentable(classEntry)) {
                super.processClassFile(builder, classEntry);
            }
        }

        @Override
        protected void processManifest(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry manifestEntry) throws IOException {
            try {
                Manifest parsedManifest = JarUtil.readManifest(manifestEntry.getContent());
                if (!JarUtil.isMultiReleaseJarManifest(parsedManifest)) {
                    // If the original JAR is not multi-release, we don't need the manifest in the transformed JAR at all.
                    return;
                }
                isMultiReleaseJar = true;

                // We want the transformed JAR to also be a proper multi-release JAR.
                // To do so it must have the "Multi-Release: true" attribute.
                // "Manifest-Version" attribute is also required.
                // For everything else (classpath, sealed, etc.) classloader will check the original JAR, so no need to copy it.
                Manifest processedManifest = new Manifest();
                copyManifestMainAttribute(parsedManifest, processedManifest, Attributes.Name.MANIFEST_VERSION);
                setManifestMainAttribute(processedManifest, JarUtil.MULTI_RELEASE_ATTRIBUTE, "true");

                builder.put(manifestEntry.getName(), toByteArray(processedManifest), manifestEntry.getCompressionMethod());
            } catch (IOException e) {
                LOGGER.debug("Failed to parse Manifest from JAR " + source);
                throw e;
            }
        }

        @Override
        protected void processResource(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry resourceEntry) {
            // Class loader loads resources from the original JAR, so there's no need to put them into the transformed JAR.
            // Even versioned resources for unsupported versions should not fail the transformation.
            // The processClassFile will abort if there's also classes, but without classes the JAR is fine.
        }

        @Override
        protected void processLoadableUnsupportedVersionedEntry(int entryVersion) {
            hasUnsupportedLoadableClasses = true;
        }

        @Override
        protected void finishProcessing() {
            if (isMultiReleaseJar && hasUnsupportedLoadableClasses) {
                throw unsupportedJar(source);
            }
        }

        private void copyManifestMainAttribute(Manifest source, Manifest destination, Attributes.Name name) {
            destination.getMainAttributes().put(name, source.getMainAttributes().getValue(name));
        }

        private void setManifestMainAttribute(Manifest manifest, String name, String value) {
            manifest.getMainAttributes().putValue(name, value);
        }

        private byte[] toByteArray(Manifest manifest) throws IOException {
            ByteArrayOutputStream manifestOutput = new ByteArrayOutputStream(512);
            manifest.write(manifestOutput);
            return manifestOutput.toByteArray();
        }
    }

    private static UnsupportedBytecodeVersionException unsupportedJar(File jar) {
        throw new UnsupportedBytecodeVersionException(
            String.format("Multi-release JAR %s contains versioned directory with class files that this version of Gradle cannot instrument", jar.getAbsolutePath()));
    }
}
