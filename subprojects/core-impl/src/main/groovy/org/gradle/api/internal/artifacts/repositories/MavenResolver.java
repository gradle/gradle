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
package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.plugins.resolver.BasicResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;

import java.util.ArrayList;
import java.util.List;

public class MavenResolver extends IBiblioResolver {
    public MavenResolver() {
        setDescriptor(BasicResolver.DESCRIPTOR_OPTIONAL);
        setM2compatible(true);
    }

    public void addArtifactUrl(String url) {
        String newArtifactPattern = url + getPattern();
        List<String> artifactPatternList = new ArrayList<String>(getArtifactPatterns());
        artifactPatternList.add(newArtifactPattern);
        setArtifactPatterns(artifactPatternList);
    }
}
