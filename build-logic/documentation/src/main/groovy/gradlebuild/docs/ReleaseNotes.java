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
import org.gradle.api.file.RegularFileProperty;

public abstract class ReleaseNotes {
    /**
     * The source markdown file for the release notes.
     */
    public abstract RegularFileProperty getMarkdownFile();

    /**
     * The base CSS file used by all documentation.
     */
    public abstract RegularFileProperty getBaseCssFile();

    /**
     * The release notes specific CSS file
     */
    public abstract RegularFileProperty getReleaseNotesCssFile();

    /**
     * The Javascript embedded in the release notes
     */
    public abstract RegularFileProperty getReleaseNotesJsFile();

    /**
     * The Jquery file to include in the release notes.
     */
    public abstract ConfigurableFileCollection getJquery();

    // TODO: Need staging root property too

    /**
     * The collection of rendered documentation.
     */
    public abstract RegularFileProperty getRenderedDocumentation();
}
