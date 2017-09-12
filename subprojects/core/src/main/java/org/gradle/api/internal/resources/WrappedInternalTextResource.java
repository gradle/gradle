/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.resources;

import com.google.common.io.Files;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.tasks.TaskDependencies;
import org.gradle.api.resources.internal.TextResourceInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.TextResource;
import org.gradle.internal.resource.TextResourceLoader;

import java.io.File;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.Charset;

/**
 * A {@link org.gradle.api.resources.TextResource} that has a wrapped {@link TextResource} internaly
 */
public class WrappedInternalTextResource implements TextResourceInternal {
    private final URI uri;
    private final TextResourceLoader textResourceLoader;
    private final TemporaryFileProvider tempFileProvider;
    private TextResource textResource;

    /**
     * Create a {@link org.gradle.api.resources.TextResource} backed by an internal {@link TextResource}.
     * @param textResourceLoader the internal resource
     * @param tempFileProvider the temporary file provider
     * @param uri the uri from where to load the file
     */
    public WrappedInternalTextResource(TextResourceLoader textResourceLoader, TemporaryFileProvider tempFileProvider, URI uri) {
        this.uri = uri;
        this.textResourceLoader = textResourceLoader;
        this.tempFileProvider = tempFileProvider;
    }

    @Override
    public String asString() {
        return getWrappedTextResource().getText();
    }

    @Override
    public Reader asReader() {
        return getWrappedTextResource().getAsReader();
    }

    @Override
    public File asFile(String targetCharset) {
        try {
            File file = getWrappedTextResource().getFile();
            if (file == null) {
                file = tempFileProvider.createTemporaryFile("wrappedInternalText", ".txt", "resource");
                Files.write(getWrappedTextResource().getText(), file, Charset.forName(targetCharset));
                return file;
            }
            return FileResourceHelper.asFile(tempFileProvider, file, targetCharset, getWrappedTextResource().getCharset(), getDisplayName(), "uriTextResource");
        } catch (Exception e) {
            throw ResourceExceptions.readFailed(getDisplayName(), e);
        }
    }

    @Override
    public File asFile() {
        return asFile(Charset.defaultCharset().name());
    }

    @Override
    public Object getInputProperties() {
        return uri;
    }

    @Override
    public FileCollection getInputFiles() {
        return null;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return TaskDependencies.EMPTY;
    }

    @Override
    public String getDisplayName() {
        return getWrappedTextResource().getDisplayName();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    private TextResource getWrappedTextResource() {
        if (textResource == null) {
            textResource = textResourceLoader.loadUri("textResource", uri);
        }
        return textResource;
    }
}
