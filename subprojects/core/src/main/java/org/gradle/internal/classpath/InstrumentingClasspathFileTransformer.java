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
import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.api.internal.file.archive.ZipInput;
import org.gradle.api.internal.file.archive.impl.FileZipInput;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.internal.classpath.transforms.BaseJarTransform;
import org.gradle.internal.classpath.transforms.ClassTransform;
import org.gradle.internal.classpath.transforms.JarTransform;
import org.gradle.internal.classpath.transforms.MultiReleaseJarTransformForLegacy;
import org.gradle.internal.classpath.transforms.JarTransformForAgent;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static java.lang.String.format;
import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class InstrumentingClasspathFileTransformer implements ClasspathFileTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentingClasspathFileTransformer.class);
    private static final int CACHE_FORMAT = 6;
    private static final int AGENT_INSTRUMENTATION_VERSION = 3;

    private final FileLockManager fileLockManager;
    private final ClasspathWalker classpathWalker;
    private final ClasspathBuilder classpathBuilder;
    private final ClasspathFileHasher fileHasher;
    private final Policy policy;
    private final ClassTransform transform;

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
        JarTransform createTransformer(InstrumentingClasspathFileTransformer owner, File file, InstrumentingTypeRegistry typeRegistry);
    }

    public InstrumentingClasspathFileTransformer(
        FileLockManager fileLockManager,
        ClasspathWalker classpathWalker,
        ClasspathBuilder classpathBuilder,
        ClasspathFileHasher classpathFileHasher,
        Policy policy,
        ClassTransform transform,
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

    private static HashCode configHashFor(Policy policy, ClassTransform transform, GradleCoreInstrumentingTypeRegistry gradleCoreInstrumentingTypeRegistry) {
        Hasher hasher = Hashing.defaultFunction().newHasher();
        hasher.putInt(CACHE_FORMAT);
        hasher.putInt(AsmConstants.MAX_SUPPORTED_JAVA_VERSION);
        gradleCoreInstrumentingTypeRegistry.getInstrumentedTypesHash().ifPresent(hasher::putHash);
        gradleCoreInstrumentingTypeRegistry.getUpgradedPropertiesHash().ifPresent(hasher::putHash);
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
    private static class SkipJarTransform implements JarTransform {
        private final File source;

        public SkipJarTransform(File source) {
            this.source = source;
        }

        @Override
        public void transform(File destination) {
            LOGGER.debug("Archive '{}' rejected by policy. Skipping instrumentation.", source.getName());
            GFileUtils.copyFile(source, destination);
        }
    }

    public static Policy instrumentForLoadingWithClassLoader() {
        return new Policy() {
            @Override
            public void applyConfigurationTo(Hasher hasher) {
                // Do nothing, this is compatible with the old instrumentation
            }

            @Override
            public JarTransform createTransformer(InstrumentingClasspathFileTransformer owner, File source, InstrumentingTypeRegistry typeRegistry) {
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
                                return new SkipJarTransform(source);
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
                    return new MultiReleaseJarTransformForLegacy(source, owner.classpathBuilder, owner.classpathWalker, typeRegistry, owner.transform);
                }
                return new BaseJarTransform(source, owner.classpathBuilder, owner.classpathWalker, typeRegistry, owner.transform);
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

    public static Policy instrumentForLoadingWithAgent() {
        return new Policy() {
            @Override
            public void applyConfigurationTo(Hasher hasher) {
                hasher.putInt(AGENT_INSTRUMENTATION_VERSION);
            }

            @Override
            public JarTransform createTransformer(InstrumentingClasspathFileTransformer owner, File file, InstrumentingTypeRegistry typeRegistry) {
                return new JarTransformForAgent(file, owner.classpathBuilder, owner.classpathWalker, typeRegistry, owner.transform);
            }

            @Override
            public String toString() {
                return "Policy(agent)";
            }
        };
    }

    public static boolean isSupportedVersion(int javaMajorVersion) {
        return javaMajorVersion <= AsmConstants.MAX_SUPPORTED_JAVA_VERSION;
    }
}
