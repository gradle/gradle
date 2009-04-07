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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.gradle.api.UnknownDependencyNotation;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleDependencyFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultModuleDependency;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.util.HelperUtil;

import java.awt.*;

/**
 * @author Hans Dockter
 */
public class ModuleDependencyFactoryTest {
    private static final String TEST_GROUP = "org.gradle";
    private static final String TEST_NAME = "gradle-core";
    private static final String TEST_VERSION = "4.4-beta2";
    private static final String TEST_TYPE = "mytype";
    private static final String TEST_CLASSIFIER = "jdk-1.4";
    private static final String TEST_MODULE_DESCRIPTOR = String.format("%s:%s:%s", TEST_GROUP, TEST_NAME, TEST_VERSION);
    private static final String TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER = TEST_MODULE_DESCRIPTOR + ":" + TEST_CLASSIFIER;
    private static final String TEST_ARTIFACT_DESCRIPTOR = TEST_MODULE_DESCRIPTOR + "@" + TEST_TYPE;
    private static final String TEST_ARTIFACT_DESCRIPTOR_WITH_CLASSIFIER = TEST_MODULE_DESCRIPTOR + String.format(":%s@%s", TEST_CLASSIFIER, TEST_TYPE);

    private ModuleDependencyFactory moduleDependencyFactory = new ModuleDependencyFactory();

    @Test
    public void testInitWithDependencyNotation() {
        DefaultModuleDependency moduleDependency = moduleDependencyFactory.createDependency(TEST_MODULE_DESCRIPTOR);
        checkCommonModuleProperties(moduleDependency);
        assert !moduleDependency.isForce();
        assertNotNull(moduleDependency.getExcludeRules());
    }

    @Test
    public void testInitWithGStringAndWithoutClassifier() {
        checkCommonModuleProperties(moduleDependencyFactory.createDependency(HelperUtil.createScript(
                 "descriptor = '" + TEST_MODULE_DESCRIPTOR + "'; \"$descriptor\"").run()));
    }

    @Test (expected = InvalidUserDataException.class) public void testSingleString() {
        moduleDependencyFactory.createDependency("singlestring");
    }

    @Test (expected = InvalidUserDataException.class) public void testMissingVersion() {
        moduleDependencyFactory.createDependency("junit:junit");
    }

    @Test (expected = UnknownDependencyNotation.class) public void testUnknownType() {
        moduleDependencyFactory.createDependency(new Point(3, 4));
    }

    @Test public void testWithModuleUserDescription() {
        DefaultModuleDependency moduleDependency = moduleDependencyFactory.createDependency(TEST_MODULE_DESCRIPTOR);
        checkCommonModuleProperties(moduleDependency);
        assertTrue(moduleDependency.isTransitive());
    }

    @Test public void testWithArtifactUserDescription() {
        DefaultModuleDependency moduleDependency = moduleDependencyFactory.createDependency(TEST_ARTIFACT_DESCRIPTOR);
        checkCommonModuleProperties(moduleDependency);
        assertFalse(moduleDependency.isTransitive());
        assertEquals(1, moduleDependency.getArtifacts().size());
        DependencyArtifact artifact = moduleDependency.getArtifacts().iterator().next();
        assertEquals(TEST_NAME, artifact.getName());
        assertEquals(TEST_TYPE, artifact.getType());
        assertEquals(null, artifact.getClassifier());
    }

    @Test public void testWithModuleUserDescriptionWithClassifier() {
        DefaultModuleDependency moduleDependency = moduleDependencyFactory.createDependency(TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER);
        checkCommonModuleProperties(moduleDependency);
        assertTrue(moduleDependency.isTransitive());
        assertEquals(1, moduleDependency.getArtifacts().size());
        DependencyArtifact artifact = moduleDependency.getArtifacts().iterator().next();
        assertEquals(TEST_NAME, artifact.getName());
        assertEquals(DependencyArtifact.DEFAULT_TYPE, artifact.getType());
        assertEquals(DependencyArtifact.DEFAULT_TYPE, artifact.getExtension());
        assertEquals(TEST_CLASSIFIER, artifact.getClassifier());
    }

    @Test public void testWithArtifactUserDescriptionWithClassifier() {
        DefaultModuleDependency moduleDependency = moduleDependencyFactory.createDependency(TEST_ARTIFACT_DESCRIPTOR_WITH_CLASSIFIER);
        checkCommonModuleProperties(moduleDependency);
        assertFalse(moduleDependency.isTransitive());
        assertEquals(1, moduleDependency.getArtifacts().size());
        DependencyArtifact artifact = moduleDependency.getArtifacts().iterator().next();
        assertEquals(TEST_NAME, artifact.getName());
        assertEquals(TEST_TYPE, artifact.getType());
        assertEquals(TEST_TYPE, artifact.getExtension());
        assertEquals(TEST_CLASSIFIER, artifact.getClassifier());
    }

    private void checkCommonModuleProperties(DefaultModuleDependency moduleDependency) {
        assertEquals(TEST_GROUP, moduleDependency.getGroup());
        assertEquals(TEST_NAME, moduleDependency.getName());
        assertEquals(TEST_VERSION, moduleDependency.getVersion());
        assertFalse(moduleDependency.isForce());
        assertFalse(moduleDependency.isChanging());
    }

}
