/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory

import org.gradle.api.internal.changedetection.state.AbstractSnapshotNormalizationStrategyTest
import org.gradle.api.internal.changedetection.state.ClasspathSnapshotNormalizationStrategy

class ClasspathSnapshotNormalizationStrategyTest extends AbstractSnapshotNormalizationStrategyTest {

    def "sensitivity CLASSPATH"() {
        def snapshots = normalizeWith ClasspathSnapshotNormalizationStrategy.INSTANCE
        expect:
        snapshots[file("dir/libs/library-a.jar")]      == "IGNORED"
        snapshots[file("dir/libs/library-b.jar")]      == "IGNORED"
        snapshots[file("dir/resources/input.txt")]     == "input.txt"
        snapshots[file("dir/resources/a")]             == "a"
        snapshots[file("dir/resources/a/input-1.txt")] == "a/input-1.txt"
        snapshots[file("dir/resources/b")]             == "b"
        snapshots[file("dir/resources/b/input-2.txt")] == "b/input-2.txt"
    }

}
