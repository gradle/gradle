package org.gradle.api.internal.tasks

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.util.Matchers.*
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.file.FileTree

class DefaultTaskInputsTest {
    private final File treeFile = new File('tree')
    private final FileTree tree = [getFiles: { [treeFile] as Set}] as FileTree
    private final FileResolver resolver = [
            resolve: {new File(it)},
            resolveFilesAsTree: {tree}
    ] as FileResolver
    private final DefaultTaskInputs inputs = new DefaultTaskInputs(resolver)

    @Test
    public void defaultValues() {
        assertThat(inputs.files.files, isEmpty())
        assertFalse(inputs.hasInputFiles)
    }

    @Test
    public void canRegisterInputFiles() {
        inputs.files('a')
        assertThat(inputs.files.files, equalTo([new File('a')] as Set))
    }

    @Test
    public void canRegisterInputDir() {
        inputs.dir('a')
        assertThat(inputs.files.files, equalTo([treeFile] as Set))
    }
    
    @Test
    public void hasInputFilesWhenEmptyInputFilesRegistered() {
        inputs.files([])
        assertTrue(inputs.hasInputFiles)
    }
    
    @Test
    public void hasInputFilesWhenNonEmptyInputFilesRegistered() {
        inputs.files('a')
        assertTrue(inputs.hasInputFiles)
    }
}