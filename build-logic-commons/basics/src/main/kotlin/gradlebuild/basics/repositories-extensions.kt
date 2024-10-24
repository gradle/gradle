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

package gradlebuild.basics

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.provider.Property
import java.net.URI


fun RepositoryHandler.googleApisJs() {
    ivy {
        name = "googleApisJs"
        val uri = URI.create("https://ajax.googleapis.com/ajax/libs")
        // TODO: Remove after Gradle 9.0
        @Suppress("INCOMPATIBLE_TYPES")
        if (this.url is Property<*>) {
            @Suppress("USELESS_CAST")
            val urlProperty = url as Property<URI>
            urlProperty.set(uri)
        } else {
            @Suppress("deprecation")
            val setter = this.javaClass.getMethod("setUrl", URI::class.java)
            setter.invoke(this, uri)
        }
        patternLayout {
            artifact("[organization]/[revision]/[module].[ext]")
            ivy("[organization]/[revision]/[module].xml")
        }
        metadataSources {
            artifact()
        }
    }
}
