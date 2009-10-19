package org.gradle.api.internal.tasks

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.util.Matchers.*
import org.gradle.api.internal.file.FileResolver

class DefaultTaskInputsTest {
    private final DefaultTaskInputs inputs = new DefaultTaskInputs({new File(it)} as FileResolver)

    @Test
    public void defaultValues() {
        assertThat(inputs.inputFiles.files, isEmpty())
        assertFalse(inputs.hasInputFiles)
    }

    @Test
    public void canRegisterInputFiles() {
        inputs.inputFiles('a')
        assertThat(inputs.inputFiles.files, equalTo([new File('a')] as Set))
    }
    
    @Test
    public void hasInputFilesWhenEmptyInputFilesRegistered() {
        inputs.inputFiles([])
        assertTrue(inputs.hasInputFiles)
    }
    
    @Test
    public void hasInputFilesWhenNonEmptyInputFilesRegistered() {
        inputs.inputFiles('a')
        assertTrue(inputs.hasInputFiles)
    }
}