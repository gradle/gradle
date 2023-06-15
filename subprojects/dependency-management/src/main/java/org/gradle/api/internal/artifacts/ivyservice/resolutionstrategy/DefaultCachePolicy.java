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

import org.gradle.api.Action;
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.cache.ArtifactResolutionControl;
import org.gradle.api.internal.artifacts.cache.DependencyResolutionControl;
import org.gradle.api.internal.artifacts.cache.ModuleResolutionControl;
import org.gradle.api.internal.artifacts.cache.ResolutionControl;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.Expiry;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultResolvedModuleVersion;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
        this.dependencyCacheRules = new ArrayList<>();
        this.moduleCacheRules = new ArrayList<>();
        this.artifactCacheRules = new ArrayList<>();

        cacheDynamicVersionsFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
        cacheChangingModulesFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
        cacheMissingArtifactsFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
    }

    DefaultCachePolicy(DefaultCachePolicy policy) {
        this.dependencyCacheRules = new ArrayList<>(policy.dependencyCacheRules);
        this.moduleCacheRules = new ArrayList<>(policy.moduleCacheRules);
        this.artifactCacheRules = new ArrayList<>(policy.artifactCacheRules);
        this.offline = policy.offline;
        this.refresh = policy.refresh;
    }

    /**
     * Sets the validator to invoke prior to each mutation.
     */
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

    public void cacheDynamicVersionsFor(final int value, final TimeUnit unit) {
        keepDynamicVersionsFor = unit.toMillis(value);
        eachDependency(dependencyResolutionControl -> {
            if (!dependencyResolutionControl.getCachedResult().isEmpty()) {
                dependencyResolutionControl.cacheFor(value, unit);
            }
        });
    }

    public void cacheChangingModulesFor(final int value, final TimeUnit units) {
        keepChangingModulesFor = units.toMillis(value);
        eachModule(moduleResolutionControl -> {
            if (moduleResolutionControl.isChanging()) {
                moduleResolutionControl.cacheFor(value, units);
            }
        });
        eachArtifact(artifactResolutionControl -> {
            if (artifactResolutionControl.belongsToChangingModule()) {
                artifactResolutionControl.cacheFor(value, units);
            }
        });
    }

    private void cacheMissingArtifactsFor(final int value, final TimeUnit units) {
        eachArtifact(artifactResolutionControl -> {
            if (artifactResolutionControl.getCachedResult() == null) {
                artifactResolutionControl.cacheFor(value, units);
            }
        });
    }

    /**
     * Apply a rule to control resolution of dependencies.
     *
     * @param rule the rule to apply
     */
    private void eachDependency(Action<? super DependencyResolutionControl> rule) {
        mutationValidator.validateMutation(STRATEGY);
        dependencyCacheRules.add(0, rule);
    }

    /**
     * Apply a rule to control resolution of modules.
     *
     * @param rule the rule to apply
     */
    private void eachModule(Action<? super ModuleResolutionControl> rule) {
        mutationValidator.validateMutation(STRATEGY);
        moduleCacheRules.add(0, rule);
    }

    /**
     * Apply a rule to control resolution of artifacts.
     *
     * @param rule the rule to apply
     */
    private void eachArtifact(Action<? super ArtifactResolutionControl> rule) {
        mutationValidator.validateMutation(STRATEGY);
        artifactCacheRules.add(0, rule);
    }

    /**
     * @param resolutionControl The resolution control to mutate
     * @return If the offline rule was applied
     */
    private boolean applyOfflineRule(ResolutionControl<?, ?> resolutionControl) {
        if (offline) {
            resolutionControl.useCachedResult();
            return true;
        }
        return false;
    }

    /**
     * @param resolutionControl The resolution control to mutate
     * @return If the refresh rule was applied
     */
    private boolean applyRefreshRule(ResolutionControl<?, ?> resolutionControl) {
        if (refresh) {
            resolutionControl.refresh();
            return true;
        }
        return false;
    }

    @Override
    public Expiry versionListExpiry(ModuleIdentifier moduleIdentifier, Set<ModuleVersionIdentifier> moduleVersions, Duration age) {
        CachedDependencyResolutionControl dependencyResolutionControl = new CachedDependencyResolutionControl(moduleIdentifier, moduleVersions, age.toMillis(), keepDynamicVersionsFor);

        if (applyOfflineRule(dependencyResolutionControl) || applyRefreshRule(dependencyResolutionControl)) {
            return dependencyResolutionControl;
        }

        for (Action<? super DependencyResolutionControl> rule : dependencyCacheRules) {
            rule.execute(dependencyResolutionControl);
            if (dependencyResolutionControl.ruleMatch()) {
                break;
            }
        }

        return dependencyResolutionControl;
    }

    @Override
    public Expiry missingModuleExpiry(ModuleComponentIdentifier component, Duration age) {
        return mustRefreshModule(component, null, age, false);
    }

    @Override
    public Expiry moduleExpiry(ModuleComponentIdentifier component, ResolvedModuleVersion resolvedModuleVersion, Duration age) {
        return mustRefreshModule(component, resolvedModuleVersion, age, false);
    }

    @Override
    public Expiry moduleExpiry(ResolvedModuleVersion resolvedModuleVersion, Duration age, boolean changing) {
        return mustRefreshModule(resolvedModuleVersion.getId(), resolvedModuleVersion, age, changing);
    }

    @Override
    public Expiry changingModuleExpiry(ModuleComponentIdentifier component, ResolvedModuleVersion resolvedModuleVersion, Duration age) {
        return mustRefreshModule(component, resolvedModuleVersion, age, true);
    }

    private Expiry mustRefreshModule(ModuleComponentIdentifier component, ResolvedModuleVersion version, Duration age, boolean changingModule) {
        return mustRefreshModule(DefaultModuleVersionIdentifier.newId(component.getModuleIdentifier(), component.getVersion()), version, age, changingModule);
    }

    private CachedModuleResolutionControl mustRefreshModule(ModuleVersionIdentifier moduleVersionId, ResolvedModuleVersion version, Duration age, boolean changingModule) {
        CachedModuleResolutionControl moduleResolutionControl = new CachedModuleResolutionControl(moduleVersionId, version, changingModule, age.toMillis(), changingModule ? keepChangingModulesFor: Long.MAX_VALUE);

        if (applyOfflineRule(moduleResolutionControl) || applyRefreshRule(moduleResolutionControl)) {
            return moduleResolutionControl;
        }

        for (Action<? super ModuleResolutionControl> rule : moduleCacheRules) {
            rule.execute(moduleResolutionControl);
            if (moduleResolutionControl.ruleMatch()) {
                break;
            }
        }

        return moduleResolutionControl;
    }

    @Override
    public Expiry moduleArtifactsExpiry(ModuleVersionIdentifier moduleVersionId, Set<ArtifactIdentifier> artifacts,
                                        Duration age, boolean belongsToChangingModule, boolean moduleDescriptorInSync) {
        CachedModuleResolutionControl resolutionControl = mustRefreshModule(moduleVersionId, new DefaultResolvedModuleVersion(moduleVersionId), age, belongsToChangingModule);
        if (belongsToChangingModule && !moduleDescriptorInSync) {
            resolutionControl.refresh();
        }
        return resolutionControl;
    }

    @Override
    public Expiry artifactExpiry(ArtifactIdentifier artifactIdentifier, File cachedArtifactFile, Duration age, boolean belongsToChangingModule, boolean moduleDescriptorInSync) {
        CachedArtifactResolutionControl artifactResolutionControl = new CachedArtifactResolutionControl(artifactIdentifier, cachedArtifactFile, age.toMillis(), keepChangingModulesFor, belongsToChangingModule);

        if (applyOfflineRule(artifactResolutionControl) || applyRefreshRule(artifactResolutionControl)) {
            return artifactResolutionControl;
        }

        for (Action<? super ArtifactResolutionControl> rule : artifactCacheRules) {
            rule.execute(artifactResolutionControl);
            if (artifactResolutionControl.ruleMatch()) {
                break;
            }
        }

        if (belongsToChangingModule && !moduleDescriptorInSync) {
            artifactResolutionControl.refresh();
        }

        return artifactResolutionControl;
    }

    DefaultCachePolicy copy() {
        return new DefaultCachePolicy(this);
    }

    private abstract static class AbstractResolutionControl<A, B> implements ResolutionControl<A, B>, Expiry {
        private final A request;
        private final B cachedResult;
        private final long ageMillis;
        private long keepForMillis;
        private boolean ruleMatch;
        private boolean mustCheck;

        private AbstractResolutionControl(A request, B cachedResult, long ageMillis, long keepForMillis) {
            this.request = request;
            this.cachedResult = cachedResult;
            this.ageMillis = correctForClockShift(ageMillis);
            this.keepForMillis = keepForMillis;
        }

        /**
         * If the age < 0, then it's probable that we've had a clock shift. In this case, treat the age as 1ms.
         */
        private long correctForClockShift(long ageMillis) {
            if (ageMillis < 0) {
                return 1;
            }
            return ageMillis;
        }

        @Override
        public A getRequest() {
            return request;
        }

        @Override
        public B getCachedResult() {
            return cachedResult;
        }

        @Override
        public void cacheFor(int value, TimeUnit units) {
            keepForMillis = TimeUnit.MILLISECONDS.convert(value, units);
            setMustCheck(ageMillis > keepForMillis);
        }

        @Override
        public void useCachedResult() {
            setMustCheck(false);
        }

        @Override
        public void refresh() {
            setMustCheck(true);
        }

        private void setMustCheck(boolean val) {
            ruleMatch = true;
            mustCheck = val;
        }

        public boolean ruleMatch() {
            return ruleMatch;
        }

        @Override
        public Duration getKeepFor() {
            if (mustCheck && ageMillis > 0) {
                // Must check and was not cached in this build, so do not keep the value
                return Duration.ZERO;
            }
            if (keepForMillis == Long.MAX_VALUE) {
                return Duration.ofMillis(Long.MAX_VALUE);
            }
            return Duration.ofMillis(Math.max(0, keepForMillis - ageMillis));
        }

        @Override
        public boolean isMustCheck() {
            return mustCheck && ageMillis > 0;
        }
    }

    private class CachedDependencyResolutionControl extends AbstractResolutionControl<ModuleIdentifier, Set<ModuleVersionIdentifier>> implements DependencyResolutionControl {
        private CachedDependencyResolutionControl(ModuleIdentifier request, Set<ModuleVersionIdentifier> result, long ageMillis, long keepForMillis) {
            super(request, result, ageMillis, keepForMillis);
        }
    }

    private class CachedModuleResolutionControl extends AbstractResolutionControl<ModuleVersionIdentifier, ResolvedModuleVersion> implements ModuleResolutionControl {
        private final boolean changing;

        private CachedModuleResolutionControl(ModuleVersionIdentifier moduleVersionId, ResolvedModuleVersion cachedVersion, boolean changing, long ageMillis, long keepForMillis) {
            super(moduleVersionId, cachedVersion, ageMillis, keepForMillis);
            this.changing = changing;
        }

        @Override
        public boolean isChanging() {
            return changing;
        }
    }

    private class CachedArtifactResolutionControl extends AbstractResolutionControl<ArtifactIdentifier, File> implements ArtifactResolutionControl {
        private final boolean belongsToChangingModule;

        private CachedArtifactResolutionControl(ArtifactIdentifier artifactIdentifier, File cachedResult, long ageMillis, long keepForMillis, boolean belongsToChangingModule) {
            super(artifactIdentifier, cachedResult, ageMillis, keepForMillis);
            this.belongsToChangingModule = belongsToChangingModule;
        }

        @Override
        public boolean belongsToChangingModule() {
            return belongsToChangingModule;
        }
    }
}
