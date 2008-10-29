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
 
package org.gradle.api.internal.dependencies;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.DependencyManager;
import org.gradle.util.WrapUtil;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;

/**
* @author Hans Dockter
*/
public class DefaultGradleArtifactTest {
    private static final String TEST_NAME = "myfile-1";

    private static final String TEST_EXT = "ext";
    private static final String TEST_TYPE = "type";
    private static final String TEST_CLASSIFIER = "classifier";
    private static final ModuleRevisionId TEST_MODULE_REVISION_ID = new ModuleRevisionId(new ModuleId("group", "name"), "version");

    @Test public void testCreateIvyArtifact() {
        DefaultPublishArtifact defaultGradleArtifact = new DefaultPublishArtifact(TEST_NAME, TEST_EXT, TEST_TYPE, null);
        Artifact artifact = defaultGradleArtifact.createIvyArtifact(TEST_MODULE_REVISION_ID);
        checkCommonProperties(artifact);
        assertEquals(new HashMap(), artifact.getExtraAttributes());
    }

    @Test public void testCreateIvyArtifactWithEmptyClassifier() {
        DefaultPublishArtifact defaultGradleArtifact = new DefaultPublishArtifact(TEST_NAME, TEST_EXT, TEST_TYPE, "");
        Artifact artifact = defaultGradleArtifact.createIvyArtifact(TEST_MODULE_REVISION_ID);
        assertEquals(new HashMap(), artifact.getExtraAttributes());
    }

    @Test public void testCreateIvyArtifactWithClassifier() {
        DefaultPublishArtifact defaultGradleArtifact = new DefaultPublishArtifact(TEST_NAME, TEST_EXT, TEST_TYPE, TEST_CLASSIFIER);
        ModuleRevisionId moduleRevisionId = new ModuleRevisionId(new ModuleId("group", "name"), "version");
        Artifact artifact = defaultGradleArtifact.createIvyArtifact(moduleRevisionId);
        checkCommonProperties(artifact);
        assertEquals(WrapUtil.toMap(DependencyManager.CLASSIFIER, TEST_CLASSIFIER), artifact.getExtraAttributes());
    }


    private void checkCommonProperties(Artifact artifact) {
        assertEquals(TEST_NAME, artifact.getName());
        assertEquals(TEST_MODULE_REVISION_ID, artifact.getModuleRevisionId());
        assertEquals(TEST_TYPE, artifact.getType());
        assertEquals(TEST_EXT, artifact.getExt());
    }
}
