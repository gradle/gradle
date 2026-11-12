/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.problems.internal.services;

import org.gradle.api.problems.AdditionalData;
import org.gradle.api.problems.OffsetInFileLocation;
import org.gradle.api.problems.internal.DetectionContext;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.api.problems.internal.TypeValidationData;
import org.gradle.api.problems.internal.location.PropertyFrame;
import org.gradle.api.problems.internal.StackTraceLocation;
import org.gradle.api.problems.ProblemLocation;
import org.gradle.api.problems.FileLocation;
import org.gradle.api.problems.LineInFileLocation;
import org.gradle.api.problems.internal.location.CompiledCodeFrame;
import org.gradle.api.problems.internal.location.CompiledCodePosition;
import com.google.common.collect.ImmutableList;
import org.gradle.api.problems.internal.TaskIdentity;
import org.gradle.api.problems.internal.location.ClassFrame;
import org.gradle.api.problems.internal.location.FileLocationFrame;
import org.gradle.api.problems.internal.location.FilePosition;
import org.gradle.api.problems.internal.location.PluginClassFrame;
import org.gradle.api.problems.internal.location.TaskFrame;
import org.gradle.api.problems.internal.location.Frame;
import org.gradle.api.problems.internal.location.Trace;
import org.gradle.internal.code.Locality;
import org.gradle.internal.code.UserCodeSource;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.util.List;

/**
 * Completes the origin traces of a problem with what only the runtime knows.
 */
final class OriginTraces {

    private OriginTraces() {
    }

    /**
     * The origin traces of a problem whose reporter gave none: one per file location, with that location as its leaf,
     * and one for the property and type of a validation problem. The flat origin list is still what reporters fill in,
     * so the traces are derived from it. Localities and lineage are added afterwards.
     */
    static List<Trace> derivedFrom(ProblemInternal problem) {
        ImmutableList.Builder<Trace> traces = ImmutableList.builder();
        for (ProblemLocation location : problem.getOriginLocations()) {
            Frame leaf = originLeafOf(location);
            if (leaf != null) {
                traces.add(new Trace(ImmutableList.of(leaf)));
            }
        }
        AdditionalData additionalData = problem.getAdditionalData();
        if (additionalData instanceof TypeValidationData) {
            TypeValidationData data = (TypeValidationData) additionalData;
            ImmutableList.Builder<Frame> frames = ImmutableList.builder();
            if (data.getPropertyName() != null) {
                String name = data.getParentPropertyName() == null ? data.getPropertyName() : data.getParentPropertyName() + "." + data.getPropertyName();
                frames.add(new PropertyFrame(name, Locality.UNKNOWN));
            }
            if (data.getTypeName() != null) {
                frames.add(new ClassFrame(data.getTypeName(), Locality.UNKNOWN));
            }
            ImmutableList<Frame> validated = frames.build();
            if (!validated.isEmpty()) {
                traces.add(new Trace(validated));
            }
        }
        return traces.build();
    }

    private static @Nullable Frame originLeafOf(ProblemLocation location) {
        if (location instanceof StackTraceLocation) {
            return stackLeaf((StackTraceLocation) location);
        }
        if (location instanceof FileLocation) {
            return new FileLocationFrame(positionOf((FileLocation) location), Locality.UNKNOWN);
        }
        return null;
    }

    /**
     * The leaf a captured stack gives. Without a named API it is the script line Gradle located.
     * With one, it is the caller of that API: the script line when the caller is a script, or else the compiled code's file and line from its debug information.
     */
    private static @Nullable Frame stackLeaf(StackTraceLocation location) {
        FileLocation scriptLocation = location.getFileLocation();
        Frame scriptLeaf = scriptLocation == null ? null : new FileLocationFrame(positionOf(scriptLocation), Locality.UNKNOWN);
        String callerApi = location.getCallerApi();
        if (callerApi == null) {
            return scriptLeaf;
        }
        StackTraceElement caller = CallerFrames.firstOutside(location.getStackTrace(), callerApi);
        if (caller == null || isScriptLine(scriptLocation, caller)) {
            return scriptLeaf;
        }
        return new CompiledCodeFrame(new CompiledCodePosition(caller.getFileName(), caller.getClassName(), null, caller.getLineNumber()), Locality.UNKNOWN);
    }

    /**
     * Gives each file location frame its locality: a file inside the root directory of the build tree is the user's own; any other file,
     * such as one in a cache or in the Gradle user home, stays unknown. Frames that already have a locality are left alone.
     */
    static List<Trace> withFileLocalities(List<Trace> originTraces, File buildTreeRootDirectory) {
        ImmutableList.Builder<Trace> result = ImmutableList.builder();
        for (Trace trace : originTraces) {
            ImmutableList.Builder<Frame> frames = ImmutableList.builder();
            for (Frame frame : trace.getFrames()) {
                if (frame instanceof FileLocationFrame && frame.getLocality() == Locality.UNKNOWN) {
                    FileLocationFrame fileFrame = (FileLocationFrame) frame;
                    frames.add(isInside(buildTreeRootDirectory, new File(fileFrame.getPosition().getPath())) ? fileFrame.withLocality(Locality.LOCAL) : fileFrame);
                } else {
                    frames.add(frame);
                }
            }
            result.add(new Trace(frames.build()));
        }
        return result.build();
    }

    /**
     * Picks a better leaf for a trace that ends in compiled code. The reporter took the first caller outside Gradle and its own API,
     * which can be a line in a library that a script or a plugin uses. Going down the stack from there, the nearest known user code becomes the leaf:
     * the script line Gradle located, or the first frame whose class comes from the jar of a plugin the detection names, with that plugin's locality.
     * Those plugins are the one being applied or the one that registered the running task, and the code that applied them.
     * Without either, the leaf the reporter chose stays.
     */
    static List<Trace> withUserCodeCallers(List<Trace> originTraces, List<ProblemLocation> originLocations, DetectionContext detection, ClassFileLookup classFiles) {
        List<UserCodeSource.Binary> plugins = pluginsWithCodeLocation(detection);
        ImmutableList.Builder<Trace> result = ImmutableList.builder();
        boolean changed = false;
        for (Trace trace : originTraces) {
            Frame leaf = trace.getFrames().get(0);
            Frame better = leaf instanceof CompiledCodeFrame ? userCodeCallerOf((CompiledCodeFrame) leaf, originLocations, plugins, classFiles) : null;
            if (better == null) {
                result.add(trace);
            } else {
                changed = true;
                result.add(new Trace(ImmutableList.<Frame>builder().add(better).addAll(trace.getFrames().subList(1, trace.getFrames().size())).build()));
            }
        }
        return changed ? result.build() : originTraces;
    }

    private static @Nullable Frame userCodeCallerOf(CompiledCodeFrame leaf, List<ProblemLocation> originLocations, List<UserCodeSource.Binary> plugins, ClassFileLookup classFiles) {
        for (ProblemLocation location : originLocations) {
            if (!(location instanceof StackTraceLocation)) {
                continue;
            }
            StackTraceLocation stackLocation = (StackTraceLocation) location;
            List<StackTraceElement> stack = stackLocation.getStackTrace();
            int leafIndex = indexOf(leaf.getPosition(), stack);
            if (leafIndex < 0) {
                continue;
            }
            FileLocation scriptLocation = stackLocation.getFileLocation();
            for (StackTraceElement element : stack.subList(leafIndex, stack.size())) {
                if (isScriptLine(scriptLocation, element)) {
                    // Whether the script is the build's own is decided with the other file leaves
                    return new FileLocationFrame(positionOf(scriptLocation), Locality.UNKNOWN);
                }
                if (element.getFileName() == null || element.getLineNumber() <= 0) {
                    continue;
                }
                for (UserCodeSource.Binary plugin : plugins) {
                    if (classFiles.contains(plugin.getCodeLocation(), element.getClassName())) {
                        return new CompiledCodeFrame(new CompiledCodePosition(element.getFileName(), element.getClassName(), null, element.getLineNumber()), plugin.getLocality());
                    }
                }
            }
            return null;
        }
        return null;
    }

    // Line and column count from 1 and offset from 0; the flat locations use -1 for a missing value, the position uses null (spec Q12)
    private static FilePosition positionOf(FileLocation location) {
        if (location instanceof LineInFileLocation) {
            LineInFileLocation line = (LineInFileLocation) location;
            return new FilePosition(line.getPath(), valueOrNull(line.getLine()), valueOrNull(line.getColumn()), null, valueOrNull(line.getLength()));
        }
        if (location instanceof OffsetInFileLocation) {
            OffsetInFileLocation offset = (OffsetInFileLocation) location;
            return new FilePosition(offset.getPath(), null, null, valueOrNull(offset.getOffset()), valueOrNull(offset.getLength()));
        }
        return new FilePosition(location.getPath(), null, null, null, null);
    }

    private static @Nullable Integer valueOrNull(int value) {
        return value < 0 ? null : value;
    }

    private static int indexOf(CompiledCodePosition position, List<StackTraceElement> stack) {
        for (int i = 0; i < stack.size(); i++) {
            StackTraceElement element = stack.get(i);
            if (element.getClassName().equals(position.getClassName()) && Integer.valueOf(element.getLineNumber()).equals(position.getLine())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isScriptLine(@Nullable FileLocation scriptLocation, StackTraceElement element) {
        if (!(scriptLocation instanceof LineInFileLocation) || element.getFileName() == null) {
            return false;
        }
        String path = scriptLocation.getPath();
        String fileName = element.getFileName();
        return ((LineInFileLocation) scriptLocation).getLine() == element.getLineNumber()
            && (path.equals(fileName) || path.endsWith("/" + fileName) || path.endsWith("\\" + fileName));
    }

    private static List<UserCodeSource.Binary> pluginsWithCodeLocation(DetectionContext detection) {
        TaskIdentity task = detection.getTask();
        ImmutableList.Builder<UserCodeSource.Binary> plugins = ImmutableList.builder();
        for (UserCodeSource source = task != null ? task.getRegisteredBy() : detection.getAppliedCode(); source != null; source = source.getAppliedBy()) {
            if (source instanceof UserCodeSource.Binary && ((UserCodeSource.Binary) source).getCodeLocation() != null) {
                plugins.add((UserCodeSource.Binary) source);
            }
        }
        return plugins.build();
    }

    // A relative path has no base Gradle knows: the write API takes a plain string, and resolving it against the daemon's working directory would be a guess
    /**
     * Adds the lineage of a trace: the code that registered a task and, above that, the code that applied each piece of code, up to the script.
     * <p>
     * A validation problem whose type is the type of the task that was running gets that task first.
     * A trace that ends in a task the reporter named, with its registration record, gets the lineage of that task, whichever task was running.
     * Other problems get no lineage yet: nothing links their leaf to an entity Gradle keeps a record for.
     */
    static List<Trace> withLineage(List<Trace> traces, @Nullable TaskIdentity task) {
        ImmutableList.Builder<Trace> result = ImmutableList.builder();
        for (Trace trace : traces) {
            List<Frame> frames = trace.getFrames();
            Frame root = frames.get(frames.size() - 1);
            // Validation names a nested class with dots, the task type with a dollar sign
            if (task != null && root instanceof ClassFrame && ((ClassFrame) root).getClassName().equals(task.getTypeName().replace('$', '.'))) {
                result.add(new Trace(ImmutableList.<Frame>builder()
                    .addAll(frames)
                    .add(new TaskFrame(task.getTaskPath(), task.getLocality()))
                    .addAll(lineageOf(task.getRegisteredBy()))
                    .build()));
            } else if (root instanceof TaskFrame && ((TaskFrame) root).getRegisteredBy() != null) {
                result.add(new Trace(ImmutableList.<Frame>builder().addAll(frames).addAll(lineageOf(((TaskFrame) root).getRegisteredBy())).build()));
            } else {
                result.add(trace);
            }
        }
        return result.build();
    }

    private static List<Frame> lineageOf(@Nullable UserCodeSource registeredBy) {
        ImmutableList.Builder<Frame> lineage = ImmutableList.builder();
        for (UserCodeSource source = registeredBy; source != null; source = source.getAppliedBy()) {
            lineage.add(frameOf(source));
        }
        return lineage.build();
    }

    // Matches the wording of task provenance: a task is registered by a plugin or in a script
    private static Frame frameOf(UserCodeSource source) {
        if (source instanceof UserCodeSource.Binary) {
            UserCodeSource.Binary plugin = (UserCodeSource.Binary) source;
            return new PluginClassFrame(plugin.getClassName(), plugin.getPluginId(), plugin.getLocality());
        }
        if (source instanceof UserCodeSource.Script) {
            URI uri = ((UserCodeSource.Script) source).getUri();
            String path = uri != null && "file".equals(uri.getScheme()) ? new File(uri).getAbsolutePath() : source.getDisplayName().getDisplayName();
            // The line of the apply call isn't recorded, so the script frame has the file alone
            return new FileLocationFrame(new FilePosition(path, null, null, null, null), source.getLocality());
        }
        return new Frame(source.getDisplayName().getDisplayName(), source.getLocality());
    }

    private static boolean isInside(File root, File file) {
        if (!file.isAbsolute()) {
            return false;
        }
        File rootPath = root.getAbsoluteFile();
        for (File current = file; current != null; current = current.getParentFile()) {
            if (current.equals(rootPath)) {
                return true;
            }
        }
        return false;
    }
}
