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

import javax.swing.JComponent;
import java.awt.Component;

/**
This is a gradle UI that is broken into two panels: one contains a tabbed pane
 of tasks, favorites, command line, etc. The other pane contains the output.
 This is meant to simplify how an IDE plugin can interact with gradle. Specifically,
 this allows the 'main' pane to be vertical and the output pane to be horizontal.

 To use this, you'll want to get an instance of this from Gradle. Then setup
 your UI and add this to it via getComponent. Then call aboutToShow before
 you display your UI. Call close before you hide your UI. You'll need to set
 the current directory (at any time) so gradle knows where your project is
 located.

 @deprecated No replacement
 */
@Deprecated
public interface DualPaneUIVersion1 extends BasicGradleUIVersion1 {
   /**
      Returns a component that shows the task tree tab, favorites tab, etc.
      suitable for inserting in your UI.
      @return the main component
    */
   public JComponent getMainComponent();

   /**
      Returns a component that shows the output of the tasks being executed.
      This is suitable for inserting in your UI.
      @return the output component
   */
   public Component getOutputPanel();

    /**
     * This gets the number of opened output tabs. This is used by the Idea plugin
     * to determine if it should close the entire output pane when a tab is closed
     * This doesn't determine whether or not the tabs are busy. See
     * GradleInterfaceVersion1.isBusy for that 
     * @return the number of opened output tabs.
     */
   public int getNumberOfOpenedOutputTabs();
}
