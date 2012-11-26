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


import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.artifacts.cache.ArtifactResolutionControl
import org.gradle.api.artifacts.cache.DependencyResolutionControl
import org.gradle.api.artifacts.cache.ModuleResolutionControl
import org.gradle.api.internal.artifacts.DefaultArtifactIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import spock.lang.Specification

import java.util.concurrent.TimeUnit

public class DefaultCachePolicySpec extends Specification {
    private static final int SECOND = 1000;
    private static final int MINUTE = SECOND * 60;
    private static final int HOUR = MINUTE * 60;
    private static final int DAY = HOUR * 24;
    private static final int WEEK = DAY * 7;
    private static final int FOREVER = Integer.MAX_VALUE

    DefaultCachePolicy cachePolicy = new DefaultCachePolicy()

    def "will cache default"() {
        expect:
        hasDynamicVersionTimeout(DAY)
        hasChangingModuleTimeout(DAY)
        hasModuleTimeout(FOREVER)
        hasMissingArtifactTimeout(DAY)
        hasMissingModuleTimeout(DAY)
    }

    def "uses changing module timeout for changing modules"() {
        when:
        cachePolicy.cacheChangingModulesFor(10, TimeUnit.SECONDS);

        then:
        hasDynamicVersionTimeout(DAY);
        hasChangingModuleTimeout(10 * SECOND)
        hasModuleTimeout(FOREVER)
        hasMissingModuleTimeout(DAY)
        hasMissingArtifactTimeout(DAY)
    }

    def "uses dynamic version timeout for dynamic versions"() {
        when:
        cachePolicy.cacheDynamicVersionsFor(10, TimeUnit.SECONDS)

        then:
        hasDynamicVersionTimeout(10 * SECOND)
        hasChangingModuleTimeout(DAY)
        hasMissingModuleTimeout(DAY)
        hasMissingArtifactTimeout(DAY)
        hasModuleTimeout(FOREVER)
    }

    def "applies invalidate rule for dynamic versions"() {
        when:
        cachePolicy.eachDependency(new Action<DependencyResolutionControl>() {
            void execute(DependencyResolutionControl t) {
                t.refresh()
            }
        })

        then:
        cachePolicy.mustRefreshDynamicVersion(null, null, 2 * SECOND)
    }

    def "applies useCachedResult for dynamic versions"() {
        when:
        cachePolicy.eachDependency(new Action<DependencyResolutionControl>() {
            void execute(DependencyResolutionControl t) {
                t.useCachedResult()
            }
        })

        then:
        !cachePolicy.mustRefreshDynamicVersion(null, null, 2 * SECOND)
    }

    def "applies cacheFor rules for dynamic versions"() {
        when:
        cachePolicy.eachDependency(new Action<DependencyResolutionControl>() {
            void execute(DependencyResolutionControl t) {
                t.cacheFor(100, TimeUnit.SECONDS)
            }
        })

        then:
        hasDynamicVersionTimeout(100 * SECOND)
    }

    def "provides details of cached dynamic version"() {
        expect:
        cachePolicy.eachDependency(new Action<DependencyResolutionControl>() {
            void execute(DependencyResolutionControl t) {
                assertId(t.request, 'g', 'n', 'v')
                assertId(t.cachedResult, 'group', 'name', 'version')
                t.refresh()
            }
        })
        cachePolicy.mustRefreshDynamicVersion(moduleSelector('g', 'n', 'v'), moduleIdentifier('group', 'name', 'version'), 0)
    }

    def "provides details of cached module"() {
        expect:
        cachePolicy.eachModule(new Action<ModuleResolutionControl>() {
            void execute(ModuleResolutionControl t) {
                assertId(t.request, 'g', 'n', 'v')
                assertId(t.cachedResult.id, 'group', 'name', 'version')
                assert !t.changing
                t.refresh()
            }
        })
        cachePolicy.mustRefreshModule(moduleIdentifier('g', 'n', 'v'), moduleVersion('group', 'name', 'version'), null, 0)
    }

    def "provides details of cached changing module"() {
        expect:
        cachePolicy.eachModule(new Action<ModuleResolutionControl>() {
            void execute(ModuleResolutionControl t) {
                assertId(t.request, 'g', 'n', 'v')
                assertId(t.cachedResult.id, 'group', 'name', 'version')
                assert t.changing
                t.refresh()
            }
        })
        cachePolicy.mustRefreshChangingModule(moduleIdentifier('g', 'n', 'v'), moduleVersion('group', 'name', 'version'), 0)
    }

    def "provides details of cached artifact"() {
        expect:
        cachePolicy.eachArtifact(new Action<ArtifactResolutionControl>() {
            void execute(ArtifactResolutionControl t) {
                assertId(t.request.moduleVersionIdentifier, 'group', 'name', 'version')
                assert t.request.name == 'artifact'
                assert t.request.type == 'type'
                assert t.request.extension == 'ext'
                assert t.request.classifier == 'classifier'
                assert t.cachedResult == null
                t.refresh()
            }
        })
        def artifactIdentifier = new DefaultArtifactIdentifier(moduleIdentifier('group', 'name', 'version'), 'artifact', 'type', 'ext', 'classifier')
        cachePolicy.mustRefreshArtifact(artifactIdentifier, null, 0, true)
    }

    def "can use cacheFor to control missing module and artifact timeout"() {
        when:
        cachePolicy.eachModule(new Action<ModuleResolutionControl>() {
            void execute(ModuleResolutionControl t) {
                if (t.cachedResult == null) {
                    t.cacheFor(10, TimeUnit.SECONDS)
                }
            }
        });
        cachePolicy.eachArtifact(new Action<ArtifactResolutionControl>() {
            void execute(ArtifactResolutionControl t) {
                if (t.cachedResult == null) {
                    t.cacheFor(20, TimeUnit.SECONDS)
                }
            }
        });

        then:
        hasDynamicVersionTimeout(DAY)
        hasChangingModuleTimeout(DAY)
        hasModuleTimeout(FOREVER)
        hasMissingModuleTimeout(10 * SECOND)
        hasMissingArtifactTimeout(20 * SECOND)
    }

    def "must refresh artifact when moduledescriptorhash not in sync"() {
        expect:
        !cachePolicy.mustRefreshArtifact(null, null, 1000, true)
        cachePolicy.mustRefreshArtifact(null, null, 1000, false)
    }

    private def hasDynamicVersionTimeout(int timeout) {
        def moduleId = moduleIdentifier('group', 'name', 'version')
        assert !cachePolicy.mustRefreshDynamicVersion(null, moduleId, 100)
        assert !cachePolicy.mustRefreshDynamicVersion(null, moduleId, timeout);
        assert !cachePolicy.mustRefreshDynamicVersion(null, moduleId, timeout - 1)
        cachePolicy.mustRefreshDynamicVersion(null, moduleId, timeout + 1)
    }

    private def hasChangingModuleTimeout(int timeout) {
        def module = moduleVersion('group', 'name', 'version')
        assert !cachePolicy.mustRefreshChangingModule(null, module, timeout - 1)
        assert !cachePolicy.mustRefreshChangingModule(null, module, timeout);
        cachePolicy.mustRefreshChangingModule(null, module, timeout + 1)
    }

    private def hasModuleTimeout(int timeout) {
        def module = moduleVersion('group', 'name', 'version')
        assert !cachePolicy.mustRefreshModule(null, module, null, timeout);
        assert !cachePolicy.mustRefreshModule(null, module, null, timeout - 1)
        if (timeout == FOREVER) {
            return true
        }
        cachePolicy.mustRefreshModule(null, module, timeout + 1)
    }

    private def hasMissingModuleTimeout(int timeout) {
        assert !cachePolicy.mustRefreshModule(null, null, null, timeout);
        assert !cachePolicy.mustRefreshModule(null, null, null, timeout - 1)
        cachePolicy.mustRefreshModule(null, null, null, timeout + 1)
    }

    private def hasMissingArtifactTimeout(int timeout) {
        assert !cachePolicy.mustRefreshArtifact(null, null, timeout, true);
        assert !cachePolicy.mustRefreshArtifact(null, null, timeout - 1, true)
        cachePolicy.mustRefreshArtifact(null, null, timeout + 1, true)
    }

    private def assertId(def moduleId, String group, String name, String version) {
        assert moduleId.group == group
        assert moduleId.name == name
        assert moduleId.version == version
    }

    private def moduleSelector(String group, String name, String version) {
        new DefaultModuleVersionSelector(group, name, version)
    }

    private def moduleIdentifier(String group, String name, String version) {
        new DefaultModuleVersionIdentifier(group, name, version)
    }

    private def moduleVersion(String group, String name, String version) {
        return new ResolvedModuleVersion() {
            ModuleVersionIdentifier getId() {
                return new DefaultModuleVersionIdentifier(group, name, version);
            }
        }
    }

}
