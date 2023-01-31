import common.VersionedSettingsBranch
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/*
 * Copyright 2023 the original author or authors.
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

class VersionedSettingsBranchTest {
    @ParameterizedTest
    @CsvSource(
        value = [
            "master , true, 0",
            "release, true, 1",
            "release6x, false, 2",
            "release7x, false, 3",
            "release27x, false, 23"
        ]
    )
    fun validBranches(branchName: String, expectedEnableVcsTriggers: Boolean, expectedNightlyPromotionTriggerHour: Int?) {
        val vsb = VersionedSettingsBranch(branchName)
        assertEquals(expectedEnableVcsTriggers, vsb.enableVcsTriggers)
        assertEquals(expectedNightlyPromotionTriggerHour, vsb.nightlyPromotionTriggerHour)
    }

    @Test
    fun invalidBranches() {
        Assertions.assertThrows(Exception::class.java) {
            VersionedSettingsBranch("release28x")
        }
    }
}
