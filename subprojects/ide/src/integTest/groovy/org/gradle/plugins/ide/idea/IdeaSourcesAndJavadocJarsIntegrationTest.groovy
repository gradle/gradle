/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.plugins.ide.idea

import org.apache.commons.io.FilenameUtils
import org.gradle.plugins.ide.AbstractSourcesAndJavadocJarsIntegrationTest
import org.gradle.plugins.ide.fixtures.IdeaModuleFixture
import org.gradle.test.fixtures.server.http.HttpArtifact

class IdeaSourcesAndJavadocJarsIntegrationTest extends AbstractSourcesAndJavadocJarsIntegrationTest {
    @Override
    String getIdeTask() {
        return "ideaModule"
    }

    void ideFileContainsEntry(String jar, List<String> sources, List<String> javadocs) {
        IdeaModuleFixture iml =  parseIml("root.iml")
        def libraryEntry = iml.dependencies.libraries.find { it.jarName == jar }
        assert libraryEntry != null : "entry for jar ${jar} not found"
        libraryEntry.assertHasSource(sources)
        libraryEntry.assertHasJavadoc(javadocs)
    }

    @Override
    void ideFileContainsGradleApiWithSources(String apiJarPrefix, String sourcesPath) {
        IdeaModuleFixture iml =  parseIml("root.iml")
        def libraryEntry = iml.dependencies.libraries.find { it.jarName.startsWith(apiJarPrefix) }
        assert libraryEntry != null : "gradle API jar not found"
        assert libraryEntry.source.size() == 1
        assert libraryEntry.source.get(0) == "file://" + FilenameUtils.separatorsToUnix(sourcesPath)
    }

    void ideFileContainsNoSourcesAndJavadocEntry() {
        IdeaModuleFixture iml =  parseIml("root.iml")
        iml.dependencies.libraries.size() == 1
        iml.dependencies.libraries[0].assertHasNoJavadoc()
        iml.dependencies.libraries[0].assertHasNoSource()
    }

    @Override
    void expectBehaviorAfterBrokenMavenArtifact(HttpArtifact httpArtifact) {
        httpArtifact.expectHead()
        httpArtifact.expectGet()
    }

    @Override
    void expectBehaviorAfterBrokenIvyArtifact(HttpArtifact httpArtifact) {
        httpArtifact.expectGet()
    }
}
