/*
 * Copyright 2010 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.PathValidation;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.resources.ReadableResource;
import org.gradle.internal.Factory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.UnsupportedNotationException;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

public abstract class AbstractFileResolver implements FileResolver {
    private final FileSystem fileSystem;
    private final NotationParser<Object, Object> fileNotationParser;

    protected AbstractFileResolver(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
        this.fileNotationParser = FileOrUriNotationParser.create(fileSystem);
    }

    public FileSystem getFileSystem() {
        return fileSystem;
    }

    public FileResolver withBaseDir(Object path) {
        return new BaseDirFileResolver(fileSystem, resolve(path));
    }

    public File resolve(Object path) {
        return resolve(path, PathValidation.NONE);
    }

    public NotationParser<Object, File> asNotationParser() {
        return new NotationParser<Object, File>() {
            public File parseNotation(Object notation) throws UnsupportedNotationException {
                // TODO Further differentiate between unsupported notation errors and others (particularly when we remove the deprecated 'notation.toString()' resolution)
                return resolve(notation, PathValidation.NONE);
            }

            public void describe(Collection<String> candidateFormats) {
                candidateFormats.add("Anything that can be converted to a file, as per Project.file()");
            }
        };
    }

    public File resolve(Object path, PathValidation validation) {
        File file = doResolve(path);

        file = normalise(file);

        validate(file, validation);

        return file;
    }

    // normalizes a path in similar ways as File.getCanonicalFile(), except that it
    // does NOT resolve symlinks (by design)
    private File normalise(File file) {
        try {
            assert file.isAbsolute() : String.format("Cannot normalize a relative file: '%s'", file);

            if (OperatingSystem.current().isWindows()) {
                // on Windows, File.getCanonicalFile() doesn't resolve symlinks
                return file.getCanonicalFile();
            }

            String[] segments = file.getPath().split(String.format("[/%s]", Pattern.quote(File.separator)));
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

            String resolvedPath = CollectionUtils.join(File.separator, path);
            boolean needLeadingSeparator = File.listRoots()[0].getPath().startsWith(File.separator);
            if (needLeadingSeparator) {
                resolvedPath = File.separator + resolvedPath;
            }
            File candidate = new File(resolvedPath);
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
            File current = File.listRoots()[0];
            for (int pos = 0; pos < path.size(); pos++) {
                File child = findChild(current, path.get(pos));
                if (child == null) {
                    current = new File(current, CollectionUtils.join(File.separator, path.subList(pos, path.size())));
                    break;
                }
                current = child;
            }
            return current;
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not normalize path for file '%s'.", file), e);
        }
    }

    private File findChild(File current, String segment) throws IOException {
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

    public Factory<File> resolveLater(final Object path) {
        return new Factory<File>() {
            public File create() {
                return resolve(path);
            }
        };
    }

    public URI resolveUri(Object path) {
        return convertObjectToURI(path);
    }

    protected abstract File doResolve(Object path);

    protected URI convertObjectToURI(Object path) {
        Object object = unpack(path);
        Object converted = fileNotationParser.parseNotation(object);
        if (converted instanceof File) {
            return resolve(converted).toURI();
        }
        return (URI) converted;
    }

    protected File convertObjectToFile(Object path) {
        Object object = unpack(path);
        if (object == null) {
            return null;
        }
        Object converted = fileNotationParser.parseNotation(object);
        if (converted instanceof File) {
            return (File) converted;
        }
        throw new InvalidUserDataException(String.format("Cannot convert URL '%s' to a file.", converted));
    }

    private Object unpack(Object path) {
        Object current = path;
        while (current != null) {
            if (current instanceof Closure) {
                current = ((Closure) current).call();
            } else if (current instanceof Callable) {
                try {
                    current = ((Callable) current).call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else if (current instanceof Factory) {
                return ((Factory) current).create();
            } else {
                return current;
            }
        }
        return null;
    }

    protected void validate(File file, PathValidation validation) {
        switch (validation) {
            case NONE:
                break;
            case EXISTS:
                if (!file.exists()) {
                    throw new InvalidUserDataException(String.format("File '%s' does not exist.", file));
                }
                break;
            case FILE:
                if (!file.exists()) {
                    throw new InvalidUserDataException(String.format("File '%s' does not exist.", file));
                }
                if (!file.isFile()) {
                    throw new InvalidUserDataException(String.format("File '%s' is not a file.", file));
                }
                break;
            case DIRECTORY:
                if (!file.exists()) {
                    throw new InvalidUserDataException(String.format("Directory '%s' does not exist.", file));
                }
                if (!file.isDirectory()) {
                    throw new InvalidUserDataException(String.format("Directory '%s' is not a directory.", file));
                }
                break;
        }
    }

    public FileCollection resolveFiles(Object... paths) {
        if (paths.length == 1 && paths[0] instanceof FileCollection) {
            return (FileCollection) paths[0];
        }
        return new DefaultConfigurableFileCollection(this, null, paths);
    }

    public FileTree resolveFilesAsTree(Object... paths) {
        return resolveFiles(paths).getAsFileTree();
    }

    public FileTree compositeFileTree(List<FileTree> fileTrees) {
        return new DefaultCompositeFileTree(fileTrees);
    }

    public ReadableResource resolveResource(Object path) {
        if (path instanceof ReadableResource) {
            return (ReadableResource) path;
        }
        return new FileResource(resolve(path));
    }
}
