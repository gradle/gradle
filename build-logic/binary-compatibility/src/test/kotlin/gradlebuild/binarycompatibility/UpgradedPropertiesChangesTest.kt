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
    fun `should report binary incompatibility for upgraded property without any metadata`() {
        checkNotBinaryCompatible(
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
            }
        ) {
            assertHasErrors(
                "Method com.example.Task.getSourceCompatibility(): Is not binary compatible." to listOf("Method return type has changed", "Method is now abstract"),
                removed("Method", "Task.setSourceCompatibility(java.lang.String)"),
            )
        }
    }

    @Test
    fun `should automatically accept binary incompatibilities for upgraded properties`() {
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
                            "replacedAccessors": [{
                                "binaryCompatibility": "ACCESSORS_REMOVED",
                                "descriptor": "()Ljava/lang/String;",
                                "name": "getSourceCompatibility"
                            }, {
                                "binaryCompatibility": "ACCESSORS_REMOVED",
                                "descriptor": "(Ljava/lang/String;)V",
                                "name": "setSourceCompatibility"
                            }]
                        }]
                        """
                )
            }
        ) {
            assertHasNoError()
            assertHasAccepted(
                "Method com.example.Task.getSourceCompatibility(): Is not binary compatible. Reason for accepting this: Upgraded property" to listOf("Method return type has changed", "Method is now abstract"),
                "Method com.example.Task.setSourceCompatibility(java.lang.String): Is not binary compatible. Reason for accepting this: Upgraded property" to listOf("Method has been removed")
            )
        }
    }

    @Test
    fun `should automatically accept binary incompatibilities for boolean upgraded properties`() {
        checkBinaryCompatible(
            v1 = {
                withFile(
                    "java/com/example/Task.java",
                    """
                        package com.example;

                        public abstract class Task {
                            public boolean isFailOnError() {
                                return false;
                            }
                            public void setFailOnError(boolean b) {
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
                            /**
                             * @since 2.0
                             */
                            public abstract Property<Boolean> getFailOnError();
                        }
                    """
                )
                withFile(
                    "resources/upgraded-properties.json",
                    """
                        [{
                            "containingType": "com.example.Task",
                            "methodName": "getFailOnError",
                            "methodDescriptor": "()Lorg/gradle/api/provider/Property;",
                            "propertyName": "failOnError",
                            "replacedAccessors": [{
                                "binaryCompatibility": "ACCESSORS_REMOVED",
                                "descriptor": "()Z",
                                "name": "isFailOnError"
                            }, {
                                "binaryCompatibility": "ACCESSORS_REMOVED",
                                "descriptor": "(Z)V",
                                "name": "setFailOnError"
                            }]
                        }]
                        """
                )
            }
        ) {
            assertHasNoError()
            assertHasAccepted(
                "Method com.example.Task.getFailOnError(): Is not annotated with @Incubating. Reason for accepting this: Upgraded property" to listOf("Method added to public class", "Abstract method has been added to this class"),
                "Method com.example.Task.isFailOnError(): Is not binary compatible. Reason for accepting this: Upgraded property" to listOf("Method has been removed"),
                "Method com.example.Task.setFailOnError(boolean): Is not binary compatible. Reason for accepting this: Upgraded property" to listOf("Method has been removed")
            )
        }
    }

    @Test
    fun `should report an error if newly added method does not have @since`() {
        checkNotBinaryCompatible(
            v1 = {
                withFile(
                    "java/com/example/Task.java",
                    """
                        package com.example;

                        public abstract class Task {
                            public boolean isFailOnError() {
                                return false;
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
                            public abstract Property<Boolean> getFailOnError();
                        }
                    """
                )
                withFile(
                    "resources/upgraded-properties.json",
                    """
                        [{
                            "containingType": "com.example.Task",
                            "methodName": "getFailOnError",
                            "methodDescriptor": "()Lorg/gradle/api/provider/Property;",
                            "propertyName": "failOnError",
                            "replacedAccessors": [{
                                "binaryCompatibility": "ACCESSORS_REMOVED",
                                "descriptor": "()Z",
                                "name": "isFailOnError"
                            }]
                        }]
                        """
                )
            }
        ) {
            assertHasErrors("Method com.example.Task.getFailOnError(): Is not annotated with @since 2.0.")
            assertHasAccepted(
                "Method com.example.Task.getFailOnError(): Is not annotated with @Incubating. Reason for accepting this: Upgraded property" to listOf("Method added to public class", "Abstract method has been added to this class"),
                "Method com.example.Task.isFailOnError(): Is not binary compatible. Reason for accepting this: Upgraded property" to listOf("Method has been removed"),
            )
        }
    }

    @Test
    fun `should fail if some method was upgraded but it was not actually changed`() {
        checkBinaryCompatibleFailsWithoutReport(
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
                            public void setSourceCompatibility(String value) {
                            }
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
                            "replacedAccessors": [{
                                "binaryCompatibility": "ACCESSORS_REMOVED",
                                "descriptor": "()Ljava/lang/String;",
                                "name": "getSourceCompatibility"
                            }, {
                                "binaryCompatibility": "ACCESSORS_REMOVED",
                                "descriptor": "(Ljava/lang/String;)V",
                                "name": "setSourceCompatibility"
                            }]
                        }]
                        """
                )
            }
        ) {
            assertOutputContains("The following accessors were upgraded, but didn't match any removed/changed method:\n\ncom.example.Task#setSourceCompatibility(Ljava/lang/String;)V")
        }
    }

    @Test
    fun `should not fail if some method was upgraded and not removed but marked as kept`() {
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
                            public void setSourceCompatibility(String value) {
                            }
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
                            "replacedAccessors": [{
                                "binaryCompatibility": "ACCESSORS_REMOVED",
                                "descriptor": "()Ljava/lang/String;",
                                "name": "getSourceCompatibility"
                            }, {
                                "binaryCompatibility": "ACCESSORS_KEPT",
                                "descriptor": "(Ljava/lang/String;)V",
                                "name": "setSourceCompatibility"
                            }]
                        }]
                        """
                )
            }
        ) {
            assertHasNoError()
            assertHasAccepted(
                "Method com.example.Task.getSourceCompatibility(): Is not binary compatible. Reason for accepting this: Upgraded property" to listOf("Method return type has changed", "Method is now abstract")
            )
        }
    }

    @Test
    fun `should not fail if some method was upgraded and removed but marked as kept`() {
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
                            public void setSourceCompatibility(String value) {
                            }
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
                            "replacedAccessors": [{
                                "binaryCompatibility": "ACCESSORS_REMOVED",
                                "descriptor": "()Ljava/lang/String;",
                                "name": "getSourceCompatibility"
                            }, {
                                "binaryCompatibility": "ACCESSORS_KEPT",
                                "descriptor": "(Ljava/lang/String;)V",
                                "name": "setSourceCompatibility"
                            }]
                        }]
                        """
                )
            }
        ) {
            assertHasNoError()
            assertHasAccepted(
                "Method com.example.Task.getSourceCompatibility(): Is not binary compatible. Reason for accepting this: Upgraded property" to listOf("Method return type has changed", "Method is now abstract")
            )
        }
    }

    @Test
    fun `should accept upgraded property that used different name`() {
        checkBinaryCompatible(
            v1 = {
                withFile(
                    "java/com/example/Task.java",
                    """
                        package com.example;

                        public abstract class Task {
                            public String getSourceCompat() {
                                return "";
                            }
                            public void setSourceCompat(String value) {
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
                           /**
                             * @since 2.0
                             */
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
                            "replacedAccessors": [{
                                "binaryCompatibility": "ACCESSORS_REMOVED",
                                "descriptor": "()Ljava/lang/String;",
                                "name": "getSourceCompat"
                            }, {
                                "binaryCompatibility": "ACCESSORS_REMOVED",
                                "descriptor": "(Ljava/lang/String;)V",
                                "name": "setSourceCompat"
                            }]
                        }]
                        """
                )
            }
        ) {
            assertHasNoError()
            assertHasAccepted(
                "Method com.example.Task.getSourceCompat(): Is not binary compatible. Reason for accepting this: Upgraded property" to listOf("Method has been removed"),
                "Method com.example.Task.getSourceCompatibility(): Is not annotated with @Incubating. Reason for accepting this: Upgraded property" to listOf("Method added to public class", "Abstract method has been added to this class"),
                "Method com.example.Task.setSourceCompat(java.lang.String): Is not binary compatible. Reason for accepting this: Upgraded property" to listOf("Method has been removed")
            )
        }
    }
}
