/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.jvm.inspection;

import org.gradle.api.NonNullApi;

/**
 * Represents something needed in a Java installation.
 */
@NonNullApi
public enum JavaInstallationCapability {
    /**
     * The installation has a Java compiler. This is not present for JREs.
     */
    JAVA_COMPILER,
    /**
     * The installation has the Javadoc tool. This is not present for JREs.
     */
    JAVADOC_TOOL,
    /**
     * The installation uses the J9 virtual machine. This is only present for IBM J9 JVMs.
     */
    J9_VIRTUAL_MACHINE
}
