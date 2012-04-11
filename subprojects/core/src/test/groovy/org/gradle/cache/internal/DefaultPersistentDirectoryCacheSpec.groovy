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

import spock.lang.Specification
import org.junit.Rule
import org.gradle.util.TemporaryFolder
import org.gradle.CacheUsage
import org.gradle.cache.CacheValidator
import org.gradle.api.Action
import static org.gradle.cache.internal.DefaultFileLockManagerTestHelper.*

class DefaultPersistentDirectoryCacheSpec extends Specification {

    @Rule TemporaryFolder tmp
    
    def "will rebuild cache if not unlocked cleanly"() {
        given:
        def dir = tmp.createDir("cache")
        def initd = false
        def init = { initd = true } as Action
        unlockUncleanly(new File(dir, "cache.properties"))
        def cache = new DefaultPersistentDirectoryCache(
                dir, "test", CacheUsage.ON, { true } as CacheValidator, [:], FileLockManager.LockMode.Exclusive, init, createDefaultFileLockManager()
        )
        
        when:
        cache.open()

        then:
        initd
    }
}
