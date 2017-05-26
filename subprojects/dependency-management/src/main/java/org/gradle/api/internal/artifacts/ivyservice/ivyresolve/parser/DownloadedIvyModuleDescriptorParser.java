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
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.internal.resource.local.FileResourceRepository;

public class DownloadedIvyModuleDescriptorParser extends IvyXmlModuleDescriptorParser {
    public DownloadedIvyModuleDescriptorParser(IvyModuleDescriptorConverter moduleDescriptorConverter, ImmutableModuleIdentifierFactory moduleIdentifierFactory, FileResourceRepository fileResourceRepository) {
        super(moduleDescriptorConverter, moduleIdentifierFactory, fileResourceRepository);
    }

    @Override
    protected void postProcess(DefaultModuleDescriptor moduleDescriptor) {
        moduleDescriptor.setDefault(false);
    }
}
