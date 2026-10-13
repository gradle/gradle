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

package org.gradle.internal.component.external.model

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.util.Matchers
import spock.lang.Issue
import spock.lang.Specification

class DefaultModuleComponentArtifactIdentifierTest extends Specification {
    def "has useful string representation"() {
        def componentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")

        expect:
        def noClassifier = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "ext")
        noClassifier.displayName == "name-version.ext (group:module:version)"
        noClassifier.toString() == "name-version.ext (group:module:version)"

        def withClassifier = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "ext", 'classifier')
        withClassifier.displayName == "name-version-classifier.ext (group:module:version)"
        withClassifier.toString() == "name-version-classifier.ext (group:module:version)"

        def noExtension = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", null, 'classifier')
        noExtension.displayName == "name-version-classifier (group:module:version)"
        noExtension.toString() == "name-version-classifier (group:module:version)"

        def nameOnly = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", null, null)
        nameOnly.displayName == "name-version (group:module:version)"
        nameOnly.toString() == "name-version (group:module:version)"
    }

    def "has same string representation as a ComponentFileArtifactIdentifier that carries the same information"() {
        def componentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")

        expect:
        def noClassifier = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "ext")
        def noClassifierFileName = new ComponentFileArtifactIdentifier(componentId, "name-version.ext")
        noClassifier.displayName == noClassifierFileName.displayName

        def withClassifier = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "ext", 'classifier')
        def withClassifierFileName = new ComponentFileArtifactIdentifier(componentId, "name-version-classifier.ext")
        withClassifier.displayName == withClassifierFileName.displayName

        def noExtension = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", null, 'classifier')
        def noExtensionFileName = new ComponentFileArtifactIdentifier(componentId, "name-version-classifier")
        noExtension.displayName == noExtensionFileName.displayName

        def nameOnly = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", null, null)
        def nameOnlyFileName = new ComponentFileArtifactIdentifier(componentId, "name-version")
        nameOnly.displayName == nameOnlyFileName.displayName
    }

    //ComponentFileArtifactIdentifier

    def "calculates a destination file name from attributes"() {
        def componentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")

        expect:
        def noClassifier = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "ext")
        noClassifier.destinationFileName == "name-version.ext"

        def withClassifier = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "ext", 'classifier')
        withClassifier.destinationFileName == "name-version-classifier.ext"

        def noExtension = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", null, 'classifier')
        noExtension.destinationFileName == "name-version-classifier"

        def nameOnly = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", null, null)
        nameOnly.destinationFileName == "name-version"
    }

    def "is equal when all attributes and module version are the same"() {
        def componentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        def otherComponentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "2")

        def withClassifier = new DefaultModuleComponentArtifactIdentifier(componentId,  "name", "type", "ext", 'classifier')
        def same = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "ext", 'classifier')
        def differentModule = new DefaultModuleComponentArtifactIdentifier(otherComponentId, "name", "type", "ext", 'classifier')
        def differentName = new DefaultModuleComponentArtifactIdentifier(componentId, "2", "type", "ext", 'classifier')
        def differentType = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "2", "ext", 'classifier')
        def differentExt = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "2", 'classifier')
        def differentAttributes = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", "ext", '2')
        def emptyParts = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", null)
        def emptyPartsSame = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "type", null)

        expect:
        withClassifier Matchers.strictlyEqual(same)
        withClassifier != differentModule
        withClassifier != differentName
        withClassifier != differentType
        withClassifier != differentExt
        withClassifier != differentAttributes
        withClassifier != emptyParts

        emptyParts Matchers.strictlyEqual(emptyPartsSame)
        emptyParts != withClassifier
    }

    def "calculates a file path from attributes"() {
        def componentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")

        expect:
        new DefaultModuleComponentArtifactIdentifier(componentId, name, type, ext, classifier).filePath == filePath

        where:
        name   | type     | ext   | classifier   | filePath
        "name" | "jar"    | "jar" | null         | "name-version.jar"
        "name" | "jar"    | "jar" | "classifier" | "name-version-classifier.jar"
        "name" | "bundle" | "jar" | null         | "bundles/name-version.jar"
        "name" | "jar"    | null  | null         | "jars/name-version"
    }

    @Issue("https://github.com/gradle/gradle/issues/38831")
    def "is equal to a file identifier only with the same component, file path and destination file name"() {
        def componentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")
        def otherComponentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "2")

        def id = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "jar", "jar", "classifier")
        def sameFile = new ModuleComponentFileArtifactIdentifier(componentId, "name-version-classifier.jar")
        def samePathDifferentName = new ModuleComponentFileArtifactIdentifier(componentId, "other.jar", "name-version-classifier.jar")
        def sameNameDifferentPath = new ModuleComponentFileArtifactIdentifier(componentId, "name-version-classifier.jar", "dir/name-version-classifier.jar")
        def differentModule = new ModuleComponentFileArtifactIdentifier(otherComponentId, "name-version-classifier.jar")

        expect:
        id Matchers.strictlyEqual(sameFile)
        id != samePathDifferentName
        id != sameNameDifferentPath
        id != differentModule
    }

    def "is equal when different attributes produce the same file path"() {
        def componentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")

        expect:
        def id = new DefaultModuleComponentArtifactIdentifier(componentId, "name", type, ext, classifier)
        def other = new DefaultModuleComponentArtifactIdentifier(componentId, "name", otherType, otherExt, otherClassifier)
        id.name != other.name
        id.filePath == filePath
        id Matchers.strictlyEqual(other)

        where:
        type     | ext      | classifier | otherType | otherExt | otherClassifier | filePath
        "jar"    | "jar"    | null       | "jar"     | "jar"    | ""              | "name-version.jar"
        "jar"    | null     | null       | "jar"     | ""       | null            | "jars/name-version"
        "gz"     | "gz"     | "x.tar"    | "tar.gz"  | "tar.gz" | "x"             | "name-version-x.tar.gz"
    }

    def "destination file name keeps path separators from the artifact name"() {
        def componentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")

        expect:
        def id = new DefaultModuleComponentArtifactIdentifier(componentId, "name", type, ext, null)
        id.filePath == filePath
        id.destinationFileName == destinationFileName

        where:
        type      | ext       | filePath                | destinationFileName
        "/../x"   | "/../x"   | "name-version./../x"    | "name-version./../x"
        "jar"     | "jar/.."  | "jars/name-version.jar/.." | "name-version.jar/.."
    }

    def "artifacts that differ only by type are not equal"() {
        def componentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "version")

        def jar = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "jar", "jar")
        def bundle = new DefaultModuleComponentArtifactIdentifier(componentId, "name", "bundle", "jar")

        expect:
        jar.destinationFileName == bundle.destinationFileName
        jar != bundle
    }
}
