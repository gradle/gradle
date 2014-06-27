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

package org.gradle.test.fixtures.ivy;

import groovy.lang.Closure;
import org.gradle.test.fixtures.Module;
import org.gradle.test.fixtures.file.TestFile;

import java.util.Map;

public interface IvyModule extends Module {
    String getOrganisation();
    String getModule();
    String getRevision();

    TestFile getIvyFile();

    TestFile getJarFile();

    /**
     * Don't publish an ivy.xml for this module.
     */
    IvyModule withNoMetaData();

    IvyModule withStatus(String status);

    IvyModule dependsOn(String organisation, String module, String revision);

    IvyModule extendsFrom(Map<String, ?> attributes);

    IvyModule dependsOn(Map<String, ?> attributes);

    IvyModule artifact(Map<String, ?> options);

    /**
     * Adds an artifact that is not declared in the ivy.xml file.
     */
    IvyModule undeclaredArtifact(Map<String, ?> options);

    IvyModule withXml(Closure action);

    IvyModule configuration(String name);

    IvyModule configuration(Map<String, ?> options, String name);

    /**
     * Publishes ivy.xml plus all artifacts with different content (and size) to previous publication.
     */
    IvyModule publishWithChangedContent();

    /**
     * Publishes ivy.xml plus all artifacts. Publishes only those artifacts whose content has changed since the
     * last call to {@code #publish()}.
     */
    IvyModule publish();

    IvyDescriptor getParsedIvy();

    /**
     * Assert that exactly the ivy.xml and jar file for this module, plus checksum files, have been published.
     */
    void assertIvyAndJarFilePublished();
}
