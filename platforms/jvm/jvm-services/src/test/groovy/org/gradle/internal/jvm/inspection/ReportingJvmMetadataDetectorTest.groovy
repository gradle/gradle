/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.jvm.inspection


import org.gradle.jvm.toolchain.internal.InstallationLocation
import spock.lang.Specification

import java.util.function.BiConsumer

class ReportingJvmMetadataDetectorTest extends Specification {

    def "delegates and reports each call"() {
        given:
        def location = new InstallationLocation(new File("jdkHome"), "test")
        def metadata = Mock(JvmInstallationMetadata)
        def reporter = Mock(BiConsumer)
        def delegate = Mock(JvmMetadataDetector)

        def detector = new ReportingJvmMetadataDetector(delegate, reporter)

        when:
        def actual1 = detector.getMetadata(location)
        def actual2 = detector.getMetadata(location)

        then:
        [actual1, actual2] == [metadata, metadata]
        2 * delegate.getMetadata(location) >> metadata
        2 * reporter.accept(location, metadata)
    }
}
