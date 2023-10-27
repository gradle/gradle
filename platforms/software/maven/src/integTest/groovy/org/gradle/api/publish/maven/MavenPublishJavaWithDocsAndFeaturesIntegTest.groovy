/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.publish.maven

import org.gradle.test.fixtures.archive.ZipTestFixture
import org.gradle.test.fixtures.maven.MavenJavaModule

class MavenPublishJavaWithDocsAndFeaturesIntegTest extends AbstractMavenPublishJavaIntegTest {

    boolean withDocs() {
        true
    }

    List<String> features() {
        [MavenJavaModule.MAIN_FEATURE, "feature2", "feature3"]
    }

    def "creates separate javadoc artifacts for the features"() {
        features().each { featureName ->
            def sourceSetName = featureName == MavenJavaModule.MAIN_FEATURE ? MavenJavaModule.MAIN_FEATURE : "${featureName}SourceSet"
            def className = classNameForFeature(featureName)
            file("src/${sourceSetName}/java/${className}.java").text = """
                /**
                 * This is my awesome implementation of the ${featureName} feature
                 */
                public class ${className} {
                }
            """
        }
        createBuildScripts("""
            publishing {
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
        """)

        when:
        // Run all javadoc tasks before the JAR tasks to expose that they output to the same location
        run "javadoc", "feature2SourceSetJavadoc", "feature3SourceSetJavadoc", "publish"

        then:
        javaLibrary.assertPublished()
        features().each { featureName ->
            def featurePostFix = featureName == MavenJavaModule.MAIN_FEATURE ? '' : "-${featureName}"
            def javadocFile = file("build/libs/${javaLibrary.artifactId}-${javaLibrary.version}${featurePostFix}-javadoc.jar")
            assert javadocFile.isFile()
            def javadocContents = new ZipTestFixture(javadocFile)
            javadocContents.assertContainsFile("${classNameForFeature(featureName)}.html")
            features().findAll { it != featureName }.each { otherFeatureName ->
                javadocContents.assertNotContainsFile("${classNameForFeature(otherFeatureName)}.html")
            }
        }
    }

    private static String classNameForFeature(String featureName) {
        "MyClassFor${featureName.capitalize()}"
    }

}
