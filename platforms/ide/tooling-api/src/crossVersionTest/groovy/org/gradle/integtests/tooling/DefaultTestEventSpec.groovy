/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.tooling


import groovy.transform.CompileStatic
import groovy.transform.RecordType
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.internal.SystemProperties
import org.gradle.tooling.events.test.TestFileAttachmentMetadataEvent
import org.gradle.tooling.events.test.TestKeyValueMetadataEvent
import org.gradle.tooling.events.test.TestMetadataEvent
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.TestOutputDescriptor
import org.gradle.tooling.events.test.TestOutputEvent
import org.jspecify.annotations.Nullable

@CompileStatic
class DefaultTestEventSpec implements GroupTestEventSpec {
    private final ProgressEvents.Operation self
    private final List<ProgressEvents.Operation> verifiedOperations

    private final List<String> actualOutput;
    private final List<Object> actualMetadata;

    @RecordType
    private static class FileAttachment {
        File file
        String mediaType
    }

    DefaultTestEventSpec(ProgressEvents.Operation self, List<ProgressEvents.Operation> verifiedOperations) {
        this.self = self
        this.verifiedOperations = verifiedOperations

        def outputs = self.statusEvents.findAll { it.event instanceof TestOutputEvent }
        actualOutput = new ArrayList<>(outputs.collect { ((TestOutputDescriptor) it.event.descriptor).message })

        def metadatas = self.statusEvents.findAll { it.event instanceof TestMetadataEvent }.collect { (TestMetadataEvent) it.event }
        actualMetadata = new ArrayList<>(metadatas.collect {
            if (it instanceof TestMetadataEvent && it.respondsTo("getValues")) {
                // Older versions of TAPI (before 9.4.0) have a TestMetadataEvent that has a getValues method
                it.invokeMethod("getValues", null)
            } else if (it instanceof TestKeyValueMetadataEvent) {
                it.values
            } else if (it instanceof TestFileAttachmentMetadataEvent) {
                new FileAttachment(it.file, it.mediaType)
            } else {
                // Don't recognize what kind of metadata this is, just return the event and let checks later fail
                it
            }
        })
    }

    @Override
    void output(String msg) {
        def expectedOutput = msg + SystemProperties.getInstance().lineSeparator
        assert actualOutput.remove(expectedOutput): "expected to find '$msg' in $self"
    }

    @Override
    void metadata(String key, String value) {
        assert actualMetadata.remove(Collections.singletonMap(key, value)): "expected to find $key -> $value in $self. Available: $actualMetadata"
    }

    @Override
    void metadata(Map<String, String> values) {
        assert actualMetadata.remove(values): "expected to find $values in $self"
    }

    @Override
    void fileAttachment(File file, @Nullable String mediaType) {
        assert actualMetadata.removeIf {
            it instanceof FileAttachment && file == it.file && mediaType == it.mediaType
        }
    }

    @Override
    void displayName(String displayName) {
        assert self.descriptor instanceof TestOperationDescriptor : "This only makes sense for tests"
        assert ((TestOperationDescriptor)self.descriptor).testDisplayName == displayName
    }

    @Override
    void nested(String name, @DelegatesTo(value = GroupTestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> assertionSpec) {
        def child = findChild(name)
        assertSpec(child, assertionSpec)
        verifyChild(child)
    }

    @Override
    void test(String name, @DelegatesTo(value = TestEventSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> assertionSpec = {}) {
        def child = findChild(name)
        assertSpec(child, assertionSpec)
        verifyChild(child)
    }

    private ProgressEvents.Operation findChild(String name) {
        // These change name depending on which worker is used
        if (name.contains("Gradle Test Executor")) {
            return self.child { it.matches(/Gradle Test Executor \d+/) }
        } else {
            return self.child(name)
        }
    }

    private void assertSpec(ProgressEvents.Operation child, @DelegatesTo(strategy = Closure.DELEGATE_FIRST) Closure<?> assertionSpec) {
        def spec = new DefaultTestEventSpec(child, verifiedOperations)
        assertionSpec.delegate = spec
        assertionSpec.resolveStrategy = Closure.DELEGATE_FIRST
        assertionSpec()
        spec.assertNothingExtra()
    }

    private void verifyChild(ProgressEvents.Operation child) {
        // If we've made it this far, this operation has been verified
        verifiedOperations << child
    }

    private void assertNothingExtra() {
        assert actualOutput.empty: "unexpected output $actualOutput in $self"
        assert actualMetadata.empty: "unexpected test metadata $actualMetadata in $self"
    }
}
