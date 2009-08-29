package org.gradle.initialization

import org.gradle.groovy.scripts.Script
import org.gradle.api.internal.project.StandardOutputRedirector

abstract class InitScript extends Script {

    def ClassLoader getContextClassloader() {
        return gradle.initscript.classLoader
    }

    def StandardOutputRedirector getStandardOutputRedirector() {
        return gradle.standardOutputRedirector
    }

    def String toString() {
        return "initialization script"
    }
}