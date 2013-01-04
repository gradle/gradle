/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.cache.internal

import org.gradle.cache.CacheOpenException
import org.gradle.internal.Factory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

class DelegateOnDemandPersistentDirectoryCacheSpec extends Specification {

    @Rule public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    DefaultPersistentDirectoryCache delegateCache = Mock()
    File dir = tmpDir.createDir("cacheDir")
    String displayName = "test display name"
    FileLockManager.LockMode lockMode = FileLockManager.LockMode.None
    FileLockManager lockManager = Mock()


    @Unroll
    def "#methodName opens delegate, executes #methodName on delegate and closes delegate"() {
        given:
        Runnable runnable = {} as Runnable
        DelegateOnDemandPersistentDirectoryCache cut = new DelegateOnDemandPersistentDirectoryCache(delegateCache)
        when:
        cut.open()
        and:
        cut."${methodName}"("operation name", runnable)
        then:
        1 * delegateCache.open()
        1 * delegateCache."${methodName}"("operation name", runnable) //checking against *params fails here. TODO: ping Peter for details
        1 * delegateCache.close()
        where:
        methodName << ["useCache", "longRunningOperation"]
    }

    @Unroll
    def "#methodName opens delegate, executes #methodName on delegate, closes delegate and returns delegate return value of delegates #methodName"() {
        given:
        org.gradle.internal.Factory factory = {-> "testResult" } as Factory
        DelegateOnDemandPersistentDirectoryCache cut = new DelegateOnDemandPersistentDirectoryCache(delegateCache)

        when:
        cut.open()
        and:
        cut."${methodName}"("operation name", factory)

        then:
        1 * delegateCache.open()
        1 * delegateCache."${methodName}"("operation name", factory) >> "testResult"
        1 * delegateCache.close()

        where:
        methodName << ["useCache", "longRunningOperation"]
    }

    @Unroll
    def "#methodName with #actionType only allowed on explicit opened cache"() {
        given:
        DelegateOnDemandPersistentDirectoryCache cut = new DelegateOnDemandPersistentDirectoryCache(delegateCache)
        when:
        cut."${methodName}"(* methodParameters)
        then:
        thrown(CacheOpenException)
        0 * delegateCache.open()
        0 * delegateCache."${methodName}"(* _)
        0 * delegateCache.close()
        where:
        methodName             | methodParameters                                                                       | actionType // for test naming only
        "useCache"             | ["operation name", {-> "factory return "} as org.gradle.internal.Factory]              | "Factory"
        "useCache"             | ["operation name", {} as Runnable]                                                     | "Runnable"
        "longRunningOperation" | ["long running operation name", {-> "factory return "} as org.gradle.internal.Factory] | "Factory"
        "longRunningOperation" | ["long running operation name", {} as Runnable]                                        | "Runnable"
    }

    @Unroll
    def "#methodName calls delegated close"() {
        given:
        DelegateOnDemandPersistentDirectoryCache cut = new DelegateOnDemandPersistentDirectoryCache(delegateCache)
        when:
        cut."${methodName}"()
        then:
        1 * delegateCache."${methodName}"()
        where:
        methodName << ['close', 'getLock']
    }
}
