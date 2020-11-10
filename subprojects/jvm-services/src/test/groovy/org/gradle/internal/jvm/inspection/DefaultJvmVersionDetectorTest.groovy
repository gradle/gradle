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

package org.gradle.internal.jvm.inspection

import org.gradle.api.JavaVersion
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.jvm.Jvm
import spock.lang.Specification

class DefaultJvmVersionDetectorTest extends Specification {
    def detector = new DefaultJvmVersionDetector(new DefaultJvmMetadataDetector(TestFiles.execHandleFactory()))

    def "can determine version of current jvm"() {
        expect:
        detector.getJavaVersion(Jvm.current()) == JavaVersion.current()
    }

    def "can determine version of java command for current jvm"() {
        expect:
        detector.getJavaVersion(Jvm.current().getJavaExecutable().path) == JavaVersion.current()
    }

}
