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

package gradlebuild.performance.tasks

import com.google.common.annotations.VisibleForTesting
import groovy.transform.CompileStatic
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

@CompileStatic
abstract class RebaselinePerformanceTests extends SourceTask {

    @Input
    String baseline

    @TaskAction
    void rebaseline() {
        for (file in getSource()) {
            file.text = rebaselineContent(file.text, baseline)
        }
    }

    @Option(option = "baseline", description = "The Gradle version to use as the new baseline for all performance tests.")
    void setBaseline(String baseline) {
        this.baseline = baseline
    }

    @VisibleForTesting
    static String rebaselineContent(String fileContent, String baseline) {
        fileContent.replaceAll('targetVersions = \\[".*"]', "targetVersions = [\"$baseline\"]")
    }
}
