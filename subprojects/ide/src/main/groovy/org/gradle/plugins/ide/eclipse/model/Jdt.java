/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model;

import java.util.Properties;

import org.gradle.api.JavaVersion;
import org.gradle.api.internal.PropertiesTransformer;
import org.gradle.plugins.ide.internal.generator.PropertiesPersistableConfigurationObject;

/**
 * Represents the Eclipse JDT settings.
 */
public class Jdt extends PropertiesPersistableConfigurationObject {
    private JavaVersion sourceCompatibility;
    private JavaVersion targetCompatibility;

    public Jdt(PropertiesTransformer transformer) {
        super(transformer);
    }
    
    /**
     * Sets the source compatibility for the compiler.
     */
    public void setSourceCompatibility(JavaVersion sourceCompatibility) {
        this.sourceCompatibility = sourceCompatibility;
    }

    /**
     * Sets the target compatibility for the compiler.
     */
    public void setTargetCompatibility(JavaVersion targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }

    @Override
    protected String getDefaultResourceName() {
        return "defaultJdtPrefs.properties";
    }

    @Override
    protected void load(Properties properties) {
    }

    @Override
    protected void store(Properties properties) {
        properties.put("org.eclipse.jdt.core.compiler.compliance", sourceCompatibility.toString());
        properties.put("org.eclipse.jdt.core.compiler.source", sourceCompatibility.toString());

        if (sourceCompatibility.compareTo(JavaVersion.VERSION_1_3) <= 0) {
            properties.put("org.eclipse.jdt.core.compiler.problem.assertIdentifier", "ignore");
            properties.put("org.eclipse.jdt.core.compiler.problem.enumIdentifier", "ignore");
        } else if (sourceCompatibility == JavaVersion.VERSION_1_4) {
            properties.put("org.eclipse.jdt.core.compiler.problem.assertIdentifier", "error");
            properties.put("org.eclipse.jdt.core.compiler.problem.enumIdentifier", "warning");
        } else {
            properties.put("org.eclipse.jdt.core.compiler.problem.assertIdentifier", "error");
            properties.put("org.eclipse.jdt.core.compiler.problem.enumIdentifier", "error");
        }

        properties.put("org.eclipse.jdt.core.compiler.codegen.targetPlatform", targetCompatibility.toString());
    }
}
