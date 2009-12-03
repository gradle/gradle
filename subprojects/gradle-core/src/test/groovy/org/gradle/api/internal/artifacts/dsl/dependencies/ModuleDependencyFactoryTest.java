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

import org.gradle.api.internal.AsmBackedClassGenerator;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.util.GUtil;

/**
 * @author Hans Dockter
 */
public class ModuleDependencyFactoryTest extends AbstractModuleFactoryTest {
    private static final String TEST_ARTIFACT_DESCRIPTOR = TEST_MODULE_DESCRIPTOR + "@" + TEST_TYPE;
    private static final String TEST_ARTIFACT_DESCRIPTOR_WITH_CLASSIFIER = TEST_MODULE_DESCRIPTOR + String.format(":%s@%s", TEST_CLASSIFIER, TEST_TYPE);
    
    private ModuleDependencyFactory moduleDependencyFactory = new ModuleDependencyFactory(new AsmBackedClassGenerator());

    protected ExternalDependency createDependency(Object notation) {
        return moduleDependencyFactory.createDependency(ExternalDependency.class, notation);
    }

    protected void checkOtherProperties(ExternalDependency moduleDependency) {
        super.checkOtherProperties(moduleDependency);
        assertFalse(((ExternalModuleDependency)moduleDependency).isChanging());
    }

    @Test
    public void testStringNotationWithArtifact() {
        ExternalDependency moduleDependency = createDependency(TEST_ARTIFACT_DESCRIPTOR);
        assertIsArtifactOnly(moduleDependency);
    }

    @Test
    public void testStringNotationWithArtifactAndClassifier() {
        ExternalDependency moduleDependency = createDependency(TEST_ARTIFACT_DESCRIPTOR_WITH_CLASSIFIER);
        assertIsArtifactOnlyWithClassifier(moduleDependency);
    }

    @Test
    public void testMapNotationWithArtifact() {
        ExternalDependency moduleDependency = createDependency(GUtil.map("group", TEST_GROUP, "name", TEST_NAME, "version", TEST_VERSION, "ext", TEST_TYPE));
        assertIsArtifactOnly(moduleDependency);
    }

    @Test
    public void testMapNotationWithArtifactAndClassifier() {
        ExternalDependency moduleDependency = createDependency(GUtil.map("group", TEST_GROUP, "name", TEST_NAME, "version",
                TEST_VERSION, "ext", TEST_TYPE, "classifier", TEST_CLASSIFIER));
        assertIsArtifactOnlyWithClassifier(moduleDependency);
    }

    private void assertIsArtifactOnlyWithClassifier(ExternalDependency moduleDependency) {
        checkCommonModuleProperties(moduleDependency);
        assertFalse(moduleDependency.isTransitive());
        assertEquals(1, moduleDependency.getArtifacts().size());
        DependencyArtifact artifact = moduleDependency.getArtifacts().iterator().next();
        assertEquals(TEST_NAME, artifact.getName());
        assertEquals(TEST_TYPE, artifact.getType());
        assertEquals(TEST_TYPE, artifact.getExtension());
        assertEquals(TEST_CLASSIFIER, artifact.getClassifier());
    }

    private void assertIsArtifactOnly(ExternalDependency moduleDependency) {
        checkCommonModuleProperties(moduleDependency);
        assertFalse(moduleDependency.isTransitive());
        assertEquals(1, moduleDependency.getArtifacts().size());
        DependencyArtifact artifact = moduleDependency.getArtifacts().iterator().next();
        assertEquals(TEST_NAME, artifact.getName());
        assertEquals(TEST_TYPE, artifact.getType());
        assertEquals(null, artifact.getClassifier());
    }
}
