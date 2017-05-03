package org.gradle.script.lang.kotlin.samples

import org.junit.Test


class HelloWorldSampleTest : AbstractSampleTest("hello-world") {

    @Test
    fun `hello world`() {
        build("test")
    }
}
