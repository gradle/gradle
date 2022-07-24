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

package org.gradle.ide.visualstudio.tasks.internal

import groovy.xml.XmlParser
import org.gradle.api.Transformer
import org.gradle.internal.xml.XmlTransformer
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class VisualStudioFiltersFileTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    Transformer<String, File> fileNameTransformer = { it.name } as Transformer<String, File>
    def filtersFile = new VisualStudioFiltersFile(new XmlTransformer(), fileNameTransformer)

    def "empty filters file"() {
        when:
        filtersFile.loadDefaults()

        then:
        Node sourceFiles = itemGroup('Filters').Filter.find({it.'@Include' == 'Source Files'}) as Node
        sourceFiles.Extensions[0].text() == 'cpp;c;cc;cxx;def;odl;idl;hpj;bat;asm;asmx'

        Node headerFiles = itemGroup('Filters').Filter.find({it.'@Include' == 'Header Files'}) as Node
        headerFiles.Extensions[0].text() == 'h;hpp;hxx;hm;inl;inc;xsd'

        Node resourceFiles = itemGroup('Filters').Filter.find({it.'@Include' == 'Resource Files'}) as Node
        resourceFiles.Extensions[0].text() == 'rc;ico;cur;bmp;dlg;rc2;rct;bin;rgs;gif;jpg;jpeg;jpe;resx;tiff;tif;png;wav'

        and:
        itemGroup('Sources').children().isEmpty()
        itemGroup('Headers').children().isEmpty()
    }

    def "adds sources and header files"() {
        when:
        filtersFile.loadDefaults()

        and:
        filtersFile.addSource(file("sourceOne"))
        filtersFile.addSource(file("sourceTwo"))

        filtersFile.addHeader(file("headerOne"))
        filtersFile.addHeader(file("headerTwo"))

        then:
        assert sourceFile(0) == "sourceOne"
        assert sourceFile(1) == "sourceTwo"

        assert headerFile(0) == "headerOne"
        assert headerFile(1) == "headerTwo"
    }

    private String sourceFile(int index) {
        def source = itemGroup('Sources').ClCompile[index]
        assert source.Filter[0].text() == 'Source Files'
        return source.'@Include'
    }

    private String headerFile(int index) {
        def header = itemGroup('Headers').ClInclude[index]
        assert header.Filter[0].text() == 'Header Files'
        return header.'@Include'
    }

    private Node itemGroup(String label) {
        return filtersXml.ItemGroup.find({it.'@Label' == label}) as Node
    }

    private def getFiltersXml() {
        return new XmlParser().parse(filtersFileContent)
    }

    private TestFile getFiltersFileContent() {
        def file = testDirectoryProvider.testDirectory.file("filters.xml")
        filtersFile.store(file)
        return file
    }

    private TestFile file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }
}
