package org.gradle.kotlin.dsl.samples

import org.junit.Test


class HelloWorldSampleTest : AbstractSampleTest("hello-world") {

    @Test
    fun `hello world`() {
        build("test")
    }
}
