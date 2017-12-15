package org.gradle.testing.junit5.plugins

import org.gradle.integtests.fixtures.WellBehavedPluginTest

class JUnitPlatformPluginGoodBehaviorTest extends WellBehavedPluginTest {
    @Override
    String getPluginName() {
        'junit-platform'
    }
}
