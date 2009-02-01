/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.Transformer;
import org.gradle.api.dependencies.Configuration;
import org.gradle.api.dependencies.PublishArtifact;
import org.gradle.api.internal.dependencies.ivyservice.DefaultIvyDependencyPublisher;
import org.gradle.util.HelperUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Test;

import java.io.File;
import java.util.Date;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public abstract class AbstractPublishArtifactTest {
    private static final File TEST_FILE = new File("artifactFile");
    private static final String TEST_NAME = "myfile-1";
    private static final String TEST_EXT = "ext";
    private static final String TEST_TYPE = "type";
    private static final String TEST_CLASSIFIER = "classifier";
    private static final ModuleRevisionId TEST_MODULE_REVISION_ID = new ModuleRevisionId(new ModuleId("group", "name"), "version");

    protected File getTestFile() {
        return TEST_FILE;
    }

    protected String getTestName() {
        return TEST_NAME;
    }

    protected String getTestExt() {
        return TEST_EXT;
    }

    protected String getTestType() {
        return TEST_TYPE;
    }

    protected String getTestClassifier() {
        return TEST_CLASSIFIER;
    }

    protected ModuleRevisionId getTestModuleRevisionId() {
        return TEST_MODULE_REVISION_ID;
    }

    protected JUnit4Mockery context = new JUnit4GroovyMockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    protected final Set<Configuration> testConfs = WrapUtil.toSet(context.mock(Configuration.class));

    @Test
    public void init() {
        assertThat(createPublishArtifact(null).getConfigurations(), Matchers.equalTo(testConfs));
    }
    
    @Test
    public void testCreateIvyArtifact() {
        PublishArtifact publishArtifact = createPublishArtifact(null);
        Artifact artifact = publishArtifact.createIvyArtifact(getTestModuleRevisionId());
        checkCommonProperties(artifact, false);
    }

    abstract PublishArtifact createPublishArtifact(String classifier);

    @Test public void testCreateIvyArtifactWithEmptyClassifier() {
        PublishArtifact publishArtifact = createPublishArtifact("");
        Artifact artifact = publishArtifact.createIvyArtifact(getTestModuleRevisionId());
        checkCommonProperties(artifact, false);
    }

    @Test public void testCreateIvyArtifactWithClassifier() {
        PublishArtifact publishArtifact = createPublishArtifact(getTestClassifier());
        ModuleRevisionId moduleRevisionId = new ModuleRevisionId(new ModuleId("group", "name"), "version");
        Artifact artifact = publishArtifact.createIvyArtifact(moduleRevisionId);
        checkCommonProperties(artifact, true);
    }

    @Test public void testCanTransformIvyArtifact() {
        PublishArtifact publishArtifact = createPublishArtifact(null);
        final Artifact transformed = new DefaultArtifact(getTestModuleRevisionId(), new Date(), "name", "type", "ext");

        final Transformer<Artifact> transformer = context.mock(Transformer.class);

        context.checking(new Expectations() {{
            one(transformer).transform(with(Matchers.notNullValue(Artifact.class)));
            will(returnValue(transformed));
        }});
        publishArtifact.addIvyTransformer(transformer);

        assertSame(transformed, publishArtifact.createIvyArtifact(getTestModuleRevisionId()));
    }

    @Test public void testCanTransformIvyArtifactUsingClosure() {
        PublishArtifact publishArtifact = createPublishArtifact(null);
        Artifact transformed = new DefaultArtifact(getTestModuleRevisionId(), new Date(), "name", "type", "ext");

        publishArtifact.addIvyTransformer(HelperUtil.returns(transformed));

        assertSame(transformed, publishArtifact.createIvyArtifact(getTestModuleRevisionId()));
    }

    private void checkCommonProperties(Artifact artifact, boolean shouldHaveClassifier) {
        assertEquals(getTestName(), artifact.getName());
        assertEquals(getTestModuleRevisionId(), artifact.getModuleRevisionId());
        assertEquals(getTestType(), artifact.getType());
        assertEquals(getTestExt(), artifact.getExt());
        assertEquals(getTestFile().getAbsolutePath(),
                artifact.getExtraAttribute(DefaultIvyDependencyPublisher.FILE_PATH_EXTRA_ATTRIBUTE));
        if (shouldHaveClassifier) {
            assertEquals(getTestClassifier(),
                artifact.getExtraAttribute(DependencyManager.CLASSIFIER));
        }
    }
}
