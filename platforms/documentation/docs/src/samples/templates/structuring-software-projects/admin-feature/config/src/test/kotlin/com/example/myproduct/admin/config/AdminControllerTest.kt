package com.example.myproduct.admin.config

import com.example.myproduct.admin.state.ConfigurationState
import com.example.myproduct.admin.state.VersionRangeSetting
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdminControllerTest {
    @Test fun updatesState() {
        AdminController.update(VersionRange("1", "2"))
        assertEquals(VersionRangeSetting("1", "2"), ConfigurationState.rangeSetting)
    }
}