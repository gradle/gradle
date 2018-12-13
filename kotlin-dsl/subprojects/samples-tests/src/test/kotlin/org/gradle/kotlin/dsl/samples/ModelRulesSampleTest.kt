package org.gradle.kotlin.dsl.samples

import org.junit.Test


class ModelRulesSampleTest : AbstractSampleTest("model-rules") {

    @Test
    fun `hello task`() {

        // when:
        val result = build("hello")

        // then:
        result.output.contains("Hello John Smith!")
    }
}
