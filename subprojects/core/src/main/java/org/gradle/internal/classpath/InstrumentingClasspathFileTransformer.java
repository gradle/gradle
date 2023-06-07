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
import java.util.jar.Manifest;

import static java.lang.String.format;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

class InstrumentingClasspathFileTransformer implements ClasspathFileTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentingClasspathFileTransformer.class);
    private static final int CACHE_FORMAT = 5;
    private static final int AGENT_INSTRUMENTATION_VERSION = 2;

    // We cannot use Attributes.Name.MULTI_RELEASE as it is only available since Java 9;
    private static final String MULTI_RELEASE_ATTRIBUTE = "Multi-Release";

    private final FileLockManager fileLockManager;
    private final ClasspathWalker classpathWalker;
    private final ClasspathBuilder classpathBuilder;
    private final Policy policy;
    private final CachedClasspathTransformer.Transform transform;
    private final HashCode configHash;

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
         * Checks if the file/directory should be instrumented. For example, legacy instrumentation doesn't support signed JARs.
         *
         * @param file the file/directory to check
         * @return {@code true} if the file/directory should be instrumented.
         */
        boolean instrumentFile(File file);

        /**
         * Processes manifest of the instrumented JAR file. The implementation may return the manifest unprocessed.
         * If the return value is {@code null}, then the manifest should not be added to the resulting instrumented JAR at all.
         *
         * @param source the file/directory that contributes the manifest
         * @param entry the entry of the manifest
         * @return the contents of the manifest to put, or {@code null} if the resulting JAR should have no manifest at all
         * @throws IOException if reading and parsing the manifest fail
         */
        @Nullable
        byte[] processManifest(File source, ClasspathEntryVisitor.Entry entry) throws IOException;

        /**
         * Checks if the entry should be included into the resulting instrumented JAR
         *
         * @param entry the entry to check
         * @return {@code true} if the entry should be included.
         */
        boolean includeEntry(ClasspathEntryVisitor.Entry entry);
    }

    public InstrumentingClasspathFileTransformer(
        FileLockManager fileLockManager,
        ClasspathWalker classpathWalker,
        ClasspathBuilder classpathBuilder,
        Policy policy,
        CachedClasspathTransformer.Transform transform
    ) {
        this.fileLockManager = fileLockManager;
        this.classpathWalker = classpathWalker;
        this.classpathBuilder = classpathBuilder;
        this.policy = policy;
        this.transform = transform;
        this.configHash = configHashFor(transform);
    }

    private HashCode configHashFor(CachedClasspathTransformer.Transform transform) {
        Hasher hasher = Hashing.defaultFunction().newHasher();
        hasher.putInt(CACHE_FORMAT);
        policy.applyConfigurationTo(hasher);
        transform.applyConfigurationTo(hasher);
        return hasher.hash();
    }

    @Override
    public File transform(File source, FileSystemLocationSnapshot sourceSnapshot, File cacheDir) {
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
            transform(source, transformed);
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

    private FileLock exclusiveLockFor(File file) {
        return fileLockManager.lock(
            file,
            mode(FileLockManager.LockMode.Exclusive).useCrossVersionImplementation(),
            "instrumented jar cache"
        );
    }

    private String hashOf(FileSystemLocationSnapshot sourceSnapshot) {
        Hasher hasher = Hashing.defaultFunction().newHasher();
        hasher.putHash(configHash);
        // TODO - apply runtime classpath normalization?
        hasher.putHash(sourceSnapshot.getHash());
        return hasher.hash().toString();
    }

    private void transform(File source, File dest) {
        if (policy.instrumentFile(source)) {
            instrument(source, dest);
        } else {
            LOGGER.debug("Signed archive '{}'. Skipping instrumentation.", source.getName());
            GFileUtils.copyFile(source, dest);
        }
    }

    private void instrument(File source, File dest) {
        classpathBuilder.jar(dest, builder -> {
            try {
                visitEntries(source, builder);
            } catch (FileException e) {
                // Badly formed archive, so discard the contents and produce an empty JAR
                LOGGER.debug("Malformed archive '{}'. Discarding contents.", source.getName(), e);
            }
        });
    }

    private void visitEntries(File source, ClasspathBuilder.EntryBuilder builder) throws IOException, FileException {
        classpathWalker.visit(source, entry -> {
            try {
                if (!policy.includeEntry(entry)) {
                    return;
                }
                if (isClassFile(entry)) {
                    ClassReader reader = new ClassReader(entry.getContent());
                    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    Pair<RelativePath, ClassVisitor> chain = transform.apply(entry, classWriter, new ClassData(reader));
                    reader.accept(chain.right, 0);
                    byte[] bytes = classWriter.toByteArray();
                    builder.put(chain.left.getPathString(), bytes, entry.getCompressionMethod());
                } else if (isManifest(entry)) {
                    byte[] processedManifest = policy.processManifest(source, entry);
                    if (processedManifest != null) {
                        builder.put(entry.getName(), processedManifest, entry.getCompressionMethod());
                    }
                } else {
                    builder.put(entry.getName(), entry.getContent(), entry.getCompressionMethod());
                }
            } catch (Throwable e) {
                throw new IOException("Failed to process the entry '" + entry.getName() + "' from '" + source + "'", e);
            }
        });
    }

    private static boolean isSignedJar(File source) {
        if (!source.isFile()) {
            return false;
        }
        try (ZipInput entries = FileZipInput.create(source)) {
            for (ZipEntry entry : entries) {
                String entryName = entry.getName();
                if (entryName.startsWith("META-INF/") && entryName.endsWith(".SF")) {
                    return true;
                }
            }
        } catch (FileException e) {
            // Ignore malformed archive
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return false;
    }

    public static Policy instrumentForLoadingWithClassLoader() {
        return new Policy() {
            @Override
            public void applyConfigurationTo(Hasher hasher) {
                // Do nothing, this is compatible with the old instrumentation
            }

            @Override
            public boolean instrumentFile(File file) {
                return !isSignedJar(file);
            }

            @Override
            public byte[] processManifest(File source, ClasspathEntryVisitor.Entry entry) throws IOException {
                // No need to modify manifest, let's put the original as is.
                return entry.getContent();
            }

            @Override
            public boolean includeEntry(ClasspathEntryVisitor.Entry entry) {
                // include everything
                return true;
            }
        };
    }

    public static Policy instrumentForLoadingWithAgent() {
        return new Policy() {
            @Override
            public void applyConfigurationTo(Hasher hasher) {
                hasher.putInt(AGENT_INSTRUMENTATION_VERSION);
            }

            @Override
            public boolean instrumentFile(File file) {
                return true;
            }

            @Override
            public byte[] processManifest(File source, ClasspathEntryVisitor.Entry entry) throws IOException {
                try {
                    Manifest parsedManifest = new Manifest(new ByteArrayInputStream(entry.getContent()));
                    if (isMultiReleaseJarManifest(parsedManifest)) {
                        // We want processed JAR to also be a proper multi-release JAR.
                        // To do so it must have the "Multi-Release: true" attribute.
                        // "Manifest-Version" attribute is also required.
                        // For everything else (classpath, sealed, etc.) classloader will check the original JAR, so no need to copy it.
                        Manifest processedManifest = new Manifest();
                        copyManifestMainAttribute(parsedManifest, processedManifest, Attributes.Name.MANIFEST_VERSION);
                        setManifestMainAttribute(processedManifest, MULTI_RELEASE_ATTRIBUTE, "true");
                        return toByteArray(processedManifest);
                    }

                    return null;
                } catch (IOException e) {
                    LOGGER.debug("Failed to parse Manifest from JAR " + source);
                    throw e;
                }
            }

            @Nullable
            private String getManifestMainAttribute(Manifest manifest, String name) {
                return manifest.getMainAttributes().getValue(name);
            }

            private void copyManifestMainAttribute(Manifest source, Manifest destination, Attributes.Name name) {
                destination.getMainAttributes().put(name, source.getMainAttributes().getValue(name));
            }

            private void setManifestMainAttribute(Manifest manifest, String name, String value) {
                manifest.getMainAttributes().putValue(name, value);
            }

            private boolean isMultiReleaseJarManifest(Manifest manifest) {
                return Boolean.parseBoolean(getManifestMainAttribute(manifest, MULTI_RELEASE_ATTRIBUTE));
            }

            private byte[] toByteArray(Manifest manifest) throws IOException {
                ByteArrayOutputStream manifestOutput = new ByteArrayOutputStream(512);
                manifest.write(manifestOutput);
                return manifestOutput.toByteArray();
            }

            @Override
            public boolean includeEntry(ClasspathEntryVisitor.Entry entry) {
                // Only include classes in the result, resources will be loaded from the original JAR by the class loader.
                return isClassFile(entry) || isManifest(entry);
            }
        };
    }

    private static boolean isClassFile(ClasspathEntryVisitor.Entry entry) {
        return entry.getName().endsWith(".class");
    }

    private static boolean isManifest(ClasspathEntryVisitor.Entry entry) {
        return entry.getName().equals("META-INF/MANIFEST.MF");
    }
}
