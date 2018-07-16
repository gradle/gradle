package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test


class ProjectPropertiesSampleTest : AbstractSampleTest("project-properties") {

    @Test
    fun `project properties`() {
        assertThat(
            build("-Plabel=answer to the ultimate question about life, the universe and everything", "compute").output,
            containsString("The answer to the ultimate question about life, the universe and everything is 42."))
    }
}
