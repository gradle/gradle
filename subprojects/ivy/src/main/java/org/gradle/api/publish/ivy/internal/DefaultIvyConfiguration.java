/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.publish.ivy.IvyConfiguration;
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifactSet;
import org.gradle.internal.reflect.Instantiator;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultIvyConfiguration implements IvyConfiguration {
    private final String name;
    private final DefaultIvyArtifactSet artifacts;
    private final Set<IvyConfiguration> extendsFrom = new LinkedHashSet<IvyConfiguration>();

    public DefaultIvyConfiguration(String name, NotationParser<IvyArtifact> ivyArtifactNotationParser, Instantiator instantiator) {
        this.name = name;
        this.artifacts = instantiator.newInstance(DefaultIvyArtifactSet.class, name, ivyArtifactNotationParser);
    }

    public String getName() {
        return name;
    }

    public IvyArtifact artifact(Object source) {
        return artifacts.artifact(source);
    }

    public IvyArtifact artifact(Object source, Action<? super IvyArtifact> config) {
        return artifacts.artifact(source, config);
    }

    public void setArtifacts(Iterable<?> sources) {
        artifacts.clear();
        for (Object source : sources) {
            artifact(source);
        }
    }

    public DefaultIvyArtifactSet getArtifacts() {
        return artifacts;
    }

    public void extend(IvyConfiguration parent) {
        extendsFrom.add(parent);
    }

    public Set<IvyConfiguration> getExtends() {
        return extendsFrom;
    }
}
