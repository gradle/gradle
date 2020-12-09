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
import org.gradle.api.file.RegularFileProperty;

/**
 * The DSL reference for this documentation.  These are higher-level than Javadoc.
 */
public abstract class DslReference {
    /**
     * The root of the DSL documentation.  This is the source of the DSL XML currently.
     */
    public abstract DirectoryProperty getRoot();

    /**
     * The stylesheet directory used by the DSL reference documentation.
     */
    public abstract DirectoryProperty getStylesheetDirectory();

    /**
     * The stylesheet used by the DSL reference to highlight code snippets.
     */
    public abstract RegularFileProperty getHighlightStylesheet();

    /**
     * Resources to include with the generated documentation.
     */
    public abstract ConfigurableFileCollection getResources();

    /**
     * Location to stage the intermediate documentation. This is like a working directory.
     */
    public abstract DirectoryProperty getStagingRoot();

    public abstract RegularFileProperty getGeneratedMetaDataFile();

    /**
     * The fully rendered documentation with all of its necessary resources.
     */
    public abstract ConfigurableFileCollection getRenderedDocumentation();
}
