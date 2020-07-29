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

import org.gradle.api.resources.ResourceException;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.hash.PrimitiveHasher;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.Charset;

public class StringTextResource implements TextResource {
    private static final HashCode SIGNATURE = Hashing.signature(StringTextResource.class);

    private final String displayName;
    private final CharSequence contents;
    private HashCode contentHash;

    public StringTextResource(String displayName, CharSequence contents) {
        this.displayName = displayName;
        this.contents = contents;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public DisplayName getLongDisplayName() {
        return Describables.of(displayName);
    }

    @Override
    public DisplayName getShortDisplayName() {
        return getLongDisplayName();
    }

    @Override
    public boolean isContentCached() {
        return true;
    }

    @Override
    public boolean getHasEmptyContent() {
        return contents.length() == 0;
    }

    @Override
    public Reader getAsReader() {
        return new StringReader(getText());
    }

    @Override
    public String getText() {
        return contents.toString();
    }

    @Override
    public HashCode getContentHash() throws ResourceException {
        if (contentHash == null) {
            PrimitiveHasher hasher = Hashing.newPrimitiveHasher();
            hasher.putHash(SIGNATURE);
            hasher.putString(getText());
            contentHash = hasher.hash();
        }
        return contentHash;
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
    public ResourceLocation getLocation() {
        return new StringResourceLocation(displayName);
    }

    @Override
    public boolean getExists() {
        return true;
    }

    private static class StringResourceLocation implements ResourceLocation {
        private final String displayName;

        public StringResourceLocation(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Nullable
        @Override
        public File getFile() {
            return null;
        }

        @Nullable
        @Override
        public URI getURI() {
            return null;
        }
    }
}
