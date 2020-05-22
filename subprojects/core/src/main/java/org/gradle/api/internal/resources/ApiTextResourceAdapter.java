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

package org.gradle.api.internal.resources;

import com.google.common.io.Files;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.resources.ResourceException;
import org.gradle.api.resources.internal.TextResourceInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.TextResource;
import org.gradle.internal.resource.TextUriResourceLoader;
import org.gradle.internal.verifier.HttpRedirectVerifier;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.Charset;

/**
 * A {@link org.gradle.api.resources.TextResource} that adapts a {@link TextResource}.
 */
public class ApiTextResourceAdapter implements TextResourceInternal {
    private final URI uri;
    private final TextUriResourceLoader textUriResourceLoader;
    private final TemporaryFileProvider tempFileProvider;
    private TextResource textResource;

    public ApiTextResourceAdapter(TextUriResourceLoader textUriResourceLoader, TemporaryFileProvider tempFileProvider, URI uri) {
        this.uri = uri;
        this.textUriResourceLoader = textUriResourceLoader;
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
                Files.asCharSink(file, Charset.forName(targetCharset)).write(getWrappedTextResource().getText());
                return file;
            }
            Charset sourceCharset = getWrappedTextResource().getCharset();
            Charset targetCharsetObj = Charset.forName(targetCharset);
            if (targetCharsetObj.equals(sourceCharset)) {
                return file;
            }

            File targetFile = tempFileProvider.createTemporaryFile("uriTextResource", ".txt", "resource");
            try {
                Files.asCharSource(file, sourceCharset).copyTo(Files.asCharSink(targetFile, targetCharsetObj));
                return targetFile;
            } catch (IOException e) {
                throw new ResourceException("Could not write " + getDisplayName() + " content to " + targetFile + ".", e);
            }
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
        return TaskDependencyInternal.EMPTY;
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
            textResource = textUriResourceLoader.loadUri("textResource", uri);
        }
        return textResource;
    }

    public static class Factory {
        private final TextUriResourceLoader.Factory textUriResourceLoaderFactory;
        private final TemporaryFileProvider tempFileProvider;

        @Inject
        public Factory(TextUriResourceLoader.Factory textUriResourceLoaderFactory, @Nullable TemporaryFileProvider tempFileProvider) {
            this.textUriResourceLoaderFactory = textUriResourceLoaderFactory;
            this.tempFileProvider = tempFileProvider;
        }

        ApiTextResourceAdapter create(URI uri, HttpRedirectVerifier httpRedirectVerifier) {
            TextUriResourceLoader uriResourceLoader = textUriResourceLoaderFactory.create(httpRedirectVerifier);
            return new ApiTextResourceAdapter(uriResourceLoader, tempFileProvider, uri);
        }
    }
}
