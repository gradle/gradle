/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.cache.ArtifactResolutionControl;
import org.gradle.api.internal.artifacts.cache.DependencyResolutionControl;
import org.gradle.api.internal.artifacts.cache.ModuleResolutionControl;
import org.gradle.api.internal.artifacts.cache.ResolutionControl;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultResolvedModuleVersion;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;

import java.io.File;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of {@link CacheExpirationControl}.
 */
public class DefaultCacheExpirationControl implements CacheExpirationControl {

    private final ImmutableList<Action<? super DependencyResolutionControl>> dependencyCacheRules;
    private final ImmutableList<Action<? super ModuleResolutionControl>> moduleCacheRules;
    private final ImmutableList<Action<? super ArtifactResolutionControl>> artifactCacheRules;
    private final long keepDynamicVersionsFor;
    private final long keepChangingModulesFor;
    private final boolean offline;
    private final boolean refresh;

    public DefaultCacheExpirationControl(
        ImmutableList<Action<? super DependencyResolutionControl>> dependencyCacheRules,
        ImmutableList<Action<? super ModuleResolutionControl>> moduleCacheRules,
        ImmutableList<Action<? super ArtifactResolutionControl>> artifactCacheRules,
        long keepDynamicVersionsFor,
        long keepChangingModulesFor,
        boolean offline,
        boolean refresh
    ) {
        this.dependencyCacheRules = dependencyCacheRules;
        this.moduleCacheRules = moduleCacheRules;
        this.artifactCacheRules = artifactCacheRules;
        this.keepDynamicVersionsFor = keepDynamicVersionsFor;
        this.keepChangingModulesFor = keepChangingModulesFor;
        this.offline = offline;
        this.refresh = refresh;
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
    public Expiry moduleArtifactsExpiry(
        ModuleVersionIdentifier moduleVersionId, Set<ModuleComponentArtifactMetadata> artifacts,
        Duration age, boolean belongsToChangingModule, boolean moduleDescriptorInSync
    ) {
        CachedModuleResolutionControl resolutionControl = mustRefreshModule(moduleVersionId, new DefaultResolvedModuleVersion(moduleVersionId), age, belongsToChangingModule);
        if (belongsToChangingModule && !moduleDescriptorInSync) {
            resolutionControl.refresh();
        }
        return resolutionControl;
    }

    @Override
    public Expiry artifactExpiry(ModuleComponentArtifactMetadata artifactMetadata, File cachedArtifactFile, Duration age, boolean belongsToChangingModule, boolean moduleDescriptorInSync) {
        CachedArtifactResolutionControl artifactResolutionControl = new CachedArtifactResolutionControl(artifactMetadata, cachedArtifactFile, age.toMillis(), keepChangingModulesFor, belongsToChangingModule);

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

    private static abstract class AbstractResolutionControl<A, B> implements ResolutionControl<A, B>, Expiry {

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
         * If the age is less than 0, then it's probable that we've had a clock shift. In this case, treat the age as 1ms.
         */
        private static long correctForClockShift(long ageMillis) {
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

    private static class CachedModuleResolutionControl extends AbstractResolutionControl<ModuleVersionIdentifier, ResolvedModuleVersion> implements ModuleResolutionControl {

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

    private static class CachedArtifactResolutionControl extends AbstractResolutionControl<ModuleComponentArtifactMetadata, File> implements ArtifactResolutionControl {

        private final boolean belongsToChangingModule;

        private CachedArtifactResolutionControl(ModuleComponentArtifactMetadata artifact, File cachedResult, long ageMillis, long keepForMillis, boolean belongsToChangingModule) {
            super(artifact, cachedResult, ageMillis, keepForMillis);
            this.belongsToChangingModule = belongsToChangingModule;
        }

        @Override
        public boolean belongsToChangingModule() {
            return belongsToChangingModule;
        }

    }

    private class CachedDependencyResolutionControl extends AbstractResolutionControl<ModuleIdentifier, Set<ModuleVersionIdentifier>> implements DependencyResolutionControl {

        private CachedDependencyResolutionControl(ModuleIdentifier request, Set<ModuleVersionIdentifier> result, long ageMillis, long keepForMillis) {
            super(request, result, ageMillis, keepForMillis);
        }

    }

}
