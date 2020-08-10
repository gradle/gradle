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

package org.gradle

import org.gradle.integtests.fixtures.MultiVersionSpecRunner
import org.gradle.integtests.fixtures.TargetVersions
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(MultiVersionSpecRunner)
@TargetVersions(['1.0', '2.0', '3.0'])
class FlakyTest {
    static version
    File file = new File("${System.getenv("HOME")}/file")

    @Test
    void test() {
        if (file.exists()) {
            file.delete()
        } else {
            file.createNewFile()
            throw new RuntimeException("flaky!")
        }
    }
}
