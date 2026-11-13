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

import org.gradle.api.internal.artifacts.configurations.CachePolicy
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.TimeUnit

import static org.gradle.api.internal.artifacts.configurations.MutationValidator.MutationType.STRATEGY

class DefaultCachePolicySpec extends Specification {

    private static final long SECOND = 1000;
    private static final long MINUTE = SECOND * 60;
    private static final long HOUR = MINUTE * 60;
    private static final long DAY = HOUR * 24;
    private static final long WEEK = DAY * 7;
    private static final long FOREVER = Long.MAX_VALUE

    CachePolicy cachePolicy = new DefaultCachePolicy()

    def "applies default expiry"() {
        expect:
        hasDynamicVersionTimeout(DAY)
        hasChangingModuleTimeout(DAY)
        hasNoModuleTimeout()
        hasMissingArtifactTimeout(DAY)
        hasNoMissingModuleTimeout()
    }

    def 'never expires missing module for dynamic versions'() {
        expect:
        def expired = cachePolicy.asImmutable().versionListExpiry(false, Duration.ofMillis(WEEK))
        !expired.mustCheck
        expired.keepFor == Duration.ZERO

        def notExpired = cachePolicy.asImmutable().versionListExpiry(false, Duration.ofMillis(2 * SECOND))
        !notExpired.mustCheck
        notExpired.keepFor == Duration.ofMillis(DAY - 2 * SECOND)

        def thisBuild = cachePolicy.asImmutable().versionListExpiry(false, Duration.ZERO)
        !thisBuild.mustCheck
        thisBuild.keepFor == Duration.ofMillis(DAY)
    }

    def 'never expires missing module for non changing module'() {
        expect:
        !cachePolicy.asImmutable().moduleExpiry(Duration.ofMillis(WEEK), false).mustCheck
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

    def "uses cached version list when offline"() {
        when:
        cachePolicy.setOffline()

        then:
        def notExpired = cachePolicy.asImmutable().versionListExpiry(true, Duration.ofMillis(2 * SECOND))
        !notExpired.mustCheck
        notExpired.keepFor == Duration.ofMillis(DAY - 2 * SECOND)

        def expired = cachePolicy.asImmutable().versionListExpiry(true, Duration.ofMillis(WEEK))
        !expired.mustCheck
        expired.keepFor == Duration.ZERO

        when:
        cachePolicy.cacheDynamicVersionsFor(5, TimeUnit.SECONDS)

        then:
        def expiry2 = cachePolicy.asImmutable().versionListExpiry(false, Duration.ofMillis(2 * SECOND))
        !expiry2.mustCheck
        expiry2.keepFor == Duration.ofMillis(3 * SECOND)
    }

    def "does not use cached version list when refresh dependencies"() {
        when:
        cachePolicy.setRefreshDependencies()

        then:
        def expired = cachePolicy.asImmutable().versionListExpiry(true, Duration.ofMillis(WEEK))
        expired.mustCheck
        expired.keepFor == Duration.ZERO

        def notExpired = cachePolicy.asImmutable().versionListExpiry(true, Duration.ofMillis(2 * SECOND))
        notExpired.mustCheck
        notExpired.keepFor == Duration.ZERO
    }

    def "uses cached version list when refresh dependencies and version was cached in this build"() {
        when:
        cachePolicy.setRefreshDependencies()

        then:
        def expiry1 = cachePolicy.asImmutable().versionListExpiry(true, Duration.ZERO)
        !expiry1.mustCheck
        expiry1.keepFor == Duration.ofMillis(DAY)

        when:
        cachePolicy.cacheDynamicVersionsFor(1, TimeUnit.SECONDS)

        then:
        def expiry2 = cachePolicy.asImmutable().versionListExpiry(false, Duration.ZERO)
        !expiry2.mustCheck
        expiry2.keepFor == Duration.ofMillis(SECOND)
    }

    def "uses cached module metadata when offline"() {
        when:
        cachePolicy.setOffline()

        then:
        def notExpired = cachePolicy.asImmutable().moduleExpiry(Duration.ofMillis(2 * SECOND), false)
        !notExpired.mustCheck
        notExpired.keepFor == Duration.ofMillis(FOREVER)

        def expired = cachePolicy.asImmutable().moduleExpiry(Duration.ofMillis(WEEK), false)
        !expired.mustCheck
        expired.keepFor == Duration.ofMillis(FOREVER)
    }

    def "uses cached changing module metadata when offline"() {
        when:
        cachePolicy.setOffline()

        then:
        def notExpired = cachePolicy.asImmutable().moduleExpiry(Duration.ofMillis(2 * SECOND), true)
        !notExpired.mustCheck
        notExpired.keepFor == Duration.ofMillis(DAY - 2 * SECOND)

        def expired = cachePolicy.asImmutable().moduleExpiry(Duration.ofMillis(WEEK), true)
        !expired.mustCheck
        expired.keepFor == Duration.ZERO

        when:
        cachePolicy.cacheChangingModulesFor(5, TimeUnit.SECONDS)

        then:
        def expiry2 = cachePolicy.asImmutable().moduleExpiry(Duration.ofMillis(2 * SECOND), true)
        !expiry2.mustCheck
        expiry2.keepFor == Duration.ofMillis(3 * SECOND)
    }

    def "does not use cached changing module when refresh dependencies"() {
        when:
        cachePolicy.setRefreshDependencies()

        then:
        def expired = cachePolicy.asImmutable().moduleExpiry(Duration.ofMillis(WEEK), true)
        expired.mustCheck
        expired.keepFor == Duration.ZERO

        def notExpired = cachePolicy.asImmutable().moduleExpiry(Duration.ofMillis(2 * SECOND), true)
        notExpired.mustCheck
        notExpired.keepFor == Duration.ZERO
    }

    def "uses cached changing module when refresh dependencies and version was cached in this build"() {
        when:
        cachePolicy.setRefreshDependencies()

        then:
        def expiry1 = cachePolicy.asImmutable().moduleExpiry(Duration.ZERO, true)
        !expiry1.mustCheck
        expiry1.keepFor == Duration.ofMillis(DAY)

        when:
        cachePolicy.cacheChangingModulesFor(1, TimeUnit.SECONDS)

        then:
        def expiry2 = cachePolicy.asImmutable().moduleExpiry(Duration.ZERO, true)
        !expiry2.mustCheck
        expiry2.keepFor == Duration.ofMillis(SECOND)
    }

    def "must refresh artifact for changing modules when moduledescriptorhash not in sync"() {
        expect:
        !cachePolicy.asImmutable().artifactExpiry(true, Duration.ofMillis(1000), false, true).mustCheck
        !cachePolicy.asImmutable().artifactExpiry(true, Duration.ofMillis(1000), false, false).mustCheck
        cachePolicy.asImmutable().artifactExpiry(true, Duration.ofMillis(1000), true, false).mustCheck
    }

    def "when rules are empty copy reuses rules"() {
        expect:
        def copy = cachePolicy.copy()

        !copy.is(cachePolicy)
        copy.asImmutable().dependencyCacheRules.is(cachePolicy.asImmutable().dependencyCacheRules)
        copy.asImmutable().moduleCacheRules.is(cachePolicy.asImmutable().moduleCacheRules)
        copy.asImmutable().artifactCacheRules.is(cachePolicy.asImmutable().artifactCacheRules)

        copy.asImmutable().dependencyCacheRules == cachePolicy.asImmutable().dependencyCacheRules
        copy.asImmutable().moduleCacheRules == cachePolicy.asImmutable().moduleCacheRules
        copy.asImmutable().artifactCacheRules == cachePolicy.asImmutable().artifactCacheRules
    }

    def "when rules are not empty copy creates independent rules"() {
        given:
        cachePolicy.cacheDynamicVersionsFor(10, TimeUnit.SECONDS)
        cachePolicy.cacheChangingModulesFor(10, TimeUnit.SECONDS)

        when:
        def copy = cachePolicy.copy()

        then:
        !copy.is(cachePolicy)
        !copy.asImmutable().dependencyCacheRules.is(cachePolicy.asImmutable().dependencyCacheRules)
        !copy.asImmutable().moduleCacheRules.is(cachePolicy.asImmutable().moduleCacheRules)
        !copy.asImmutable().artifactCacheRules.is(cachePolicy.asImmutable().artifactCacheRules)

        copy.asImmutable().dependencyCacheRules == cachePolicy.asImmutable().dependencyCacheRules
        copy.asImmutable().moduleCacheRules == cachePolicy.asImmutable().moduleCacheRules
        copy.asImmutable().artifactCacheRules == cachePolicy.asImmutable().artifactCacheRules

        when:
        copy.cacheDynamicVersionsFor(20, TimeUnit.SECONDS)
        copy.cacheChangingModulesFor(20, TimeUnit.SECONDS)

        then:
        copy.asImmutable().dependencyCacheRules != cachePolicy.asImmutable().dependencyCacheRules
        copy.asImmutable().moduleCacheRules != cachePolicy.asImmutable().moduleCacheRules
        copy.asImmutable().artifactCacheRules != cachePolicy.asImmutable().artifactCacheRules
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
        cachePolicy.setOffline()

        then:
        1 * validator.validateMutation(STRATEGY)

        when:
        cachePolicy.setRefreshDependencies()

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
        copy.setOffline()

        then:
        0 * validator.validateMutation(_)

        when:
        copy.setRefreshDependencies()

        then:
        0 * validator.validateMutation(_)
    }

    private void hasDynamicVersionTimeout(long timeout) {
        def immutablePolicy = cachePolicy.asImmutable()

        def thisBuild = immutablePolicy.versionListExpiry(true, Duration.ZERO)
        assert !thisBuild.mustCheck
        assert thisBuild.keepFor == Duration.ofMillis(timeout)

        def atTimeout = immutablePolicy.versionListExpiry(true, Duration.ofMillis(timeout))
        assert !atTimeout.mustCheck
        assert atTimeout.keepFor == Duration.ZERO

        def almostExpired = immutablePolicy.versionListExpiry(true, Duration.ofMillis(timeout - 1))
        assert !almostExpired.mustCheck
        assert almostExpired.keepFor == Duration.ofMillis(1)

        def expired = immutablePolicy.versionListExpiry(true, Duration.ofMillis(timeout + 1))
        assert expired.mustCheck
        assert expired.keepFor == Duration.ZERO
    }

    private void hasChangingModuleTimeout(long timeout) {
        def immutablePolicy = cachePolicy.asImmutable()

        def thisBuild = immutablePolicy.moduleExpiry(Duration.ZERO, true)
        assert !thisBuild.mustCheck
        assert thisBuild.keepFor == Duration.ofMillis(timeout)

        def almostExpired = immutablePolicy.moduleExpiry(Duration.ofMillis(timeout - 1), true)
        assert !almostExpired.mustCheck
        assert almostExpired.keepFor == Duration.ofMillis(1)

        def atTimeout = immutablePolicy.moduleExpiry(Duration.ofMillis(timeout), true)
        assert !atTimeout.mustCheck
        assert atTimeout.keepFor == Duration.ZERO

        def expired = immutablePolicy.moduleExpiry(Duration.ofMillis(timeout + 1), true)
        assert expired.mustCheck
        assert expired.keepFor == Duration.ZERO
    }

    private void hasNoModuleTimeout() {
        def immutablePolicy = cachePolicy.asImmutable()

        def thisBuild = immutablePolicy.moduleExpiry(Duration.ZERO, false)
        assert !thisBuild.mustCheck
        assert thisBuild.keepFor == Duration.ofMillis(FOREVER)

        def expiry1 = immutablePolicy.moduleExpiry(Duration.ofMillis(2 * DAY), false)
        assert !expiry1.mustCheck
        assert expiry1.keepFor == Duration.ofMillis(FOREVER)

        def expiry2 = immutablePolicy.moduleExpiry(Duration.ofMillis(FOREVER), false)
        assert !expiry2.mustCheck
        assert expiry2.keepFor == Duration.ofMillis(FOREVER)
    }

    private void hasNoMissingModuleTimeout() {
        def immutablePolicy = cachePolicy.asImmutable()

        def thisBuild = immutablePolicy.moduleExpiry(Duration.ZERO, false)
        assert !thisBuild.mustCheck
        assert thisBuild.keepFor == Duration.ofMillis(FOREVER)

        def expiry1 = immutablePolicy.moduleExpiry(Duration.ofMillis(2 * DAY), false)
        assert !expiry1.mustCheck
        assert expiry1.keepFor == Duration.ofMillis(FOREVER)

        def expiry2 = immutablePolicy.moduleExpiry(Duration.ofMillis(FOREVER), false)
        assert !expiry2.mustCheck
        assert expiry2.keepFor == Duration.ofMillis(FOREVER)
    }

    private void hasMissingArtifactTimeout(long timeout) {
        def immutablePolicy = cachePolicy.asImmutable()

        def atTimeout = immutablePolicy.artifactExpiry(true, Duration.ofMillis(timeout), false, false)
        assert !atTimeout.mustCheck
        assert atTimeout.keepFor == Duration.ZERO

        def almostExpired = immutablePolicy.artifactExpiry(true, Duration.ofMillis(timeout - 1), false, false)
        assert !almostExpired.mustCheck
        assert almostExpired.keepFor == Duration.ofMillis(1)

        if (timeout != FOREVER) {
            def expired = immutablePolicy.artifactExpiry(true, Duration.ofMillis(timeout + 1), false, false)
            assert expired.mustCheck
            assert expired.keepFor == Duration.ZERO
        }
    }

}
