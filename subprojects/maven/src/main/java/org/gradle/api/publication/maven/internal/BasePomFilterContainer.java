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
package org.gradle.api.publication.maven.internal;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.maven.PomFilterContainer;
import org.gradle.api.artifacts.maven.PublishFilter;
import org.gradle.internal.Factory;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.WrapUtil;

import java.util.HashMap;
import java.util.Map;

public class BasePomFilterContainer implements PomFilterContainer {
    private Map<String, PomFilter> pomFilters = new HashMap<String, PomFilter>();

    private PomFilter defaultPomFilter;

    private Factory<MavenPom> mavenPomFactory;

    public BasePomFilterContainer(Factory<MavenPom> mavenPomFactory) {
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

    public void filter(Closure filter) {
        setFilter(toFilter(filter));
    }

    public MavenPom addFilter(String name, Closure filter) {
        return addFilter(name, toFilter(filter));
    }

    private PublishFilter toFilter(final Closure filter) {
        return (PublishFilter) DefaultGroovyMethods.asType(filter, PublishFilter.class);
    }

    public MavenPom pom(Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, getPom());
    }

    public MavenPom pom(String name, Closure configureClosure) {
        return ConfigureUtil.configure(configureClosure, pom(name));
    }

    @Override
    public MavenPom pom(Action<? super MavenPom> configureAction) {
        MavenPom pom = getPom();
        configureAction.execute(pom);
        return pom;
    }

    @Override
    public MavenPom pom(String name, Action<? super MavenPom> configureAction) {
        MavenPom pom = pom(name);
        configureAction.execute(pom);
        return pom;
    }

    public MavenPom addFilter(String name, PublishFilter publishFilter) {
        if (name == null || publishFilter == null) {
            throw new InvalidUserDataException("Name and Filter must not be null.");
        }
        MavenPom pom = mavenPomFactory.create();
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

    public Factory<MavenPom> getMavenPomFactory() {
        return mavenPomFactory;
    }

    public PomFilter getDefaultPomFilter() {
        if (defaultPomFilter == null) {
            defaultPomFilter = new DefaultPomFilter(PomFilterContainer.DEFAULT_ARTIFACT_POM_NAME, mavenPomFactory.create(),
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

    protected BasePomFilterContainer newInstance() {
        return new BasePomFilterContainer(mavenPomFactory);
    }
}
