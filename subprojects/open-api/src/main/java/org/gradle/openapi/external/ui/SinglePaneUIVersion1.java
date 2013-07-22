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

import javax.swing.*;

/*
 This is a gradle UI that is entirely within a single panel (and only a panel;
 no dialog or frame). This is meant to simplify how a plugin can interact with
 gradle.

 To use this, you'll want to get an instance of this from Gradle. Then setup
 your UI and add this to it via getComponent. Then call aboutToShow before
 you display your UI. Call close before you hide your UI. You'll need to set
 the current directory (at any time) so gradle knows where your project is
 located.

 @deprecated No replacement
  */
@Deprecated
public interface SinglePaneUIVersion1 extends BasicGradleUIVersion1 {
   /**
   Returns this panel as a Swing object suitable for inserting in your UI.
   @return the main component
      */
   public JComponent getComponent();
}
