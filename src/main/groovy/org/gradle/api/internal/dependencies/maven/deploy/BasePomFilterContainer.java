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
package org.gradle.api.internal.dependencies.maven.deploy;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.dependencies.maven.MavenPom;
import org.gradle.api.dependencies.maven.PomFilterContainer;
import org.gradle.api.dependencies.maven.PublishFilter;
import org.gradle.api.dependencies.maven.CopyablePomFilterContainer;
import org.gradle.api.internal.dependencies.maven.MavenPomFactory;
import org.gradle.util.WrapUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class BasePomFilterContainer implements CopyablePomFilterContainer {
    private Map<String, PomFilter> pomFilters = new HashMap<String, PomFilter>();

    private PomFilter defaultPomFilter = null;

    private MavenPomFactory mavenPomFactory;

    public BasePomFilterContainer(MavenPomFactory mavenPomFactory) {
        this.mavenPomFactory = mavenPomFactory;
    }

    public PublishFilter getFilter() {
        return getDefaultPomFilter().getFilter();
    }

    public void setFilter(PublishFilter defaultFilter) {
        getDefaultPomFilter().setFilter(defaultFilter);
    }

    public MavenPom getPom() {
        return getDefaultPomFilter().getPomTemplate();
    }

    public void setPom(MavenPom defaultPom) {
        getDefaultPomFilter().setPomTemplate(defaultPom);
    }

    public MavenPom addFilter(String name, PublishFilter publishFilter) {
        if (name == null || publishFilter == null) {
            throw new InvalidUserDataException("Name and Filter must not be null.");
        }
        MavenPom pom = mavenPomFactory.createMavenPom();
        pomFilters.put(name, new DefaultPomFilter(name, pom, publishFilter));
        return pom;
    }

    public PublishFilter filter(String name) {
        if (name == null) {
            throw new InvalidUserDataException("Name must not be null.");
        }
        return pomFilters.get(name).getFilter();
    }

    public MavenPom pom(String name) {
        if (name == null) {
            throw new InvalidUserDataException("Name must not be null.");
        }
        return pomFilters.get(name).getPomTemplate();
    }

    public Iterable<PomFilter> getActivePomFilters() {
        Iterable<PomFilter> activeArtifactPoms;
        if (pomFilters.size() == 0 && getDefaultPomFilter() != null) {
            activeArtifactPoms = WrapUtil.toSet(getDefaultPomFilter());
        } else {
            activeArtifactPoms = pomFilters.values();
        }
        return activeArtifactPoms;
    }

    public MavenPomFactory getMavenPomFactory() {
        return mavenPomFactory;
    }

    public PomFilter getDefaultPomFilter() {
        if (defaultPomFilter == null) {
            defaultPomFilter = new DefaultPomFilter(PomFilterContainer.DEFAULT_ARTIFACT_POM_NAME, mavenPomFactory.createMavenPom(),
                PublishFilter.ALWAYS_ACCEPT);
        }
        return defaultPomFilter;
    }

    public void setDefaultPomFilter(PomFilter defaultPomFilter) {
        this.defaultPomFilter = defaultPomFilter;
    }

    public Map<String, PomFilter> getPomFilters() {
        return pomFilters;
    }

    public PomFilterContainer copy() {
        BasePomFilterContainer newPomFilterContainer = newInstance();
        newPomFilterContainer.setDefaultPomFilter(getDefaultPomFilter().copy());
        for (String key : pomFilters.keySet()) {
            newPomFilterContainer.getPomFilters().put(key, pomFilters.get(key).copy());
        }
        return newPomFilterContainer;
    }

    protected BasePomFilterContainer newInstance() {
        return new BasePomFilterContainer(mavenPomFactory);
    }
}
