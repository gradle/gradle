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
import org.gradle.api.internal.dependencies.ivy2Maven.dependencies.Conf2ScopeMappingContainer;
import org.gradle.api.internal.dependencies.ivy2Maven.dependencies.PomModuleDescriptorDependenciesWriter;

import java.io.PrintWriter;

/**
 * @author Hans Dockter
 */
public class DefaultPomModuleDescriptorWriter implements PomModuleDescriptorWriter {
    private PomModuleDescriptorHeaderWriter headerWriter;
    private PomModuleDescriptorModuleIdWriter moduleIdWriter;

    private PomModuleDescriptorDependenciesWriter dependenciesWriter;
    private boolean skipDependenciesWithUnmappedConfiguration = true;

    public DefaultPomModuleDescriptorWriter(PomModuleDescriptorHeaderWriter headerWriter, PomModuleDescriptorModuleIdWriter moduleIdWriter,
                                            PomModuleDescriptorDependenciesWriter dependenciesWriter) {
        assert headerWriter != null && moduleIdWriter != null && dependenciesWriter != null;
        this.headerWriter = headerWriter;
        this.moduleIdWriter = moduleIdWriter;
        this.dependenciesWriter = dependenciesWriter;
    }

    public void convert(ModuleDescriptor moduleDescriptor, String packaging, String licenseHeader,
                        Conf2ScopeMappingContainer conf2ScopeMappingContainer, PrintWriter out) {
        headerWriter.convert(licenseHeader, out);
        moduleIdWriter.convert(moduleDescriptor, packaging, out);
        dependenciesWriter.convert(moduleDescriptor, skipDependenciesWithUnmappedConfiguration,
                conf2ScopeMappingContainer, out);
        out.println("</" + ROOT_ELEMENT_NAME + ">");
    }

    public PomModuleDescriptorHeaderWriter getHeaderWriter() {
        return headerWriter;
    }

    public void setHeaderWriter(PomModuleDescriptorHeaderWriter headerWriter) {
        this.headerWriter = headerWriter;
    }

    public PomModuleDescriptorModuleIdWriter getModuleIdWriter() {
        return moduleIdWriter;
    }

    public void setModuleIdWriter(PomModuleDescriptorModuleIdWriter moduleIdWriter) {
        this.moduleIdWriter = moduleIdWriter;
    }

    public PomModuleDescriptorDependenciesWriter getDependenciesWriter() {
        return dependenciesWriter;
    }

    public void setDependenciesWriter(PomModuleDescriptorDependenciesWriter dependenciesWriter) {
        this.dependenciesWriter = dependenciesWriter;
    }

    public boolean isSkipDependenciesWithUnmappedConfiguration() {
        return skipDependenciesWithUnmappedConfiguration;
    }

    public void setSkipDependenciesWithUnmappedConfiguration(boolean skipDependenciesWithUnmappedConfiguration) {
        this.skipDependenciesWithUnmappedConfiguration = skipDependenciesWithUnmappedConfiguration;
    }
}
