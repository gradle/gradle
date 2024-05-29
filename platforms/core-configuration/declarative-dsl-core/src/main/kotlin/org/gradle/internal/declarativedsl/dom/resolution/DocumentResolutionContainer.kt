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

package org.gradle.internal.declarativedsl.dom.resolution

import org.gradle.internal.declarativedsl.dom.DocumentResolution.DocumentNodeResolution
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ElementResolution
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ErrorResolution
import org.gradle.internal.declarativedsl.dom.DocumentResolution.PropertyResolution
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ValueNodeResolution
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ValueNodeResolution.LiteralValueResolved
import org.gradle.internal.declarativedsl.dom.DocumentResolution.ValueNodeResolution.ValueFactoryResolution
import org.gradle.internal.declarativedsl.dom.data.NodeDataContainer
import org.gradle.internal.declarativedsl.dom.data.ValueDataContainer


interface DocumentResolutionContainer :
    NodeDataContainer<DocumentNodeResolution, ElementResolution, PropertyResolution, ErrorResolution>,
    ValueDataContainer<ValueNodeResolution, ValueFactoryResolution, LiteralValueResolved>


