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

package org.gradle.test.fixtures.gradle

import groovy.transform.CompileStatic

@CompileStatic
class FileSpec {
    String name
    // Url written in the Gradle Module Metadata
    String url
    // Url where this file will be published.
    // Normally publishUrl is the same as url, but it can be set differently for testing some broken behavior.
    String publishUrl
    String ext = 'jar'

    FileSpec(String name) {
        this(name, name, name)
    }

    FileSpec(String name, String url) {
        this(name, url, url)
    }

    FileSpec(String name, String url, String publishUrl) {
        this.name = name
        this.url = url
        this.publishUrl = publishUrl
    }
}
