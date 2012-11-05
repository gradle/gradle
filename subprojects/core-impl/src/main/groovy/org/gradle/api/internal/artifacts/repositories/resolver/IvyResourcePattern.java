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

package org.gradle.api.internal.artifacts.repositories.resolver;

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.descriptor.Artifact;

import java.util.HashMap;
import java.util.Map;

public class IvyResourcePattern implements ResourcePattern {
    private final String pattern;

    public IvyResourcePattern(String pattern) {
        this.pattern = pattern;
    }

    public String getPattern() {
        return pattern;
    }

    @Override
    public String toString() {
        return String.format("Ivy pattern '%s'", pattern);
    }

    public String toPath(Artifact artifact) {
        Map<String, Object> attributes = toAttributes(artifact);
        return IvyPatternHelper.substituteTokens(pattern, attributes);
    }

    public String toPathWithoutRevision(Artifact artifact) {
        Map<String, Object> attributes = toAttributes(artifact);
        attributes.remove(IvyPatternHelper.REVISION_KEY);
        return IvyPatternHelper.substituteTokens(pattern, attributes);
    }

    public String toModulePath(Artifact artifact) {
        throw new UnsupportedOperationException("not implemented yet.");
    }

    public String toModuleVersionPath(Artifact artifact) {
        throw new UnsupportedOperationException("not implemented yet.");
    }

    protected Map<String, Object> toAttributes(Artifact artifact) {
        return new HashMap<String, Object>(artifact.getAttributes());
    }
}
