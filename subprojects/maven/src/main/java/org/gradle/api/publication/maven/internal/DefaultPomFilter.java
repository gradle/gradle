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
package org.gradle.api.publication.maven.internal;

import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.maven.PublishFilter;

public class DefaultPomFilter implements PomFilter {
    private String name;

    private MavenPom pom;

    private PublishFilter filter;

    public DefaultPomFilter(String name, MavenPom pom, PublishFilter filter) {
        this.name = name;
        this.pom = pom;
        this.filter = filter;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public MavenPom getPomTemplate() {
        return pom;
    }

    @Override
    public void setPomTemplate(MavenPom pom) {
        this.pom = pom;
    }

    @Override
    public PublishFilter getFilter() {
        return filter;
    }

    @Override
    public void setFilter(PublishFilter filter) {
        this.filter = filter;
    }
}
