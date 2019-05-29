/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.layout;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.repositories.IvyPatternRepositoryLayout;
import org.gradle.api.internal.artifacts.repositories.resolver.PatternBasedResolver;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A repository layout that uses user-supplied patterns. Each pattern will be appended to the base URI for the repository.
 * At least one artifact pattern must be specified. If no Ivy patterns are specified, then the artifact patterns will be used.
 * Optionally supports a Maven style layout for the 'organisation' part, replacing any dots with forward slashes.
 */
public class DefaultIvyPatternRepositoryLayout extends AbstractRepositoryLayout implements IvyPatternRepositoryLayout {
    private final Set<String> artifactPatterns = new LinkedHashSet<String>();
    private final Set<String> ivyPatterns = new LinkedHashSet<String>();
    private boolean m2compatible;

    /**
     * {@inheritDoc}
     */
    @Override
    public void artifact(String pattern) {
        artifactPatterns.add(pattern);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void ivy(String pattern) {
        ivyPatterns.add(pattern);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getM2Compatible() {
        return m2compatible;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setM2compatible(boolean m2compatible) {
        this.m2compatible = m2compatible;
    }

    @Override
    public void apply(URI baseUri, PatternBasedResolver resolver) {
        if (baseUri == null) {
            return;
        }

        resolver.setM2compatible(m2compatible);

        for (String artifactPattern : artifactPatterns) {
            resolver.addArtifactLocation(baseUri, artifactPattern);
        }

        Set<String> usedIvyPatterns = ivyPatterns.isEmpty() ? artifactPatterns : ivyPatterns;
        for (String ivyPattern : usedIvyPatterns) {
            resolver.addDescriptorLocation(baseUri, ivyPattern);
        }
    }

    @Override
    public Set<String> getIvyPatterns() {
        return ImmutableSet.copyOf(ivyPatterns);
    }

    @Override
    public Set<String> getArtifactPatterns() {
        return ImmutableSet.copyOf(artifactPatterns);
    }
}
