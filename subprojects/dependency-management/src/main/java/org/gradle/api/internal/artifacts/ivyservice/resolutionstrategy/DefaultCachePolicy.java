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
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultResolvedModuleVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.gradle.api.internal.artifacts.configurations.MutationValidator.MutationType.STRATEGY;

public class DefaultCachePolicy implements CachePolicy {
    private static final int SECONDS_IN_DAY = 24 * 60 * 60;

    final List<Action<? super DependencyResolutionControl>> dependencyCacheRules;
    final List<Action<? super ModuleResolutionControl>> moduleCacheRules;
    final List<Action<? super ArtifactResolutionControl>> artifactCacheRules;
    private MutationValidator mutationValidator = MutationValidator.IGNORE;

    public DefaultCachePolicy() {
        this.dependencyCacheRules = new ArrayList<Action<? super DependencyResolutionControl>>();
        this.moduleCacheRules = new ArrayList<Action<? super ModuleResolutionControl>>();
        this.artifactCacheRules = new ArrayList<Action<? super ArtifactResolutionControl>>();

        cacheDynamicVersionsFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
        cacheChangingModulesFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
        cacheMissingArtifactsFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
    }

    DefaultCachePolicy(DefaultCachePolicy policy) {
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

    @Override
    public void setOffline() {
        eachDependency(new Action<DependencyResolutionControl>() {
            @Override
            public void execute(DependencyResolutionControl dependencyResolutionControl) {
                dependencyResolutionControl.useCachedResult();
            }
        });
        eachModule(new Action<ModuleResolutionControl>() {
            @Override
            public void execute(ModuleResolutionControl moduleResolutionControl) {
                moduleResolutionControl.useCachedResult();
            }
        });
        eachArtifact(new Action<ArtifactResolutionControl>() {
            @Override
            public void execute(ArtifactResolutionControl artifactResolutionControl) {
                artifactResolutionControl.useCachedResult();
            }
        });
    }

    @Override
    public void setRefreshDependencies() {
        eachDependency(new Action<DependencyResolutionControl>() {
            @Override
            public void execute(DependencyResolutionControl dependencyResolutionControl) {
                dependencyResolutionControl.cacheFor(0, TimeUnit.SECONDS);
            }
        });
        eachModule(new Action<ModuleResolutionControl>() {
            @Override
            public void execute(ModuleResolutionControl moduleResolutionControl) {
                moduleResolutionControl.cacheFor(0, TimeUnit.SECONDS);
            }
        });
        eachArtifact(new Action<ArtifactResolutionControl>() {
            @Override
            public void execute(ArtifactResolutionControl artifactResolutionControl) {
                artifactResolutionControl.cacheFor(0, TimeUnit.SECONDS);
            }
        });
    }

    public void cacheDynamicVersionsFor(final int value, final TimeUnit unit) {
        eachDependency(new Action<DependencyResolutionControl>() {
            @Override
            public void execute(DependencyResolutionControl dependencyResolutionControl) {
                if (!dependencyResolutionControl.getCachedResult().isEmpty()) {
                    dependencyResolutionControl.cacheFor(value, unit);
                }
            }
        });
    }

    public void cacheChangingModulesFor(final int value, final TimeUnit units) {
        eachModule(new Action<ModuleResolutionControl>() {
            @Override
            public void execute(ModuleResolutionControl moduleResolutionControl) {
                if (moduleResolutionControl.isChanging()) {
                    moduleResolutionControl.cacheFor(value, units);
                }
            }
        });
        eachArtifact(new Action<ArtifactResolutionControl>() {
            @Override
            public void execute(ArtifactResolutionControl artifactResolutionControl) {
                if (artifactResolutionControl.belongsToChangingModule()) {
                    artifactResolutionControl.cacheFor(value, units);
                }
            }
        });
    }

    private void cacheMissingArtifactsFor(final int value, final TimeUnit units) {
        eachArtifact(new Action<ArtifactResolutionControl>() {
            @Override
            public void execute(ArtifactResolutionControl artifactResolutionControl) {
                if (artifactResolutionControl.getCachedResult() == null) {
                    artifactResolutionControl.cacheFor(value, units);
                }
            }
        });
    }

    /**
     * Apply a rule to control resolution of dependencies.
     * @param rule the rule to apply
     */
    private void eachDependency(Action<? super DependencyResolutionControl> rule) {
        mutationValidator.validateMutation(STRATEGY);
        dependencyCacheRules.add(0, rule);
    }

    /**
     * Apply a rule to control resolution of modules.
     * @param rule the rule to apply
     */
    private void eachModule(Action<? super ModuleResolutionControl> rule) {
        mutationValidator.validateMutation(STRATEGY);
        moduleCacheRules.add(0, rule);
    }

    /**
     * Apply a rule to control resolution of artifacts.
     * @param rule the rule to apply
     */
    private void eachArtifact(Action<? super ArtifactResolutionControl> rule) {
        mutationValidator.validateMutation(STRATEGY);
        artifactCacheRules.add(0, rule);
    }

    @Override
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

    @Override
    public boolean mustRefreshMissingModule(ModuleComponentIdentifier component, long ageMillis) {
        return mustRefreshModule(component, null, ageMillis, false);
    }

    @Override
    public boolean mustRefreshModule(ModuleComponentIdentifier component, ResolvedModuleVersion resolvedModuleVersion, long ageMillis) {
        return mustRefreshModule(component, resolvedModuleVersion, ageMillis, false);
    }

    @Override
    public boolean mustRefreshModule(ResolvedModuleVersion resolvedModuleVersion, long ageMillis, boolean changing) {
        return mustRefreshModule(resolvedModuleVersion.getId(), resolvedModuleVersion, ageMillis, changing);
    }

    @Override
    public boolean mustRefreshChangingModule(ModuleComponentIdentifier component, ResolvedModuleVersion resolvedModuleVersion, long ageMillis) {
        return mustRefreshModule(component, resolvedModuleVersion, ageMillis, true);
    }

    private boolean mustRefreshModule(ModuleComponentIdentifier component, ResolvedModuleVersion version, long ageMillis, boolean changingModule) {
        return mustRefreshModule(DefaultModuleVersionIdentifier.newId(component.getModuleIdentifier(), component.getVersion()), version, ageMillis, changingModule);
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

    @Override
    public boolean mustRefreshModuleArtifacts(ModuleVersionIdentifier moduleVersionId, Set<ArtifactIdentifier> artifacts,
                                              long ageMillis, boolean belongsToChangingModule, boolean moduleDescriptorInSync) {
        if (belongsToChangingModule && !moduleDescriptorInSync) {
            return true;
        }
        return mustRefreshModule(moduleVersionId, new DefaultResolvedModuleVersion(moduleVersionId), ageMillis, belongsToChangingModule);
    }

    @Override
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
            long expiryMillis = TimeUnit.MILLISECONDS.convert(value, units);
            if (ageMillis > expiryMillis) {
                setMustCheck(true);
            } else {
                setMustCheck(false);
            }
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

        @Override
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

        @Override
        public boolean belongsToChangingModule() {
            return belongsToChangingModule;
        }
    }
}
