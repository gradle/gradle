/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.component.external.descriptor;

import org.apache.ivy.core.module.id.ArtifactId;
import org.gradle.api.Transformer;
import org.gradle.internal.component.model.Exclude;
import org.gradle.util.CollectionUtils;

import java.util.List;

public class DefaultExclude implements Exclude {
    private final ArtifactId artifactId;
    private final String[] configurations;
    private final String patternMatcher;

    public DefaultExclude(ArtifactId artifactId, String[] configurations, String patternMatcher) {
        this.artifactId = artifactId;
        this.configurations = configurations;
        this.patternMatcher = patternMatcher;
    }

    @Override
    public ArtifactId getId() {
        return artifactId;
    }

    @Override
    public String[] getConfigurations() {
        return configurations;
    }

    @Override
    public String getMatcher() {
        return patternMatcher;
    }

    public static Exclude forIvyExclude(org.apache.ivy.core.module.descriptor.ExcludeRule excludeRule) {
        return new DefaultExclude(excludeRule.getId(), excludeRule.getConfigurations(), excludeRule.getMatcher().getName());
    }

    public static List<Exclude> forIvyExcludes(org.apache.ivy.core.module.descriptor.ExcludeRule[] excludeRules) {
        return CollectionUtils.collect(excludeRules, new Transformer<Exclude, org.apache.ivy.core.module.descriptor.ExcludeRule>() {
            @Override
            public Exclude transform(org.apache.ivy.core.module.descriptor.ExcludeRule excludeRule) {
                return forIvyExclude(excludeRule);
            }
        });
    }
}
