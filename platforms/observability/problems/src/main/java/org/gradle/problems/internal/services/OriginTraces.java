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
import org.gradle.api.problems.internal.location.FileLocationFrame;
import org.gradle.api.problems.internal.location.Frame;
import org.gradle.api.problems.internal.location.Trace;
import org.gradle.internal.code.Locality;

import java.io.File;
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
    static List<Trace> withFileLocalities(List<Trace> traces, File buildTreeRootDirectory) {
        ImmutableList.Builder<Trace> result = ImmutableList.builder();
        for (Trace trace : traces) {
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
