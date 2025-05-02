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

package org.gradle.internal.resource;

import org.gradle.api.resources.MissingResourceException;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.DisplayName;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.hash.PrimitiveHasher;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;

public class CachingTextResource implements TextResource {
    private static final HashCode SIGNATURE = Hashing.signature(CachingTextResource.class);
    private final TextResource resource;
    private String content;
    private HashCode contentHash;

    public CachingTextResource(TextResource resource) {
        this.resource = resource;
    }

    @Override
    public String getDisplayName() {
        return resource.getDisplayName();
    }

    @Override
    public DisplayName getLongDisplayName() {
        return resource.getLongDisplayName();
    }

    @Override
    public DisplayName getShortDisplayName() {
        return resource.getShortDisplayName();
    }

    @Override
    public ResourceLocation getLocation() {
        return resource.getLocation();
    }

    @Override
    public File getFile() {
        return resource.getFile();
    }

    @Override
    public Charset getCharset() {
        return resource.getCharset();
    }

    @Override
    public boolean isContentCached() {
        return true;
    }

    @Override
    public boolean getExists() {
        try {
            maybeFetch();
        } catch (MissingResourceException e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean getHasEmptyContent() {
        maybeFetch();
        return content.length() == 0;
    }

    @Override
    public String getText() {
        maybeFetch();
        return content;
    }

    @Override
    public HashCode getContentHash() throws ResourceException {
        maybeFetch();
        return contentHash;
    }

    @Override
    public Reader getAsReader() {
        maybeFetch();
        return new StringReader(content);
    }

    private void maybeFetch() {
        if (content == null) {
            content = resource.getText();
            PrimitiveHasher hasher = Hashing.newPrimitiveHasher();
            hasher.putHash(SIGNATURE);
            hasher.putString(content);
            contentHash = hasher.hash();
        }
    }
}
