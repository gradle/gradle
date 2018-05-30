/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state.mirror;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.changedetection.state.DirectoryFileSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileSnapshot;
import org.gradle.api.internal.changedetection.state.MissingFileSnapshot;
import org.gradle.api.internal.changedetection.state.RegularFileSnapshot;

import java.io.File;

public class FileSnapshotHelper {
    private static final Joiner JOINER =Joiner.on(File.separatorChar).skipNulls();

    public static FileSnapshot create(String basePath, Iterable<String> relativePath, FileContentSnapshot content) {
        String[] segments = Iterables.toArray(relativePath, String.class);
        FileSnapshot snapshot;
        String absolutePath = JOINER.join(Strings.emptyToNull(basePath), Strings.emptyToNull(JOINER.join(relativePath)));
        switch (content.getType()) {
            case Directory:
                snapshot = new DirectoryFileSnapshot(absolutePath, new RelativePath(false, segments), false);
                break;
            case Missing:
                snapshot = new MissingFileSnapshot(absolutePath, new RelativePath(true, segments));
                break;
            case RegularFile:
                snapshot = new RegularFileSnapshot(absolutePath, new RelativePath(true, segments), false, content);
                break;
            default:
                throw new IllegalArgumentException("Unsupported file type: " + content.getType());
        }
        return snapshot;
    }
}
