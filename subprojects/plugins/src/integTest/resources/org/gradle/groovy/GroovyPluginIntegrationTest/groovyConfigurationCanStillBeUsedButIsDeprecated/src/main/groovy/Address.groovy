package org.gradle.groovy.GroovyPluginIntegrationTest.groovyConfigurationCanStillBeUsedButIsDeprecated.src.main.groovy

import com.google.common.base.Strings

class Address {
    String street

    String getNonNullStreet() {
        Strings.nullToEmpty(street)
    }
}
