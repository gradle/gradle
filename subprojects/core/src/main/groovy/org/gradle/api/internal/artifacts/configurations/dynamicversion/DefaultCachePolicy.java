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
package org.gradle.api.internal.artifacts.configurations.dynamicversion;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.artifacts.cache.ArtifactResolutionControl;
import org.gradle.api.artifacts.cache.ResolutionRules;
import org.gradle.api.artifacts.cache.DependencyResolutionControl;
import org.gradle.api.artifacts.cache.ModuleResolutionControl;
import org.gradle.api.artifacts.cache.ResolutionControl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DefaultCachePolicy implements CachePolicy, ResolutionRules {
    private static final int SECONDS_IN_DAY = 24 * 60 * 60;

    private final List<Action<? super DependencyResolutionControl>> dependencyCacheRules = new ArrayList<Action<? super DependencyResolutionControl>>();
    private final List<Action<? super ModuleResolutionControl>> moduleCacheRules = new ArrayList<Action<? super ModuleResolutionControl>>();
    private final List<Action<? super ArtifactResolutionControl>> artifactCacheRules = new ArrayList<Action<? super ArtifactResolutionControl>>();

    public DefaultCachePolicy() {
        cacheDynamicVersionsFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
        cacheChangingModulesFor(SECONDS_IN_DAY, TimeUnit.SECONDS);
    }

    public void eachDependency(Action<? super DependencyResolutionControl> action) {
        dependencyCacheRules.add(0, action);
    }

    public void eachModule(Action<? super ModuleResolutionControl> action) {
        moduleCacheRules.add(0, action);
    }

    public void eachArtifact(Action<? super ArtifactResolutionControl> action) {
        artifactCacheRules.add(0, action);
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
                // Treat missing modules like changing modules
                if (moduleResolutionControl.getCachedResult() == null) {
                    moduleResolutionControl.cacheFor(value, units);
                }
            }
        });
        eachArtifact(new Action<ArtifactResolutionControl>() {
            public void execute(ArtifactResolutionControl artifactResolutionControl) {
                // Treat missing artifacts like changing modules
                if (artifactResolutionControl.getCachedResult() == null) {
                    artifactResolutionControl.cacheFor(value, units);
                }
            }
        });
    }

    public boolean mustRefreshDynamicVersion(final ResolvedModuleVersion version, final long ageMillis) {
        CachedDependencyResolutionControl dependencyResolutionControl = new CachedDependencyResolutionControl(version, ageMillis);

        for (Action<? super DependencyResolutionControl> rule : dependencyCacheRules) {
            rule.execute(dependencyResolutionControl);
            if (dependencyResolutionControl.ruleMatch()) {
                return dependencyResolutionControl.mustCheck();
            }
        }
        
        return false;
    }

    public boolean mustRefreshModule(final ResolvedModuleVersion version, final long ageMillis) {
        return mustRefreshModule(version, ageMillis, false);
    }

    public boolean mustRefreshChangingModule(ResolvedModuleVersion version, long ageMillis) {
        return mustRefreshModule(version, ageMillis, true);
    }

    private boolean mustRefreshModule(final ResolvedModuleVersion version, final long ageMillis, final boolean changingModule) {
        CachedModuleResolutionControl moduleResolutionControl = new CachedModuleResolutionControl(version, changingModule, ageMillis);

        for (Action<? super ModuleResolutionControl> rule : moduleCacheRules) {
            rule.execute(moduleResolutionControl);
            if (moduleResolutionControl.ruleMatch()) {
                return moduleResolutionControl.mustCheck();
            }
        }

        return false;
    }

    public boolean mustRefreshArtifact(File cachedArtifactFile, long ageMillis) {
        CachedArtifactResolutionControl artifactResolutionControl = new CachedArtifactResolutionControl(cachedArtifactFile, ageMillis);

        for (Action<? super ArtifactResolutionControl> rule : artifactCacheRules) {
            rule.execute(artifactResolutionControl);
            if (artifactResolutionControl.ruleMatch()) {
                return artifactResolutionControl.mustCheck();
            }
        }

        return false;
    }

    private static class AbstractResolutionControl implements ResolutionControl {
        private final long ageMillis;

        private Boolean mustCheck = null;

        private AbstractResolutionControl(long ageMillis) {
            this.ageMillis = ageMillis;
        }

        public void cacheFor(int value, TimeUnit units) {
            long timeoutMillis = TimeUnit.MILLISECONDS.convert(value, units);
            if (ageMillis <= timeoutMillis) {
                mustCheck = Boolean.FALSE;
            } else {
                mustCheck = Boolean.TRUE;
            }
        }

        public void useCachedResult() {
            mustCheck = Boolean.FALSE;
        }

        public void invalidate() {
            mustCheck = Boolean.TRUE;
        }
        
        public boolean ruleMatch() {
            return mustCheck != null;
        }
        
        public boolean mustCheck() {
            return mustCheck;
        }
    }
    
    private class CachedDependencyResolutionControl extends AbstractResolutionControl implements DependencyResolutionControl {
        private final ModuleVersionIdentifier cachedVersion;

        private CachedDependencyResolutionControl(ResolvedModuleVersion cachedVersion, long ageMillis) {
            super(ageMillis);
            this.cachedVersion = cachedVersion == null ? null : cachedVersion.getId();
        }

        public ModuleVersionIdentifier getCachedResult() {
            return cachedVersion;
        }
    }
    
    private class CachedModuleResolutionControl extends AbstractResolutionControl implements ModuleResolutionControl {
        private final ResolvedModuleVersion cachedVersion;
        private final boolean changing;

        private CachedModuleResolutionControl(ResolvedModuleVersion cachedVersion, boolean changing, long ageMillis) {
            super(ageMillis);
            this.cachedVersion = cachedVersion;
            this.changing = changing;
        }

        public ResolvedModuleVersion getCachedResult() {
            return cachedVersion;
        }

        public boolean isChanging() {
            return changing;
        }
    }

    private class CachedArtifactResolutionControl extends AbstractResolutionControl implements ArtifactResolutionControl {
        private final File cachedResult;

        private CachedArtifactResolutionControl(File cachedResult, long ageMillis) {
            super(ageMillis);
            this.cachedResult = cachedResult;
        }

        public File getCachedResult() {
            return cachedResult;
        }
    }
}
