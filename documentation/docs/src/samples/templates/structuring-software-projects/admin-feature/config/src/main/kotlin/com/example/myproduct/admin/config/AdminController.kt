package com.example.myproduct.admin.config

import com.example.myproduct.admin.state.ConfigurationState

object AdminController {

    fun update(versionRange: VersionRange) {
        ConfigurationState.rangeSetting.minVersion = versionRange.fromVersion
        ConfigurationState.rangeSetting.maxVersion = versionRange.toVersion
    }
}
