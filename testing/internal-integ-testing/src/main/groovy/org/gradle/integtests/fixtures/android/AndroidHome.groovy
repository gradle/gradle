/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.fixtures.android

import static org.hamcrest.CoreMatchers.notNullValue
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assume.assumeThat


class AndroidHome {

    private static final String ENV_VARIABLE_NAME = "ANDROID_SDK_ROOT"

    static void assumeIsSet() {
        assumeThat(NO_ENV_MESSAGE, System.getenv(ENV_VARIABLE_NAME), notNullValue())
    }

    static void assertIsSet() {
        assertHasValue(System.getenv(ENV_VARIABLE_NAME))
    }

    static String get() {
        def sdkRoot = System.getenv(ENV_VARIABLE_NAME)
        assertHasValue(sdkRoot)
        sdkRoot
    }

    private static void assertHasValue(String sdkRoot) {
        assertThat(NO_ENV_MESSAGE, sdkRoot, notNullValue())
    }

    private static final String NO_ENV_MESSAGE = """
        In order to run these tests the ANDROID_SDK_ROOT directory must be set.
        It is not necessary to install the whole android SDK via Android Studio - it is enough if there is a ${'$'}ANDROID_SDK_ROOT/licenses/android-sdk-license containing the license keys from an Android Studio installation.
        The Gradle Android plugin will then download the SDK by itself, see https://developer.android.com/studio/intro/update.html#download-with-gradle
    """.stripIndent()

    private AndroidHome() {}
}
