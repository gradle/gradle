/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.groovy.scripts.internal;

import org.gradle.api.resources.ResourceException;
import org.gradle.groovy.scripts.DelegatingScriptSource;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.DisplayName;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.hash.PrimitiveHasher;
import org.gradle.internal.resource.ResourceLocation;
import org.gradle.internal.resource.TextResource;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;

/**
 * The source of a script as seen by the initial compilation pass: only the parts that the initial pass looks at,
 * as computed by {@link InitialPassSourceReducer}. Its content hash keys the compiled classes of the initial pass,
 * so edits to the rest of the script do not cause the initial pass to be recompiled.
 */
public class InitialPassScriptSource extends DelegatingScriptSource {

    private final TextResource resource;

    /**
     * Returns the reduced source, or the source itself when it cannot be reduced safely.
     */
    public static ScriptSource of(ScriptSource source) {
        TextResource resource = source.getResource();
        if (!resource.isContentCached() || resource.getHasEmptyContent()) {
            return source;
        }
        String text = resource.getText();
        String reduced = InitialPassSourceReducer.reduce(text);
        if (reduced == null || reduced.equals(text)) {
            return source;
        }
        return new InitialPassScriptSource(source, reduced);
    }

    private InitialPassScriptSource(ScriptSource source, String reducedText) {
        super(source);
        this.resource = new ReducedTextResource(source.getResource(), reducedText);
    }

    @Override
    public TextResource getResource() {
        return resource;
    }

    private static class ReducedTextResource implements TextResource {
        private static final HashCode SIGNATURE = Hashing.signature(ReducedTextResource.class);

        private final TextResource original;
        private final String text;
        private final HashCode contentHash;

        ReducedTextResource(TextResource original, String text) {
            this.original = original;
            this.text = text;
            PrimitiveHasher hasher = Hashing.newPrimitiveHasher();
            hasher.putHash(SIGNATURE);
            hasher.putString(text);
            this.contentHash = hasher.hash();
        }

        @Override
        public String getDisplayName() {
            return original.getDisplayName();
        }

        @Override
        public DisplayName getLongDisplayName() {
            return original.getLongDisplayName();
        }

        @Override
        public DisplayName getShortDisplayName() {
            return original.getShortDisplayName();
        }

        @Override
        public ResourceLocation getLocation() {
            return original.getLocation();
        }

        @Override
        public @Nullable File getFile() {
            return original.getFile();
        }

        @Override
        public @Nullable Charset getCharset() {
            return original.getCharset();
        }

        @Override
        public boolean isContentCached() {
            return true;
        }

        @Override
        public boolean getExists() throws ResourceException {
            return true;
        }

        @Override
        public boolean getHasEmptyContent() throws ResourceException {
            return text.isEmpty();
        }

        @Override
        public Reader getAsReader() throws ResourceException {
            return new StringReader(text);
        }

        @Override
        public String getText() throws ResourceException {
            return text;
        }

        @Override
        public HashCode getContentHash() throws ResourceException {
            return contentHash;
        }
    }
}
