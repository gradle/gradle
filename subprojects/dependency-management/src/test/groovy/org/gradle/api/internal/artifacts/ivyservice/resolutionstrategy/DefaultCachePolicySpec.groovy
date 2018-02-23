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
package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy

import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.artifacts.cache.ArtifactResolutionControl
import org.gradle.api.artifacts.cache.DependencyResolutionControl
import org.gradle.api.artifacts.cache.ModuleResolutionControl
import org.gradle.api.internal.artifacts.DefaultArtifactIdentifier
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.internal.Actions
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Specification

import java.util.concurrent.TimeUnit

import static java.util.Collections.emptySet
import static org.gradle.api.internal.artifacts.configurations.MutationValidator.MutationType.STRATEGY

public class DefaultCachePolicySpec extends Specification {
    private static final int SECOND = 1000;
    private static final int MINUTE = SECOND * 60;
    private static final int HOUR = MINUTE * 60;
    private static final int DAY = HOUR * 24;
    private static final int WEEK = DAY * 7;
    private static final int FOREVER = Integer.MAX_VALUE

    DefaultCachePolicy cachePolicy = new DefaultCachePolicy(new DefaultImmutableModuleIdentifierFactory())

    def "will cache default"() {
        expect:
        hasDynamicVersionTimeout(DAY)
        hasChangingModuleTimeout(DAY)
        hasModuleTimeout(FOREVER)
        hasMissingArtifactTimeout(DAY)
        hasMissingModuleTimeout(FOREVER)
    }

    def 'never expires missing module for dynamic versions'() {
        when:
        def moduleIdentifier = new DefaultModuleIdentifier('org', 'foo')
        def versions = emptySet()

        then:
        !cachePolicy.mustRefreshVersionList(moduleIdentifier, versions, WEEK)
    }

    def 'never expires missing module for non changing module'() {
        when:
        def module = moduleComponent('org', 'foo', '1.0')

        then:
        !cachePolicy.mustRefreshMissingModule(module, WEEK)
    }

    def "uses changing module timeout for changing modules"() {
        when:
        cachePolicy.cacheChangingModulesFor(10, TimeUnit.SECONDS);

        then:
        hasDynamicVersionTimeout(DAY);
        hasChangingModuleTimeout(10 * SECOND)
        hasModuleTimeout(FOREVER)
        hasMissingArtifactTimeout(DAY)
        hasMissingModuleTimeout(FOREVER)
    }

    def "uses dynamic version timeout for dynamic versions"() {
        when:
        cachePolicy.cacheDynamicVersionsFor(10, TimeUnit.SECONDS)

        then:
        hasDynamicVersionTimeout(10 * SECOND)
        hasChangingModuleTimeout(DAY)
        hasModuleTimeout(FOREVER)
        hasMissingArtifactTimeout(DAY)
        hasMissingModuleTimeout(FOREVER)
    }

    def "applies invalidate rule for dynamic versions"() {
        when:
        cachePolicy.eachDependency(new Action<DependencyResolutionControl>() {
            void execute(DependencyResolutionControl t) {
                t.refresh()
            }
        })

        then:
        cachePolicy.mustRefreshVersionList(null, null, 2 * SECOND)
    }

    def "applies useCachedResult for dynamic versions"() {
        when:
        cachePolicy.eachDependency(new Action<DependencyResolutionControl>() {
            void execute(DependencyResolutionControl t) {
                t.useCachedResult()
            }
        })

        then:
        !cachePolicy.mustRefreshVersionList(null, null, 2 * SECOND)
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

    def "provides details of cached version list"() {
        expect:
        cachePolicy.eachDependency(new Action<DependencyResolutionControl>() {
            void execute(DependencyResolutionControl t) {
                assert t.request.group == 'g'
                assert t.request.name == 'n'
                assertId(t.cachedResult.iterator().next(), 'group', 'name', 'version')
                t.refresh()
            }
        })
        cachePolicy.mustRefreshVersionList(moduleIdentifier('g', 'n', 'v').module, [moduleIdentifier('group', 'name', 'version')] as Set, 0)
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
        cachePolicy.mustRefreshModule(moduleComponent('g', 'n', 'v'), moduleVersion('group', 'name', 'version'), 0)
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
        cachePolicy.mustRefreshChangingModule(moduleComponent('g', 'n', 'v'), moduleVersion('group', 'name', 'version'), 0)
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
        cachePolicy.mustRefreshArtifact(artifactIdentifier, null, 0, true, true)
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

    def "must refresh artifact for changing modules when moduledescriptorhash not in sync"() {
        expect:
        !cachePolicy.mustRefreshArtifact(null, null, 1000, false, true)
        !cachePolicy.mustRefreshArtifact(null, null, 1000, false, false)
        cachePolicy.mustRefreshArtifact(null, null, 1000, true, false)
    }

    def "provides a copy"() {
        expect:
        def copy = cachePolicy.copy()

        !copy.is(cachePolicy)
        !copy.dependencyCacheRules.is(cachePolicy.dependencyCacheRules)
        !copy.moduleCacheRules.is(cachePolicy.moduleCacheRules)
        !copy.artifactCacheRules.is(cachePolicy.artifactCacheRules)

        copy.dependencyCacheRules == cachePolicy.dependencyCacheRules
        copy.moduleCacheRules == cachePolicy.moduleCacheRules
        copy.artifactCacheRules == cachePolicy.artifactCacheRules
    }

    def "mutation is checked"() {
        def validator = Mock(MutationValidator)
        given:
        cachePolicy.setMutationValidator(validator)

        when: cachePolicy.cacheChangingModulesFor(0, TimeUnit.HOURS)
        then: (1.._) * validator.validateMutation(STRATEGY)

        when: cachePolicy.cacheDynamicVersionsFor(0, TimeUnit.HOURS)
        then: 1 * validator.validateMutation(STRATEGY)

        when: cachePolicy.eachArtifact(Actions.doNothing())
        then: 1 * validator.validateMutation(STRATEGY)

        when: cachePolicy.eachDependency(Actions.doNothing())
        then: 1 * validator.validateMutation(STRATEGY)

        when: cachePolicy.eachModule(Actions.doNothing())
        then: 1 * validator.validateMutation(STRATEGY)
    }

    def "mutation is not checked for copy"() {
        def validator = Mock(MutationValidator)
        given:
        cachePolicy.setMutationValidator(validator)
        def copy = cachePolicy.copy()

        when: copy.cacheChangingModulesFor(0, TimeUnit.HOURS)
        then: 0 * validator.validateMutation(_)

        when: copy.cacheDynamicVersionsFor(0, TimeUnit.HOURS)
        then: 0 * validator.validateMutation(_)

        when: copy.eachArtifact(Actions.doNothing())
        then: 0 * validator.validateMutation(_)

        when: copy.eachDependency(Actions.doNothing())
        then: 0 * validator.validateMutation(_)

        when: copy.eachModule(Actions.doNothing())
        then: 0 * validator.validateMutation(_)
    }

    private def hasDynamicVersionTimeout(int timeout) {
        def moduleId = moduleIdentifier('group', 'name', 'version')
        assert !cachePolicy.mustRefreshVersionList(null, [moduleId] as Set, 100)
        assert !cachePolicy.mustRefreshVersionList(null, [moduleId] as Set, timeout);
        assert !cachePolicy.mustRefreshVersionList(null, [moduleId] as Set, timeout - 1)
        assert cachePolicy.mustRefreshVersionList(null, [moduleId] as Set, timeout + 1)
        true
    }

    private def hasChangingModuleTimeout(int timeout) {
        def id = moduleComponent('group', 'name', 'version')
        def module = moduleVersion('group', 'name', 'version')
        assert !cachePolicy.mustRefreshChangingModule(id, module, timeout - 1)
        assert !cachePolicy.mustRefreshChangingModule(id, module, timeout);
        assert cachePolicy.mustRefreshChangingModule(id, module, timeout + 1)
        true
    }

    private def hasModuleTimeout(int timeout) {
        def id = moduleComponent('group', 'name', 'version')
        def module = moduleVersion('group', 'name', 'version')
        assert !cachePolicy.mustRefreshModule(id, module, timeout);
        assert !cachePolicy.mustRefreshModule(id, module, timeout - 1)
        if (timeout != FOREVER) {
            assert cachePolicy.mustRefreshModule(id, module, timeout + 1)
        }
        true
    }

    private def hasMissingModuleTimeout(int timeout) {
        def id = moduleComponent('group', 'name', 'version')
        assert !cachePolicy.mustRefreshModule(id, null, timeout);
        assert !cachePolicy.mustRefreshModule(id, null, timeout - 1)
        if (timeout != FOREVER) {
            assert cachePolicy.mustRefreshModule(id, null, timeout + 1)
        }
        return true
    }

    private def hasMissingArtifactTimeout(int timeout) {
        assert !cachePolicy.mustRefreshArtifact(null, null, timeout, false, false);
        assert !cachePolicy.mustRefreshArtifact(null, null, timeout - 1, false, false)
        if (timeout != FOREVER) {
            assert cachePolicy.mustRefreshArtifact(null, null, timeout + 1, false, false)
        }
        true
    }

    private def assertId(def moduleId, String group, String name, String version) {
        assert moduleId.group == group
        assert moduleId.name == name
        assert moduleId.version == version
    }

    private def moduleComponent(String group, String name, String version) {
        new DefaultModuleComponentIdentifier(group, name, version)
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
