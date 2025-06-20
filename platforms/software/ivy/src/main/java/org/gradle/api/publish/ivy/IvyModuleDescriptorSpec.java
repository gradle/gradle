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

package org.gradle.api.publish.ivy;

import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

/**
 * The descriptor of any Ivy publication.
 * <p>
 * Corresponds to the <a href="http://ant.apache.org/ivy/history/latest-milestone/ivyfile.html">XML version of the Ivy Module Descriptor</a>.
 * <p>
 * The {@link #withXml(org.gradle.api.Action)} method can be used to modify the descriptor after it has been generated according to the publication data.
 * However, the preferred way to customize the project information to be published is to use the dedicated configuration methods exposed by this class, e.g.
 * {@link #description(Action)}.
 *
 * @since 1.3
 */
@HasInternalProtocol
public interface IvyModuleDescriptorSpec {

    /**
     * Allow configuration of the descriptor, after it has been generated according to the input data.
     *
     * <pre class='autoTested'>
     * plugins {
     *     id 'ivy-publish'
     * }
     *
     * publishing {
     *   publications {
     *     ivy(IvyPublication) {
     *       descriptor {
     *         withXml {
     *           asNode().dependencies.dependency.find { it.@org == "junit" }.@rev = "4.10"
     *         }
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
     * For details on the structure of the XML to be modified, see <a href="http://ant.apache.org/ivy/history/latest-milestone/ivyfile.html">the
     * Ivy Module Descriptor reference</a>.
     *
     *
     * @param action The configuration action.
     * @see IvyPublication
     * @see XmlProvider
     */
    void withXml(Action<? super XmlProvider> action);

    /**
     * The status for this publication.
     */
    @Optional
    @ReplacesEagerProperty
    Property<String> getStatus();

    /**
     * The branch for this publication
     */
    @Optional
    @ReplacesEagerProperty
    Property<String> getBranch();

    /**
     * Returns the extra info element spec for this publication
     */
    @Nested
    IvyExtraInfoSpec getExtraInfo();

    /**
     * Adds a new extra info element to the publication
     */
    void extraInfo(String namespace, String elementName, String value);

    /**
     * Creates, configures and adds a license to this publication.
     *
     * @since 4.8
     */
    void license(Action<? super IvyModuleDescriptorLicense> action);

    /**
     * Creates, configures and adds an author to this publication.
     *
     * @since 4.8
     */
    void author(Action<? super IvyModuleDescriptorAuthor> action);

    /**
     * Configures the description for this publication.
     *
     * @since 4.8
     */
    void description(Action<? super IvyModuleDescriptorDescription> action);

}
