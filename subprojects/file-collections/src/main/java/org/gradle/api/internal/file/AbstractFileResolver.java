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
import org.gradle.api.PathValidation;
import org.gradle.internal.FileUtils;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.UnsupportedNotationException;
import org.gradle.util.DeferredUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;

public abstract class AbstractFileResolver implements FileResolver {
    private final NotationParser<Object, Object> fileNotationParser;

    protected AbstractFileResolver() {
        this.fileNotationParser = FileOrUriNotationConverter.parser();
    }

    public FileResolver withBaseDir(Object path) {
        return new BaseDirFileResolver(resolve(path));
    }

    @Override
    public FileResolver newResolver(File baseDir) {
        return new BaseDirFileResolver(baseDir);
    }

    @Override
    public File resolve(Object path) {
        return resolve(path, PathValidation.NONE);
    }

    @Override
    public NotationParser<Object, File> asNotationParser() {
        return new NotationParser<Object, File>() {
            @Override
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

    @Override
    public String resolveForDisplay(Object path) {
        return resolveAsRelativePath(path);
    }

    @Override
    public File resolve(Object path, PathValidation validation) {
        File file = doResolve(path);

        file = FileUtils.normalize(file);

        validate(file, validation);

        return file;
    }

    @Override
    public URI resolveUri(Object path) {
        return convertObjectToURI(path);
    }

    protected abstract File doResolve(Object path);

    protected URI convertObjectToURI(Object path) {
        Object object = DeferredUtil.unpack(path);
        Object converted = fileNotationParser.parseNotation(object);
        if (converted instanceof File) {
            return resolve(converted).toURI();
        }
        return (URI) converted;
    }

    @Nullable
    protected File convertObjectToFile(Object path) {
        Object object = DeferredUtil.unpack(path);
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

}
