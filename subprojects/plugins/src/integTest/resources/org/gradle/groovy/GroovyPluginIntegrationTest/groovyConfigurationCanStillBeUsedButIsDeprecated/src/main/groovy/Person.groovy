package org.gradle.groovy.GroovyPluginIntegrationTest.groovyConfigurationCanStillBeUsedButIsDeprecated.src.main.groovy

class Person {
    String name
    int age
    Address address

    void sing() {
        println "tra-la-la"
    }
}