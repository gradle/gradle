/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Documents a type-erasure heap-pollution surface reachable via {@link LazyPublishArtifact}.
 * <p>
 * The class's {@code provider} field is declared {@code ProviderInternal<File>} but the
 * constructor accepts an arbitrary {@code Provider<?>} and stores it via an unchecked cast.
 * The dispatch inside {@link LazyPublishArtifact#getDelegate()} legitimately handles values
 * of {@code RegularFile}, {@code Directory}, {@code AbstractArchiveTask}, {@code Task}, or
 * arbitrary {@code Object} — none of which are {@code File}. So the internal type declaration
 * lies to any code that reads the field-typed value out again.
 * <p>
 * {@link LazyPublishArtifact#getProvider()} is currently typed {@code ProviderInternal<?>},
 * which prevents implicit widening at call sites — but call sites can (and do) narrow the
 * returned value with an unchecked cast. Both {@code MavenArtifactsFileCollection.classify()}
 * and {@code IvyArtifactsFileCollection.classify()} do exactly that today:
 *
 * <pre>
 * &#64;SuppressWarnings("unchecked")
 * var fileProvider = (ProviderInternal&lt;File&gt;) lazy.getProvider();
 * </pre>
 *
 * This test reproduces that exact pattern. It constructs a {@code LazyPublishArtifact}
 * with a {@code Provider<String>}, mimics the {@code classify()} cast, and reads
 * {@code .get()} into a {@code File} local. The compiler inserts a synthetic
 * {@code checkcast File} bytecode at the assignment, which throws
 * {@link ClassCastException} at runtime because the actual stored value is a String.
 * <p>
 * If the {@link LazyPublishArtifact}'s field is tightened back to
 * {@code ProviderInternal<?>} <strong>and</strong> the classify sites drop their unchecked
 * casts, the heap-pollution surface disappears: callers would then need to demonstrate at
 * their own call sites that the value is actually a {@code File}. This test is the guard
 * that pins the current design as still exhibiting the F3 behaviour.
 */
public class LazyPublishArtifactHeapPollutionTest {

    @Test
    public void classify_cast_pattern_yields_a_provider_whose_get_throws_ClassCastException() {
        // A LazyPublishArtifact legitimately constructed with a provider whose value type
        // is not File. This mirrors what MavenArtifactNotationParserFactory does when a
        // caller passes `artifact(taskProvider)` — the wrapped provider resolves to a Task,
        // not a File. A String here is a minimal fixture; any non-File value type reproduces
        // the same heap pollution.
        LazyPublishArtifact lazy = new LazyPublishArtifact(
            Providers.of("not-a-file"),
            /* version */ null,
            mock(FileResolver.class),
            DefaultTaskDependencyFactory.withNoAssociatedProject()
        );

        // The exact cast pattern used inside MavenArtifactsFileCollection.classify() and
        // IvyArtifactsFileCollection.classify() for their "changing value" branch.
        @SuppressWarnings("unchecked")
        ProviderInternal<File> pretendsToBeFile = (ProviderInternal<File>) lazy.getProvider();

        // The `File _unused = pretendsToBeFile.get();` line compiles because of the declared
        // generic type. At runtime, `.get()` returns the underlying String, and the compiler-
        // inserted `checkcast File` on the assignment throws.
        assertThrows(ClassCastException.class, () -> {
            @SuppressWarnings("unused")
            File unused = pretendsToBeFile.get();
        });
    }
}
