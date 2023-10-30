/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven;

import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.internal.HasInternalProtocol;

/**
 * The POM for a Maven publication.
 *
 * <p>The {@link #withXml(org.gradle.api.Action)} method can be used to modify the
 * descriptor after it has been generated according to the publication data.
 * However, the preferred way to customize the project information to be published
 * is to use the dedicated properties exposed by this class, e.g.
 * {@link #getDescription()}. Please refer to the official
 * <a href="https://maven.apache.org/pom.html">POM Reference</a> for detailed
 * information about the individual properties.
 *
 * @since 1.4
 */
@HasInternalProtocol
public interface MavenPom {

    /**
     * Returns the packaging (for example: jar, war) for the publication represented by this POM.
     */
    String getPackaging();

    /**
     * Sets the packaging for the publication represented by this POM.
     */
    void setPackaging(String packaging);

    /**
     * The name for the publication represented by this POM.
     *
     * @since 4.8
     */
    Property<String> getName();

    /**
     * A short, human-readable description for the publication represented by this POM.
     *
     * @since 4.8
     */
    Property<String> getDescription();

    /**
     * The URL of the home page for the project producing the publication represented by this POM.
     *
     * @since 4.8
     */
    Property<String> getUrl();

    /**
     * The year the project producing the publication represented by this POM was first created.
     *
     * @since 4.8
     */
    Property<String> getInceptionYear();

    /**
     * Configures the licenses for the publication represented by this POM.
     *
     * @since 4.8
     */
    void licenses(Action<? super MavenPomLicenseSpec> action);

    /**
     * Configures the organization for the publication represented by this POM.
     *
     * @since 4.8
     */
    void organization(Action<? super MavenPomOrganization> action);

    /**
     * Configures the developers for the publication represented by this POM.
     *
     * @since 4.8
     */
    void developers(Action<? super MavenPomDeveloperSpec> action);

    /**
     * Configures the contributors for the publication represented by this POM.
     *
     * @since 4.8
     */
    void contributors(Action<? super MavenPomContributorSpec> action);

    /**
     * Configures the SCM (source control management) for the publication represented by this POM.
     *
     * @since 4.8
     */
    void scm(Action<? super MavenPomScm> action);

    /**
     * Configures the issue management for the publication represented by this POM.
     *
     * @since 4.8
     */
    void issueManagement(Action<? super MavenPomIssueManagement> action);

    /**
     * Configures the CI management for the publication represented by this POM.
     *
     * @since 4.8
     */
    void ciManagement(Action<? super MavenPomCiManagement> action);

    /**
     * Configures the distribution management for the publication represented by this POM.
     *
     * @since 4.8
     */
    void distributionManagement(Action<? super MavenPomDistributionManagement> action);

    /**
     * Configures the mailing lists for the publication represented by this POM.
     *
     * @since 4.8
     */
    void mailingLists(Action<? super MavenPomMailingListSpec> action);

    /**
     * Returns the properties for the publication represented by this POM.
     *
     * @since 5.3
     */
    MapProperty<String, String> getProperties();

    /**
     * Allows configuration of the POM, after it has been generated according to the input data.
     *
     * <pre class='autoTested'>
     * plugins {
     *     id 'maven-publish'
     * }
     *
     * publishing {
     *   publications {
     *     maven(MavenPublication) {
     *       pom.withXml {
     *         asNode().appendNode('properties').appendNode('my-property', 'my-value')
     *       }
     *     }
     *   }
     * }
     * </pre>
     *
     * Note that due to Gradle's internal type conversion system, you can pass a Groovy closure to this method and
     * it will be automatically converted to an {@code Action}.
     * <p>
     * Each action/closure passed to this method will be stored as a callback, and executed when the publication
     * that this descriptor is attached to is published.
     * <p>
     * For details on the structure of the XML to be modified, see <a href="http://maven.apache.org/pom.html">the POM reference</a>.
     *
     * @param action The configuration action.
     * @see MavenPublication
     * @see XmlProvider
     */
    void withXml(Action<? super XmlProvider> action);

}
