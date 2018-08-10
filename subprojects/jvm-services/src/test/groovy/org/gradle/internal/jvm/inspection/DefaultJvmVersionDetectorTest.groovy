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
    def detector = new DefaultJvmVersionDetector(TestFiles.execHandleFactory())

    def "can determine version of current jvm"() {
        expect:
        detector.getJavaVersion(Jvm.current()) == JavaVersion.current()
    }

    def "can determine version of java command for current jvm"() {
        expect:
        detector.getJavaVersion(Jvm.current().getJavaExecutable().path) == JavaVersion.current()
    }

    def "can parse version number"() {
        expect:
        detector.parseJavaVersionCommandOutput("/usr/bin/java", new BufferedReader(new StringReader(output))) == JavaVersion.toVersion(version)

        where:
        output                             | version
        'java version "1.5"'               | "1.5"
        'java version "1.5"\ntrailers'     | "1.5"
        'headers\njava version "1.5"'      | "1.5"
        'java version "1.5"\r\ntrailers'   | "1.5"
        'headers\r\njava version "1.5"'    | "1.5"
        'java version "1.8.0_11"'          | "1.8"
        'openjdk version "1.8.0-internal"' | "1.8"
        """java version "1.9.0-ea"
Java(TM) SE Runtime Environment (build 1.9.0-ea-b53)
Java HotSpot(TM) 64-Bit Server VM (build 1.9.0-ea-b53, mixed mode)
"""                             | "1.9"
        """Picked up _JAVA_OPTIONS: -Dset_by_java_options=1
java version "1.8.0_91"
Java(TM) SE Runtime Environment (build 1.8.0_91-b14)
Java HotSpot(TM) 64-Bit Server VM (build 25.91-b14, mixed mode)
"""                             | "1.8"
        """java version "1.8.0_91"
        Java(TM) SE Runtime Environment (build 1.8.0_91-b14)
        Java HotSpot(TM) 64-Bit Server VM (build 25.91-b14, mixed mode)
        """                     | "1.8"
        """java version "9.0.1"
        Java(TM) SE Runtime Environment (build 9.0.1+11)
        Java HotSpot(TM) 64-Bit Server VM (build 9.0.1+11, mixed mode)
        """                     | "9.0.1"
        """java version "10-ea"
        Java(TM) SE Runtime Environment 18.3 (build 10-ea+35)
        Java HotSpot(TM) 64-Bit Server VM 18.3 (build 10-ea+35, mixed mode)
        """                     | "10"
        """java version "10-ea" 2018-03-20
        Java(TM) SE Runtime Environment 18.3 (build 10-ea+36)
        Java HotSpot(TM) 64-Bit Server VM 18.3 (build 10-ea+36, mixed mode)
        """                     | "10"
    }

    def "fails to parse version number"() {
        when:
        detector.parseJavaVersionCommandOutput("/usr/bin/java", new BufferedReader(new StringReader(output)))

        then:
        def e = thrown RuntimeException
        e.message == "Could not determine Java version using executable /usr/bin/java."

        where:
        output << [
                '',
                '\n',
                'foo',
                'foo\nbar',
                'foo\r\nbar'
        ]
    }
}
