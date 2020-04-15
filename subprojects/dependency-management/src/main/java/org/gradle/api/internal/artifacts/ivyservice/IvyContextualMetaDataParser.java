/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.DescriptorParseContext;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParseException;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;

import java.io.File;

public class IvyContextualMetaDataParser<T extends MutableModuleComponentResolveMetadata> implements MetaDataParser<T> {
    private final MetaDataParser<T> delegate;
    private final IvyContextManager ivyContextManager;

    public IvyContextualMetaDataParser(IvyContextManager ivyContextManager, MetaDataParser<T> delegate) {
        this.delegate = delegate;
        this.ivyContextManager = ivyContextManager;
    }

    @Override
    public ParseResult<T> parseMetaData(final DescriptorParseContext context, final LocallyAvailableExternalResource resource) throws MetaDataParseException {
        return ivyContextManager.withIvy(ivy -> {
            return delegate.parseMetaData(context, resource);
        });
    }

    @Override
    public ParseResult<T> parseMetaData(final DescriptorParseContext ivySettings, final File descriptorFile) throws MetaDataParseException {
        return ivyContextManager.withIvy(ivy -> {
            return delegate.parseMetaData(ivySettings, descriptorFile);
        });
    }

    @Override
    public ParseResult<T> parseMetaData(final DescriptorParseContext ivySettings, final File descriptorFile, final boolean validate) throws MetaDataParseException {
        return ivyContextManager.withIvy(ivy -> {
            return delegate.parseMetaData(ivySettings, descriptorFile, validate);
        });
    }
}
