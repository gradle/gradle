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

package org.gradle.api.initialization.internal;

import org.gradle.api.file.ProjectLayout;
import org.gradle.api.initialization.SharedModelDefaults;

public interface SharedModelDefaultsInternal extends SharedModelDefaults {

    /**
     * Runs {@code action} with {@code projectLayout} bound as the current project layout for any
     * {@link org.gradle.api.initialization.SharedModelDefaults#getLayout()} calls made on the same
     * thread during execution. Restores the previously bound layout (which may be absent) on
     * completion, including when {@code action} throws.
     *
     * <p>Nested calls preserve the outer binding for code that runs after the nested action
     * returns, so that recursive defaults application (e.g. when a defaults action triggers
     * application of another project feature whose own defaults are applied) does not strip the
     * outer scope of its layout binding.</p>
     *
     * @param projectLayout the layout to bind for the duration of {@code action}
     * @param action the action to run while {@code projectLayout} is bound
     */
    void withProjectLayout(ProjectLayout projectLayout, Runnable action);

    /**
     * Processes all registered defaults.  This should be called after all settings evaluated hooks have been applied
     * but before build script evaluation starts.
     */
    void processRegistrations();
}
