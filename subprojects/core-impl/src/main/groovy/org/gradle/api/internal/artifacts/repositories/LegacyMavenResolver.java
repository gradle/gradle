/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories;

import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver;

public class LegacyMavenResolver extends LegacyDependencyResolver {
    private final MavenResolver resolver;

    public LegacyMavenResolver(MavenResolver resolver) {
        super(resolver);
        this.resolver = resolver;
    }

    // A bunch of configuration properties that we don't (yet) support in our model via the DSL. Users can still tweak these on the resolver using mavenRepo().
    public boolean isUsepoms() {
        return resolver.isUsepoms();
    }

    public void setUsepoms(boolean usepoms) {
        resolver.setUsepoms(usepoms);
    }

    public boolean isUseMavenMetadata() {
        return resolver.isUseMavenMetadata();
    }

    public void setUseMavenMetadata(boolean useMavenMetadata) {
        resolver.setUseMavenMetadata(useMavenMetadata);
    }

    public String getPattern() {
        return resolver.getPattern();
    }

    public void setPattern(String pattern) {
        resolver.setPattern(pattern);
    }

    public String getRoot() {
        return resolver.getRoot();
    }

    public void setRoot(String root) {
        resolver.setRoot(root);
    }
}
