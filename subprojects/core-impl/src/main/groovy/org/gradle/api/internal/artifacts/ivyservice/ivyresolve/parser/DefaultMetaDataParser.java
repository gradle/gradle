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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.parser.ModuleDescriptorParser;
import org.apache.ivy.plugins.parser.ParserSettings;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.IvyContextualiser;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;

public class DefaultMetaDataParser implements MetaDataParser {
    private final ParserRegistry parserRegistry;

    public DefaultMetaDataParser(ParserRegistry parserRegistry) {
        this.parserRegistry = parserRegistry;
    }

    public Artifact getMetadataArtifact(ModuleRevisionId moduleRevisionId, Resource resource) {
        return parserRegistry.forResource(resource).getMetadataArtifact(moduleRevisionId, resource);
    }

    public ModuleDescriptor parseModuleDescriptor(ModuleRevisionId moduleRevisionId, File moduleDescriptorFile, Resource resource, DependencyResolver resolver) throws ParseException {
        try {
            IvySettings ivySettings = IvyContextualiser.getIvyContext().getSettings();
            ParserSettings parserSettings = new ModuleScopedParserSettings(ivySettings, resolver, moduleRevisionId);
            ModuleDescriptorParser parser = parserRegistry.forResource(resource);
            return parser.parseDescriptor(parserSettings, new URL(moduleDescriptorFile.toURI().toASCIIString()), resource, false);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

}
