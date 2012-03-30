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

package org.gradle.internal.nativeplatform.filesystem

import spock.lang.Specification
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition;

public class PosixUtilTest extends Specification {

    @Requires(TestPrecondition.UNKNOWN_OS)
    def "PosixUtil.current returns FallbackPOSIX on Unknown OS"() {
        expect:
        PosixUtil.current() instanceof FallbackPOSIX
    }

    @Requires(TestPrecondition.WINDOWS)
    def "PosixUtil.current returns FallbackPOSIX on WindowsOS"() {
        expect:
        PosixUtil.current() instanceof FallbackPOSIX
    }

    @Requires(TestPrecondition.FILE_PERMISSIONS)
    def "PosixUtil.current returns no FallbackPOSIX on Macosx and Unix"() {
        expect:
        !(PosixUtil.current() instanceof FallbackPOSIX)
    }
}
