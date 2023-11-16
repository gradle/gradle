/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.cache.internal.filelock

import org.gradle.cache.LockOptions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.cache.FileLockManager.LockMode.OnDemand

class DefaultLockOptionsTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "if using alternate lock dir, can't be using LockTargetType.CachePropertiesFile"() {
        given:
        File alternateLockDir = tmpDir.createDir("alternate-lock-dir")

        when:
        new DefaultLockOptions(OnDemand, false, alternateLockDir, LockOptions.LockTargetType.CachePropertiesFile)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot use alternate lock directory with lock target type: CachePropertiesFile"
    }
}
