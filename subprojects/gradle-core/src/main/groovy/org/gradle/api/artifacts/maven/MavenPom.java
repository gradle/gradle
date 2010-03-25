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

import groovy.lang.Closure;
import org.apache.maven.project.MavenProject;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;

import java.io.Writer;
import java.util.List;
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
     * Provides a builder for the Maven pom for adding or modifying properties of the MavenProject.
     * The syntax is exactly the same as used by polyglot Maven. For example:
     *
     * <pre>
     * pom.project {
     *    inceptionYear '2008'
     *    licenses {
     *       license {
     *          name 'The Apache Software License, Version 2.0'
     *          url 'http://www.apache.org/licenses/LICENSE-2.0.txt'
     *          distribution 'repo'
     *       }
     *    }
     * }
     * </pre>
     *
     * @param pom
     */
    void project(Closure pom);

    /**
     * @see org.apache.maven.project.MavenProject#setGroupId(String)
     */
    String getGroupId();

    /**
     * org.apache.maven.project.MavenProject#getGroupId
     */
    void setGroupId(String groupId);

    /**
     * @see org.apache.maven.project.MavenProject#getArtifactId()
     */
    String getArtifactId();

    /**
     * @see org.apache.maven.project.MavenProject#setArtifactId(String)
     */
    void setArtifactId(String artifactId);

    /**
     * @see org.apache.maven.project.MavenProject#getVersion()
     */
    String getVersion();

    /**
     * @see org.apache.maven.project.MavenProject#setVersion(String)
     */
    void setVersion(String version);

    /**
     * @see org.apache.maven.project.MavenProject#getPackaging()
     */
    String getPackaging();

    /**
     * @see org.apache.maven.project.MavenProject#setPackaging(String)
     */
    void setPackaging(String packaging);

    /**
     * @see org.apache.maven.project.MavenProject#setDependencies(java.util.List)
     */
    void setDependencies(List dependencies);

    /**
     * @see org.apache.maven.project.MavenProject#getDependencies()
     */
    List getDependencies();

    /**
     * Returns the underlying native Maven {@link org.apache.maven.project.MavenProject} object. The MavenPom object
     * delegates all the configuration information to this object. There are delegation methods only for a subset of
     * options. For configuring aspects of the pom where the MavenPom object does not provide delegation methods,
     * you can access the native Maven object directly.
     *
     * @return the underlying native Maven object
     */
    MavenProject getMavenProject();

    /**
     * Adds the pom dependency information from the Gradle dependency metadata.
     *
     * @param configurations The configuration from which the dependencies should be added to the pom.
     * @see #getScopeMappings()
     */
    void addDependencies(Set<Configuration> configurations);

    /**
     * Writes the generated pom.xml to a writer.
     *
     * @param writer The writer to write the pom xml.
     */
    void write(Writer writer);

    /**
     * <p>Adds a closure to be called when the pom has been configured. The pom is passed to the closure as a
     * parameter.</p>
     *
     * @param closure The closure to execute when the pom has been configured.
     */
    void whenConfigured(Closure closure);

    /**
     * <p>Adds an action to be called when the pom has been configured. The pom is passed to the action as a
     * parameter.</p>
     *
     * @param action The action to execute when the pom has been configured.
     */
    void whenConfigured(Action<MavenPom> action);

    /**
     * <p>Adds a closure to be called when the pom xml has been created. The xml is passed to the closure as a
     * parameter in form of a {@link org.gradle.api.artifacts.maven.XmlProvider}. The xml might be modified.</p>
     *
     * @param closure The closure to execute when the pom xml has been created.
     */
    void withXml(Closure closure);

    /**
     * <p>Adds an action to be called when the pom xml has been created. The xml is passed to the action as a
     * parameter in form of a {@link org.gradle.api.artifacts.maven.XmlProvider}. The xml might be modified.</p>
     *
     * @param action The action to execute when the pom xml has been created.
     */
    void withXml(Action<XmlProvider> action);
}
