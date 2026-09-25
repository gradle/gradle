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

package gradlebuild.docs;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.Sync;
import org.gradle.work.DisableCachingByDefault;

/**
 * Stages the user manual sources into one directory.
 * <p>
 * The staging directory is exposed as an annotated output property, so that providers derived from it
 * carry this task as their producer. With the configuration cache, the Asciidoctor tasks then list the
 * files to convert when they run, instead of when the cache entry is stored, before anything is staged.
 */
@DisableCachingByDefault(because = "Copies files, not worth caching")
public abstract class StageUserManualSources extends Sync {
    @OutputDirectory
    public abstract DirectoryProperty getStagingDirectory();
}
