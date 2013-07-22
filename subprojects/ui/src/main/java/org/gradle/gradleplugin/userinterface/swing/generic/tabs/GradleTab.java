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
package org.gradle.gradleplugin.userinterface.swing.generic.tabs;

import java.awt.*;

/**
 * Interface for a tab in the gradle UI.
 */
public interface GradleTab {
    /**
     * @return the name of this tab
     */
    public String getName();

    /**
     * This is where we should create the component.
     *
     * @return the component
     */
    public Component createComponent();

    /**
     * Notification that this component is about to be shown. Do whatever initialization you choose.
     */
    public void aboutToShow();
}
