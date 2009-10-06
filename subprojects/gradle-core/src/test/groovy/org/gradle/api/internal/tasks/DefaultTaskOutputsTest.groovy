package org.gradle.api.internal.tasks

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.util.Matchers.*
import org.gradle.api.internal.file.FileResolver;

class DefaultTaskOutputsTest {
    private final DefaultTaskOutputs outputs = new DefaultTaskOutputs({new File(it)} as FileResolver)

    @Test
    public void defaultValues() {
        assertThat(outputs.outputFiles.files, isEmpty())
    }

    @Test
    public void canRegisterOutputFiles() {
        outputs.outputFiles('a')
        assertThat(outputs.outputFiles.files, equalTo([new File('a')] as Set))
    }
}