/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugins.ide.eclipse

import org.gradle.plugins.ide.fixtures.AbstractMultiBuildIdeIntegrationTest
import org.gradle.test.fixtures.file.TestFile

class EclipseMultiBuildIntegrationTest extends AbstractMultiBuildIdeIntegrationTest {
    String pluginId = "eclipse"
    String workspaceTask = "eclipse"
    String libraryPluginId = "java-library"

    @Override
    EclipseWorkspaceFixture workspace(TestFile workspaceDir, String ideWorkspaceName) {
        return new EclipseWorkspaceFixture(workspaceDir)
    }

    @Override
    EclipseProjectFixture project(TestFile projectDir, String ideProjectName) {
        return EclipseProjectFixture.create(projectDir)
    }
}
