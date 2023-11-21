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

package org.gradle.nativeplatform.fixtures.app

import org.gradle.integtests.fixtures.SourceFile

/**
 * A Swift app composed of 2 modules, an application and a library, and an optional compile time feature.
 */
class SwiftAppWithLibraryAndOptionalFeature {
    final library = new SwiftGreeterWithOptionalFeature()
    final application = new SwiftMainWithOptionalFeature(library.withFeatureDisabled()) {
        @Override
        List<SourceFile> getFiles() {
            return super.getFiles().collect {
                sourceFile(it.path, it.name, "import Greeter\n${it.content}")
            }
        }
    }

    AppElement withFeatureEnabled() {
        return new SwiftMainWithOptionalFeature(library.withFeatureEnabled()).withFeatureEnabled()
    }

    AppElement withFeatureDisabled() {
        return application.withFeatureDisabled()
    }
}
