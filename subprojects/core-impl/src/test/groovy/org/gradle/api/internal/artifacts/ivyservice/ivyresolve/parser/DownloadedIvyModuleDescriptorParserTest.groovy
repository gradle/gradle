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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.parser.ParserSettings
import org.apache.ivy.plugins.repository.Resource
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DownloadedIvyModuleDescriptorParserTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir
    final DownloadedIvyModuleDescriptorParser parser = new DownloadedIvyModuleDescriptorParser()

    def "discards the default attribute"() {
        def ivyFile = tmpDir.createFile("ivy.xml")
        ivyFile.text = """<?xml version="1.0" encoding="UTF-8"?>
<ivy-module version="1.0">
    <info organisation="org" module="someModule" revision="1.2" default="true"/>
</ivy-module>
"""
        def url = ivyFile.toURI().toURL()
        ParserSettings settings = new IvySettings()
        Resource resource = Mock()

        when:
        def descriptor = parser.parseDescriptor(settings, url, resource, true)

        then:
        !descriptor.default
    }
}
