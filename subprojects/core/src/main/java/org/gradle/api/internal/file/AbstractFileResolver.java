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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Nullable;
import org.gradle.api.PathValidation;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.resources.internal.ReadableResourceInternal;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.UnsupportedNotationException;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.net.URI;
import java.util.List;

public abstract class AbstractFileResolver implements FileResolver {
    private final FileSystem fileSystem;
    private final NotationParser<Object, Object> fileNotationParser;
    private final Factory<PatternSet> patternSetFactory;
    private final FileNormaliser fileNormaliser;

    protected AbstractFileResolver(FileSystem fileSystem, Factory<PatternSet> patternSetFactory) {
        this.fileSystem = fileSystem;
        this.fileNormaliser = new FileNormaliser(fileSystem);
        this.fileNotationParser = FileOrUriNotationConverter.parser(fileSystem);
        this.patternSetFactory = patternSetFactory;
    }

    public FileSystem getFileSystem() {
        return fileSystem;
    }

    public FileResolver withBaseDir(Object path) {
        return new BaseDirFileResolver(fileSystem, resolve(path), patternSetFactory);
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

            @Override
            public void describe(DiagnosticsVisitor visitor) {
                visitor.candidate("Anything that can be converted to a file, as per Project.file()");
            }
        };
    }

    public File resolve(Object path, PathValidation validation) {
        File file = doResolve(path);

        file = fileNormaliser.normalise(file);

        validate(file, validation);

        return file;
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
        Object object = GFileUtils.unpack(path);
        Object converted = fileNotationParser.parseNotation(object);
        if (converted instanceof File) {
            return resolve(converted).toURI();
        }
        return (URI) converted;
    }

    @Nullable
    protected File convertObjectToFile(Object path) {
        Object object = GFileUtils.unpack(path);
        if (object == null) {
            return null;
        }
        Object converted = fileNotationParser.parseNotation(object);
        if (converted instanceof File) {
            return (File) converted;
        }
        throw new InvalidUserDataException(String.format("Cannot convert URL '%s' to a file.", converted));
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

    public FileCollectionInternal resolveFiles(Object... paths) {
        if (paths.length == 1 && paths[0] instanceof FileCollection) {
            return Cast.cast(FileCollectionInternal.class, paths[0]);
        }
        return new DefaultConfigurableFileCollection(this, null, paths);
    }

    public FileTreeInternal resolveFilesAsTree(Object... paths) {
        return Cast.cast(FileTreeInternal.class, resolveFiles(paths).getAsFileTree());
    }

    public FileTreeInternal compositeFileTree(List<? extends FileTree> fileTrees) {
        return new DefaultCompositeFileTree(CollectionUtils.checkedCast(FileTreeInternal.class, fileTrees));
    }

    public ReadableResourceInternal resolveResource(Object path) {
        if (path instanceof ReadableResourceInternal) {
            return (ReadableResourceInternal) path;
        }
        return new FileResource(resolve(path));
    }

    @Override
    public Factory<PatternSet> getPatternSetFactory() {
        return patternSetFactory;
    }
}
