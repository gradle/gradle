/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.signing

import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact

class SignOperationSpec extends SigningProjectSpec {
    
    def input1
    def input2
    def input1Artifact
    def input2Artifact
    def output1
    def output2
    
    def setup() {
        applyPlugin()
        addSigningProperties()
        
        input1 = getResourceFile("1.txt")
        input1Artifact = new DefaultPublishArtifact(input1.name, "Text File", "txt", null, null, input1)
        output1 = signing.signatureType.fileFor(input1)
        
        input2 = getResourceFile("2.txt")
        output2 = signing.signatureType.fileFor(input2)
        input2Artifact = new DefaultPublishArtifact(input2.name, "Text File", "txt", null, null, input2)
        
        [output1, output2].each { output ->
            assert !output.exists() || output.delete()
        }
        
        assert input1.text && input2.text  // don't care what it is, just need some
    }
    
    def "sign single file with defaults"() {
        when:
        def operation = signing.sign(input1)
        
        then:
        output1.exists()
        output1 == operation.singleSignature.file
    }
    
    def "sign single artifact with defaults"() {
        when:
        def operation = signing.sign(input1Artifact)
        
        then:
        output1.exists()
        output1 == operation.singleSignature.file
    }
    
    def "sign multiple files with defaults"() {
        when:
        def operation = signing.sign(input1, input2)
        
        then:
        output1.exists() && output2.exists()
        [input1, input2] == operation.filesToSign.files.toList()[0..1]
        [output1, output2] == operation.signatureFiles.files.toList()[0..1]
        [output1, output2] == operation.signatures*.file.toList()
    }

    def "sign multiple artifacts with defaults"() {
        when:
        def operation = signing.sign(input1Artifact, input2Artifact)
        
        then:
        output1.exists() && output2.exists()
        [input1, input2] == operation.filesToSign.files.toList()[0..1]
        [output1, output2] == operation.signatureFiles.files.toList()[0..1]
        [output1, output2] == operation.signatures*.file.toList()
    }
    
}