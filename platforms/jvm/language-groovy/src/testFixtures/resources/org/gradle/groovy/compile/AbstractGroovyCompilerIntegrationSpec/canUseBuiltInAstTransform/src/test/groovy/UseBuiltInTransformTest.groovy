package org.gradle.groovy.compile.GroovyCompilerIntegrationSpec.canUseBuiltInAstTransform.src.test.groovy

import org.junit.Test

class UseBuiltInTransformTest {
    @Delegate final TestDelegate delegate = new TestDelegate()

    @Test
    void transformHasBeenApplied() {
        assert doStuff("hi") == "[hi]"
    }
}
