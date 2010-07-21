/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks

import org.junit.Test
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import static org.gradle.util.Matchers.*
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.file.FileTree
import java.util.concurrent.Callable
import org.gradle.api.file.FileCollection

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
        assertFalse(inputs.hasInputs)
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
    public void canRegisterInputProperty() {
        inputs.property('a', 'value')
        assertThat(inputs.properties, equalTo([a: 'value']))
    }
    
    @Test
    public void canRegisterInputPropertyUsingAClosure() {
        inputs.property('a', { 'value' })
        assertThat(inputs.properties, equalTo([a: 'value']))
    }

    @Test
    public void canRegisterInputPropertyUsingACallable() {
        inputs.property('a', { 'value' } as Callable)
        assertThat(inputs.properties, equalTo([a: 'value']))
    }

    @Test
    public void canRegisterInputPropertyUsingAFileCollection() {
        def files = [new File('file')] as Set
        inputs.property('a', [getFiles: { files }] as FileCollection)
        assertThat(inputs.properties, equalTo([a: files]))
    }

    @Test
    public void inputPropertyCanBeNestedCallableAndClosure() {
        def files = [new File('file')] as Set
        def fileCollection = [getFiles: { files }] as FileCollection
        def callable = {fileCollection} as Callable
        inputs.property('a', { callable })
        assertThat(inputs.properties, equalTo([a: files]))
    }

    @Test
    public void hasInputsWhenEmptyInputFilesRegistered() {
        inputs.files([])
        assertTrue(inputs.hasInputs)
    }

    @Test
    public void hasInputsWhenNonEmptyInputFilesRegistered() {
        inputs.files('a')
        assertTrue(inputs.hasInputs)
    }

    @Test
    public void hasInputsWhenInputPropertyRegistered() {
        inputs.property('a', 'value')
        assertTrue(inputs.hasInputs)
    }
}