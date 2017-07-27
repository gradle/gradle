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
import groovy.lang.GroovyObject;
import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.ConfigurationContainer;

import java.io.Writer;
import java.util.List;

/**
 * Is used for generating a Maven POM file and customizing the generation.
 * To learn about the Maven POM see: <a href="http://maven.apache.org/pom.html">http://maven.apache.org/pom.html</a>
 */
public interface MavenPom {

    String POM_FILE_ENCODING = "UTF-8";

    /**
     * Returns the scope mappings used for generating this POM.
     */
    Conf2ScopeMappingContainer getScopeMappings();

    /**
     * Provides a builder for the Maven POM for adding or modifying properties of the Maven {@link #getModel()}.
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
     * @return this
     */
    MavenPom project(Closure pom);

    /**
     * Provides a builder for the Maven POM for adding or modifying properties of the Maven {@link #getModel()}.
     * The syntax is exactly the same as used by polyglot Maven.
     *
     * @return this
     *
     * @see #project(Closure)
     * @since 4.1
     */
    MavenPom project(Action<? super GroovyObject> pom);

    /**
     * Returns the group id for this POM.
     *
     * @see org.apache.maven.model.Model#setGroupId(String)
     */
    String getGroupId();

    /**
     * Sets the group id for this POM.
     *
     * @see org.apache.maven.model.Model#getGroupId
     * @return this
     */
    MavenPom setGroupId(String groupId);

    /**
     * Returns the artifact id for this POM.
     *
     * @see org.apache.maven.model.Model#getArtifactId()
     */
    String getArtifactId();

    /**
     * Sets the artifact id for this POM.
     *
     * @see org.apache.maven.model.Model#setArtifactId(String)
     * @return this
     */
    MavenPom setArtifactId(String artifactId);

    /**
     * Returns the version for this POM.
     *
     * @see org.apache.maven.model.Model#getVersion()
     */
    String getVersion();

    /**
     * Sets the version for this POM.
     *
     * @see org.apache.maven.model.Model#setVersion(String)
     * @return this
     */
    MavenPom setVersion(String version);

    /**
     * Returns the packaging for this POM.
     *
     * @see org.apache.maven.model.Model#getPackaging()
     */
    String getPackaging();

    /**
     * Sets the packaging for this POM.
     *
     * @see org.apache.maven.model.Model#setPackaging(String)
     * @return this
     */
    MavenPom setPackaging(String packaging);

    /**
     * Sets the dependencies for this POM.
     *
     * @see org.apache.maven.model.Model#setDependencies(java.util.List)
     * @return this
     */
    MavenPom setDependencies(List<?> dependencies);

    /**
     * Returns the dependencies for this POM.
     *
     * @see org.apache.maven.model.Model#getDependencies()
     */
    List<?> getDependencies();

    /**
     * Returns the underlying native Maven {@link org.apache.maven.model.Model} object. The MavenPom object
     * delegates all the configuration information to this object. There Gradle MavenPom objects provides
     * delegation methods just for setting the groupId, artifactId, version and packaging. For all other
     * elements, either use the model object or {@link #project(groovy.lang.Closure)}.
     *
     * @return the underlying native Maven object
     */
    Object getModel();

    /**
     * Sets the underlying native Maven {@link org.apache.maven.model.Model} object.
     *
     * @return this
     * @see #getModel()
     */
    MavenPom setModel(Object model);

    /**
     * Writes the {@link #getEffectivePom()} XML to a writer while applying the {@link #withXml(org.gradle.api.Action)} actions. Closes the supplied
     * Writer when finished.
     *
     * @param writer The writer to write the POM to.
     * @return this
     */
    MavenPom writeTo(Writer writer);

    /**
     * Writes the {@link #getEffectivePom()} XML to a file while applying the {@link #withXml(org.gradle.api.Action)} actions.
     * The path is resolved as defined by {@link org.gradle.api.Project#files(Object...)}
     * The file will be encoded as UTF-8.
     *
     * @param path The path of the file to write the POM into.
     * @return this
     */
    MavenPom writeTo(Object path);

    /**
     * <p>Adds a closure to be called when the POM has been configured. The POM is passed to the closure as a
     * parameter.</p>
     *
     * @param closure The closure to execute when the POM has been configured.
     * @return this
     */
    MavenPom whenConfigured(Closure closure);

    /**
     * <p>Adds an action to be called when the POM has been configured. The POM is passed to the action as a
     * parameter.</p>
     *
     * @param action The action to execute when the POM has been configured.
     * @return this
     */
    MavenPom whenConfigured(Action<MavenPom> action);

    /**
     * <p>Adds a closure to be called when the POM XML has been created. The XML is passed to the closure as a
     * parameter in form of a {@link org.gradle.api.XmlProvider}. The action can modify the XML.</p>
     *
     * @param closure The closure to execute when the POM XML has been created.
     * @return this
     */
    MavenPom withXml(Closure closure);

    /**
     * <p>Adds an action to be called when the POM XML has been created. The XML is passed to the action as a
     * parameter in form of a {@link org.gradle.api.XmlProvider}. The action can modify the XML.</p>
     *
     * @param action The action to execute when the POM XML has been created.
     * @return this
     */
    MavenPom withXml(Action<XmlProvider> action);

    /**
     * Returns the configuration container used for mapping configurations to Maven scopes.
     */
    ConfigurationContainer getConfigurations();

    /**
     * Sets the configuration container used for mapping configurations to Maven scopes.
     * @return this
     */
    MavenPom setConfigurations(ConfigurationContainer configurations);

    /**
     * Returns a POM with the generated dependencies and the {@link #whenConfigured(org.gradle.api.Action)} actions applied.
     *
     * @return the effective POM
     */
    MavenPom getEffectivePom();
}
