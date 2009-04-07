/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter;

import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.artifacts.DefaultModule;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
public class DefaultModuleDescriptorFactoryTest {
    @Test
    public void testCreateModuleDescriptor() {
        Module module = new DefaultModule("org", "name", "version", "status");
        DefaultModuleDescriptor moduleDescriptor = new DefaultModuleDescriptorFactory().createModuleDescriptor(module);
        assertThat(moduleDescriptor.getModuleRevisionId().getOrganisation(), equalTo(module.getGroup()));
        assertThat(moduleDescriptor.getModuleRevisionId().getName(), equalTo(module.getName()));
        assertThat(moduleDescriptor.getModuleRevisionId().getRevision(), equalTo(module.getVersion()));
        assertThat(moduleDescriptor.getStatus(), equalTo(module.getStatus()));
        assertThat(moduleDescriptor.getPublicationDate(), equalTo(null));
    }
}
