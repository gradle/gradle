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
import org.gradle.api.Incubating;
import org.gradle.api.XmlProvider;
import org.gradle.internal.HasInternalProtocol;

/**
 * The POM for a Maven publication.
 *
 * The {@link #withXml(org.gradle.api.Action)} method can be used to modify the descriptor after it has been generated according to the publication data.
 *
 * @since 1.4
 */
@Incubating
@HasInternalProtocol
public interface MavenPom {

    /**
     * Allows configuration of the POM, after it has been generated according to the input data.
     *
     * <pre autoTested="true">
     * apply plugin: "maven-publish"
     *
     * publishing {
     *   publications {
     *     maven(MavenPublication) {
     *       pom.withXml {
     *         asNode().appendNode('description', 'A demonstration of Maven POM customization')
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

    /**
     * Returns the packaging for this publication.
     */
    String getPackaging();

    /**
     * Sets the packaging for this publication.
     */
    void setPackaging(String packaging);


}
