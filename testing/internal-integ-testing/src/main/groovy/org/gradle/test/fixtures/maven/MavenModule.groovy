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
package org.gradle.test.fixtures.maven

import org.gradle.test.fixtures.Module
import org.gradle.test.fixtures.ModuleArtifact
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.gradle.VariantMetadataSpec

interface MavenModule extends Module {
    /**
     * Publishes the pom.xml plus main artifact, plus any additional artifacts for this module. Publishes only those artifacts whose content has changed since the last call to {@code # publish ( )}.
     *
     * @return this
     */
    MavenModule publish()

    /**
     * Publishes the pom.xml only
     */
    MavenModule publishPom()

    /**
     * Publishes the pom.xml plus main artifact, plus any additional artifacts for this module, with different content (and size) to any
     * previous publication.
     */
    MavenModule publishWithChangedContent()

    MavenModule withNonUniqueSnapshots()

    MavenModule withNoPom()

    MavenModule withoutExtraChecksums()

    MavenModule withExtraChecksums()

    /**
     * Include the Gradle module metadata file in the published module.
     * @return this
     */
    MavenModule withModuleMetadata()

    /**
     * Sets up this module and its variant to represent a publish Gradle platform.
     * It implies the publication of the Gradle module metadata.
     * This creates two platform variants named 'api' and 'runtime"
     *
     * @return this
     */
    MavenModule asGradlePlatform()

    MavenModule withoutGradleMetadataRedirection();

    MavenModule parent(String group, String artifactId, String version)

    MavenModule dependsOnModules(String... dependencyArtifactIds)

    MavenModule dependsOn(Module module)

    MavenModule dependsOn(Map<String, ?> attributes, Module module)

    MavenModule dependsOn(String group, String artifactId, String version)

    MavenModule dependsOn(String group, String artifactId, String version, String type, String scope, String classifier)

    MavenModule dependencyConstraint(Module module)

    MavenModule dependencyConstraint(Map<String, ?> attributes, Module module)

    MavenModule hasPackaging(String packaging)

    /**
     * Sets the type of the main artifact for this module.
     */
    MavenModule hasType(String type)

    /**
     * Define a variant with attributes. Variants are only published when using {@link #withModuleMetadata()}.
     */
    MavenModule variant(String variant, Map<String, String> attributes)

    /**
     * Define a variant with attributes. Variants are only published when using {@link #withModuleMetadata()}.
     */
    MavenModule variant(String variant, Map<String, String> attributes, @DelegatesTo(value= VariantMetadataSpec, strategy=Closure.DELEGATE_FIRST) Closure<?> variantConfiguration)

    /**
     * Clears all predefined variants
     */
    MavenModule adhocVariants()

    String getPublishArtifactVersion()

    String getGroupId()

    String getArtifactId()

    String getVersion()

    /**
     * Returns the path of this module relative to the root of the repository.
     */
    String getPath()

    /**
     * Returns the POM file of this module.
     */
    ModuleArtifact getPom()

    TestFile getPomFile()

    /**
     * Returns the main artifact of this module.
     */
    ModuleArtifact getArtifact()

    /**
     * Returns an artifact of this module, using Maven coordinates.
     *
     * @param options : 'type' and 'classifier'
     */
    ModuleArtifact getArtifact(Map<String, ?> options)

    /**
     * Returns a file relative to this module, using a relative path.
     */
    ModuleArtifact getArtifact(String relativePath)

    TestFile getArtifactFile()

    TestFile getArtifactFile(Map options)

    TestFile getMetaDataFile()

    MavenPom getParsedPom()

    ModuleArtifact getRootMetaData()

    ModuleArtifact getSnapshotMetaData()

    boolean getUniqueSnapshots()

    String getUniqueSnapshotVersion()

    /**
     * Asserts pom and module files have not been published.
     */
    void assertNotPublished()

    /**
     * Asserts pom and module files are published correctly. Does not verify artifacts.
     */
    void assertPublished()

    /**
     * Asserts exactly pom and jar published, along with checksums.
     * If created {@link #withModuleMetadata()}, module file is also expected.
     */
    void assertPublishedAsJavaModule()
}
