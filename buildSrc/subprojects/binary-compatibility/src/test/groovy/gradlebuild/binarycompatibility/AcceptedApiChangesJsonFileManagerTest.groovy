/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.binarycompatibility

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Subject

class AcceptedApiChangesJsonFileManagerTest extends Specification {

    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    @Subject AcceptedApiChangesJsonFileManager jsonFileManager = new AcceptedApiChangesJsonFileManager()

    def jsonFile

    def setup() {
        jsonFile = temporaryFolder.newFile('acceptedChanges.json')
    }

    def "can clean existing API changes"() {
        given:
        jsonFile << existingAcceptedApiChanges()

        when:
        jsonFileManager.emptyAcceptedApiChanges(jsonFile)

        then:
        jsonFile.text == nonExistentAcceptedApiChanges()
    }

    def "can clean non-existing API changes"() {
        given:
        jsonFile << nonExistentAcceptedApiChanges()

        when:
        jsonFileManager.emptyAcceptedApiChanges(jsonFile)

        then:
        jsonFile.text == nonExistentAcceptedApiChanges()
    }

    static String existingAcceptedApiChanges() {
        """
            {
                "acceptedApiChanges": [
                    {
                        "type": "org.gradle.api.plugins.quality.Checkstyle",
                        "member": "Method org.gradle.api.plugins.quality.Checkstyle.getInstantiator()",
                        "changes": ["Method has been removed"],
                        "acceptation": "use ObjectFactory instead"
                    },
                    {
                        "type": "org.gradle.api.plugins.quality.CodeNarc",
                        "member": "Method org.gradle.api.plugins.quality.CodeNarc.getInstantiator()",
                        "changes": ["Method has been removed"],
                        "acceptation": "use ObjectFactory instead"
                    }
                ]
            }
        """
    }

    static String nonExistentAcceptedApiChanges() {
        """{
    "acceptedApiChanges": []
}"""
    }
}
