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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.LocallyAvailableExternalResource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;
import java.util.Map;

import static org.gradle.api.internal.artifacts.ivyservice.IvyUtil.createModuleRevisionId;

public class DisconnectedIvyXmlModuleDescriptorParser extends IvyXmlModuleDescriptorParser {
    public DisconnectedIvyXmlModuleDescriptorParser(ResolverStrategy resolverStrategy) {
        super(resolverStrategy);
    }

    @Override
    protected Parser createParser(DescriptorParseContext parseContext, LocallyAvailableExternalResource resource, Map<String, String> properties, ResolverStrategy resolverStrategy) throws MalformedURLException {
        return new DisconnectedParser(parseContext, resource, resource.getLocalResource().getFile().toURI().toURL(), properties, resolverStrategy);
    }

    private static class DisconnectedParser extends Parser {
        public DisconnectedParser(DescriptorParseContext parseContext, ExternalResource res, URL descriptorURL, Map<String, String> properties, ResolverStrategy resolverStrategy) {
            super(parseContext, res, descriptorURL, properties, resolverStrategy);
        }

        @Override
        public Parser newParser(ExternalResource res, URL descriptorURL) {
            Parser parser = new DisconnectedParser(getParseContext(), res, descriptorURL, properties, resolverStrategy);
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