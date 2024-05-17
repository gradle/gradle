/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.apache.ivy.core.module.descriptor.Configuration
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import spock.lang.Specification

import static com.google.common.collect.Sets.newHashSet

class IvyModuleResolveMetadataBuilderTest extends Specification {

    def md = new DefaultModuleDescriptor(ModuleRevisionId.newInstance("org", "foo", "1.0"), "release", null)
    def ivyMetadataFactory = DependencyManagementTestUtil.ivyMetadataFactory()
    def meta = new IvyModuleResolveMetaDataBuilder(md, new IvyModuleDescriptorConverter(new DefaultImmutableModuleIdentifierFactory()), ivyMetadataFactory)

    def "adds correct artifact to meta-data"() {
        def a = ivyArtifact("foo", "jar", "ext", "classifier")
        md.addConfiguration(new Configuration("runtime"))

        when: meta.addArtifact(a, newHashSet("runtime"))

        then:
        def artifacts = meta.build().asImmutable().getConfiguration("runtime").artifacts
        artifacts.size() == 1
        artifacts[0].name.name == "foo"
        artifacts[0].name.type == "jar"
        artifacts[0].name.extension == "ext"
        artifacts[0].name.classifier == "classifier"
    }

    private static IvyArtifactName ivyArtifact(String name, String type, String ext, String classifier = null) {
        return new DefaultIvyArtifactName(name, type, ext, classifier)
    }

    def "prevents adding artifact without configurations"() {
        def unattached = ivyArtifact("foo", "jar", "ext")
        md.addConfiguration(new Configuration("runtime"))

        when:
        meta.addArtifact(unattached, newHashSet())
        meta.build()

        then: thrown(IllegalArgumentException)
    }

    def "can be added to metadata that already contains artifacts"() {
        def a1 = ivyArtifact("foo", "jar", "jar")
        def a2 = ivyArtifact("foo-all", "zip", "zip")

        md.addConfiguration(new Configuration("runtime"))
        md.addConfiguration(new Configuration("testUtil"))

        when:
        meta.addArtifact(a1, newHashSet("runtime"))
        meta.addArtifact(a2, newHashSet("testUtil"))

        then:
        def resolveMetaData = meta.build().asImmutable()
        def runtimeArtifacts = resolveMetaData.getConfiguration("runtime").artifacts
        def testArtifacts = resolveMetaData.getConfiguration("testUtil").artifacts

        runtimeArtifacts*.id.displayName as Set == ["foo-1.0.jar (org:foo:1.0)"] as Set
        testArtifacts*.id.displayName as Set == ["foo-all-1.0.zip (org:foo:1.0)"] as Set
    }

    def "can be added to metadata that already contains the same artifact in different configuration"() {
        def a1 = ivyArtifact("foo", "jar", "jar")
        //some publishers create ivy metadata that contains separate entries for the same artifact but different configurations
        def a2 = ivyArtifact("foo", "jar", "jar")

        md.addConfiguration(new Configuration("runtime"))
        md.addConfiguration(new Configuration("archives"))

        when:
        meta.addArtifact(a1, newHashSet("archives"))
        meta.addArtifact(a2, newHashSet("runtime"))

        then:
        def resolveMetaData = meta.build().asImmutable()
        def runtimeArtifacts = resolveMetaData.getConfiguration("runtime").artifacts
        def archivesArtifacts = resolveMetaData.getConfiguration("archives").artifacts

        runtimeArtifacts*.id.displayName as Set == ["foo-1.0.jar (org:foo:1.0)"] as Set
        archivesArtifacts*.id.displayName as Set == ["foo-1.0.jar (org:foo:1.0)"] as Set
    }
}
