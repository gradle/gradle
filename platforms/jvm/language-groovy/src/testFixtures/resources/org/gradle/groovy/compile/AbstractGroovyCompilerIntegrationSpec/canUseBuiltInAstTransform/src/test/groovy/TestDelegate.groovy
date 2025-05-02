package org.gradle.groovy.compile.GroovyCompilerIntegrationSpec.canUseBuiltInAstTransform.src.test.groovy

class TestDelegate {
    def doStuff(String value) {
        return "[$value]"
    }
}
