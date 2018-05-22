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

package org.gradle.integtests.samples.java9plus

import org.gradle.api.Transformer

class JavadocWarningsOutputFormatter implements Transformer<String, String> {
    @Override
    String transform(String s) {
        // javadoc: warning - You have not specified the version of HTML to use.
        return s.replace('> Task :javadoc\n','\n> Task :javadoc\n1 warning\n\n')
    }
}
