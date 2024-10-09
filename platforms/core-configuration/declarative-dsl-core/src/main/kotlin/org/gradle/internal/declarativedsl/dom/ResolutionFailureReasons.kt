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

package org.gradle.internal.declarativedsl.dom


sealed interface ResolutionFailureReason


sealed interface PropertyNotAssignedReason : ResolutionFailureReason


sealed interface ElementNotResolvedReason : ResolutionFailureReason


sealed interface ValueNotResolvedReason : ResolutionFailureReason


sealed interface NamedReferenceNotResolvedReason : ValueNotResolvedReason

data object NonEnumValueNamedReference : NamedReferenceNotResolvedReason

sealed interface ValueFactoryNotResolvedReason : ValueNotResolvedReason


data object CrossScopeAccess : PropertyNotAssignedReason, ValueFactoryNotResolvedReason, ElementNotResolvedReason


data object UnresolvedValueUsed : ValueNotResolvedReason, PropertyNotAssignedReason


data object ValueTypeMismatch : PropertyNotAssignedReason


data object BlockMismatch : ElementNotResolvedReason, ValueFactoryNotResolvedReason


data object UnresolvedSignature : ElementNotResolvedReason, ValueFactoryNotResolvedReason


data object UnresolvedName : PropertyNotAssignedReason, ElementNotResolvedReason, ValueFactoryNotResolvedReason, NamedReferenceNotResolvedReason


data object OpaqueValueInIdentityKey : ElementNotResolvedReason, ValueFactoryNotResolvedReason


data object NotAssignable : PropertyNotAssignedReason


data object AmbiguousName : ElementNotResolvedReason, ValueFactoryNotResolvedReason


data object IsError : ElementNotResolvedReason, ValueFactoryNotResolvedReason


data object UnresolvedBase : PropertyNotAssignedReason, ElementNotResolvedReason, ValueFactoryNotResolvedReason, NamedReferenceNotResolvedReason
