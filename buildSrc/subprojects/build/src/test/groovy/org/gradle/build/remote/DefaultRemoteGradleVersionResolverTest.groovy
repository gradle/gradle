/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.build.remote

import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DefaultRemoteGradleVersionResolverTest extends Specification {

    @Subject def defaultRemoteGradleVersionResolver = new DefaultRemoteGradleVersionResolver()

    @Unroll
    @Requires({
        try {
            new URL('http://google.com').openConnection().getInputStream().close()
            true
        } catch (IOException) {
            false
        }
    })
    def "can retrieve #versionType version"() {
        when:
        def version = defaultRemoteGradleVersionResolver.getVersionAsJson(versionType)

        then:
        version

        where:
        versionType << [VersionType.NIGHTLY, VersionType.CURRENT]
    }
}
