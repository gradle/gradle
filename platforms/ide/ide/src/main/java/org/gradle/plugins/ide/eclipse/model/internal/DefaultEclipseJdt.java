/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model.internal;

import org.gradle.api.JavaVersion;
import org.gradle.plugins.ide.api.PropertiesFileContentMerger;
import org.gradle.plugins.ide.eclipse.model.EclipseJdt;

import javax.inject.Inject;

public abstract class DefaultEclipseJdt extends EclipseJdt implements EclipseJdtInternal{
    @Inject
    public DefaultEclipseJdt(PropertiesFileContentMerger file) {
        super(file);
    }

    @Override
    public void setSourceCompatibility(Object sourceCompatibility) {
        JavaVersion version = JavaVersion.toVersion(sourceCompatibility);
        if (version != null) {
            this.getSourceCompatibilityProperty().set(version);
        }
    }

    @Override
    public JavaVersion getSourceCompatibility() {
        return getSourceCompatibilityProperty().get();
    }


    /**
     * The target JVM to generate {@code .class} files for.
     * <p>
     * For example see docs for {@link EclipseJdt}
     */
    @Override
    public JavaVersion getTargetCompatibility() {
        return getTargetCompatibilityProperty().get();
    }

    @Override
    public void setTargetCompatibility(Object targetCompatibility) {
        JavaVersion version = JavaVersion.toVersion(targetCompatibility);
        if (version != null) {
            this.getTargetCompatibilityProperty().set(version);
        }
    }

    /**
     * The name of the Java Runtime to use.
     * <p>
     * For example see docs for {@link EclipseJdt}
     */
    @Override
    public String getJavaRuntimeName() {
        return getJavaRuntimeNameProperty().get();
    }

    @Override
    public void setJavaRuntimeName(String javaRuntimeName) {
        this.getJavaRuntimeNameProperty().set(javaRuntimeName);
    }
}
