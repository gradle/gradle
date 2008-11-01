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
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.DependencyManager;
import org.gradle.api.internal.Transformer;
import org.gradle.util.WrapUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.HelperUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.hamcrest.Matchers;

import java.util.HashMap;
import java.util.Date;

/**
* @author Hans Dockter
*/
@RunWith(JMock.class)
public class DefaultGradleArtifactTest {
    private static final String TEST_NAME = "myfile-1";

    private static final String TEST_EXT = "ext";
    private static final String TEST_TYPE = "type";
    private static final String TEST_CLASSIFIER = "classifier";
    private static final ModuleRevisionId TEST_MODULE_REVISION_ID = new ModuleRevisionId(new ModuleId("group", "name"), "version");

    private JUnit4Mockery context = new JUnit4GroovyMockery();

    @Test public void testCreateIvyArtifact() {
        DefaultPublishArtifact publishArtifact = new DefaultPublishArtifact(TEST_NAME, TEST_EXT, TEST_TYPE, null);
        Artifact artifact = publishArtifact.createIvyArtifact(TEST_MODULE_REVISION_ID);
        checkCommonProperties(artifact);
        assertEquals(new HashMap(), artifact.getExtraAttributes());
    }

    @Test public void testCreateIvyArtifactWithEmptyClassifier() {
        DefaultPublishArtifact publishArtifact = new DefaultPublishArtifact(TEST_NAME, TEST_EXT, TEST_TYPE, "");
        Artifact artifact = publishArtifact.createIvyArtifact(TEST_MODULE_REVISION_ID);
        assertEquals(new HashMap(), artifact.getExtraAttributes());
    }

    @Test public void testCreateIvyArtifactWithClassifier() {
        DefaultPublishArtifact publishArtifact = new DefaultPublishArtifact(TEST_NAME, TEST_EXT, TEST_TYPE, TEST_CLASSIFIER);
        ModuleRevisionId moduleRevisionId = new ModuleRevisionId(new ModuleId("group", "name"), "version");
        Artifact artifact = publishArtifact.createIvyArtifact(moduleRevisionId);
        checkCommonProperties(artifact);
        assertEquals(WrapUtil.toMap(DependencyManager.CLASSIFIER, TEST_CLASSIFIER), artifact.getExtraAttributes());
    }

    @Test public void testCanTransformIvyArtifact() {
        DefaultPublishArtifact publishArtifact = new DefaultPublishArtifact(TEST_NAME, TEST_EXT, TEST_TYPE, null);
        final Artifact transformed = new DefaultArtifact(TEST_MODULE_REVISION_ID, new Date(), "name", "type", "ext");

        final Transformer<Artifact> transformer = context.mock(Transformer.class);

        context.checking(new Expectations() {{
            one(transformer).transform(with(Matchers.notNullValue(Artifact.class)));
            will(returnValue(transformed));
        }});
        publishArtifact.addIvyTransformer(transformer);

        assertSame(transformed, publishArtifact.createIvyArtifact(TEST_MODULE_REVISION_ID));
    }

    @Test public void testCanTransformIvyArtifactUsingClosure() {
        DefaultPublishArtifact publishArtifact = new DefaultPublishArtifact(TEST_NAME, TEST_EXT, TEST_TYPE, null);
        Artifact transformed = new DefaultArtifact(TEST_MODULE_REVISION_ID, new Date(), "name", "type", "ext");

        publishArtifact.addIvyTransformer(HelperUtil.returns(transformed));

        assertSame(transformed, publishArtifact.createIvyArtifact(TEST_MODULE_REVISION_ID));
    }

    private void checkCommonProperties(Artifact artifact) {
        assertEquals(TEST_NAME, artifact.getName());
        assertEquals(TEST_MODULE_REVISION_ID, artifact.getModuleRevisionId());
        assertEquals(TEST_TYPE, artifact.getType());
        assertEquals(TEST_EXT, artifact.getExt());
    }
}
