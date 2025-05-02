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
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.Map;

import static org.gradle.api.internal.artifacts.ivyservice.IvyUtil.createModuleRevisionId;

public class DisconnectedIvyXmlModuleDescriptorParser extends IvyXmlModuleDescriptorParser {
    private final IvyModuleDescriptorConverter moduleDescriptorConverter;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final IvyMutableModuleMetadataFactory metadataFactory;

    public DisconnectedIvyXmlModuleDescriptorParser(IvyModuleDescriptorConverter moduleDescriptorConverter,
                                                    ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                                    FileResourceRepository fileResourceRepository,
                                                    IvyMutableModuleMetadataFactory metadataFactory) {
        super(moduleDescriptorConverter, moduleIdentifierFactory, fileResourceRepository, metadataFactory);
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.metadataFactory = metadataFactory;
    }

    @Override
    protected Parser createParser(DescriptorParseContext parseContext, LocallyAvailableExternalResource resource, Map<String, String> properties) throws MalformedURLException {
        return new DisconnectedParser(parseContext, moduleDescriptorConverter, resource, resource.getFile().toURI().toURL(), properties, moduleIdentifierFactory, metadataFactory);
    }

    private static class DisconnectedParser extends Parser {
        private final IvyModuleDescriptorConverter moduleDescriptorConverter;
        private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
        private final IvyMutableModuleMetadataFactory metadataFactory;

        public DisconnectedParser(DescriptorParseContext parseContext,
                                  IvyModuleDescriptorConverter moduleDescriptorConverter,
                                  ExternalResource res,
                                  URL descriptorURL,
                                  Map<String, String> properties,
                                  ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                  IvyMutableModuleMetadataFactory metadataFactory) {
            super(parseContext, moduleDescriptorConverter, res, descriptorURL, moduleIdentifierFactory, metadataFactory, properties);
            this.moduleDescriptorConverter = moduleDescriptorConverter;
            this.moduleIdentifierFactory = moduleIdentifierFactory;
            this.metadataFactory = metadataFactory;
        }

        @Override
        public Parser newParser(ExternalResource res, URL descriptorURL) {
            Parser parser = new DisconnectedParser(getParseContext(), moduleDescriptorConverter, res, descriptorURL, properties, moduleIdentifierFactory, metadataFactory);
            parser.setValidate(isValidate());
            return parser;
        }

        @Override
        protected ModuleDescriptor parseOtherIvyFile(String parentOrganisation,
                                                     String parentModule, String parentRevision) throws IOException, ParseException, SAXException {
            ModuleRevisionId parentMrid = createModuleRevisionId(parentOrganisation, parentModule, parentRevision);
            return new DefaultModuleDescriptor(parentMrid, "release", new Date());
        }
    }
}
