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
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;

import java.io.File;

public abstract class AbstractModuleDescriptorParser<T extends MutableModuleComponentResolveMetadata> implements MetaDataParser<T> {
    private final FileResourceRepository fileResourceRepository;

    public AbstractModuleDescriptorParser(FileResourceRepository fileResourceRepository) {
        this.fileResourceRepository = fileResourceRepository;
    }

    @Override
    public ParseResult<T> parseMetaData(DescriptorParseContext ivySettings, File descriptorFile, boolean validate) throws MetaDataParseException {
        LocallyAvailableExternalResource resource = fileResourceRepository.resource(descriptorFile);
        return parseDescriptor(ivySettings, resource, validate);
    }

    @Override
    public ParseResult<T> parseMetaData(DescriptorParseContext ivySettings, File descriptorFile) throws MetaDataParseException {
        return parseMetaData(ivySettings, descriptorFile, false);
    }

    @Override
    public ParseResult<T> parseMetaData(DescriptorParseContext ivySettings, LocallyAvailableExternalResource resource) throws MetaDataParseException {
        return parseDescriptor(ivySettings, resource, false);
    }

    protected ParseResult<T> parseDescriptor(DescriptorParseContext ivySettings, LocallyAvailableExternalResource resource, boolean validate) throws MetaDataParseException {
        try {
            ParseResult<T> parseResult = doParseDescriptor(ivySettings, resource, validate);
            T metadata = parseResult.getResult();
            if (metadata != null) {
                metadata.setContentHash(HashUtil.createHash(resource.getFile(), "MD5"));
            }
            return parseResult;
        } catch (MetaDataParseException e) {
            throw e;
        } catch (Exception e) {
            throw new MetaDataParseException(getTypeName(), resource, e);
        }
    }

    protected abstract String getTypeName();

    protected abstract ParseResult<T> doParseDescriptor(DescriptorParseContext ivySettings, LocallyAvailableExternalResource resource, boolean validate) throws Exception;
}
