/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.build.docs.dsl.javadoc

import org.gradle.build.docs.dsl.XmlSpecification
import org.gradle.build.docs.dsl.model.ClassMetaData
import org.gradle.build.docs.model.ClassMetaDataRepository

class JavadocLinkConverterTest extends XmlSpecification {
    final ClassMetaDataRepository<ClassMetaData> metaDataRepository = Mock()
    final ClassMetaData classMetaData = Mock()
    final JavadocLinkConverter converter = new JavadocLinkConverter(document, metaDataRepository)

    def resolvesFullyQualifiedClassName() {
        when:
        def link = converter.resolve('org.gradle.SomeClass', classMetaData)

        then:
        format(link) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <apilink class="org.gradle.SomeClass"/>
</root>
'''
        _ * metaDataRepository.find('org.gradle.SomeClass') >> classMetaData
    }

    def resolvesUnknownFullyQualifiedClassName() {
        when:
        def link = converter.resolve('org.gradle.SomeClass', classMetaData)

        then:
        format(link) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>!!UNKNOWN LINK org.gradle.SomeClass!!</root>
'''
    }

    def resolvesImportedClassInSamePackage() {
        when:
        def link = converter.resolve('SomeClass', classMetaData)

        then:
        format(link) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <apilink class="org.gradle.SomeClass"/>
</root>
'''
        _ * classMetaData.imports >> []
        _ * classMetaData.className >> 'org.gradle.ImportingClass'
        _ * metaDataRepository.find('org.gradle.SomeClass') >> classMetaData
    }

    def resolvesImportedClass() {
        when:
        def link = converter.resolve('SomeClass', classMetaData)

        then:
        format(link) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <apilink class="org.gradle.SomeClass"/>
</root>
'''
        _ * classMetaData.imports >> ['org.gradle.SomeClass']
    }

    def resolvesClassInImportedPackage() {
        when:
        def link = converter.resolve('SomeClass', classMetaData)

        then:
        format(link) == '''<?xml version="1.0" encoding="UTF-8"?>
<root>
  <apilink class="org.gradle.SomeClass"/>
</root>
'''
        _ * classMetaData.imports >> ['org.gradle.*']
        _ * metaDataRepository.find('org.gradle.SomeClass') >> classMetaData
    }
}
