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
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.internal.artifacts.dependencies.DefaultClientModule;
import org.gradle.util.HelperUtil;

import java.awt.*;

import groovy.lang.GString;

/**
 * @author Hans Dockter
 */
public class DefaultClientModuleFactoryTest extends AbstractModuleFactoryTest {
    private DefaultClientModuleFactory clientModuleFactory = new DefaultClientModuleFactory();

    protected ExternalDependency createDependency(Object notation) {
        return clientModuleFactory.createClientModule(notation);
    }
}
