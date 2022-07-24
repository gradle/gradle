/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes;

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.CachingExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.ExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.LoggingExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.NormalizingExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories.OptimizingExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple.DefaultExcludeFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ModuleExclusions {
    private final CachingExcludeFactory.MergeCaches mergeCaches = new CachingExcludeFactory.MergeCaches();
    // please keep the formatting below as it helps enabling or disabling stages
    private final ExcludeFactory factory = new OptimizingExcludeFactory(// optimizes for nulls, 2-params, ... mandatory
        new CachingExcludeFactory(// caches the result of TL operations
            LoggingExcludeFactory.maybeLog(new NormalizingExcludeFactory(// performs algebra
                new CachingExcludeFactory(// caches the result of optimization operations
                    new DefaultExcludeFactory(), // the end of the chain, mandatory
                    mergeCaches // shares the same caches as the top level one as after reducing we can find already cached merge operations
                )
            )),
            mergeCaches
        )
    );
    private final Map<ExcludeMetadata, ExcludeSpec> metadataToExcludeCache = Maps.newConcurrentMap();
    private final ExcludeSpec nothing;

    public ModuleExclusions() {
        nothing = factory.nothing();
    }

    public ExcludeSpec excludeAny(Collection<? extends ExcludeMetadata> excludes) {
        if (excludes.isEmpty()) {
            // avoids creation of empty hashset
            return nothing;
        }
        if (excludes.size() == 1) {
            return forExclude(excludes.iterator().next());
        }
        Set<ExcludeSpec> result = new HashSet<>();
        for (ExcludeMetadata exclude : excludes) {
            result.add(forExclude(exclude));
        }
        return factory.anyOf(result);
    }

    public ExcludeSpec nothing() {
        return nothing;
    }

    private ExcludeSpec forExclude(ExcludeMetadata r) {
        return metadataToExcludeCache.computeIfAbsent(r, rule -> {
            // For custom ivy pattern matchers, don't inspect the rule any more deeply: this prevents us from doing smart merging later
            if (!PatternMatchers.isExactMatcher(rule.getMatcher())) {
                return factory.ivyPatternExclude(rule.getModuleId(), rule.getArtifact(), rule.getMatcher());
            }

            ModuleIdentifier moduleId = rule.getModuleId();
            IvyArtifactName artifact = rule.getArtifact();
            boolean anyOrganisation = isWildcard(moduleId.getGroup());
            boolean anyModule = isWildcard(moduleId.getName());

            // Build a strongly typed (mergeable) exclude spec for each supplied rule
            if (artifact == null) {
                if (!anyOrganisation && !anyModule) {
                    return factory.moduleId(moduleId);
                } else if (!anyModule) {
                    return factory.module(moduleId.getName());
                } else if (!anyOrganisation) {
                    return factory.group(moduleId.getGroup());
                } else {
                    return factory.everything();
                }
            } else {
                return factory.ivyPatternExclude(moduleId, artifact, rule.getMatcher());
            }
        });
    }

    private static boolean isWildcard(String attribute) {
        return PatternMatchers.ANY_EXPRESSION.equals(attribute);
    }

    public ExcludeSpec excludeAny(ExcludeSpec one, ExcludeSpec two) {
        return factory.anyOf(one, two);
    }

    public ExcludeSpec excludeAll(ExcludeSpec one, ExcludeSpec two) {
        return factory.allOf(one, two);
    }

    public ExcludeSpec excludeAll(Set<ExcludeSpec> specs) {
        return factory.allOf(specs);
    }

    public ExcludeSpec excludeAny(Set<ExcludeSpec> specs) {
        return factory.anyOf(specs);
    }
}
