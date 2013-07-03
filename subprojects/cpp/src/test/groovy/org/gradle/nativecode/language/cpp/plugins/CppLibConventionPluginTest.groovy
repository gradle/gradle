/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.nativecode.language.cpp.plugins

import org.gradle.util.HelperUtil
import spock.lang.Specification

class CppLibConventionPluginTest extends Specification {
    final def project = HelperUtil.createRootProject()

    def "adds and configures main library"() {
        given:
        project.plugins.apply(CppLibConventionPlugin)

        expect:
        def library = project.libraries.main
        def sourceSet = project.sources.main.cpp
        library.baseName == project.name
        library.source as List == [sourceSet]
    }
}
