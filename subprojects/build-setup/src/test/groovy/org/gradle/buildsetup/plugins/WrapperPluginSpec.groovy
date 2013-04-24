/*
 * Copyright 2013 the original author or authors.
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



package org.gradle.buildsetup.plugins

import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.util.HelperUtil
import spock.lang.Specification

class WrapperPluginSpec extends Specification {
    def project = HelperUtil.createRootProject()

    def "adds 'wrapper' task"() {
        when:
        project.plugins.apply WrapperPlugin

        then:
        project.tasks.wrapper instanceof Wrapper
        project.tasks.wrapper.group == BuildSetupPlugin.GROUP
    }
}
