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

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;

public class EmptyFileTextResource extends UriTextResource {
    EmptyFileTextResource(String description, File sourceFile) {
        super(description, sourceFile);
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
    public String getText() {
        return "";
    }

    @Override
    public boolean getExists() {
        return true;
    }
}
