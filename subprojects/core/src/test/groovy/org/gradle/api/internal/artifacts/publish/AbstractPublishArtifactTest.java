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
package org.gradle.api.internal.artifacts.publish;

import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;

import java.io.File;
import java.util.Date;

import static org.junit.Assert.assertEquals;

public abstract class AbstractPublishArtifactTest {
    private static final File TEST_FILE = new File("artifactFile");
    private static final String TEST_NAME = "myfile-1";
    private static final String TEST_EXT = "ext";
    private static final String TEST_TYPE = "type";
    private static final String TEST_CLASSIFIER = "classifier";
    private static final Date TEST_DATE = new Date();
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

    protected Date getDate() {
        return TEST_DATE;
    }

    protected JUnit4Mockery context = new JUnit4GroovyMockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};

    abstract PublishArtifact createPublishArtifact(String classifier);

    protected void assertCommonPropertiesAreSet(PublishArtifact artifact, boolean shouldHaveClassifier) {
        assertEquals(getTestName(), artifact.getName());
        assertEquals(getTestType(), artifact.getType());
        assertEquals(getTestExt(), artifact.getExtension());
        assertEquals(getTestFile(), artifact.getFile());
        if (shouldHaveClassifier) {
            assertEquals(getTestClassifier(), artifact.getClassifier());
        }
    }
}
