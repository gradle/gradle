/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.nativeplatform.internal.resolve

import org.gradle.api.DomainObjectSet
import org.gradle.util.TestUtil
import spock.lang.Specification

class CachingLibraryBinaryLocatorTest extends Specification {
    def target = Mock(LibraryBinaryLocator)
    def locator = new CachingLibraryBinaryLocator(target, TestUtil.domainObjectCollectionFactory())

    def "locates library once and reuses result for subsequent lookups"() {
        def lib = new LibraryIdentifier("project", "lib")
        def bins = Stub(DomainObjectSet)

        when:
        def bins1 = locator.getBinaries(lib)
        def bins2 = locator.getBinaries(lib)

        then:
        bins1.is(bins)
        bins2.is(bins)

        and:
        1 * target.getBinaries(lib) >> bins
        0 * target._
    }

    def "caches null result"() {
        def lib = new LibraryIdentifier("project", "lib")

        when:
        def bins1 = locator.getBinaries(lib)
        def bins2 = locator.getBinaries(lib)

        then:
        bins1 == null
        bins2 == null

        and:
        1 * target.getBinaries(lib) >> null
        0 * target._
    }

}
