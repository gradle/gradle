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
import org.gradle.api.internal.artifacts.cache.ArtifactResolutionControl;
import org.gradle.api.internal.artifacts.cache.DependencyResolutionControl;
import org.gradle.api.internal.artifacts.cache.ModuleResolutionControl;
import org.gradle.api.internal.artifacts.cache.ResolutionControl;

import java.time.Duration;
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
    public Expiry versionListExpiry(boolean hasVersions, Duration age) {
        CachedDependencyResolutionControl dependencyResolutionControl = new CachedDependencyResolutionControl(hasVersions, age.toMillis(), keepDynamicVersionsFor);

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
    public Expiry moduleExpiry(Duration age, boolean changing) {
        return mustRefreshModule(age, changing);
    }

    @Override
    public Expiry moduleArtifactsExpiry(Duration age, boolean belongsToChangingModule, boolean moduleDescriptorInSync) {
        CachedModuleResolutionControl resolutionControl = mustRefreshModule(age, belongsToChangingModule);
        if (belongsToChangingModule && !moduleDescriptorInSync) {
            resolutionControl.refresh();
        }
        return resolutionControl;
    }

    @Override
    public Expiry artifactExpiry(boolean missing, Duration age, boolean belongsToChangingModule, boolean moduleDescriptorInSync) {
        CachedArtifactResolutionControl artifactResolutionControl = new CachedArtifactResolutionControl(missing, age.toMillis(), keepChangingModulesFor, belongsToChangingModule);

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

    private CachedModuleResolutionControl mustRefreshModule(Duration age, boolean changingModule) {
        CachedModuleResolutionControl moduleResolutionControl = new CachedModuleResolutionControl(changingModule, age.toMillis(), changingModule ? keepChangingModulesFor: Long.MAX_VALUE);

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
    private boolean applyOfflineRule(ResolutionControl resolutionControl) {
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
    private boolean applyRefreshRule(ResolutionControl resolutionControl) {
        if (refresh) {
            resolutionControl.refresh();
            return true;
        }
        return false;
    }

    @Override
    public ImmutableList<Action<? super DependencyResolutionControl>> getDependencyCacheRules() {
        return dependencyCacheRules;
    }

    @Override
    public ImmutableList<Action<? super ModuleResolutionControl>> getModuleCacheRules() {
        return moduleCacheRules;
    }

    @Override
    public ImmutableList<Action<? super ArtifactResolutionControl>> getArtifactCacheRules() {
        return artifactCacheRules;
    }

    private static abstract class AbstractResolutionControl implements ResolutionControl, Expiry {

        private final long ageMillis;
        private long keepForMillis;
        private boolean ruleMatch;
        private boolean mustCheck;

        private AbstractResolutionControl(long ageMillis, long keepForMillis) {
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

    private static class CachedModuleResolutionControl extends AbstractResolutionControl implements ModuleResolutionControl {

        private final boolean changing;

        private CachedModuleResolutionControl(boolean changing, long ageMillis, long keepForMillis) {
            super(ageMillis, keepForMillis);
            this.changing = changing;
        }

        @Override
        public boolean isChanging() {
            return changing;
        }

    }

    private static class CachedArtifactResolutionControl extends AbstractResolutionControl implements ArtifactResolutionControl {

        private final boolean missing;
        private final boolean belongsToChangingModule;

        private CachedArtifactResolutionControl(boolean missing, long ageMillis, long keepForMillis, boolean belongsToChangingModule) {
            super(ageMillis, keepForMillis);
            this.missing = missing;
            this.belongsToChangingModule = belongsToChangingModule;
        }

        @Override
        public boolean isMissing() {
            return missing;
        }

        @Override
        public boolean belongsToChangingModule() {
            return belongsToChangingModule;
        }

    }

    private static class CachedDependencyResolutionControl extends AbstractResolutionControl implements DependencyResolutionControl {

        private final boolean hasVersions;

        private CachedDependencyResolutionControl(boolean hasVersions, long ageMillis, long keepForMillis) {
            super(ageMillis, keepForMillis);
            this.hasVersions = hasVersions;
        }

        @Override
        public boolean hasVersions() {
            return hasVersions;
        }

    }

}
