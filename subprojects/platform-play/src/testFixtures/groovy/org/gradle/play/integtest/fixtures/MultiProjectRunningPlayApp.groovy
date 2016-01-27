/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.play.integtest.fixtures

import org.gradle.test.fixtures.file.TestFile

import static org.gradle.integtests.fixtures.UrlValidator.assertUrlContent


class MultiProjectRunningPlayApp extends RunningPlayApp {
    MultiProjectRunningPlayApp(TestFile testDirectory) {
        super(testDirectory)
    }

    @Override
    void verifyContent() {
        assertUrlContent playUrl(), "Your new application is ready."
        assertUrlContent playUrl("assets/primary.txt"), "Primary asset"
        assertUrlContent playUrl("submodule"), "Submodule page"
        assertUrlContent playUrl("assets/submodule.txt"), "Submodule asset"
    }
}
