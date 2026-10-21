/*
 * Copyright 2026 the original author or authors.
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

package acme

import groovy.transform.CompileStatic
import org.gradle.internal.problems.BoundedCallerStackCapturer

/**
 * Lives in a non-{@code org.gradle} package so the capturer treats it as user code (the first
 * user-code frame above the capturer). {@code @CompileStatic} keeps the call direct, with no Groovy
 * call-site frames between this method and the capturer.
 */
@CompileStatic
class StackCapturerCaller {
    static Throwable siteA(BoundedCallerStackCapturer capturer) {
        capturer.captureCallerStack()
    }

    static Throwable siteB(BoundedCallerStackCapturer capturer) {
        capturer.captureCallerStack()
    }
}
