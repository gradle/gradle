/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.ModuleConfigurationMetadata
import spock.lang.Specification

class ModuleConfigurationMetadataTest extends Specification {
    private static final ModuleComponentIdentifier ORG_BAR_ID = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org", "bar"), "1.0")

    def "returns declared artifact with same name and file name"() {
        def declared = urlBackedArtifact("bar-1.0.jar", "bar-1.0.jar")
        def other = urlBackedArtifact("bar-1.0-sources.jar", "bar-1.0-sources.jar")

        when:
        def artifact = configurationMetadata([other, declared]).artifact(barArtifactName("jar", "jar"))

        then:
        artifact.is(declared)
    }

    def "returns declared artifact with same name, file name and classifier"() {
        def declared = urlBackedArtifact("bar-1.0-sources.jar", "bar-1.0-sources.jar")

        when:
        def artifact = configurationMetadata([declared]).artifact(barArtifactName("jar", "jar", "sources"))

        then:
        artifact.is(declared)
    }

    def "creates new artifact when no declared artifact matches #scenario"() {
        when:
        def artifact = configurationMetadata(declared).artifact(requested)

        then:
        artifact instanceof DefaultModuleComponentArtifactMetadata
        artifact.id == new DefaultModuleComponentArtifactIdentifier(ORG_BAR_ID, requested)

        where:
        scenario                              | declared                                             | requested
        "no declared artifacts"               | []                                                   | barArtifactName("jar", "jar")
        "different classifier"                | [urlBackedArtifact("bar-1.0.jar", "bar-1.0.jar")]    | barArtifactName("jar", "jar", "sources")
        "different extension"                 | [urlBackedArtifact("bar-1.0.jar", "bar-1.0.jar")]    | barArtifactName("zip", "zip")
        "type different from extension"       | [urlBackedArtifact("bar-1.0.jar", "bar-1.0.jar")]    | barArtifactName("bundle", "jar")
        "empty classifier"                    | [urlBackedArtifact("bar-1.0.jar", "bar-1.0.jar")]    | barArtifactName("jar", "jar", "")
        "different file name"                 | [urlBackedArtifact("other.jar", "bar-1.0.jar")]      | barArtifactName("jar", "jar")
        "url in a directory"                  | [urlBackedArtifact("bar-1.0.jar", "../bar-1.0.jar")] | barArtifactName("jar", "jar")
        "declared artifact is not url backed" | [defaultArtifact(barArtifactName("jar", "jar"))]     | barArtifactName("jar", "jar")
        "declared artifact is optional"       | [optionalArtifact(barArtifactName("jar", "jar"))]    | barArtifactName("jar", "jar")
    }

    private ModuleConfigurationMetadata configurationMetadata(List<? extends ComponentArtifactMetadata> declared) {
        def variant = Stub(ComponentVariant) {
            getName() >> "compile"
            getArtifacts() >> ImmutableList.copyOf(declared)
        }
        new AbstractVariantBackedConfigurationMetadata(ORG_BAR_ID, variant, [])
    }

    private static UrlBackedArtifactMetadata urlBackedArtifact(String fileName, String relativeUrl) {
        new UrlBackedArtifactMetadata(ORG_BAR_ID, fileName, relativeUrl)
    }

    private static DefaultModuleComponentArtifactMetadata defaultArtifact(IvyArtifactName name) {
        new DefaultModuleComponentArtifactMetadata(ORG_BAR_ID, name)
    }

    private static ModuleComponentOptionalArtifactMetadata optionalArtifact(IvyArtifactName name) {
        new ModuleComponentOptionalArtifactMetadata(ORG_BAR_ID, name)
    }

    private static IvyArtifactName barArtifactName(String type, String extension, String classifier = null) {
        new DefaultIvyArtifactName("bar", type, extension, classifier)
    }
}
