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
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Date;

public class DisconnectedIvyXmlModuleDescriptorParser extends IvyXmlModuleDescriptorParser {
    @Override
    protected Parser createParser(DescriptorParseContext parseContext, LocallyAvailableExternalResource resource) throws MalformedURLException {
        return new DisconnectedParser(parseContext, resource, resource.getLocalResource().getFile().toURI().toURL());
    }

    private static class DisconnectedParser extends Parser {
        public DisconnectedParser(DescriptorParseContext parseContext, ExternalResource res, URL descriptorURL) {
            super(parseContext, res, descriptorURL);
        }

        @Override
        public Parser newParser(ExternalResource res, URL descriptorURL) {
            Parser parser = new DisconnectedParser(getParseContext(), res, descriptorURL);
            parser.setValidate(isValidate());
            return parser;
        }

        @Override
        protected ModuleDescriptor parseOtherIvyFile(String parentOrganisation,
                                                     String parentModule, String parentRevision) throws IOException, ParseException, SAXException {
            ModuleId parentModuleId = new ModuleId(parentOrganisation, parentModule);
            ModuleRevisionId parentMrid = new ModuleRevisionId(parentModuleId, parentRevision);
            return new DefaultModuleDescriptor(parentMrid, "release", new Date());
        }
    }
}