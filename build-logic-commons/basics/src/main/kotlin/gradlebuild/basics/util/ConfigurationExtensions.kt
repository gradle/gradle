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

package gradlebuild.basics.util

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider
import java.io.File

fun Configuration.getSingleFileProvider(): Provider<File> {
    val name = this.name
    return incoming.files.elements.map {
        require(it.size <= 1) {
            "Expected at most one file in configuration '$name' but found: $it"
        }
        it.firstOrNull()?.asFile
    }
}

fun NamedDomainObjectProvider<Configuration>.getSingleFileProvider(): Provider<File> {
    return this.flatMap { configuration ->
        configuration.getSingleFileProvider()
    }
}
