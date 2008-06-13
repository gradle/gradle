/*
 * Copyright 2007 the original author or authors.
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
 
package org.gradle.api.dependencies

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.DependencyManager

/**
* @author Hans Dockter
*/
class DefaultGradleArtifactTest extends GroovyTestCase {
    static final String TEST_NAME_1 = 'myfile-1'

    DefaultGradleArtifact defaultGradleArtifact


    void testCreateIvyArtifact() {
        defaultGradleArtifact = new DefaultGradleArtifact(TEST_NAME_1 + '@jar')
        ModuleRevisionId moduleRevisionId = new ModuleRevisionId(new ModuleId('group', 'name'), 'version')
        Artifact artifact = defaultGradleArtifact.createIvyArtifact(moduleRevisionId)
        assertEquals(TEST_NAME_1, artifact.name)
        assertEquals(moduleRevisionId, artifact.moduleRevisionId)
        assertEquals('jar', artifact.ext)
        assertEquals('jar', artifact.type)
        assert !artifact.extraAttributes[DependencyManager.CLASSIFIER]
    }

    void testCreateIvyArtifactWithClassifier() {
        defaultGradleArtifact = new DefaultGradleArtifact(TEST_NAME_1 + ':src@jar')
        ModuleRevisionId moduleRevisionId = new ModuleRevisionId(new ModuleId('group', 'name'), 'version')
        Artifact artifact = defaultGradleArtifact.createIvyArtifact(moduleRevisionId)
        assertEquals(TEST_NAME_1, artifact.name)
        assertEquals(moduleRevisionId, artifact.moduleRevisionId)
        assertEquals('jar', artifact.ext)
        assertEquals('jar', artifact.type)
        assertEquals('src', artifact.extraAttributes[DependencyManager.CLASSIFIER])
    }
}
