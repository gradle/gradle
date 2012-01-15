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


import java.util.concurrent.TimeUnit
import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.artifacts.cache.DependencyResolutionControl
import spock.lang.Specification
import org.gradle.api.artifacts.cache.ModuleResolutionControl
import org.gradle.api.artifacts.cache.ArtifactResolutionControl
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier

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
    }

    def "uses changing module timeout for changing modules and missing artifacts"() {
        when:
        cachePolicy.cacheChangingModulesFor(10, TimeUnit.SECONDS);

        then:
        hasDynamicVersionTimeout(DAY);
        hasChangingModuleTimeout(10 * SECOND)
        hasModuleTimeout(FOREVER)
        hasMissingArtifactTimeout(10 * SECOND)
    }

    def "uses dynamic version timeout for dynamic versions"() {
        when:
        cachePolicy.cacheDynamicVersionsFor(10, TimeUnit.SECONDS)

        then:
        hasDynamicVersionTimeout(10 * SECOND)
        hasChangingModuleTimeout(DAY)
        hasMissingArtifactTimeout(DAY)
        hasModuleTimeout(FOREVER)
    }

    def "applies invalidate rule for dynamic versions"() {
        when:
        cachePolicy.eachDependency(new Action<DependencyResolutionControl>() {
            void execute(DependencyResolutionControl t) {
                t.invalidate()
            }
        })

        then:
        cachePolicy.mustRefreshDynamicVersion(null, 2 * SECOND)
    }

    def "applies useCachedResult for dynamic versions"() {
        when:
        cachePolicy.eachDependency(new Action<DependencyResolutionControl>() {
            void execute(DependencyResolutionControl t) {
                t.useCachedResult()
            }
        })

        then:
        !cachePolicy.mustRefreshDynamicVersion(null, 2 * SECOND)
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
                assert t.cachedResult.group == 'group'
                assert t.cachedResult.name == 'name'
                assert t.cachedResult.version == 'version'
                t.invalidate()
            }
        })
        cachePolicy.mustRefreshDynamicVersion(moduleVersion('group', 'name', 'version'), 0)
    }
    
    def "provides details of cached module"() {
        expect:
        cachePolicy.eachModule(new Action<ModuleResolutionControl>() {
            void execute(ModuleResolutionControl t) {
                assert t.cachedResult.id.group == 'group'
                assert t.cachedResult.id.name == 'name'
                assert t.cachedResult.id.version == 'version'
                assert !t.changing
                t.invalidate()
            }
        })
        cachePolicy.mustRefreshModule(moduleVersion('group', 'name', 'version'), 0)
    }
    
    def "provides details of cached changing module"() {
        expect:
        cachePolicy.eachModule(new Action<ModuleResolutionControl>() {
            void execute(ModuleResolutionControl t) {
                assert t.cachedResult.id.group == 'group'
                assert t.cachedResult.id.name == 'name'
                assert t.cachedResult.id.version == 'version'
                assert t.changing
                t.invalidate()
            }
        })
        cachePolicy.mustRefreshChangingModule(moduleVersion('group', 'name', 'version'), 0)
    }
    
    def "provides null for missing cached artifact"() {
        expect:
        cachePolicy.eachArtifact(new Action<ArtifactResolutionControl>() {
            void execute(ArtifactResolutionControl t) {
                assert t.cachedResult == null
                t.invalidate()
            }
        })
        cachePolicy.mustRefreshMissingArtifact(0)
    }
    
    private def hasDynamicVersionTimeout(int timeout) {
        assert !cachePolicy.mustRefreshDynamicVersion(null, 100)
        assert !cachePolicy.mustRefreshDynamicVersion(null, timeout);
        assert !cachePolicy.mustRefreshDynamicVersion(null, timeout - 1)
        cachePolicy.mustRefreshDynamicVersion(null, timeout + 1)
    }

    private def hasChangingModuleTimeout(int timeout) {
        assert !cachePolicy.mustRefreshChangingModule(null, timeout - 1)
        assert !cachePolicy.mustRefreshChangingModule(null, timeout);
        cachePolicy.mustRefreshChangingModule(null, timeout + 1)
    }

    private def hasModuleTimeout(int timeout) {
        assert !cachePolicy.mustRefreshModule(null, timeout);
        assert !cachePolicy.mustRefreshModule(null, timeout - 1)
        if (timeout == FOREVER) {
            return true
        }
        cachePolicy.mustRefreshModule(null, timeout + 1)
    }

    private def hasMissingArtifactTimeout(int timeout) {
        assert !cachePolicy.mustRefreshMissingArtifact(timeout);
        assert !cachePolicy.mustRefreshMissingArtifact(timeout - 1)
        cachePolicy.mustRefreshMissingArtifact(timeout + 1)
    }

    private def moduleVersion(String group, String name, String version) {
        return new ResolvedModuleVersion() {
            ModuleVersionIdentifier getId() {
                return new DefaultModuleVersionIdentifier(group, name, version);
            }
        }
    }

}
