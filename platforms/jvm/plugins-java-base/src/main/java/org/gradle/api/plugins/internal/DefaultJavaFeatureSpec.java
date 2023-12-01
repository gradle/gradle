/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.plugins.internal;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.jvm.internal.DefaultJvmFeature;
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultJavaFeatureSpec implements FeatureSpecInternal {
    private final String name;
    private final Set<Capability> capabilities = new LinkedHashSet<>(1);
    private final ProjectInternal project;

    private SourceSet sourceSet;
    private boolean withJavadocJar = false;
    private boolean withSourcesJar = false;
    private boolean allowPublication = true;

    public DefaultJavaFeatureSpec(String name, ProjectInternal project) {
        this.name = name;
        this.project = project;
    }

    @Override
    public void usingSourceSet(SourceSet sourceSet) {
        this.sourceSet = sourceSet;
    }

    @Override
    public void capability(String group, String name, String version) {
        capabilities.add(new DefaultImmutableCapability(group, name, version));
    }

    @Override
    public void withJavadocJar() {
        withJavadocJar = true;
    }

    @Override
    public void withSourcesJar() {
        withSourcesJar = true;
    }

    @Override
    public void disablePublication() {
        allowPublication = false;
    }

    @Override
    public boolean isPublished() {
        return allowPublication;
    }

    @Override
    public JvmFeatureInternal create() {
        if (sourceSet == null) {
            throw new InvalidUserCodeException("You must specify which source set to use for feature '" + name + "'");
        }

        if (capabilities.isEmpty()) {
            capabilities.add(new ProjectDerivedCapability(project, name));
        }

        JvmFeatureInternal feature = new DefaultJvmFeature(name, sourceSet, capabilities, project, true, SourceSet.isMain(sourceSet));
        feature.withApi();

        if (withJavadocJar) {
            feature.withJavadocJar();
        }

        if (withSourcesJar) {
            feature.withSourcesJar();
        }

        return feature;
    }

}
