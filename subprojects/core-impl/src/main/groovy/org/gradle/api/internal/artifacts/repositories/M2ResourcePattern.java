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

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.descriptor.Artifact;

import java.util.HashMap;
import java.util.Map;

public class M2ResourcePattern implements ResourcePattern {
    private final String pattern;

    public M2ResourcePattern(String pattern) {
        this.pattern = pattern;
    }

    public String getPattern() {
        return pattern;
    }

    public String transform(Artifact artifact) {
        Map<String, Object> attributes = new HashMap<String, Object>(artifact.getAttributes());
        String org = (String) attributes.get(IvyPatternHelper.ORGANISATION_KEY);
        if (org != null) {
            attributes.put(IvyPatternHelper.ORGANISATION_KEY, org.replace(".", "/"));
        }
        return IvyPatternHelper.substituteTokens(pattern, attributes);
    }
}
