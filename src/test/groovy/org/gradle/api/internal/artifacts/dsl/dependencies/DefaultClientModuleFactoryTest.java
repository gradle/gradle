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

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UnknownDependencyNotation;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.internal.artifacts.dependencies.DefaultClientModule;
import org.gradle.util.HelperUtil;

import java.awt.*;

import groovy.lang.GString;

/**
 * @author Hans Dockter
 */
public class DefaultClientModuleFactoryTest {
    private static final String TEST_GROUP = "org.gradle";
    private static final String TEST_NAME = "gradle-core";
    private static final String TEST_VERSION = "4.4-beta2";
    private static final String TEST_CLASSIFIER = "jdk-1.4";
    private static final String TEST_MODULE_DESCRIPTOR = String.format("%s:%s:%s", TEST_GROUP, TEST_NAME, TEST_VERSION);
    private static final String TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER = TEST_MODULE_DESCRIPTOR + ":" + TEST_CLASSIFIER;

    private DefaultClientModuleFactory clientModuleFactory = new DefaultClientModuleFactory();
    
    @Test
    public void testInitWithoutClassifier() {
        checkInit(clientModuleFactory.createClientModule(TEST_MODULE_DESCRIPTOR));
    }

    @Test
    public void testInitWithGStringAndWithoutClassifier() {
        checkInit(clientModuleFactory.createClientModule(HelperUtil.createScript(
                 "descriptor = '" + TEST_MODULE_DESCRIPTOR + "'; \"$descriptor\"").run()));
    }

    @Test
    public void testInitWithClassifier() {
        ClientModule clientModule = clientModuleFactory.createClientModule(TEST_MODULE_DESCRIPTOR_WITH_CLASSIFIER);
        checkInit(clientModule);
        DependencyArtifact artifact = clientModule.getArtifacts().iterator().next();
        assertEquals(TEST_NAME, artifact.getName());
        assertEquals(DependencyArtifact.DEFAULT_TYPE, artifact.getType());
        assertEquals(DependencyArtifact.DEFAULT_TYPE, artifact.getExtension());
        assertEquals(TEST_CLASSIFIER, artifact.getClassifier());
    }

    private void checkInit(ClientModule clientModule) {
        assertEquals(TEST_GROUP, clientModule.getGroup());
        assertEquals(TEST_NAME, clientModule.getName());
        assertEquals(TEST_VERSION, clientModule.getVersion());
        assertFalse(clientModule.isForce());
        assertTrue(clientModule.isTransitive());
    }

    @Test(expected = InvalidUserDataException.class)
    public void testInitWithFiveParts() {
        clientModuleFactory.createClientModule("1:2:3:4:5");
    }

    @Test(expected = InvalidUserDataException.class)
    public void testInitWithTwoParts() {
        clientModuleFactory.createClientModule("1:2");
    }

    @Test(expected = InvalidUserDataException.class)
    public void testInitWithOneParts() {
        clientModuleFactory.createClientModule("1");
    }

    @Test(expected = UnknownDependencyNotation.class)
    public void createWithUnknownDependencyNotation_shouldThrowUnknownDependencyNotationEx() {
        clientModuleFactory.createClientModule(new Point(4, 3));
    }
}
