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
package org.gradle.internal.resource;

import org.gradle.api.resources.ResourceException;
import org.gradle.internal.file.RelativeFilePathResolver;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;

public class EmptyFileTextResource extends UriTextResource {
    private static final HashCode SIGNATURE = Hashing.signature(EmptyFileTextResource.class);

    EmptyFileTextResource(String description, File sourceFile, RelativeFilePathResolver resolver) {
        super(description, sourceFile, resolver);
    }

    @Override
    public boolean isContentCached() {
        return true;
    }

    @Override
    public boolean getHasEmptyContent() {
        return true;
    }

    @Override
    public File getFile() {
        return null;
    }

    @Override
    public Charset getCharset() {
        return null;
    }

    @Override
    public Reader getAsReader() {
        return new StringReader("");
    }

    @Override
    public HashCode getContentHash() throws ResourceException {
        return SIGNATURE;
    }

    @Override
    public String getText() {
        return "";
    }

    @Override
    public boolean getExists() {
        return true;
    }
}
