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
package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.internal.artifacts.cache.ArtifactResolutionControl;
import org.gradle.api.internal.artifacts.cache.DependencyResolutionControl;
import org.gradle.api.internal.artifacts.cache.ModuleResolutionControl;
import org.gradle.api.internal.artifacts.configurations.CachePolicy;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl;
import org.gradle.api.internal.artifacts.ivyservice.DefaultCacheExpirationControl;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.gradle.api.internal.artifacts.configurations.MutationValidator.MutationType.STRATEGY;

public class DefaultCachePolicy implements CachePolicy {
    private static final int SECONDS_IN_DAY = 24 * 60 * 60;
    private static final int MILLISECONDS_IN_DAY = SECONDS_IN_DAY * 1000;

    private static final Action<DependencyResolutionControl> DEFAULT_DYNAMIC_VERSIONS_RULE = dependencyResolutionControl -> {
        if (!dependencyResolutionControl.getCachedResult().isEmpty()) {
            dependencyResolutionControl.cacheFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
        }
    };
    private static final Action<ModuleResolutionControl> DEFAULT_CHANGING_MODULE_RULE = moduleResolutionControl -> {
        if (moduleResolutionControl.isChanging()) {
            moduleResolutionControl.cacheFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
        }
    };
    private static final Action<ArtifactResolutionControl> DEFAULT_CHANGING_MODULE_ARTIFACT_RULE = artifactResolutionControl -> {
        if (artifactResolutionControl.belongsToChangingModule()) {
            artifactResolutionControl.cacheFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
        }
    };
    private static final Action<ArtifactResolutionControl> DEFAULT_MISSING_ARTIFACT_RULE = artifactResolutionControl -> {
        if (artifactResolutionControl.getCachedResult() == null) {
            artifactResolutionControl.cacheFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
        }
    };

    private static final ImmutableList<Action<? super DependencyResolutionControl>> DEFAULT_DEPENDENCY_CACHE_RULES = ImmutableList.of(DEFAULT_DYNAMIC_VERSIONS_RULE);
    private static final ImmutableList<Action<? super ModuleResolutionControl>> DEFAULT_MODULE_CACHE_RULES = ImmutableList.of(DEFAULT_CHANGING_MODULE_RULE);
    private static final ImmutableList<Action<? super ArtifactResolutionControl>> DEFAULT_ARTIFACT_CACHE_RULES = ImmutableList.of(DEFAULT_MISSING_ARTIFACT_RULE, DEFAULT_CHANGING_MODULE_ARTIFACT_RULE);

    @Nullable
    List<Action<? super DependencyResolutionControl>> dependencyCacheRules;
    @Nullable
    List<Action<? super ModuleResolutionControl>> moduleCacheRules;
    @Nullable
    List<Action<? super ArtifactResolutionControl>> artifactCacheRules;

    private MutationValidator mutationValidator = MutationValidator.IGNORE;
    private long keepDynamicVersionsFor = MILLISECONDS_IN_DAY;
    private long keepChangingModulesFor = MILLISECONDS_IN_DAY;
    private boolean offline = false;
    private boolean refresh = false;

    public DefaultCachePolicy() {
    }

    private DefaultCachePolicy(DefaultCachePolicy policy) {
        this.dependencyCacheRules = policy.dependencyCacheRules == null ? null : new ArrayList<>(policy.dependencyCacheRules);
        this.moduleCacheRules = policy.moduleCacheRules == null ? null : new ArrayList<>(policy.moduleCacheRules);
        this.artifactCacheRules = policy.artifactCacheRules == null ? null : new ArrayList<>(policy.artifactCacheRules);
        this.keepDynamicVersionsFor = policy.keepDynamicVersionsFor;
        this.keepChangingModulesFor = policy.keepChangingModulesFor;
        this.offline = policy.offline;
        this.refresh = policy.refresh;
    }

    /**
     * Sets the validator to invoke prior to each mutation.
     */
    @Override
    public void setMutationValidator(MutationValidator validator) {
        this.mutationValidator = validator;
    }

    @Override
    public void setOffline() {
        mutationValidator.validateMutation(STRATEGY);
        offline = true;
    }

    @Override
    public void setRefreshDependencies() {
        mutationValidator.validateMutation(STRATEGY);
        refresh = true;
    }

    @Override
    public void cacheDynamicVersionsFor(final int value, final TimeUnit unit) {
        keepDynamicVersionsFor = unit.toMillis(value);
        mutationValidator.validateMutation(STRATEGY);
        if (dependencyCacheRules == null) {
            dependencyCacheRules = new ArrayList<>(1);
        }
        dependencyCacheRules.add(0, dependencyResolutionControl -> {
            if (!dependencyResolutionControl.getCachedResult().isEmpty()) {
                dependencyResolutionControl.cacheFor(value, unit);
            }
        });
    }

    @Override
    public void cacheChangingModulesFor(final int value, final TimeUnit units) {
        keepChangingModulesFor = units.toMillis(value);
        mutationValidator.validateMutation(STRATEGY);

        if (moduleCacheRules == null) {
            moduleCacheRules = new ArrayList<>(1);
        }
        moduleCacheRules.add(0, moduleResolutionControl -> {
            if (moduleResolutionControl.isChanging()) {
                moduleResolutionControl.cacheFor(value, units);
            }
        });

        if (artifactCacheRules == null) {
            artifactCacheRules = new ArrayList<>(1);
        }
        artifactCacheRules.add(0, artifactResolutionControl -> {
            if (artifactResolutionControl.belongsToChangingModule()) {
                artifactResolutionControl.cacheFor(value, units);
            }
        });
    }

    @Override
    public CachePolicy copy() {
        return new DefaultCachePolicy(this);
    }

    @Override
    public CacheExpirationControl asImmutable() {
        return new DefaultCacheExpirationControl(
            withDefaultRules(dependencyCacheRules, DEFAULT_DEPENDENCY_CACHE_RULES),
            withDefaultRules(moduleCacheRules, DEFAULT_MODULE_CACHE_RULES),
            withDefaultRules(artifactCacheRules, DEFAULT_ARTIFACT_CACHE_RULES),
            keepDynamicVersionsFor,
            keepChangingModulesFor,
            offline,
            refresh
        );
    }

    /**
     * Appends the shared default rules to the user-provided rules, preserving the historic
     * evaluation order where user rules are evaluated before the default rules.
     */
    private static <T> ImmutableList<Action<? super T>> withDefaultRules(@Nullable List<Action<? super T>> userRules, ImmutableList<Action<? super T>> defaultRules) {
        if (userRules == null) {
            return defaultRules;
        }
        return ImmutableList.<Action<? super T>>builder()
            .addAll(userRules)
            .addAll(defaultRules)
            .build();
    }

}
