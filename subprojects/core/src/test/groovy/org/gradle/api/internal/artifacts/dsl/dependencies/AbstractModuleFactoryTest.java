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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.util.HelperUtil;
import org.gradle.util.GUtil;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.*;

import java.awt.*;

/**
 * @author Hans Dockter
 */
public abstract class AbstractModuleFactoryTest {
    protected static final String TEST_GROUP = "org.gradle";
    protected static final String TEST_NAME = "gradle-core";
    protected static final String TEST_VERSION = "4.4-beta2";
    protected static final String TEST_CONFIGURATION = "testConf";
    protected static final String TEST_TYPE = "mytype";
    protected static final String TEST_CLASSIFIER = "jdk-1.4";
    protected static final String TEST_MODULE_DESCRIPTOR = String.format("%s:%s:%s", TEST_GROUP, TEST_NAME, TEST_VERSION);
    protected static final String TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER = TEST_MODULE_DESCRIPTOR + ":" + TEST_CLASSIFIER;

    protected abstract ExternalDependency createDependency(Object notation);

    @Test(expected = IllegalDependencyNotation.class)
    public void testStringNotationWithOneElementStringShouldThrowInvalidUserDataEx() {
        createDependency("singlestring");
    }

    @Test(expected = IllegalDependencyNotation.class)
    public void testUnknownTypeShouldThrowInvalidUserDataEx() {
        createDependency(new Point(3, 4));
    }

    @Test
    public void testStringNotationWithGString() {
        checkCommonModuleProperties(createDependency(HelperUtil.createScript(
                 "descriptor = '" + TEST_MODULE_DESCRIPTOR + "'; \"$descriptor\"").run()));
    }

    @Test
    public void testStringNotationWithModule() {
        ExternalDependency moduleDependency = createDependency(TEST_MODULE_DESCRIPTOR);
        checkCommonModuleProperties(moduleDependency);
        assertTrue(moduleDependency.isTransitive());
    }

    @Test
    public void testStringNotationWithNoGroup() {
        ExternalDependency moduleDependency = createDependency(
                String.format(":%s:%s", TEST_NAME, TEST_VERSION));
        checkName(moduleDependency);
        checkVersion(moduleDependency);
        assertThat(moduleDependency.getGroup(), nullValue());
        assertTrue(moduleDependency.isTransitive());
    }

    @Test
    public void testStringNotationWithNoVersion() {
        ExternalDependency moduleDependency = createDependency(
                String.format("%s:%s", TEST_GROUP, TEST_NAME));
        checkGroup(moduleDependency);
        checkName(moduleDependency);
        assertThat(moduleDependency.getVersion(), nullValue());
        assertTrue(moduleDependency.isTransitive());
    }

    @Test
    public void testStringNotationWithNoVersionAndNoGroup() {
        ExternalDependency moduleDependency = createDependency(
                String.format(":%s", TEST_NAME));
        checkName(moduleDependency);
        assertThat(moduleDependency.getGroup(), nullValue());
        assertThat(moduleDependency.getVersion(), nullValue());
        assertTrue(moduleDependency.isTransitive());
    }

    @Test
    public void testStringNotationWithModuleAndClassifier() {
        ExternalDependency moduleDependency = createDependency(TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER);
        assertCorrectnesForModuleWithClassifier(moduleDependency);
    }

    @Test
    public void testMapNotationWithModuleAndClassifier() {
        ExternalDependency moduleDependency = createDependency(
                GUtil.map("group", TEST_GROUP, "name", TEST_NAME, "version", TEST_VERSION, "classifier", TEST_CLASSIFIER));
        assertCorrectnesForModuleWithClassifier(moduleDependency);
    }

    private void assertCorrectnesForModuleWithClassifier(ExternalDependency moduleDependency) {
        checkCommonModuleProperties(moduleDependency);
        assertTrue(moduleDependency.isTransitive());
        assertEquals(1, moduleDependency.getArtifacts().size());
        DependencyArtifact artifact = moduleDependency.getArtifacts().iterator().next();
        assertEquals(TEST_NAME, artifact.getName());
        assertEquals(DependencyArtifact.DEFAULT_TYPE, artifact.getType());
        assertEquals(DependencyArtifact.DEFAULT_TYPE, artifact.getExtension());
        assertEquals(TEST_CLASSIFIER, artifact.getClassifier());
    }

    @Test
    public void mapNotation() {
        ExternalDependency moduleDependency = createDependency(GUtil.map("group", TEST_GROUP, "name", TEST_NAME, "version", TEST_VERSION));
        checkCommonModuleProperties(moduleDependency);
        assertTrue(moduleDependency.isTransitive());
    }

    @Test
    public void mapNotationWithConfiguration() {
        ExternalDependency moduleDependency = createDependency(GUtil.map("group", TEST_GROUP, "name", TEST_NAME, "version", TEST_VERSION,
                "configuration", TEST_CONFIGURATION));
        checkCommonModuleProperties(moduleDependency);
        assertTrue(moduleDependency.isTransitive());
        assertThat(moduleDependency.getConfiguration(), equalTo(TEST_CONFIGURATION));
    }

    @Test
    public void mapNotationWithProperty() {
        ExternalDependency moduleDependency = createDependency(
                GUtil.map("group", TEST_GROUP, "name", TEST_NAME, "version", TEST_VERSION, "transitive", false));
        checkCommonModuleProperties(moduleDependency);
        assertThat(moduleDependency.isTransitive(), equalTo(false));
    }

    protected void checkCommonModuleProperties(ExternalDependency moduleDependency) {
        checkGroup(moduleDependency);
        checkName(moduleDependency);
        checkVersion(moduleDependency);
        checkOtherProperties(moduleDependency);
    }

    protected void checkOtherProperties(ExternalDependency moduleDependency) {
        assertFalse(moduleDependency.isForce());
    }

    private void checkVersion(ExternalDependency moduleDependency) {
        assertEquals(TEST_VERSION, moduleDependency.getVersion());
    }

    private void checkName(ExternalDependency moduleDependency) {
        assertEquals(TEST_NAME, moduleDependency.getName());
    }

    private void checkGroup(ExternalDependency moduleDependency) {
        assertEquals(TEST_GROUP, moduleDependency.getGroup());
    }
}
