import common.VersionedSettingsBranch
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

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
            "master,    0",
            "release,   1",
            "release6x, 2",
            "release7x, 3",
            "release27x,23"
        ]
    )
    fun branchesWithVcsTriggerEnabled(branchName: String, expectedNightlyPromotionTriggerHour: Int?) {
        val vsb = VersionedSettingsBranch(branchName)
        assertTrue(vsb.enableVcsTriggers)
        assertEquals(expectedNightlyPromotionTriggerHour, vsb.nightlyPromotionTriggerHour)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "experimental",
            "placeholder-1",
            "whatever",
        ]
    )
    fun branchesWithVcsTriggerDisabled(branchName: String) {
        val vsb = VersionedSettingsBranch(branchName)
        assertFalse(vsb.enableVcsTriggers)
        assertNull(vsb.nightlyPromotionTriggerHour)
    }

    @Test
    fun invalidBranches() {
        Assertions.assertThrows(Exception::class.java) {
            VersionedSettingsBranch("release28x")
        }
    }
}
