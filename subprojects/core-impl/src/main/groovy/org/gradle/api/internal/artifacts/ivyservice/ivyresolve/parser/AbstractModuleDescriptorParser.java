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

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.plugins.parser.ParserSettings;
import org.gradle.api.internal.externalresource.DefaultLocallyAvailableExternalResource;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource;
import org.gradle.internal.resource.local.LocallyAvailableResource;

import java.io.File;

public abstract class AbstractModuleDescriptorParser implements ModuleDescriptorParser {
    public ModuleDescriptor parseDescriptor(ParserSettings ivySettings, File descriptorFile, boolean validate) throws MetaDataParseException {
        LocallyAvailableResource localResource = new DefaultLocallyAvailableResource(descriptorFile);
        LocallyAvailableExternalResource resource = new DefaultLocallyAvailableExternalResource(descriptorFile.toURI().toString(), localResource);
        return parseDescriptor(ivySettings, resource, validate);
    }

    public DefaultModuleDescriptor parseDescriptor(ParserSettings ivySettings, LocallyAvailableExternalResource resource, boolean validate) throws MetaDataParseException {
        try {
            return doParseDescriptor(ivySettings, resource, validate);
        } catch (MetaDataParseException e) {
            throw e;
        } catch (Exception e) {
            throw new MetaDataParseException(String.format("Could not parse %s %s", getTypeName(), resource.getName()), e);
        }
    }

    protected abstract String getTypeName();

    protected abstract DefaultModuleDescriptor doParseDescriptor(ParserSettings ivySettings, LocallyAvailableExternalResource resource, boolean validate) throws Exception;
}
