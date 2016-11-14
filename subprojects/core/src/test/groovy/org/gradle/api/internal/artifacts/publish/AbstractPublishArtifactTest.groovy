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
package org.gradle.api.internal.artifacts.publish

import org.gradle.api.artifacts.PublishArtifact
import spock.lang.Specification

public abstract class AbstractPublishArtifactTest extends Specification {
    private static final File TEST_FILE = new File("artifactFile");
    private static final String TEST_NAME = "myfile-1";
    private static final String TEST_EXT = "ext";
    private static final String TEST_TYPE = "type";
    private static final String TEST_CLASSIFIER = "classifier";
    private static final Date TEST_DATE = new Date();

    protected File getTestFile() {
        TEST_FILE
    }

    protected String getTestName() {
        TEST_NAME
    }

    protected String getTestExt() {
        TEST_EXT
    }

    protected String getTestType() {
        TEST_TYPE
    }

    protected String getTestClassifier() {
        TEST_CLASSIFIER
    }

    protected Date getDate() {
        TEST_DATE
    }

    protected void assertCommonPropertiesAreSet(PublishArtifact artifact, boolean shouldHaveClassifier) {
        assert testName == artifact.name
        assert testType == artifact.type
        assert testExt == artifact.extension
        assert testFile == artifact.file
        if (shouldHaveClassifier) {
            assert testClassifier == artifact.classifier
        }
    }
}
