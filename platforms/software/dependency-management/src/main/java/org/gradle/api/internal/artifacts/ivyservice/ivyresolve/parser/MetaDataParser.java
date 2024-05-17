/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;

import java.io.File;

public interface MetaDataParser<T extends MutableModuleComponentResolveMetadata> {
    /**
     * If the following marker is found in a `pom` file, Gradle (version 5.3 or higher)
     * will look for a `module` file to use it instead of the `pom` file.
     */
    String GRADLE_METADATA_MARKER = "do-not-remove: published-with-gradle-metadata";
    /**
     * If the following marker is found in a `pom` file, Gradle (version 6.0 or higher)
     * will look for a `module` file to use it instead of the `pom` file.
     * This marker was introduced to allow library authors to publish Gradle Module Metadata
     * for Gradle 6+ consumers only. Wider adoption of the format only starts with Gradle 6.
     * There were some semantic changes and fixes with 6.0 in `module` metadata interpretation.
     * These can break certain 5.x users in unexpected ways when consuming a library that
     * moved from publishing `pom` only to also publish `module` files.
     */
    String GRADLE_6_METADATA_MARKER = "do_not_remove: published-with-gradle-metadata";
    String[] GRADLE_METADATA_MARKER_COMMENT_LINES = {
            "This module was also published with a richer model, Gradle metadata, ",
            "which should be used instead. Do not delete the following line which ",
            "is to indicate to Gradle or any Gradle module metadata file consumer ",
            "that they should prefer consuming it instead."};

    ParseResult<T> parseMetaData(DescriptorParseContext context, LocallyAvailableExternalResource resource) throws MetaDataParseException;

    ParseResult<T> parseMetaData(DescriptorParseContext context, File descriptorFile) throws MetaDataParseException;

    ParseResult<T> parseMetaData(DescriptorParseContext context, File descriptorFile, boolean validate) throws MetaDataParseException;

    interface ParseResult<T> {
        boolean hasGradleMetadataRedirectionMarker();
        T getResult();

        static <T> ParseResult<T> of(T parsed, boolean hasGradleMetadataRedirect) {
            return new DefaultParseResult<>(parsed, hasGradleMetadataRedirect);
        }

    }

}

class DefaultParseResult<T> implements MetaDataParser.ParseResult<T> {

    private final T result;
    private final boolean redirect;

    DefaultParseResult(T result, boolean redirect) {
        this.result = result;
        this.redirect = redirect;
    }

    @Override
    public boolean hasGradleMetadataRedirectionMarker() {
        return redirect;
    }

    @Override
    public T getResult() {
        return result;
    }
}
