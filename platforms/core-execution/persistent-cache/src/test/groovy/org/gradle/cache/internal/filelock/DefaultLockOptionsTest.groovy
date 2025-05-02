/*
 * Copyright 2013 the original author or authors.
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

import spock.lang.Specification

import static org.gradle.cache.FileLockManager.LockMode.Exclusive
import static org.gradle.cache.FileLockManager.LockMode.Shared

class DefaultLockOptionsTest extends Specification {
    def "can make copy of options"() {
        def builder = DefaultLockOptions.mode(Exclusive).useCrossVersionImplementation()

        when:
        def copy = builder.copyWithMode(Shared)

        then:
        !copy.is(builder)
        copy.mode == Shared
        copy.useCrossVersionImplementation
    }
}
