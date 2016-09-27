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
package org.gradle.jvm;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * An exploded binary containing resources and compiled class files.
 */
// TODO: maybe we need to allow additional dirs like SourceSetOutput does
// (esp. for backwards compatibility). Wonder if it's still necessary to distinguish
// between classes and resources dirs, instead of just maintaining a collection of dirs.
// As far as generated resources are concerned, it might be better to model
// them as an additional (Buildable) ResourceSet.
@Incubating @HasInternalProtocol
public interface ClassDirectoryBinarySpec extends JvmBinarySpec {
}
