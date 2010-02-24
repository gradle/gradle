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
package org.gradle.api.artifacts.maven;

import org.apache.maven.project.MavenProject;
import org.gradle.api.artifacts.Configuration;

import java.io.Writer;
import java.util.Set;

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
     * @see #setPackaging(String)
     */
    String getPackaging();

    /**
     * Sets the packaging property of the to be generated Maven pom.
     */
    void setPackaging(String packaging);

    MavenProject getMavenProject();

    void addDependencies(Set<Configuration> configurations);

    void write(Writer pomWriter);
}
