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

package org.gradle.build

class ReproduciblePropertiesWriter {

    /**
     * Writes {@link Map} of data as {@link Properties} to a file, but without including the timestamp comment.
     */
    static void store(Map<String, ?> data, File file, String comment = null) {
        def properties = new Properties()
        data.each { key, value ->
            properties.put(key, value == null ? null : value.toString())
        }
        store(properties, file, comment)
    }

    /**
     * Writes {@link Properties} to a file, but without including the timestamp comment.
     */
    static void store(Properties properties, File file, String comment = null) {
        def sw = new StringWriter()
        properties.store(sw, null)
        String systemLineSeparator = System.lineSeparator()
        String lineSeparator = "\n" // Use LF to have the same result on Windows and on Linux
        def content = sw.toString().split(systemLineSeparator).findAll { !it.startsWith("#") }.join(lineSeparator)
        file.parentFile.mkdirs()
        file.withWriter("8859_1") { BufferedWriter bw ->
            if (comment) {
                bw.write("# ${comment}")
                bw.write(lineSeparator)
            }
            bw.write(content)
        }
    }
}
