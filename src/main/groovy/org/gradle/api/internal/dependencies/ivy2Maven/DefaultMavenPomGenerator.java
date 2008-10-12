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

import org.gradle.api.dependencies.MavenPomGenerator;
import org.gradle.api.internal.dependencies.ivy2Maven.PomModuleDescriptorFileWriter;
import org.gradle.api.internal.dependencies.ivy2Maven.dependencies.Conf2ScopeMappingContainer;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultMavenPomGenerator implements MavenPomGenerator {
    private PomModuleDescriptorFileWriter pomModuleDescriptorFileWriter;
    private Conf2ScopeMappingContainer scopeMappings;
    private String packaging;
    private String licenseHeader;

    public DefaultMavenPomGenerator(PomModuleDescriptorFileWriter pomModuleDescriptorFileWriter, Conf2ScopeMappingContainer scopeMappings) {
        this.pomModuleDescriptorFileWriter = pomModuleDescriptorFileWriter;
        this.scopeMappings = scopeMappings;
    }

    public String getPackaging() {
        return packaging;
    }

    public Conf2ScopeMappingContainer getScopeMappings() {
        return scopeMappings;
    }

    public MavenPomGenerator setPackaging(String packaging) {
        this.packaging = packaging;
        return this;
    }

    public String getLicenseHeader() {
        return licenseHeader;
    }

    public MavenPomGenerator setLicenseHeader(String licenseHeader) {
        this.licenseHeader = licenseHeader;
        return this;
    }

    public MavenPomGenerator toPomFile(ModuleDescriptor moduleDescriptor, File pomFile) {
        pomModuleDescriptorFileWriter.write(moduleDescriptor, getPackaging(), getLicenseHeader(), scopeMappings, pomFile);
        return this;
    }
}
