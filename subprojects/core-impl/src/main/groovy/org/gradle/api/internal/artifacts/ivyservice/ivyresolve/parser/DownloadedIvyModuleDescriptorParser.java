/*
 * Copyright 2012 the original author or authors.
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
import org.apache.ivy.plugins.parser.ParserSettings;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;

import java.io.IOException;
import java.text.ParseException;

public class DownloadedIvyModuleDescriptorParser extends IvyXmlModuleDescriptorParser {
    @Override
    public DefaultModuleDescriptor parseDescriptor(ParserSettings ivySettings, LocallyAvailableExternalResource resource, boolean validate) throws ParseException, IOException {
        DefaultModuleDescriptor descriptor = super.parseDescriptor(ivySettings, resource, validate);
        descriptor.setDefault(false);
        return descriptor;
    }
}
