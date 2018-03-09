/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.file.copy

import org.gradle.api.file.CopySpec
import org.gradle.api.file.RelativePath

trait CopySpecTestSpec {

    abstract CopySpecInternal getSpec()

    DefaultResolvedCopySpec resolvedSpec() {
        return spec.resolveAsRoot().spec
    }

    DefaultResolvedCopySpec resolvedChild(int index = 0) {
        return spec.resolveAsRoot().children[index].spec
    }

    List<RelativePath> resolvedDestPaths() {
        List<RelativePath> paths = []
        spec.resolveAsRoot().walk { DefaultResolvedCopySpec spec ->
            paths.add spec.destPath
        }
        return paths
    }

    DefaultCopySpec unpackWrapper(CopySpec copySpec) {
        (copySpec as CopySpecWrapper).delegate as DefaultCopySpec
    }

    RelativePath relativeDirectory(String... segments) {
        new RelativePath(false, segments)
    }

    RelativePath relativeFile(String segments) {
        RelativePath.parse(true, segments)
    }
}
