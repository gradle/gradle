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
import org.gradle.api.internal.artifacts.DefaultArtifactIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.cache.ArtifactResolutionControl
import org.gradle.api.internal.artifacts.cache.DependencyResolutionControl
import org.gradle.api.internal.artifacts.cache.ModuleResolutionControl
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.internal.Actions
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit

import static java.util.Collections.emptySet
import static org.gradle.api.internal.artifacts.configurations.MutationValidator.MutationType.STRATEGY

class DefaultCachePolicySpec extends Specification {
    private static final long SECOND = 1000;
    private static final long MINUTE = SECOND * 60;
    private static final long HOUR = MINUTE * 60;
    private static final long DAY = HOUR * 24;
    private static final long WEEK = DAY * 7;
    private static final long FOREVER = Long.MAX_VALUE

    DefaultCachePolicy cachePolicy = new DefaultCachePolicy()

    def "applies default expiry"() {
        expect:
        hasDynamicVersionTimeout(DAY)
        hasChangingModuleTimeout(DAY)
        hasNoModuleTimeout()
        hasMissingArtifactTimeout(DAY)
        hasNoMissingModuleTimeout()
    }

    def 'never expires missing module for dynamic versions'() {
        when:
        def moduleIdentifier = DefaultModuleIdentifier.newId('org', 'foo')
        def versions = emptySet()

        then:
        def expired = cachePolicy.versionListExpiry(moduleIdentifier, versions, Duration.ofMillis(WEEK))
        !expired.mustCheck
        expired.keepFor == Duration.ZERO

        def notExpired = cachePolicy.versionListExpiry(moduleIdentifier, versions, Duration.ofMillis(2 * SECOND))
        !notExpired.mustCheck
        notExpired.keepFor == Duration.ofMillis(DAY - 2 * SECOND)

        def thisBuild = cachePolicy.versionListExpiry(moduleIdentifier, versions, Duration.ZERO)
        !thisBuild.mustCheck
        thisBuild.keepFor == Duration.ofMillis(DAY)
    }

    def 'never expires missing module for non changing module'() {
        when:
        def module = moduleComponent('org', 'foo', '1.0')

        then:
        !cachePolicy.missingModuleExpiry(module, Duration.ofMillis(WEEK)).mustCheck
    }

    def "uses changing module timeout for changing modules"() {
        when:
        cachePolicy.cacheChangingModulesFor(10, TimeUnit.SECONDS);

        then:
        hasDynamicVersionTimeout(DAY);
        hasChangingModuleTimeout(10 * SECOND)
        hasNoModuleTimeout()
        hasMissingArtifactTimeout(DAY)
        hasNoMissingModuleTimeout()
    }

    def "uses dynamic version timeout for dynamic versions"() {
        when:
        cachePolicy.cacheDynamicVersionsFor(10, TimeUnit.SECONDS)

        then:
        hasDynamicVersionTimeout(10 * SECOND)
        hasChangingModuleTimeout(DAY)
        hasNoModuleTimeout()
        hasMissingArtifactTimeout(DAY)
        hasNoMissingModuleTimeout()
    }

    def "applies invalidate rule for dynamic versions"() {
        when:
        cachePolicy.eachDependency(new Action<DependencyResolutionControl>() {
            void execute(DependencyResolutionControl t) {
                t.refresh()
            }
        })

        then:
        def notExpired = cachePolicy.versionListExpiry(null, null, Duration.ofMillis(2 * SECOND))
        notExpired.mustCheck
        notExpired.keepFor == Duration.ZERO

        def thisBuild = cachePolicy.versionListExpiry(null, null, Duration.ZERO)
        !thisBuild.mustCheck
        thisBuild.keepFor == Duration.ofMillis(DAY)
    }

    def "applies useCachedResult for dynamic versions"() {
        when:
        cachePolicy.eachDependency(new Action<DependencyResolutionControl>() {
            void execute(DependencyResolutionControl t) {
                t.useCachedResult()
            }
        })

        then:
        def notExpired = cachePolicy.versionListExpiry(null, null, Duration.ofMillis(2 * SECOND))
        !notExpired.mustCheck
        notExpired.keepFor == Duration.ofMillis(DAY - 2 * SECOND)

        def expired = cachePolicy.versionListExpiry(null, null, Duration.ofMillis(WEEK))
        !expired.mustCheck
        expired.keepFor == Duration.ZERO

        def thisBuild = cachePolicy.versionListExpiry(null, null, Duration.ZERO)
        !thisBuild.mustCheck
        thisBuild.keepFor == Duration.ofMillis(DAY)
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

        def expiry = cachePolicy.versionListExpiry(moduleIdentifier('g', 'n', 'v').module, [moduleIdentifier('group', 'name', 'version')] as Set, Duration.ofMillis(5))
        expiry.mustCheck
        expiry.keepFor == Duration.ZERO
    }

    def "uses cached version list when offline"() {
        def moduleIdentifier = DefaultModuleIdentifier.newId('org', 'foo')

        when:
        cachePolicy.setOffline()

        then:
        def notExpired = cachePolicy.versionListExpiry(null, null, Duration.ofMillis(2 * SECOND))
        !notExpired.mustCheck
        notExpired.keepFor == Duration.ofMillis(DAY - 2 * SECOND)

        def expired = cachePolicy.versionListExpiry(null, null, Duration.ofMillis(WEEK))
        !expired.mustCheck
        expired.keepFor == Duration.ZERO

        when:
        cachePolicy.cacheDynamicVersionsFor(5, TimeUnit.SECONDS)

        then:
        def expiry2 = cachePolicy.versionListExpiry(moduleIdentifier, emptySet(), Duration.ofMillis(2 * SECOND))
        !expiry2.mustCheck
        expiry2.keepFor == Duration.ofMillis(3 * SECOND)
    }

    def "does not use cached version list when refresh dependencies"() {
        when:
        cachePolicy.setRefreshDependencies()

        then:
        def expired = cachePolicy.versionListExpiry(null, null, Duration.ofMillis(WEEK))
        expired.mustCheck
        expired.keepFor == Duration.ZERO

        def notExpired = cachePolicy.versionListExpiry(null, null, Duration.ofMillis(2 * SECOND))
        notExpired.mustCheck
        notExpired.keepFor == Duration.ZERO
    }

    def "uses cached version list when refresh dependencies and version was cached in this build"() {
        def moduleIdentifier = DefaultModuleIdentifier.newId('org', 'foo')

        when:
        cachePolicy.setRefreshDependencies()

        then:
        def expiry1 = cachePolicy.versionListExpiry(moduleIdentifier, null, Duration.ZERO)
        !expiry1.mustCheck
        expiry1.keepFor == Duration.ofMillis(DAY)

        when:
        cachePolicy.cacheDynamicVersionsFor(1, TimeUnit.SECONDS)

        then:
        def expiry2 = cachePolicy.versionListExpiry(moduleIdentifier, emptySet(), Duration.ZERO)
        !expiry2.mustCheck
        expiry2.keepFor == Duration.ofMillis(SECOND)
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
        cachePolicy.moduleExpiry(moduleComponent('g', 'n', 'v'), moduleVersion('group', 'name', 'version'), Duration.ofMillis(5)).mustCheck
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
        cachePolicy.changingModuleExpiry(moduleComponent('g', 'n', 'v'), moduleVersion('group', 'name', 'version'), Duration.ofMillis(5)).mustCheck
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
        cachePolicy.artifactExpiry(artifactIdentifier, null, Duration.ofMillis(5), true, true).mustCheck
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
        hasNoModuleTimeout()
        hasMissingModuleTimeout(10 * SECOND)
        hasMissingArtifactTimeout(20 * SECOND)
    }

    def "uses cached module metadata when offline"() {
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("test", "test"), "1.2.3")

        when:
        cachePolicy.setOffline()

        then:
        def notExpired = cachePolicy.moduleExpiry(id, null, Duration.ofMillis(2 * SECOND))
        !notExpired.mustCheck
        notExpired.keepFor == Duration.ofMillis(FOREVER)

        def expired = cachePolicy.moduleExpiry(id, null, Duration.ofMillis(WEEK))
        !expired.mustCheck
        expired.keepFor == Duration.ofMillis(FOREVER)
    }

    def "uses cached changing module metadata when offline"() {
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("test", "test"), "1.2.3")

        when:
        cachePolicy.setOffline()

        then:
        def notExpired = cachePolicy.changingModuleExpiry(id, null, Duration.ofMillis(2 * SECOND))
        !notExpired.mustCheck
        notExpired.keepFor == Duration.ofMillis(DAY - 2 * SECOND)

        def expired = cachePolicy.changingModuleExpiry(id, null, Duration.ofMillis(WEEK))
        !expired.mustCheck
        expired.keepFor == Duration.ZERO

        when:
        cachePolicy.cacheChangingModulesFor(5, TimeUnit.SECONDS)

        then:
        def expiry2 = cachePolicy.changingModuleExpiry(id, null, Duration.ofMillis(2 * SECOND))
        !expiry2.mustCheck
        expiry2.keepFor == Duration.ofMillis(3 * SECOND)
    }

    def "does not use cached changing module when refresh dependencies"() {
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("test", "test"), "1.2.3")

        when:
        cachePolicy.setRefreshDependencies()

        then:
        def expired = cachePolicy.changingModuleExpiry(id, null, Duration.ofMillis(WEEK))
        expired.mustCheck
        expired.keepFor == Duration.ZERO

        def notExpired = cachePolicy.changingModuleExpiry(id, null, Duration.ofMillis(2 * SECOND))
        notExpired.mustCheck
        notExpired.keepFor == Duration.ZERO
    }

    def "uses cached changing module when refresh dependencies and version was cached in this build"() {
        def id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("test", "test"), "1.2.3")

        when:
        cachePolicy.setRefreshDependencies()

        then:
        def expiry1 = cachePolicy.changingModuleExpiry(id, null, Duration.ZERO)
        !expiry1.mustCheck
        expiry1.keepFor == Duration.ofMillis(DAY)

        when:
        cachePolicy.cacheChangingModulesFor(1, TimeUnit.SECONDS)

        then:
        def expiry2 = cachePolicy.changingModuleExpiry(id, null, Duration.ZERO)
        !expiry2.mustCheck
        expiry2.keepFor == Duration.ofMillis(SECOND)
    }

    def "must refresh artifact for changing modules when moduledescriptorhash not in sync"() {
        expect:
        !cachePolicy.artifactExpiry(null, null, Duration.ofMillis(1000), false, true).mustCheck
        !cachePolicy.artifactExpiry(null, null, Duration.ofMillis(1000), false, false).mustCheck
        cachePolicy.artifactExpiry(null, null, Duration.ofMillis(1000), true, false).mustCheck
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

        when:
        cachePolicy.cacheChangingModulesFor(0, TimeUnit.HOURS)
        then:
        (1.._) * validator.validateMutation(STRATEGY)

        when:
        cachePolicy.cacheDynamicVersionsFor(0, TimeUnit.HOURS)
        then:
        1 * validator.validateMutation(STRATEGY)

        when:
        cachePolicy.eachArtifact(Actions.doNothing())
        then:
        1 * validator.validateMutation(STRATEGY)

        when:
        cachePolicy.eachDependency(Actions.doNothing())
        then:
        1 * validator.validateMutation(STRATEGY)

        when:
        cachePolicy.eachModule(Actions.doNothing())
        then:
        1 * validator.validateMutation(STRATEGY)
    }

    def "mutation is not checked for copy"() {
        def validator = Mock(MutationValidator)
        given:
        cachePolicy.setMutationValidator(validator)
        def copy = cachePolicy.copy()

        when:
        copy.cacheChangingModulesFor(0, TimeUnit.HOURS)
        then:
        0 * validator.validateMutation(_)

        when:
        copy.cacheDynamicVersionsFor(0, TimeUnit.HOURS)
        then:
        0 * validator.validateMutation(_)

        when:
        copy.eachArtifact(Actions.doNothing())
        then:
        0 * validator.validateMutation(_)

        when:
        copy.eachDependency(Actions.doNothing())
        then:
        0 * validator.validateMutation(_)

        when:
        copy.eachModule(Actions.doNothing())
        then:
        0 * validator.validateMutation(_)
    }

    private void hasDynamicVersionTimeout(long timeout) {
        def moduleId = moduleIdentifier('group', 'name', 'version')

        def thisBuild = cachePolicy.versionListExpiry(null, [moduleId] as Set, Duration.ZERO)
        assert !thisBuild.mustCheck
        assert thisBuild.keepFor == Duration.ofMillis(timeout)

        def atTimeout = cachePolicy.versionListExpiry(null, [moduleId] as Set, Duration.ofMillis(timeout))
        assert !atTimeout.mustCheck
        assert atTimeout.keepFor == Duration.ZERO

        def almostExpired = cachePolicy.versionListExpiry(null, [moduleId] as Set, Duration.ofMillis(timeout - 1))
        assert !almostExpired.mustCheck
        assert almostExpired.keepFor == Duration.ofMillis(1)

        def expired = cachePolicy.versionListExpiry(null, [moduleId] as Set, Duration.ofMillis(timeout + 1))
        assert expired.mustCheck
        assert expired.keepFor == Duration.ZERO
    }

    private void hasChangingModuleTimeout(long timeout) {
        def id = moduleComponent('group', 'name', 'version')
        def module = moduleVersion('group', 'name', 'version')

        def thisBuild = cachePolicy.changingModuleExpiry(id, module, Duration.ZERO)
        assert !thisBuild.mustCheck
        assert thisBuild.keepFor == Duration.ofMillis(timeout)

        def almostExpired = cachePolicy.changingModuleExpiry(id, module, Duration.ofMillis(timeout - 1))
        assert !almostExpired.mustCheck
        assert almostExpired.keepFor == Duration.ofMillis(1)

        def atTimeout = cachePolicy.changingModuleExpiry(id, module, Duration.ofMillis(timeout))
        assert !atTimeout.mustCheck
        assert atTimeout.keepFor == Duration.ZERO

        def expired = cachePolicy.changingModuleExpiry(id, module, Duration.ofMillis(timeout + 1))
        assert expired.mustCheck
        assert expired.keepFor == Duration.ZERO
    }

    private void hasNoModuleTimeout() {
        def id = moduleComponent('group', 'name', 'version')
        def module = moduleVersion('group', 'name', 'version')

        def thisBuild = cachePolicy.moduleExpiry(id, module, Duration.ZERO)
        assert !thisBuild.mustCheck
        assert thisBuild.keepFor == Duration.ofMillis(FOREVER)

        def expiry1 = cachePolicy.moduleExpiry(id, module, Duration.ofMillis(2 * DAY))
        assert !expiry1.mustCheck
        assert expiry1.keepFor == Duration.ofMillis(FOREVER)

        def expiry2 = cachePolicy.moduleExpiry(id, module, Duration.ofMillis(FOREVER))
        assert !expiry2.mustCheck
        assert expiry2.keepFor == Duration.ofMillis(FOREVER)
    }

    private void hasMissingModuleTimeout(long timeout) {
        def id = moduleComponent('group', 'name', 'version')

        def thisBuild = cachePolicy.moduleExpiry(id, null, Duration.ZERO)
        assert !thisBuild.mustCheck
        assert thisBuild.keepFor == Duration.ofMillis(timeout)

        def atTimeout = cachePolicy.moduleExpiry(id, null, Duration.ofMillis(timeout))
        assert !atTimeout.mustCheck
        assert atTimeout.keepFor == Duration.ZERO

        def almostExpired = cachePolicy.moduleExpiry(id, null, Duration.ofMillis(timeout - 1))
        assert !almostExpired.mustCheck
        assert almostExpired.keepFor == Duration.ofMillis(1)

        def expired = cachePolicy.moduleExpiry(id, null, Duration.ofMillis(timeout + 1))
        assert expired.mustCheck
        assert expired.keepFor == Duration.ZERO
    }

    private void hasNoMissingModuleTimeout() {
        def id = moduleComponent('group', 'name', 'version')

        def thisBuild = cachePolicy.moduleExpiry(id, null, Duration.ZERO)
        assert !thisBuild.mustCheck
        assert thisBuild.keepFor == Duration.ofMillis(FOREVER)

        def expiry1 = cachePolicy.moduleExpiry(id, null, Duration.ofMillis(2 * DAY))
        assert !expiry1.mustCheck
        assert expiry1.keepFor == Duration.ofMillis(FOREVER)

        def expiry2 = cachePolicy.moduleExpiry(id, null, Duration.ofMillis(FOREVER))
        assert !expiry2.mustCheck
        assert expiry2.keepFor == Duration.ofMillis(FOREVER)
    }

    private void hasMissingArtifactTimeout(long timeout) {
        def atTimeout = cachePolicy.artifactExpiry(null, null, Duration.ofMillis(timeout), false, false)
        assert !atTimeout.mustCheck
        assert atTimeout.keepFor == Duration.ZERO

        def almostExpired = cachePolicy.artifactExpiry(null, null, Duration.ofMillis(timeout - 1), false, false)
        assert !almostExpired.mustCheck
        assert almostExpired.keepFor == Duration.ofMillis(1)

        if (timeout != FOREVER) {
            def expired = cachePolicy.artifactExpiry(null, null, Duration.ofMillis(timeout + 1), false, false)
            assert expired.mustCheck
            assert expired.keepFor == Duration.ZERO
        }
    }

    private def assertId(def moduleId, String group, String name, String version) {
        assert moduleId.group == group
        assert moduleId.name == name
        assert moduleId.version == version
    }

    private def moduleComponent(String group, String name, String version) {
        new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(group, name), version)
    }

    private def moduleIdentifier(String group, String name, String version) {
        DefaultModuleVersionIdentifier.newId(DefaultModuleIdentifier.newId(group, name), version)
    }

    private def moduleVersion(String group, String name, String version) {
        return new ResolvedModuleVersion() {
            ModuleVersionIdentifier getId() {
                return DefaultModuleVersionIdentifier.newId(DefaultModuleIdentifier.newId(group, name), version);
            }
        }
    }

}
