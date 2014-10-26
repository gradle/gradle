/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

/**
 * DO NOT REMOVE.
 *
 * @deprecated This is here because tasks implemented in Groovy that are compiled against older versions of Gradle have this type baked into their byte-code, and cannot be loaded if it's not found.
 */
@Deprecated
public interface GroovyJavaJointCompiler {
}
