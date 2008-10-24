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
package org.gradle.api.dependencies.maven;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.dependencies.maven.Conf2ScopeMappingContainer;

import java.io.File;
import java.util.List;

/**
 * Is used for generating a Maven pom file and customizing the generation.
 * To learn about the Maven pom see: <a href="http://maven.apache.org/pom.html">http://maven.apache.org/pom.html</a>
 *
 * @author Hans Dockter
 */
public interface MavenPom {
    /**
     * Returns the scope mappings used for generating this pom.
     */
    Conf2ScopeMappingContainer getScopeMappings();

    /**
     * Generates the pom and writes it into a file.
     * @param pomFile The file to write the generated pom to.
     */
    void toPomFile(File pomFile);

    /**
     * @see #setGroupId(String)
     */
    String getGroupId();

    /**
     * Sets the groupId property of the to be generated Maven pom.
     */
    void setGroupId(String groupId);

    /**
     * @see #setArtifactId(String)
     */
    String getArtifactId();

    /**
     * Sets the artifactId property of the to be generated Maven pom.
     */
    void setArtifactId(String artifactId);

    /**
     * @see #setVersion(String) 
     */
    String getVersion();

    /**
     * Sets the version property of the to be generated Maven pom.
     */
    void setVersion(String version);

    /**
     * @see #setClassifier(String)  
     */
    String getClassifier();

    /**
     * Sets the classifier property of the to be generated Maven pom.
     */
    void setClassifier(String classifier);

    /**
     * @see #setPackaging(String)
     */
    String getPackaging();

    /**
     * Sets the packaging property of the to be generated Maven pom.
     */
    void setPackaging(String packaging);

    /**
     * @see #setLicenseHeader(String)
     */
    String getLicenseHeader();

    /**
     * Sets the licenseHeader property of the to be generated Maven pom.
     */
    void setLicenseHeader(String licenseHeader);

    /**
     * @see #setDependencies(java.util.List)
     */
    List<DependencyDescriptor> getDependencies();

    /**
     * Sets the Ivy dependency descriptors as the dependencies to be transformed into pom dependencies.
     */
    //todo Use Gradle dependency objects instead of Ivy ones
    void setDependencies(List<DependencyDescriptor> dependencyDescriptors);
}
