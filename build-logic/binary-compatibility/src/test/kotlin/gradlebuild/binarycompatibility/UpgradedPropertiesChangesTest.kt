/*
 * Copyright 2023 the original author or authors.
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

import org.junit.Test

class UpgradedPropertiesChangesTest : AbstractBinaryCompatibilityTest() {

    @Test
    fun `should not report binary incompatibility for upgraded properties`() {
        checkBinaryCompatible(
            v1 = {
                withFile(
                    "java/com/example/Task.java",
                    """
                        package com.example;

                        public abstract class Task {
                            public String getSourceCompatibility() {
                                return "";
                            }
                            public void setSourceCompatibility(String value) {
                            }
                        }
                    """
                )
            },
            v2 = {
                withFile(
                    "java/com/example/Task.java",
                    """
                        package com.example;
                        import org.gradle.api.provider.Property;

                        public abstract class Task {
                            public abstract Property<String> getSourceCompatibility();
                        }
                    """
                )
                withFile(
                    "resources/upgraded-properties.json",
                    """
                        [{
                            "containingType": "com.example.Task",
                            "methodName": "getSourceCompatibility",
                            "methodDescriptor": "()Lorg/gradle/api/provider/Property;",
                            "propertyName": "sourceCompatibility",
                            "upgradedMethods": [{
                                "descriptor": "()Ljava/lang/String;",
                                "name": "getSourceCompatibility"
                            }, {
                                "descriptor": "(Ljava/lang/String;)V",
                                "name": "setSourceCompatibility"
                            }]
                        }]
                        """
                )
            }
        ) {
            assertHasNoError()
        }
    }

}
