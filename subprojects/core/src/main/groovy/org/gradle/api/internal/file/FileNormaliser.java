/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.file;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class FileNormaliser {
    private static final String FILE_PATH_SEPARATORS = File.separatorChar != '/' ? ("/" + File.separator) : File.separator;
    private final FileSystem fileSystem;
    private final boolean isWindowsOs;

    FileNormaliser(FileSystem fileSystem) {
        this(fileSystem, OperatingSystem.current());
    }

    FileNormaliser(FileSystem fileSystem, OperatingSystem operatingSystem) {
        this.fileSystem = fileSystem;
        this.isWindowsOs = operatingSystem.isWindows();
    }

    // normalizes a path in similar ways as File.getCanonicalFile(), except that it
    // does NOT resolve symlinks (by design)
    public File normalise(File file) {
        try {
            if (!file.isAbsolute()) {
                throw new IllegalArgumentException(String.format("Cannot normalize a relative file: '%s'", file));
            }

            if (isWindowsOs) {
                // on Windows, File.getCanonicalFile() doesn't resolve symlinks
                return file.getCanonicalFile();
            }

            File candidate;
            String filePath = file.getPath();
            List<String> path = null;
            if (isNormalisingRequiredForAbsolutePath(filePath)) {
                path = splitAndNormalisePath(filePath);
                String resolvedPath = CollectionUtils.join(File.separator, path);
                boolean needLeadingSeparator = File.listRoots()[0].getPath().startsWith(File.separator);
                if (needLeadingSeparator) {
                    resolvedPath = File.separator + resolvedPath;
                }
                candidate = new File(resolvedPath);
            } else {
                candidate = file;
            }

            // If the file system is case sensitive, we don't have to normalise it further
            if (fileSystem.isCaseSensitive()) {
                return candidate;
            }

            // Short-circuit the slower lookup method by using the canonical file
            File canonical = candidate.getCanonicalFile();
            if (candidate.getPath().equalsIgnoreCase(canonical.getPath())) {
                return canonical;
            }

            // Canonical path is different to what we expected (eg there is a link somewhere in there). Normalise a segment at a time
            // TODO - start resolving only from where the expected and canonical paths are different
            if (path == null) {
                path = splitAndNormalisePath(filePath);
            }
            return normaliseUnixPathIgnoringCase(path);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not normalize path for file '%s'.", file), e);
        }
    }

    boolean isNormalisingRequiredForAbsolutePath(String filePath) {
        if (File.pathSeparatorChar != '/') {
            filePath = filePath.replace(File.pathSeparatorChar, '/');
        }
        if (filePath.charAt(0) != '/') {
            throw new IllegalArgumentException("Only absolute unix paths are currently handled. filePath=" + filePath);
        }
        if (filePath.contains("/../") || filePath.contains("/./") || filePath.contains("//")) {
            return true;
        }
        if (filePath.endsWith("/") || filePath.endsWith("/.") || filePath.endsWith("/..")) {
            return true;
        }
        return false;
    }

    private List<String> splitAndNormalisePath(String filePath) {
        String[] segments = splitPath(filePath);
        List<String> path = new ArrayList<String>(segments.length);
        for (String segment : segments) {
            if (segment.equals("..")) {
                if (!path.isEmpty()) {
                    path.remove(path.size() - 1);
                }
            } else if (!segment.equals(".") && segment.length() > 0) {
                path.add(segment);
            }
        }
        return path;
    }

    private String[] splitPath(String filePath) {
        return StringUtils.split(filePath, FILE_PATH_SEPARATORS);
    }

    private File normaliseUnixPathIgnoringCase(List<String> path) throws IOException {
        File current = File.listRoots()[0];
        for (int pos = 0; pos < path.size(); pos++) {
            File child = findChildIgnoringCase(current, path.get(pos));
            if (child == null) {
                current = new File(current, CollectionUtils.join(File.separator, path.subList(pos, path.size())));
                break;
            }
            current = child;
        }
        return current;
    }

    private File findChildIgnoringCase(File current, String segment) throws IOException {
        String[] children = current.list();
        if (children == null) {
            return null;
        }
        // TODO - find some native methods for doing this
        for (String child : children) {
            if (child.equalsIgnoreCase(segment)) {
                return new File(current, child);
            }
        }
        return new File(current, segment);
    }
}
