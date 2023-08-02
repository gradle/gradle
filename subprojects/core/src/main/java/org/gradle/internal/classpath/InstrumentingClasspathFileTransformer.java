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

import org.gradle.api.NonNullApi;
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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;
import static org.gradle.internal.classpath.InstrumentingClasspathFileTransformer.MrJarUtils.isInUnsupportedMrJarVersionedDirectory;

public class InstrumentingClasspathFileTransformer implements ClasspathFileTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentingClasspathFileTransformer.class);
    private static final int CACHE_FORMAT = 6;
    private static final int AGENT_INSTRUMENTATION_VERSION = 2;

    // We cannot use Attributes.Name.MULTI_RELEASE as it is only available since Java 9;
    private static final String MULTI_RELEASE_ATTRIBUTE = "Multi-Release";

    // Pattern for JAR entries in the versioned directories.
    // See https://docs.oracle.com/en/java/javase/20/docs/specs/jar/jar.html#multi-release-jar-files
    private static final Pattern VERSIONED_JAR_ENTRY_PATH = Pattern.compile("^META-INF/versions/(\\d+)/.+$");

    private final FileLockManager fileLockManager;
    private final ClasspathWalker classpathWalker;
    private final ClasspathBuilder classpathBuilder;
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
        GradleCoreInstrumentingTypeRegistry gradleCoreInstrumentingTypeRegistry
    ) {
        this.fileLockManager = fileLockManager;
        this.classpathWalker = classpathWalker;
        this.classpathBuilder = classpathBuilder;

        this.fileHasher = createFileHasherWithConfig(
            configHashFor(policy, transform, gradleCoreInstrumentingTypeRegistry),
            classpathFileHasher);
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

    private static ClasspathFileHasher createFileHasherWithConfig(HashCode configHash, ClasspathFileHasher fileHasher) {
        return sourceSnapshot -> {
            Hasher hasher = Hashing.defaultFunction().newHasher();
            hasher.putHash(configHash);
            hasher.putHash(fileHasher.hashOf(sourceSnapshot));
            return hasher.hash();
        };
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

        private boolean isClassFile(ClasspathEntryVisitor.Entry entry) {
            return entry.getName().endsWith(".class");
        }

        private boolean isManifest(ClasspathEntryVisitor.Entry entry) {
            return isManifestName(entry.getName());
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
                            if (isMultiReleaseJar == null && isManifestName(entryName)) {
                                isMultiReleaseJar = isMultiReleaseJarManifest(readManifest(entry.getContent()));
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
            if (!isInUnsupportedMrJarVersionedDirectory(classEntry)) {
                super.processClassFile(builder, classEntry);
            }
        }

        @Override
        protected void processResource(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry resourceEntry) throws IOException {
            // The entries should only be filtered out if we're transforming the proper multi-release JAR.
            // Otherwise, even if the entry path looks like it is inside the versioned directory, it may still be accessed as a
            // resource.
            // Of course, user code can try to access resources inside versioned directories with full paths anyway, but that's
            // a tradeoff we're making.
            if (!isInUnsupportedMrJarVersionedDirectory(resourceEntry)) {
                super.processResource(builder, resourceEntry);
            }
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
        public TransformationForAgent(File source, InstrumentingTypeRegistry typeRegistry) {
            super(source, typeRegistry);
        }

        @Override
        protected void processClassFile(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry classEntry) throws IOException {
            // We can filter out "unsupported" classes without checking the manifest beforehand.
            // Even if this JAR isn't multi-release per manifest, classes in META-INF/ cannot be loaded, so they are just weird resources.
            // The agent-based instrumentation doesn't load resources from the instrumented JAR.
            if (!isInUnsupportedMrJarVersionedDirectory(classEntry)) {
                super.processClassFile(builder, classEntry);
            }
        }

        @Override
        protected void processManifest(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry manifestEntry) throws IOException {
            try {
                Manifest parsedManifest = readManifest(manifestEntry.getContent());
                if (!isMultiReleaseJarManifest(parsedManifest)) {
                    // If the original JAR is not multi-release, we don't need the manifest in the transformed JAR at all.
                    return;
                }

                // We want the transformed JAR to also be a proper multi-release JAR.
                // To do so it must have the "Multi-Release: true" attribute.
                // "Manifest-Version" attribute is also required.
                // For everything else (classpath, sealed, etc.) classloader will check the original JAR, so no need to copy it.
                Manifest processedManifest = new Manifest();
                copyManifestMainAttribute(parsedManifest, processedManifest, Attributes.Name.MANIFEST_VERSION);
                setManifestMainAttribute(processedManifest, MULTI_RELEASE_ATTRIBUTE, "true");

                builder.put(manifestEntry.getName(), toByteArray(processedManifest), manifestEntry.getCompressionMethod());
            } catch (IOException e) {
                LOGGER.debug("Failed to parse Manifest from JAR " + source);
                throw e;
            }
        }

        @Override
        protected void processResource(ClasspathBuilder.EntryBuilder builder, ClasspathEntryVisitor.Entry resourceEntry) {
            // Class loader loads resources from the original JAR, so there's no need to put them into the transformed JAR.
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

    private static boolean isManifestName(String name) {
        return name.equals(JarFile.MANIFEST_NAME);
    }

    private static Manifest readManifest(byte[] content) throws IOException {
        return new Manifest(new ByteArrayInputStream(content));
    }

    private static boolean isMultiReleaseJarManifest(Manifest manifest) {
        return Boolean.parseBoolean(getManifestMainAttribute(manifest, MULTI_RELEASE_ATTRIBUTE));
    }

    @Nullable
    private static String getManifestMainAttribute(Manifest manifest, String name) {
        return manifest.getMainAttributes().getValue(name);
    }

    @NonNullApi
    public static class MrJarUtils {
        /**
         * Checks that the given entry is in the versioned directory of the multi-release JAR and this Java version is not yet supported by the instrumentation.
         * The function doesn't check if the entry is actually in the multi-release JAR.
         *
         * @param entry the entry to check
         * @return {@code true} if the entry is in the versioned directory and the Java version isn't supported
         * @see <a href="https://docs.oracle.com/en/java/javase/20/docs/specs/jar/jar.html#multi-release-jar-files">MR JAR specification</a>
         */
        public static boolean isInUnsupportedMrJarVersionedDirectory(ClasspathEntryVisitor.Entry entry) {
            Matcher match = VERSIONED_JAR_ENTRY_PATH.matcher(entry.getName());
            if (match.matches()) {
                try {
                    int version = Integer.parseInt(match.group(1));
                    return version > AsmConstants.MAX_SUPPORTED_JAVA_VERSION;
                } catch (NumberFormatException ignored) {
                    // Even though the pattern ensures that the version name is all digits, it fails to parse, probably because it is too big.
                    // Technically it may be a valid MR JAR for Java >Integer.MAX_VALUE, but we are too far away from this.
                    // We assume that JAR author didn't intend it to be a versioned directory and keep it.
                }
            }

            // The entry is not in the versioned directory at all.
            return false;
        }
    }
}
