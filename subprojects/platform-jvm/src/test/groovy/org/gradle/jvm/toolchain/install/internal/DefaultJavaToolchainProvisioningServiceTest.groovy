/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.install.internal


import org.gradle.cache.FileLock
import org.gradle.jvm.toolchain.JavaToolchainSpec
import spock.lang.Specification

class DefaultJavaToolchainProvisioningServiceTest extends Specification {

    def "cache is properly locked around provisioning a jdk"() {
        def cache = Mock(JdkCacheDirectory)
        def lock = Mock(FileLock)
        def binary = Mock(AdoptOpenJdkRemoteBinary)
        def spec = Mock(JavaToolchainSpec)

        given:
        binary.toFilename(spec) >> 'jdk-123.zip'
        cache.getDownloadLocation(_ as String) >> Mock(File)
        def provisioningService = new DefaultJavaToolchainProvisioningService(binary, cache)

        when:
        provisioningService.tryInstall(spec)

        then:
        1 * cache.acquireWriteLock("jdk-123.zip", _) >> lock

        then:
        1 * binary.download(_, _) >> Optional.empty()

        then:
        1 * lock.close()
    }

}
