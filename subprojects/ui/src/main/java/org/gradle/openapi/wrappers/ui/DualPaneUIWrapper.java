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
package org.gradle.openapi.wrappers.ui;

import org.gradle.gradleplugin.userinterface.swing.generic.DualPaneUIInstance;
import org.gradle.openapi.external.ui.DualPaneUIInteractionVersion1;
import org.gradle.openapi.external.ui.DualPaneUIVersion1;

import javax.swing.*;
import java.awt.*;

/**
 * This wraps a DualPaneUIVersion1 for the purpose of being instantiated for an external tool such an IDE plugin. It wraps several interfaces and uses delegation in an effort to make this backward and
 * forward compatible. Most of the work is done in AbstractOpenAPIUIWrapper
 */
public class DualPaneUIWrapper extends AbstractOpenAPIUIWrapper<DualPaneUIInstance> implements DualPaneUIVersion1 {
    public DualPaneUIWrapper(DualPaneUIInteractionVersion1 dualPaneUIArguments) {

        super(dualPaneUIArguments.instantiateSettings(), dualPaneUIArguments.instantiateAlternateUIInteraction());

        //the main thing this does in instantiate the DualPaneUIInstance.
        DualPaneUIInstance uiInstance = new DualPaneUIInstance();
        uiInstance.initialize(settingsVersionWrapper, alternateUIInteractionVersionWrapper);
        initialize(uiInstance);
    }

    /**
     * Returns a component that shows the task tree tab, favorites tab, etc. suitable for inserting in your UI.
     *
     * @return the main component
     */
    public JComponent getMainComponent() {
        return getGradleUI().getComponent();
    }

    /**
     * Returns a component that shows the output of the tasks being executed.
     *
     * @return the output component
     */
    public Component getOutputPanel() {
        return getGradleUI().getOutputPanel();
    }

    public int getNumberOfOpenedOutputTabs() {
        return getGradleUI().getOutputUILord().getTabCount();
    }
}
