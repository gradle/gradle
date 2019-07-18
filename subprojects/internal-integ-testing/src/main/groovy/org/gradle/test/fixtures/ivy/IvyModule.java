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
import org.gradle.test.fixtures.ModuleArtifact;
import org.gradle.test.fixtures.file.TestFile;

import java.util.Map;

public interface IvyModule extends Module {
    String getOrganisation();
    @Override
    String getModule();
    String getRevision();

    ModuleArtifact getIvy();

    ModuleArtifact getJar();

    TestFile getIvyFile();

    TestFile getJarFile();

    TestFile getModuleMetadataFile();

    /**
     * Don't publish an ivy.xml / .module for this module.
     */
    IvyModule withNoMetaData();

    /**
     * Don't publish any ivy.xml for this module, but publish .module
     */
    IvyModule withNoIvyMetaData();

    IvyModule withStatus(String status);

    IvyModule withBranch(String branch);

    IvyModule dependsOn(String organisation, String module, String revision);

    IvyModule extendsFrom(Map<String, ?> attributes);

    IvyModule withGradleMetadataRedirection();

    /**
     * Attributes:
     *  organisation
     *  module
     *  revision
     *  revConstraint
     *  conf
     *  exclusions - list of maps: [[group: ?, module: ?], ...]
     */
    IvyModule dependsOn(Map<String, ?> attributes);

    IvyModule dependsOn(Module target);

    IvyModule dependsOn(Map<String, ?> attributes, Module target);

    IvyModule dependencyConstraint(Module module);

    IvyModule dependencyConstraint(Map<String, ?> attributes, Module module);

    /**
     * Options:
     *  name
     *  type
     *  classifier
     *  conf
     */
    IvyModule artifact(Map<String, ?> options);

    /**
     * Adds an artifact that is not declared in the ivy.xml file.
     */
    IvyModule undeclaredArtifact(Map<String, ?> options);

    IvyModule withXml(Closure action);

    IvyModule configuration(String name);

    /**
     * Options:
     *  extendsFrom
     *  transitive
     *  visibility
     */
    IvyModule configuration(Map<String, ?> options, String name);

    /**
     * Define a variant with attributes. Variants are only published when using {@link #withModuleMetadata()}.
     */
    IvyModule variant(String variant, Map<String, String> attributes);

    /**
     * Publishes ivy.xml plus all artifacts with different content (and size) to previous publication.
     */
    @Override
    IvyModule publishWithChangedContent();

    /**
     * Publishes ivy.xml plus all artifacts. Publishes only those artifacts whose content has changed since the
     * last call to {@code #publish()}.
     */
    @Override
    IvyModule publish();

    IvyDescriptor getParsedIvy();

    /**
     * Asserts that an ivy.xml is present
     */
    void assertPublished();

    /**
     * Asserts that exactly the given artifacts, plus checksum files, have been published.
     */
    void assertArtifactsPublished(String... names);

    /**
     * Assert that exactly the ivy.xml and jar file for this module, plus checksum files, have been published.
     */
    void assertIvyAndJarFilePublished();

    /**
     * Assert that exactly the module metadata file, ivy.xml and jar file for this module, plus checksum files, have been published.
     */
    void assertMetadataAndJarFilePublished();

    void assertPublishedAsJavaModule();

    void assertPublishedAsWebModule();
}
