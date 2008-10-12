/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.dependencies.ivy2Maven;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;

import java.io.PrintWriter;

/**
 * @author Hans Dockter
 */
public class DefaultPomModuleDescriptorModuleIdWriter implements PomModuleDescriptorModuleIdWriter {

    public void convert(ModuleDescriptor moduleDescriptor, String packaging, PrintWriter out) {
        assert moduleDescriptor != null;
        ModuleRevisionId mrid = moduleDescriptor.getModuleRevisionId();
        assert mrid.getOrganisation() != null && mrid.getName() != null && mrid.getRevision() != null;
        out.println(enclose(PomModuleDescriptorWriter.GROUP_ID, mrid.getOrganisation()));
        out.println(enclose(PomModuleDescriptorWriter.ARTIFACT_ID, mrid.getName()));
        out.println(enclose(PomModuleDescriptorWriter.VERSION, mrid.getRevision()));
        if (packaging != null) {
            out.println(enclose(PomModuleDescriptorWriter.PACKAGING, packaging));
        }
        if (moduleDescriptor.getHomePage() != null) {
            out.println(enclose(PomModuleDescriptorWriter.HOME_PAGE, moduleDescriptor.getHomePage()));
        }
    }

    private String enclose(String tagValue, String text) {
        return XmlHelper.enclose(PomModuleDescriptorWriter.DEFAULT_INDENT, tagValue, text);
    }
}
