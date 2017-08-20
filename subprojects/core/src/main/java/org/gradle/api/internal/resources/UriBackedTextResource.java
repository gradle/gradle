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
import org.gradle.api.resources.ResourceException;
import org.gradle.api.resources.internal.TextResourceInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.TextResource;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.Charset;

/**
 * A text resource that is backed by an URI and an internal {@link TextResource}.
 */
public class UriBackedTextResource implements TextResourceInternal {
    private final URI uri;
    private final TextResource textResource;
    private final TemporaryFileProvider tempFileProvider;

    public UriBackedTextResource(URI uri, TextResource textResource, TemporaryFileProvider tempFileProvider) {
        this.uri = uri;
        this.textResource = textResource;
        this.tempFileProvider = tempFileProvider;
    }

    @Override
    public String asString() {
        return textResource.getText();
    }

    @Override
    public Reader asReader() {
        return textResource.getAsReader();
    }

    @Override
    public File asFile(String targetCharset) {
        try {
            Charset targetCharsetObj = Charset.forName(targetCharset);

            File file = textResource.getFile();
            if (file == null || targetCharsetObj.equals(textResource.getCharset())) {
                return file;
            }

            File targetFile = tempFileProvider.createTemporaryFile("uriTextResource", ".txt", "resource");
            try {
                Files.asCharSource(file, textResource.getCharset()).copyTo(Files.asCharSink(targetFile, targetCharsetObj));
            } catch (IOException e) {
                throw new ResourceException("Could not write " + getDisplayName() + " content to " + targetFile + ".", e);
            }
            return targetFile;
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
        return textResource.getDisplayName();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
