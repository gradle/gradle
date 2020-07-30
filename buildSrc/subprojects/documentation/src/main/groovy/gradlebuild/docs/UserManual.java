/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.docs;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;

/**
 * Configuration for user manual documentation
 */
public abstract class UserManual {
    /**
     * The root of the user manual documentation.  This is the source of the adoc files.
     */
    public abstract DirectoryProperty getRoot();

    /**
     * Source of snippets that can be inserted into the user manual
     */
    public abstract DirectoryProperty getSnippets();

    /**
     * Source of samples that can be inserted into the user manual
     *
     */
    public abstract DirectoryProperty getSamples();

    /**
     * Working directory for staging directory for intermediate user manual files
     */
    public abstract DirectoryProperty getStagingRoot();

    public abstract DirectoryProperty getStagedDocumentation();

    /**
     * Additional resources to include in the final docs
     */
    public abstract ConfigurableFileCollection getResources();

    /**
     * A collection of the final rendered user manual
     */
    public abstract ConfigurableFileCollection getRenderedDocumentation();
}
