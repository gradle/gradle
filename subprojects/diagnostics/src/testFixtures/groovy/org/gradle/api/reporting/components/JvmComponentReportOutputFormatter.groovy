/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.reporting.components

import org.gradle.api.JavaVersion


class JvmComponentReportOutputFormatter extends ComponentReportOutputFormatter {
    @Override
    String transform(String original) {
        def java = JavaVersion.current()
        return super.transform(
            original
                .replace("java7", "Java SE " + java.majorVersion)
                .replace("JDK 7 (1.7)", "JDK " + java.majorVersion + " (" + java + ")")
        )
    }
}
