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
package org.gradle.api.internal.dependencies.ivy.moduleconverter;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor;
import org.gradle.util.HelperUtil;
import org.hamcrest.Matchers;

import java.util.Date;

/**
 * @author Hans Dockter
 */
public class DefaultModuleDescriptorFactoryTest {
    @Test
    public void testCreateModuleDescriptor() {
        ModuleRevisionId testModuleRevisionId = ModuleRevisionId.newInstance("org", "name", "version");
        String testStatus = "testStatus";
        Date testDate = new Date();
        DefaultModuleDescriptor moduleDescriptor = new DefaultModuleDescriptorFactory().createModuleDescriptor(
                testModuleRevisionId,
                testStatus,
                testDate);
        assertThat(moduleDescriptor.getModuleRevisionId(), Matchers.equalTo(testModuleRevisionId));
        assertThat(moduleDescriptor.getStatus(), Matchers.equalTo(testStatus));
        assertThat(moduleDescriptor.getPublicationDate(), Matchers.equalTo(testDate));
    }
}
