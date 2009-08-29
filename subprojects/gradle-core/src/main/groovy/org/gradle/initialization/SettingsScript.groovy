package org.gradle.initialization

import org.gradle.groovy.scripts.Script
import org.gradle.api.internal.project.StandardOutputRedirector

abstract class SettingsScript extends Script {
    def String toString() {
        return settings
    }

    def StandardOutputRedirector getStandardOutputRedirector() {
        return settings.standardOutputRedirector
    }

    def ClassLoader getContextClassloader() {
        return settings.classLoader
    }
}