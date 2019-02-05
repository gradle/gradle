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
    String GRADLE_METADATA_MARKER = "do-not-remove: published-with-gradle-metadata";
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
