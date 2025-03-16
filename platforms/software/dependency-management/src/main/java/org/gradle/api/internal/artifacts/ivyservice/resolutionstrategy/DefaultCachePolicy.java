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
import org.gradle.api.internal.artifacts.ivyservice.DefaultCacheExpirationControl;
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.gradle.api.internal.artifacts.configurations.MutationValidator.MutationType.STRATEGY;

public class DefaultCachePolicy implements CachePolicy {
    private static final int SECONDS_IN_DAY = 24 * 60 * 60;
    private static final int MILLISECONDS_IN_DAY = SECONDS_IN_DAY * 1000;

    final List<Action<? super DependencyResolutionControl>> dependencyCacheRules;
    final List<Action<? super ModuleResolutionControl>> moduleCacheRules;
    final List<Action<? super ArtifactResolutionControl>> artifactCacheRules;
    private MutationValidator mutationValidator = MutationValidator.IGNORE;
    private long keepDynamicVersionsFor = MILLISECONDS_IN_DAY;
    private long keepChangingModulesFor = MILLISECONDS_IN_DAY;
    private boolean offline = false;
    private boolean refresh = false;

    public DefaultCachePolicy() {
        this.dependencyCacheRules = new ArrayList<>(1);
        this.moduleCacheRules = new ArrayList<>(1);
        this.artifactCacheRules = new ArrayList<>(2);

        cacheDynamicVersionsFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
        cacheChangingModulesFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
        cacheMissingArtifactsFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
    }

    private DefaultCachePolicy(DefaultCachePolicy policy) {
        this.dependencyCacheRules = new ArrayList<>(policy.dependencyCacheRules);
        this.moduleCacheRules = new ArrayList<>(policy.moduleCacheRules);
        this.artifactCacheRules = new ArrayList<>(policy.artifactCacheRules);
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

        moduleCacheRules.add(0, moduleResolutionControl -> {
            if (moduleResolutionControl.isChanging()) {
                moduleResolutionControl.cacheFor(value, units);
            }
        });

        artifactCacheRules.add(0, artifactResolutionControl -> {
            if (artifactResolutionControl.belongsToChangingModule()) {
                artifactResolutionControl.cacheFor(value, units);
            }
        });
    }

    private void cacheMissingArtifactsFor(final int value, final TimeUnit units) {
        mutationValidator.validateMutation(STRATEGY);
        artifactCacheRules.add(0, artifactResolutionControl -> {
            if (artifactResolutionControl.getCachedResult() == null) {
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
            ImmutableList.copyOf(dependencyCacheRules),
            ImmutableList.copyOf(moduleCacheRules),
            ImmutableList.copyOf(artifactCacheRules),
            keepDynamicVersionsFor,
            keepChangingModulesFor,
            offline,
            refresh
        );
    }

}
