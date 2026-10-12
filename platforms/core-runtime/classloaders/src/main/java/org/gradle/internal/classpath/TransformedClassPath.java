/*
 * Copyright 2022 the original author or authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.specs.Spec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A special ClassPath that keeps track of the transformed "doubles" of the original classpath entries (JARs and class directories).
 * An entry can only appear once in the class path, regardless of having a transformed "double".
 * <p>
 * Operations on this ClassPath ({@code plus}, {@code removeIf}) keep the transforms for retained entries.
 * However, it is possible to override the entry when combining class paths, when both the receiver and the argument of {@code plus} contain the same entry.
 * As the class path is typically searched left-to-right when looking for a class, the entry (and its transformed "double" if present) from the receiver wins.
 * <p>
 * The class loaders constructed from this classpath can replace classes from the original classpath entries with transformed ones (from "double") when loading.
 */
@NullMarked
public class TransformedClassPath implements ClassPath {
    public enum FileMarker {
        /**
         * A marker file put next to the instrumentation entry to indicate that this is part of instrumentation.
         * This is currently used just to not delete the folders for performance testing.
         */
        INSTRUMENTATION_CLASSPATH_MARKER(".gradle-instrumented-classpath.marker"),
        /**
         * A marker file put next to the instrumented entry of an external dependency to indicate that it is using agent instrumentation.
         * The marker is followed by the instrumented entry, the dependency analysis file and the original entry.
         */
        AGENT_INSTRUMENTATION_EXTERNAL_MARKER(".gradle-agent-instrumented-external.marker"),
        /**
         * A marker file put next to the instrumented entry of a project dependency to indicate that it is using agent instrumentation.
         * The marker is followed by the instrumented entry and the original entry.
         */
        AGENT_INSTRUMENTATION_PROJECT_MARKER(".gradle-agent-instrumented-project.marker"),
        /**
         * A marker file put next to the instrumented entry to indicate that it is using legacy instrumentation.
         */
        LEGACY_INSTRUMENTATION_MARKER(".gradle-legacy-instrumented.marker"),
        /**
         * A marker file put next to the instrumented entry to indicate that original file doesn't exist so there was no instrumentation.
         */
        ORIGINAL_FILE_DOES_NOT_EXIST_MARKER(".original-file-does-not-exist.marker"),
        UNKNOWN_FILE_MARKER("");

        private static final Map<String, FileMarker> FILE_MARKER_MAP;
        static {
            ImmutableMap.Builder<String, FileMarker> builder = ImmutableMap.builder();
            for (FileMarker fileMarker : values()) {
                builder.put(fileMarker.fileName, fileMarker);
            }
            FILE_MARKER_MAP = builder.build();
        }

        private final String fileName;

        FileMarker(String fileName) {
            this.fileName = fileName;
        }

        public String getFileName() {
            return fileName;
        }

        public static FileMarker of(String fileName) {
            FileMarker marker = FILE_MARKER_MAP.get(fileName);
            return marker == null ? UNKNOWN_FILE_MARKER : marker;
        }
    }

    public static final String INSTRUMENTED_DIR_NAME = "instrumented";
    public static final String ORIGINAL_DIR_NAME = "original";
    public static final String INSTRUMENTED_ENTRY_PREFIX = "instrumented-";
    /**
     * The name of the per-artifact dependency analysis file produced by the instrumentation pipeline.
     */
    public static final String DEPENDENCY_ANALYSIS_FILE_NAME = "instrumentation-dependencies.bin";

    /**
     * The instrumentation pipeline that produced a transformed entry.
     * Determines how classes of the entry are re-instrumented at class load time when Gradle instrumentation
     * is composed with a third-party agent instead of substituting the pre-instrumented "double".
     */
    public enum InstrumentationKind {
        /**
         * The external dependency pipeline: the entry is instrumented and has property upgrades applied,
         * using the type hierarchy recorded in the dependency analysis file.
         */
        EXTERNAL_DEPENDENCY,
        /**
         * The project dependency pipeline: the entry is instrumented only.
         */
        PROJECT_DEPENDENCY,
        /**
         * The provenance of the entry is not recorded. Such entries are not re-instrumented at class load time.
         */
        UNKNOWN
    }

    /**
     * The transformed "double" of an original classpath entry, together with the data needed to re-instrument
     * the original entry at class load time.
     */
    public static final class TransformedEntry {
        private final File instrumentedFile;
        @Nullable
        private final File analysisFile;
        private final InstrumentationKind kind;

        public TransformedEntry(File instrumentedFile, @Nullable File analysisFile, InstrumentationKind kind) {
            this.instrumentedFile = instrumentedFile;
            this.analysisFile = analysisFile;
            this.kind = kind;
        }

        /**
         * The pre-instrumented "double" of the original entry.
         */
        public File getInstrumentedFile() {
            return instrumentedFile;
        }

        /**
         * The per-artifact dependency analysis file, or {@code null} if the producing pipeline does not use one.
         */
        @Nullable
        public File getAnalysisFile() {
            return analysisFile;
        }

        /**
         * The instrumentation pipeline that produced this entry.
         */
        public InstrumentationKind getKind() {
            return kind;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            TransformedEntry other = (TransformedEntry) obj;
            return instrumentedFile.equals(other.instrumentedFile)
                && Objects.equals(analysisFile, other.analysisFile)
                && kind == other.kind;
        }

        @Override
        public int hashCode() {
            return Objects.hash(instrumentedFile, analysisFile, kind);
        }

        @Override
        public String toString() {
            return instrumentedFile + " (" + kind + (analysisFile != null ? ", " + analysisFile : "") + ")";
        }
    }

    private final ClassPath originalClassPath;
    // mapping of original -> "double"
    private final ImmutableMap<File, TransformedEntry> transforms;
    @Nullable
    private final ClassLoadTimeTransform classLoadTimeTransform;

    private TransformedClassPath(ClassPath originalClassPath, Map<File, TransformedEntry> transforms, @Nullable ClassLoadTimeTransform classLoadTimeTransform) {
        assert !(originalClassPath instanceof TransformedClassPath);
        this.originalClassPath = originalClassPath;
        this.transforms = ImmutableMap.copyOf(transforms);
        this.classLoadTimeTransform = classLoadTimeTransform;
    }

    /**
     * Returns the class-load-time transform associated with this classpath, or {@code null} if classes loaded from
     * this classpath should be substituted with the cached pre-rewritten "doubles" instead.
     */
    @Nullable
    public ClassLoadTimeTransform getClassLoadTimeTransform() {
        return classLoadTimeTransform;
    }

    /**
     * Returns a copy of this classpath that carries the given class-load-time transform.
     * Classes loaded from this classpath will be transformed at class load by composing
     * with any third-party {@link java.lang.instrument.ClassFileTransformer} registered before
     * Gradle's transformer.
     * <p>
     * The transform is derived data: it can be rebuilt from the {@link TransformedEntry transformed entries}
     * of this classpath and the agent status of the current JVM. It does not participate in equality and
     * is not persisted; it is only attached on the way from a producer of the classpath to a classloader.
     */
    public TransformedClassPath withClassLoadTimeTransform(ClassLoadTimeTransform classLoadTimeTransform) {
        // TODO(mlopatkin): The TransformedClassPath is mostly to have an opaque transport between the classpath constructor,
        //  where we know it must be instrumented, and the ClassLoaderFactory, where we actually build the classloader.
        //  With the single ClassPath passed down we don't have to additionally plumb the instrumentation information.
        //  However, with this runtime transform, the scheme becomes a leaky abstraction.
        return new TransformedClassPath(originalClassPath, transforms, classLoadTimeTransform);
    }

    @Override
    public boolean isEmpty() {
        return originalClassPath.isEmpty();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method returns the list of original JAR/class directory URIs.
     */
    @Override
    public List<URI> getAsURIs() {
        return originalClassPath.getAsURIs();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method returns the list of original JARs/class directories.
     */
    @Override
    public List<File> getAsFiles() {
        return originalClassPath.getAsFiles();
    }

    /**
     * Returns the list of JARs/class directories that this class path consists of, but original entries are replaced with corresponding transformed "doubles".
     * The entries that have no "doubles" are returned as is.
     * <p>
     * This is the view of the classpath a classloader observes when the "doubles" are substituted at class load.
     * It is currently only used by tests, but is kept as part of the classpath API on purpose.
     *
     * @return the list of JARs/class directories
     */
    public List<File> getAsTransformedFiles() {
        List<File> originals = new ArrayList<File>(originalClassPath.getAsFiles());
        ListIterator<File> iter = originals.listIterator();
        while (iter.hasNext()) {
            File original = iter.next();
            TransformedEntry entry = transforms.get(original);
            if (entry != null) {
                iter.set(entry.getInstrumentedFile());
            }
        }
        return originals;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method returns the list of original JAR/class directory URLs.
     */
    @Override
    public List<URL> getAsURLs() {
        return originalClassPath.getAsURLs();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method returns the array of original JAR/class directory URLs.
     */
    @Override
    public URL[] getAsURLArray() {
        return originalClassPath.getAsURLArray();
    }

    /**
     * Prepends the given classPath to this classpath.
     * No transformations are applied to the prepended class path.
     * <p>
     * This is a helper to implement DefaultClassPath.plus(TransformedClassPath) efficiently.
     *
     * @param classPath the classPath to prepend to this classpath
     * @return the new transformed classpath
     */
    TransformedClassPath prepend(DefaultClassPath classPath) {
        // If some entries from this classpath are also in the prepended classpath, then the prepended ones win.
        // Existing transforms for these entries have to be discarded.
        // We can think of the prepended classpath as the TransformedClassPath without actual transforms,
        // and then just append this classpath to it to achieve the desired behavior.
        return new TransformedClassPath(classPath, ImmutableMap.of(), null).plusWithTransforms(this);
    }

    private TransformedClassPath plusWithTransforms(TransformedClassPath classPath) {
        ClassPath mergedOriginals = originalClassPath.plus(classPath.originalClassPath);

        // Merge transformations, keeping in mind that classpath is searched left-to-right.
        ImmutableMap.Builder<File, TransformedEntry> mergedTransforms = ImmutableMap.builderWithExpectedSize(transforms.size() + classPath.transforms.size());
        Set<File> thisClassPathFiles = ImmutableSet.copyOf(originalClassPath.getAsFiles());
        mergedTransforms.putAll(transforms);
        for (Map.Entry<File, TransformedEntry> appendedTransform : classPath.transforms.entrySet()) {
            // If the file is already present on this classpath, it keeps its transform (or lack thereof).
            if (!thisClassPathFiles.contains(appendedTransform.getKey())) {
                mergedTransforms.put(appendedTransform);
            }
        }

        // The class-load-time transform is a per-origin snapshot of the classpath it was composed for, so it
        // cannot represent the merge of two composed classpaths - classes of the other classpath would load
        // without Gradle instrumentation. Fail loudly instead of losing instrumentation silently; the merged
        // classpath has to be composed anew by the caller in that case.
        checkArgument(
            classLoadTimeTransform == null || classPath.classLoadTimeTransform == null,
            "Cannot merge two classpaths that both carry a class-load-time transform; compose the merged classpath instead."
        );
        // Keeping the only transform is correct for the entries it was composed from; entries of the other
        // classpath are unknown to it and load untouched, like any entry without a transformed "double".
        ClassLoadTimeTransform mergedClassLoadTimeTransform = classLoadTimeTransform != null ? classLoadTimeTransform : classPath.classLoadTimeTransform;

        // In the end, at most one instance of a transformed entry should be recorded for any given file.
        return new TransformedClassPath(mergedOriginals, mergedTransforms.buildOrThrow(), mergedClassLoadTimeTransform);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The appended classpath is not transformed.
     */
    @Override
    public TransformedClassPath plus(Collection<File> classPath) {
        return new TransformedClassPath(originalClassPath.plus(classPath), transforms, classLoadTimeTransform);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The appended classpath is not additionally transformed.
     */
    @Override
    public TransformedClassPath plus(ClassPath classPath) {
        if (classPath instanceof TransformedClassPath) {
            return plusWithTransforms((TransformedClassPath) classPath);
        }
        return new TransformedClassPath(originalClassPath.plus(classPath), transforms, classLoadTimeTransform);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The predicate is applied to the original classpath entries. The returned classpath keeps corresponding transformations.
     */
    @Override
    public TransformedClassPath removeIf(Spec<? super File> filter) {
        ClassPath filteredClassPath = originalClassPath.removeIf(filter);
        Set<File> remainingOriginals = ImmutableSet.copyOf(filteredClassPath.getAsFiles());
        ImmutableMap.Builder<File, TransformedEntry> remainingTransforms = ImmutableMap.builderWithExpectedSize(Math.min(remainingOriginals.size(), transforms.size()));
        for (Map.Entry<File, TransformedEntry> remainingEntry : transforms.entrySet()) {
            if (remainingOriginals.contains(remainingEntry.getKey())) {
                remainingTransforms.put(remainingEntry);
            }
        }
        return new TransformedClassPath(filteredClassPath, remainingTransforms.build(), classLoadTimeTransform);
    }

    /**
     * Looks up the transformed file corresponding to the given classpath entry (JAR/classes directory), if it is available. Otherwise, returns {@code null}.
     *
     * @param originalClassPathEntry the original classpath entry
     * @return the transformed file for the entry or {@code null} if there is none
     */
    @Nullable
    public File findTransformedEntryFor(File originalClassPathEntry) {
        TransformedEntry entry = transforms.get(originalClassPathEntry);
        return entry != null ? entry.getInstrumentedFile() : null;
    }

    /**
     * Looks up the transformed entry, including its instrumentation metadata, for the given classpath entry (JAR/classes directory),
     * if it is available. Otherwise, returns {@code null}.
     *
     * @param originalClassPathEntry the original classpath entry
     * @return the transformed entry or {@code null} if there is none
     */
    @Nullable
    public TransformedEntry findEntryFor(File originalClassPathEntry) {
        return transforms.get(originalClassPathEntry);
    }

    // The class-load-time transform is deliberately excluded from equals/hashCode:
    // it is derived data, reconstructible from the transformed entries and the JVM-constant agent status,
    // and is only attached transiently on the way to a classloader.
    @Override
    public int hashCode() {
        return originalClassPath.hashCode() + transforms.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        TransformedClassPath other = (TransformedClassPath) obj;
        return originalClassPath.equals(other.originalClassPath)
            && transforms.equals(other.transforms);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (File original : originalClassPath.getAsFiles()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(original);
            File transformed = findTransformedEntryFor(original);
            if (transformed != null) {
                builder.append("->").append(transformed);
            }
            first = false;
        }
        builder.append("]");
        return builder.toString();
    }

    /**
     * Constructs a TransformedClassPath out of the ordinary JAR/directory list, strict produced by the instrumenting ArtifactTransform.
     *
     * Artifact transform classpath should always contain groups of files, where the first file is a marker file, followed by other entries for the instrumented artifact,
     * e.g. [marker file 1, instrumented entry 1, original entry 1, marker file 2, instrumented entry 2, original entry 2,...].
     *
     * Marker files rules are as follows:
     * <ul>
     *      <li>{@link FileMarker#AGENT_INSTRUMENTATION_EXTERNAL_MARKER} is followed by the instrumented entry, the dependency analysis file and the original entry.</li>
     *      <li>{@link FileMarker#AGENT_INSTRUMENTATION_PROJECT_MARKER} is followed by the instrumented entry and the original entry.</li>
     *      <li>An entry after {@link FileMarker#LEGACY_INSTRUMENTATION_MARKER} is instrumented entry without original entry, but it's added to a classpath as "original", to comply with legacy setup.</li>
     *      <li>An entry after {@link FileMarker#ORIGINAL_FILE_DOES_NOT_EXIST_MARKER} is a marker that indicates there is no original/instrumented entry, it's skipped</li>
     *      <li>An entry after {@link FileMarker#INSTRUMENTATION_CLASSPATH_MARKER} is a marker that indicates that this is an instrumented classpath, it's skipped</li>
     * </ul>
     *
     * Any combination that doesn't follow given rules will throw an exception.
     *
     * @param classPath the classpath to process
     * @return the classpath that carries the instrumentation mappings if needed
     */
    public static ClassPath handleInstrumentingArtifactTransform(List<File> classPath) {
        if (classPath.isEmpty()) {
            return DefaultClassPath.of(classPath);
        }

        return fromInstrumentingArtifactTransformOutput(classPath);
    }

    private static ClassPath fromInstrumentingArtifactTransformOutput(List<File> inputFiles) {
        // An empty value means the entry is used for classloading directly, without a transformed "double".
        Map<File, Optional<TransformedEntry>> transformedEntries = Maps.newLinkedHashMapWithExpectedSize(inputFiles.size());
        for (int i = 0; i < inputFiles.size();) {
            File markerFile = inputFiles.get(i++);
            FileMarker fileMarker = FileMarker.of(markerFile.getName());
            switch (fileMarker) {
                case AGENT_INSTRUMENTATION_EXTERNAL_MARKER: {
                    // External dependency agent instrumentation always contains 4 entries:
                    // [a marker, a transformed file, a dependency analysis file, an original file or a copy of it]
                    checkArgument(i + 2 < inputFiles.size(), "Missing the instrumented, analysis or original entry for classpath %s", inputFiles);
                    File instrumentedEntry = inputFiles.get(i++);
                    File analysisEntry = inputFiles.get(i++);
                    File originalEntry = inputFiles.get(i++);
                    checkArgument(
                        analysisEntry.getName().equals(DEPENDENCY_ANALYSIS_FILE_NAME),
                        "Expected the dependency analysis file after the instrumented entry %s, but got %s",
                        instrumentedEntry.getAbsolutePath(),
                        analysisEntry.getAbsolutePath()
                    );
                    checkArgument(
                        areInstrumentedAndOriginalEntriesValid(instrumentedEntry, originalEntry),
                        "Instrumented entry %s doesn't match original entry %s",
                        instrumentedEntry.getAbsolutePath(),
                        originalEntry.getAbsolutePath()
                    );
                    transformedEntries.putIfAbsent(
                        originalEntry,
                        Optional.of(new TransformedEntry(instrumentedEntry, analysisEntry, InstrumentationKind.EXTERNAL_DEPENDENCY))
                    );
                    break;
                }
                case AGENT_INSTRUMENTATION_PROJECT_MARKER: {
                    // Project dependency agent instrumentation always contains 3 entries:
                    // [a marker, a transformed file, an original file or a copy of it]
                    checkArgument(i + 1 < inputFiles.size(), "Missing the instrumented or original entry for classpath %s", inputFiles);
                    File instrumentedEntry = inputFiles.get(i++);
                    File originalEntry = inputFiles.get(i++);
                    checkArgument(
                        areInstrumentedAndOriginalEntriesValid(instrumentedEntry, originalEntry),
                        "Instrumented entry %s doesn't match original entry %s",
                        instrumentedEntry.getAbsolutePath(),
                        originalEntry.getAbsolutePath()
                    );
                    transformedEntries.putIfAbsent(
                        originalEntry,
                        Optional.of(new TransformedEntry(instrumentedEntry, null, InstrumentationKind.PROJECT_DEPENDENCY))
                    );
                    break;
                }
                case LEGACY_INSTRUMENTATION_MARKER:
                    // Legacy instrumentation always contain 2 entries:
                    // [a marker, a transformed file]
                    checkArgument(i < inputFiles.size(), "Missing the instrumented entry for classpath %s", inputFiles);
                    File legacyInstrumentedEntry = inputFiles.get(i++);
                    transformedEntries.putIfAbsent(legacyInstrumentedEntry, Optional.empty());
                    break;
                case INSTRUMENTATION_CLASSPATH_MARKER:
                case ORIGINAL_FILE_DOES_NOT_EXIST_MARKER:
                    // Ignore these markers
                    break;
                case UNKNOWN_FILE_MARKER:
                default:
                    throw new IllegalArgumentException("Unexpected marker file: " + markerFile + " in instrumented buildscript classpath. " +
                        "Possible reason: Injecting custom artifact transform in between instrumentation stages is not supported.");
            }
        }
        Builder result = builderWithExactSize(transformedEntries.size());
        for (Map.Entry<File, Optional<TransformedEntry>> entry : transformedEntries.entrySet()) {
            if (entry.getValue().isPresent()) {
                result.add(entry.getKey(), entry.getValue().get());
            } else {
                result.addUntransformed(entry.getKey());
            }
        }
        return result.build();
    }

    private static boolean areInstrumentedAndOriginalEntriesValid(File instrumentedEntry, File originalEntry) {
        return instrumentedEntry.getParentFile() != null
            && instrumentedEntry.getParentFile().getName().equals(INSTRUMENTED_DIR_NAME)
            && !originalEntry.equals(instrumentedEntry)
            && instrumentedEntry.getName().equals(INSTRUMENTED_ENTRY_PREFIX + originalEntry.getName());
    }

    /**
     * Creates a builder for the classpath with {@code size} original entries.
     *
     * @param size the number of the original entries in the classpath
     * @return the builder
     */
    public static Builder builderWithExactSize(int size) {
        return new Builder(size);
    }

    /**
     * Constructs a transformed classpath.
     */
    public static class Builder {
        private final DefaultClassPath.Builder originals;
        private final ImmutableMap.Builder<File, TransformedEntry> transforms;

        private Builder(int exactSize) {
            originals = DefaultClassPath.builderWithExactSize(exactSize);
            transforms = ImmutableMap.builderWithExpectedSize(exactSize);
        }

        /**
         * Adds the classpath entry with the corresponding transformed entry.
         *
         * @param original the original JAR or classes directory
         * @param entry the transformed entry
         * @return this builder
         */
        public Builder add(File original, TransformedEntry entry) {
            originals.add(original);
            transforms.put(original, entry);
            return this;
        }

        /**
         * Adds the classpath entry without the transformed entry.
         * This entry will be used for classloading directly.
         *
         * @param original the original JAR or classes directory
         * @return this builder
         */
        public Builder addUntransformed(File original) {
            originals.add(original);
            return this;
        }

        /**
         * Constructs the TransformedClassPath instance.
         *
         * @return the new classpath instance
         */
        public TransformedClassPath build() {
            Map<File, TransformedEntry> transformedMap = transforms.build();
            return new TransformedClassPath(originals.build(), transformedMap, null);
        }
    }
}
