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
package gradlebuild

import gradlebuild.basics.capitalize
import gradlebuild.samples.tasks.GenerateSample
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.buildinit.plugins.internal.modifiers.Language.CPP
import org.gradle.buildinit.plugins.internal.modifiers.Language.GROOVY
import org.gradle.buildinit.plugins.internal.modifiers.Language.JAVA
import org.gradle.buildinit.plugins.internal.modifiers.Language.KOTLIN
import org.gradle.buildinit.plugins.internal.modifiers.Language.SCALA
import org.gradle.buildinit.plugins.internal.modifiers.Language.SWIFT
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import org.gradle.docs.samples.Dsl

plugins {
    id("org.gradle.samples")
}
