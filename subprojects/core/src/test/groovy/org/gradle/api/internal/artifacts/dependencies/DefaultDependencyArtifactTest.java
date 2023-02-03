/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.artifacts.DependencyArtifact;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DefaultDependencyArtifactTest {
    @Test
    public void testInit() {
        String testName = "name";
        String testType = "type";
        String testExtension = "ext";
        String testClassifier = "classifier";
        String testUrl = "url";
        DependencyArtifact artifact = new DefaultDependencyArtifact(testName, testType, testExtension, testClassifier, testUrl);
        assertEquals(testName, artifact.getName());
        assertEquals(testType, artifact.getType());
        assertEquals(testExtension, artifact.getExtension());
        assertEquals(testClassifier, artifact.getClassifier());
        assertEquals(testUrl, artifact.getUrl());
    }
}
