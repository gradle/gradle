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

    // A relative path has no base Gradle knows: the write API takes a plain string, and resolving it against the daemon's working directory would be a guess
    /**
     * Adds the lineage of a validation problem: when its type is the type of the task that was running, the task comes next,
     * then the code that registered the task and, above that, the code that applied each piece of code, up to the script.
     * Other problems get no lineage yet: nothing links their leaf to an entity Gradle keeps a record for.
     */
    static List<Trace> withLineage(List<Trace> traces, @Nullable TaskIdentity task) {
        if (task == null) {
            return traces;
        }
        ImmutableList.Builder<Trace> result = ImmutableList.builder();
        for (Trace trace : traces) {
            List<Frame> frames = trace.getFrames();
            Frame root = frames.get(frames.size() - 1);
            if (root instanceof ClassFrame && ((ClassFrame) root).getClassName().equals(task.getTypeName())) {
                ImmutableList.Builder<Frame> withLineage = ImmutableList.<Frame>builder().addAll(frames);
                withLineage.add(new TaskFrame(task.getTaskPath(), task.getLocality()));
                for (UserCodeSource source = task.getRegisteredBy(); source != null; source = source.getAppliedBy()) {
                    withLineage.add(frameOf(source));
                }
                result.add(new Trace(withLineage.build()));
            } else {
                result.add(trace);
            }
        }
        return result.build();
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
