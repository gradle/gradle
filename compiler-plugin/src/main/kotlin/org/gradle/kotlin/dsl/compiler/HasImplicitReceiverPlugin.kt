/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.kotlin.dsl.compiler

import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.com.intellij.mock.MockProject
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.extensions.AnnotationBasedExtension
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.load.java.sam.SamWithReceiverResolver
import org.jetbrains.kotlin.psi.KtModifierListOwner


object HasImplicitReceiverPlugin {

    fun apply(project: Project) {
        StorageComponentContainerContributor.registerExtension(project, ComponentContributor)
    }

    private
    object ComponentContributor : StorageComponentContainerContributor {

        override fun onContainerComposed(container: ComponentProvider, moduleInfo: ModuleInfo?) {
            container.get<SamWithReceiverResolver>().registerExtension(SamWithReceiverResolverExtension)
        }
    }

    private
    object SamWithReceiverResolverExtension : SamWithReceiverResolver.Extension, AnnotationBasedExtension {

        override fun getAnnotationFqNames(modifierListOwner: KtModifierListOwner?) =
            listOf("org.gradle.api.HasImplicitReceiver")

        override fun shouldConvertFirstSamParameterToReceiver(function: FunctionDescriptor) =
            (function.containingDeclaration as? ClassDescriptor)?.hasSpecialAnnotation(null) ?: false
    }
}


class GradleScriptKotlinComponentRegistrar : ComponentRegistrar {

    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        HasImplicitReceiverPlugin.apply(project)
    }
}
