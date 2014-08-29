/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.fixtures

class UrlValidator {

    static void available(String theUrl, String application = null, int timeout = 30000) {
        URL url = new URL(theUrl)
        long expiry = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() <= expiry) {
            try {
                url.text
                return
            } catch (IOException e) {
                // continue
            }
            Thread.sleep(200)
        }
        throw new RuntimeException(String.format("Timeout waiting for %s to become available.", application!=null ? application : theUrl));
    }
}
