/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice

import spock.lang.Specification

/**
 * @author Hans Dockter
 */
class GradleIBiblioResolverTest extends Specification {
    GradleIBiblioResolver gradleIBiblioResolver = new GradleIBiblioResolver()

    def defaults() {
        expect:
        gradleIBiblioResolver.getSnapshotTimeout().is(GradleIBiblioResolver.DAILY)
    }

    def usesDailyExpiryForRemoteUrls() {
        when:
        gradleIBiblioResolver.setRoot("http://server/repo")

        then:
        gradleIBiblioResolver.getSnapshotTimeout().is(GradleIBiblioResolver.DAILY)
    }

    def usesAlwaysExpiryForLocalUrls() {
        when:
        gradleIBiblioResolver.setRoot(new File(".").toURI().toString())

        then:
        gradleIBiblioResolver.getSnapshotTimeout().is(GradleIBiblioResolver.ALWAYS)
    }

    def timeoutStrategyNever_shouldReturnAlwaysFalse() {
        expect:
        !GradleIBiblioResolver.NEVER.isCacheTimedOut(0)
        !GradleIBiblioResolver.NEVER.isCacheTimedOut(System.currentTimeMillis())
    }

    def timeoutStrategyAlways_shouldReturnAlwaysTrue() {
        expect:
        GradleIBiblioResolver.ALWAYS.isCacheTimedOut(0)
        GradleIBiblioResolver.ALWAYS.isCacheTimedOut(System.currentTimeMillis())
    }

    def timeoutStrategyDaily() {
        expect:
        !GradleIBiblioResolver.DAILY.isCacheTimedOut(System.currentTimeMillis())
        GradleIBiblioResolver.ALWAYS.isCacheTimedOut(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
    }

    def timeoutInterval() {
        def interval = new GradleIBiblioResolver.Interval(1000)

        expect:
        interval.isCacheTimedOut(System.currentTimeMillis() - 5000)
        !interval.isCacheTimedOut(System.currentTimeMillis())
    }

    def setTimeoutByMilliseconds() {
        when:
        gradleIBiblioResolver.setSnapshotTimeout(1000)

        then:
        ((GradleIBiblioResolver.Interval) gradleIBiblioResolver.getSnapshotTimeout()).interval == 1000
    }

    def setTimeoutByStrategy() {
        when:
        gradleIBiblioResolver.setSnapshotTimeout(GradleIBiblioResolver.NEVER)

        then:
        gradleIBiblioResolver.getSnapshotTimeout().is(GradleIBiblioResolver.NEVER)
    }
}
