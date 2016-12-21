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

package org.gradle.process.internal.health.memory

import org.gradle.process.internal.ExecHandleFactory
import spock.lang.Specification

class VmstatAvailableMemoryTest extends Specification {
    def "parses free memory from vm_stat on MacOS"() {
        expect:
        new VmstatAvailableMemory(Stub(ExecHandleFactory)).parseFreeMemoryFromVmstat(mockVmstatOutput()) == 5_717_209_088L
    }

    private static List<String> mockVmstatOutput() {
        """Mach Virtual Memory Statistics: (page size of 4096 bytes)
Pages free:                              235271.
Pages active:                           1552064.
Pages inactive:                         1419355.
Pages speculative:                        28612.
Pages throttled:                              0.
Pages wired down:                        801506.
Pages purgeable:                          59751.
"Translation faults":                 710260691.
Pages copy-on-write:                   27561109.
Pages zero filled:                    244159969.
Pages reactivated:                      3774906.
Pages purged:                            420232.
File-backed pages:                      1160532.
Anonymous pages:                        1839499.
Pages stored in compressor:              422034.
Pages occupied by compressor:            156894.
Decompressions:                         3406193.
Compressions:                           5099722.
Pageins:                                4299406.
Pageouts:                                 41419.
Swapins:                                2439008.
Swapouts:                               2552292.
""".split(/\n/)
    }
}
