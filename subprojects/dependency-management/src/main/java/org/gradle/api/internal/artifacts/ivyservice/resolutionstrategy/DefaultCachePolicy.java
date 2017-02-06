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
import org.gradle.api.artifacts.cache.ArtifactResolutionControl;
import org.gradle.api.artifacts.cache.DependencyResolutionControl;
import org.gradle.api.artifacts.cache.ModuleResolutionControl;
import org.gradle.api.artifacts.cache.ResolutionControl;
import org.gradle.api.artifacts.cache.ResolutionRules;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.DefaultResolvedModuleVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.gradle.api.internal.artifacts.configurations.MutationValidator.MutationType.STRATEGY;

public class DefaultCachePolicy implements CachePolicy, ResolutionRules {
    private static final int SECONDS_IN_DAY = 24 * 60 * 60;

    final List<Action<? super DependencyResolutionControl>> dependencyCacheRules;
    final List<Action<? super ModuleResolutionControl>> moduleCacheRules;
    final List<Action<? super ArtifactResolutionControl>> artifactCacheRules;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private MutationValidator mutationValidator = MutationValidator.IGNORE;

    public DefaultCachePolicy(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.dependencyCacheRules = new ArrayList<Action<? super DependencyResolutionControl>>();
        this.moduleCacheRules = new ArrayList<Action<? super ModuleResolutionControl>>();
        this.artifactCacheRules = new ArrayList<Action<? super ArtifactResolutionControl>>();

        cacheDynamicVersionsFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
        cacheChangingModulesFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
        cacheMissingArtifactsFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
    }

    DefaultCachePolicy(DefaultCachePolicy policy) {
        this.moduleIdentifierFactory = policy.moduleIdentifierFactory;
        this.dependencyCacheRules = new ArrayList<Action<? super DependencyResolutionControl>>(policy.dependencyCacheRules);
        this.moduleCacheRules = new ArrayList<Action<? super ModuleResolutionControl>>(policy.moduleCacheRules);
        this.artifactCacheRules = new ArrayList<Action<? super ArtifactResolutionControl>>(policy.artifactCacheRules);
    }

    /**
     * Sets the validator to invoke prior to each mutation.
     */
    public void setMutationValidator(MutationValidator validator) {
        this.mutationValidator = validator;
    }

    public void eachDependency(Action<? super DependencyResolutionControl> rule) {
        mutationValidator.validateMutation(STRATEGY);
        dependencyCacheRules.add(0, rule);
    }

    public void eachModule(Action<? super ModuleResolutionControl> rule) {
        mutationValidator.validateMutation(STRATEGY);
        moduleCacheRules.add(0, rule);
    }

    public void eachArtifact(Action<? super ArtifactResolutionControl> rule) {
        mutationValidator.validateMutation(STRATEGY);
        artifactCacheRules.add(0, rule);
    }

    public void cacheDynamicVersionsFor(final int value, final TimeUnit unit) {
        eachDependency(new Action<DependencyResolutionControl>() {
            public void execute(DependencyResolutionControl dependencyResolutionControl) {
                dependencyResolutionControl.cacheFor(value, unit);
            }
        });
    }

    public void cacheChangingModulesFor(final int value, final TimeUnit units) {
        eachModule(new Action<ModuleResolutionControl>() {
            public void execute(ModuleResolutionControl moduleResolutionControl) {
                if (moduleResolutionControl.isChanging()) {
                    moduleResolutionControl.cacheFor(value, units);
                }
            }
        });
        eachArtifact(new Action<ArtifactResolutionControl>() {
            public void execute(ArtifactResolutionControl artifactResolutionControl) {
                if (artifactResolutionControl.belongsToChangingModule()) {
                    artifactResolutionControl.cacheFor(value, units);
                }
            }
        });
    }

    private void cacheMissingArtifactsFor(final int value, final TimeUnit units) {
        eachArtifact(new Action<ArtifactResolutionControl>() {
            public void execute(ArtifactResolutionControl artifactResolutionControl) {
                if (artifactResolutionControl.getCachedResult() == null) {
                    artifactResolutionControl.cacheFor(value, units);
                }
            }
        });
    }

    public boolean mustRefreshVersionList(final ModuleIdentifier moduleIdentifier, Set<ModuleVersionIdentifier> matchingVersions, long ageMillis) {
        CachedDependencyResolutionControl dependencyResolutionControl = new CachedDependencyResolutionControl(moduleIdentifier, matchingVersions, ageMillis);

        for (Action<? super DependencyResolutionControl> rule : dependencyCacheRules) {
            rule.execute(dependencyResolutionControl);
            if (dependencyResolutionControl.ruleMatch()) {
                return dependencyResolutionControl.mustCheck();
            }
        }

        return false;
    }

    public boolean mustRefreshMissingModule(ModuleComponentIdentifier component, long ageMillis) {
        return mustRefreshModule(component, null, ageMillis, false);
    }

    public boolean mustRefreshModule(ModuleComponentIdentifier component, ResolvedModuleVersion resolvedModuleVersion, long ageMillis) {
        return mustRefreshModule(component, resolvedModuleVersion, ageMillis, false);
    }

    public boolean mustRefreshChangingModule(ModuleComponentIdentifier component, ResolvedModuleVersion resolvedModuleVersion, long ageMillis) {
        return mustRefreshModule(component, resolvedModuleVersion, ageMillis, true);
    }

    private boolean mustRefreshModule(ModuleComponentIdentifier component, ResolvedModuleVersion version, long ageMillis, boolean changingModule) {
        return mustRefreshModule(moduleIdentifierFactory.moduleWithVersion(component.getGroup(), component.getModule(), component.getVersion()), version, ageMillis, changingModule);
    }

    private boolean mustRefreshModule(ModuleVersionIdentifier moduleVersionId, ResolvedModuleVersion version, long ageMillis, boolean changingModule) {
        CachedModuleResolutionControl moduleResolutionControl = new CachedModuleResolutionControl(moduleVersionId, version, changingModule, ageMillis);

        for (Action<? super ModuleResolutionControl> rule : moduleCacheRules) {
            rule.execute(moduleResolutionControl);
            if (moduleResolutionControl.ruleMatch()) {
                return moduleResolutionControl.mustCheck();
            }
        }

        return false;
    }

    public boolean mustRefreshModuleArtifacts(ModuleVersionIdentifier moduleVersionId, Set<ArtifactIdentifier> artifacts,
                                              long ageMillis, boolean belongsToChangingModule, boolean moduleDescriptorInSync) {
        if (belongsToChangingModule && !moduleDescriptorInSync) {
            return true;
        }
        return mustRefreshModule(moduleVersionId, new DefaultResolvedModuleVersion(moduleVersionId), ageMillis, belongsToChangingModule);
    }

    public boolean mustRefreshArtifact(ArtifactIdentifier artifactIdentifier, File cachedArtifactFile, long ageMillis, boolean belongsToChangingModule, boolean moduleDescriptorInSync) {
        CachedArtifactResolutionControl artifactResolutionControl = new CachedArtifactResolutionControl(artifactIdentifier, cachedArtifactFile, ageMillis, belongsToChangingModule);
        if(belongsToChangingModule && !moduleDescriptorInSync){
            return true;
        }
        for (Action<? super ArtifactResolutionControl> rule : artifactCacheRules) {
            rule.execute(artifactResolutionControl);
            if (artifactResolutionControl.ruleMatch()) {
                return artifactResolutionControl.mustCheck();
            }
        }
        return false;
    }

    DefaultCachePolicy copy() {
        return new DefaultCachePolicy(this);
    }

    private abstract static class AbstractResolutionControl<A, B> implements ResolutionControl<A, B> {
        private final A request;
        private final B cachedResult;
        private final long ageMillis;
        private boolean ruleMatch;
        private boolean mustCheck;

        private AbstractResolutionControl(A request, B cachedResult, long ageMillis) {
            this.request = request;
            this.cachedResult = cachedResult;
            this.ageMillis = correctForClockShift(ageMillis);
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


        public A getRequest() {
            return request;
        }

        public B getCachedResult() {
            return cachedResult;
        }

        public void cacheFor(int value, TimeUnit units) {
            long expiryMillis = TimeUnit.MILLISECONDS.convert(value, units);
            if (ageMillis > expiryMillis) {
                setMustCheck(true);
            } else {
                setMustCheck(false);
            }
        }

        public void useCachedResult() {
            setMustCheck(false);
        }

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

        public boolean mustCheck() {
            return mustCheck;
        }
    }

    private class CachedDependencyResolutionControl extends AbstractResolutionControl<ModuleIdentifier, Set<ModuleVersionIdentifier>> implements DependencyResolutionControl {
        private CachedDependencyResolutionControl(ModuleIdentifier request, Set<ModuleVersionIdentifier> result, long ageMillis) {
            super(request, result, ageMillis);
        }
    }

    private class CachedModuleResolutionControl extends AbstractResolutionControl<ModuleVersionIdentifier, ResolvedModuleVersion> implements ModuleResolutionControl {
        private final boolean changing;

        private CachedModuleResolutionControl(ModuleVersionIdentifier moduleVersionId, ResolvedModuleVersion cachedVersion, boolean changing, long ageMillis) {
            super(moduleVersionId, cachedVersion, ageMillis);
            this.changing = changing;
        }

        public boolean isChanging() {
            return changing;
        }
    }

    private class CachedArtifactResolutionControl extends AbstractResolutionControl<ArtifactIdentifier, File> implements ArtifactResolutionControl {
        private final boolean belongsToChangingModule;

        private CachedArtifactResolutionControl(ArtifactIdentifier artifactIdentifier, File cachedResult, long ageMillis, boolean belongsToChangingModule) {
            super(artifactIdentifier, cachedResult, ageMillis);
            this.belongsToChangingModule = belongsToChangingModule;
        }

        public boolean belongsToChangingModule() {
            return belongsToChangingModule;
        }
    }
}
