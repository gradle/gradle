/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

class OutputPathNormalizationStrategyTest extends AbstractPathNormalizationStrategyTest {

    def "output file normalization"() {
        def snapshots = normalizeWith OutputPathNormalizationStrategy.instance
        expect:
        snapshots[file("dir/libs/library-a.jar")] == file("dir/libs/library-a.jar").absolutePath
        snapshots[file("dir/libs/library-b.jar")] == file("dir/libs/library-b.jar").absolutePath
        snapshots[file("dir/resources/input.txt")] == file("dir/resources/input.txt").absolutePath
        snapshots[file("dir/resources/a")] == file("dir/resources/a").absolutePath
        snapshots[file("dir/resources/a/input-1.txt")] == file("dir/resources/a/input-1.txt").absolutePath
        snapshots[file("dir/resources/b")] == file("dir/resources/b").absolutePath
        snapshots[file("dir/resources/b/input-2.txt")] == file("dir/resources/b/input-2.txt").absolutePath
        snapshots[file("empty-dir")] == file("empty-dir").absolutePath
        snapshots[file("missing-file")] == "NO SNAPSHOT"
    }
}
