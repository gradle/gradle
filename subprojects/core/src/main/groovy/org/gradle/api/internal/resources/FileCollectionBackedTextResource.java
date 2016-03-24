/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.resources.ResourceException;
import org.gradle.api.resources.internal.TextResourceInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.resource.ResourceExceptions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;

public class FileCollectionBackedTextResource implements TextResourceInternal {
    private final TemporaryFileProvider tempFileProvider;
    private final FileCollection fileCollection;
    private final Charset charset;

    public FileCollectionBackedTextResource(TemporaryFileProvider tempFileProvider, FileCollection fileCollection, Charset charset) {
        this.tempFileProvider = tempFileProvider;
        this.fileCollection = fileCollection;
        this.charset = charset;
    }

    @Override
    public String getDisplayName() {
        return fileCollection.toString();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String asString() {
        File file = asFile();
        try {
            return Files.toString(file, charset);
        } catch (FileNotFoundException e) {
            throw ResourceExceptions.readMissing(file, e);
        } catch (IOException e) {
            throw ResourceExceptions.readFailed(file, e);
        }
    }

    public Reader asReader() {
        File file = asFile();
        try {
            return Files.newReader(asFile(), charset);
        } catch (FileNotFoundException e) {
            throw ResourceExceptions.readMissing(file, e);
        }
    }

    public File asFile(String targetCharset) {
        try {
            Charset targetCharsetObj = Charset.forName(targetCharset);

            if (targetCharsetObj.equals(charset)) {
                return fileCollection.getSingleFile();
            }

            File targetFile = tempFileProvider.createTemporaryFile("fileCollection", ".txt", "resource");
            try {
                Files.asCharSource(fileCollection.getSingleFile(), charset).copyTo(Files.asCharSink(targetFile, targetCharsetObj));
            } catch (IOException e) {
                throw new ResourceException("Could not write " + getDisplayName() + " content to " + targetFile + ".", e);
            }
            return targetFile;
        } catch (Exception e) {
            throw ResourceExceptions.readFailed(getDisplayName(), e);
        }
    }

    public File asFile() {
        return asFile(Charset.defaultCharset().name());
    }

    public TaskDependency getBuildDependencies() {
        return fileCollection.getBuildDependencies();
    }

    public Object getInputProperties() {
        return charset.name();
    }

    public FileCollection getInputFiles() {
        return fileCollection;
    }
}
