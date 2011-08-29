/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher

import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 8/29/11
 */
class DaemonTimeoutTest extends Specification {

    def "knows timeout"() {
        expect:
        "-Dorg.gradle.daemon.idletimeout=1000" == new DaemonTimeout(null, 1000).toArg()
        "-Dorg.gradle.daemon.idletimeout=1000" == new DaemonTimeout("-Dfoo=bar", 1000).toArg()
        "-Dorg.gradle.daemon.idletimeout=1000" == new DaemonTimeout("-Dorg.gradle.daemon.idletimeout=foo", 1000).toArg()
        "-Dorg.gradle.daemon.idletimeout=2000" == new DaemonTimeout("-Dorg.gradle.daemon.idletimeout=2000", 1000).toArg()
    }
}
