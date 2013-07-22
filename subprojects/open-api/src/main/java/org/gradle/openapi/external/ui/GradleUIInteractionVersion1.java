/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.openapi.external.ui;

/**
 This interface holds onto our options and allows us to interact with the
 caller. This is meant to interact with the Gradle UI across class loader
 and version boundaries. That is, the open API has a single entry point
 that shouldn't change across versions. New interfaces can be expected, but
 we'll always allow 'version1'. This is to provide backward/forward compatibility.

 @deprecated No replacement
*/
@Deprecated
public interface GradleUIInteractionVersion1 {
   /*
      This is only called once and is how we get a hold of the AlternateUIInteraction.
      @return an AlternateUIInteraction object. This cannot be null.
   */
   public AlternateUIInteractionVersion1 instantiateAlternateUIInteraction();

   /*
      This is only called once and is how we get a hold of how the owner wants
      to store preferences.
      @return a settings object. This cannot be null.
   */
   public SettingsNodeVersion1 instantiateSettings();
}
