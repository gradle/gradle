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
package org.gradle.api.internal.dependencies.maven;

import org.gradle.api.dependencies.maven.MavenPom;
import org.gradle.api.dependencies.maven.Conf2ScopeMappingContainer;
import org.gradle.api.internal.dependencies.maven.PomFileWriter;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;

import java.io.File;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class DefaultMavenPom implements MavenPom {
    private PomFileWriter pomFileWriter;
    private Conf2ScopeMappingContainer scopeMappings;
    private String groupId;
    private String artifactId;
    private String version;
    private String classifier;
    private String packaging;
    private String licenseHeader;

    public DefaultMavenPom(PomFileWriter pomFileWriter, Conf2ScopeMappingContainer scopeMappings) {
        this.pomFileWriter = pomFileWriter;
        this.scopeMappings = scopeMappings;
    }

    public Conf2ScopeMappingContainer getScopeMappings() {
        return scopeMappings;
    }

    public void toPomFile(File pomFile, List<DependencyDescriptor> dependencies) {
        pomFileWriter.write(this, dependencies, pomFile);
    }

    public PomFileWriter getPomModuleDescriptorFileWriter() {
        return pomFileWriter;
    }

    public void setPomModuleDescriptorFileWriter(PomFileWriter pomFileWriter) {
        this.pomFileWriter = pomFileWriter;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public String getLicenseHeader() {
        return licenseHeader;
    }

    public void setLicenseHeader(String licenseHeader) {
        this.licenseHeader = licenseHeader;
    }
}
