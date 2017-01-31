/*
 * Copyright 2013 the original author or authors.
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

import org.apache.ivy.core.module.id.ArtifactRevisionId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.internal.component.external.descriptor.ModuleDescriptorState
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.MutableMavenModuleResolveMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

abstract class AbstractGradlePomModuleDescriptorParserTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final ImmutableModuleIdentifierFactory moduleIdentifierFactory = Mock() {
        module(_, _) >> { args ->
            DefaultModuleIdentifier.newId(*args)
        }
    }
    final ModuleExclusions moduleExclusions = new ModuleExclusions(moduleIdentifierFactory)
    final GradlePomModuleDescriptorParser parser = new GradlePomModuleDescriptorParser(new DefaultVersionSelectorScheme(), moduleIdentifierFactory, moduleExclusions)
    final parseContext = Mock(DescriptorParseContext)
    TestFile pomFile
    ModuleDescriptorState descriptor
    MutableMavenModuleResolveMetadata metadata

    def "setup"() {
        pomFile = tmpDir.file('foo')
    }

    protected void parsePom() {
        metadata = parseMetaData()
        descriptor = metadata.descriptor
    }

    protected MutableMavenModuleResolveMetadata parseMetaData() {
        parser.parseMetaData(parseContext, pomFile, true)
    }

    protected void hasDefaultDependencyArtifact(DependencyMetadata descriptor) {
        assert descriptor.dependencyArtifacts.empty
    }

    protected void hasDependencyArtifact(DependencyMetadata descriptor, String name, String type, String ext, String classifier = null) {
        def artifact = single(descriptor.dependencyArtifacts).artifactName
        assert artifact.name == name
        assert artifact.type == type
        assert artifact.extension == ext
        assert artifact.classifier == classifier
    }

    protected static ModuleComponentIdentifier componentId(String group, String name, String version) {
        DefaultModuleComponentIdentifier.newId(group, name, version)
    }

    protected static ModuleVersionSelector moduleId(String group, String name, String version) {
        DefaultModuleVersionSelector.newSelector(group, name, version)
    }

    protected ArtifactRevisionId artifactId(ModuleRevisionId moduleId, String name, String type, String ext) {
        ArtifactRevisionId.newInstance(moduleId, name, type, ext)
    }

    static <T> T single(Iterable<T> elements) {
        assert elements.size() == 1
        return elements.first()
    }
}
