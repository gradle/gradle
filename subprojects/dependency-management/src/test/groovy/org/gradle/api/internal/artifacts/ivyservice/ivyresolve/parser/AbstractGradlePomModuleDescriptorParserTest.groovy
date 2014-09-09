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

import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ArtifactRevisionId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

abstract class AbstractGradlePomModuleDescriptorParserTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final GradlePomModuleDescriptorParser parser = new GradlePomModuleDescriptorParser()
    final parseContext = Mock(DescriptorParseContext)
    TestFile pomFile

    def "setup"() {
        pomFile = tmpDir.file('foo')
    }

    protected ModuleDescriptor parsePom() {
        parseMetaData().descriptor
    }

    protected MutableModuleVersionMetaData parseMetaData() {
        parser.parseMetaData(parseContext, pomFile, true)
    }

    protected void hasArtifact(ModuleDescriptor descriptor, String name, String type, String ext, String classifier = null) {
        assert descriptor.allArtifacts.length == 1
        def artifact = descriptor.allArtifacts.first()
        assert artifact.id == artifactId(descriptor.moduleRevisionId, name, type, ext)
        assert artifact.extraAttributes['classifier'] == classifier
    }

    protected void hasDefaultDependencyArtifact(DependencyDescriptor descriptor) {
        assert descriptor.allDependencyArtifacts.length == 0
    }

    protected void hasDependencyArtifact(DependencyDescriptor descriptor, String name, String type, String ext, String classifier = null) {
        assert descriptor.allDependencyArtifacts.length == 1
        def artifact = descriptor.allDependencyArtifacts.first()
        assert artifact.name == name
        assert artifact.type == type
        assert artifact.ext == ext
        assert artifact.extraAttributes['classifier'] == classifier
    }

    protected ModuleRevisionId moduleId(String group, String name, String version) {
        IvyUtil.createModuleRevisionId(group, name, version)
    }

    protected ArtifactRevisionId artifactId(ModuleRevisionId moduleId, String name, String type, String ext) {
        ArtifactRevisionId.newInstance(moduleId, name, type, ext)
    }
}
