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

import org.gradle.gradleplugin.userinterface.swing.generic.SinglePaneUIInstance;
import org.gradle.openapi.external.ui.SettingsNodeVersion1;
import org.gradle.openapi.external.ui.SinglePaneUIInteractionVersion1;
import org.gradle.openapi.external.ui.SinglePaneUIVersion1;
import org.gradle.openapi.wrappers.NoLongerSupportedException;

import javax.swing.*;

/**
 * This wraps a SinglePaneUIVersion1 for the purpose of being instantiated for an external tool such an IDE plugin. It wraps several interfaces and uses delegation in an effort to make this backward
 * and forward compatible. Most of the work is done in AbstractOpenAPIUIWrapper
 */
public class SinglePaneUIWrapper extends AbstractOpenAPIUIWrapper<SinglePaneUIInstance> implements SinglePaneUIVersion1 {
    public SinglePaneUIWrapper(SinglePaneUIInteractionVersion1 singlePaneUIArguments) {
        super(notSupported(), null);
    }

    private static SettingsNodeVersion1 notSupported() {
        throw new NoLongerSupportedException();
    }

    /**
     * The open API uses the other constructor.
     */
    public SinglePaneUIWrapper(SinglePaneUIInteractionVersion1 singlePaneUIArguments, boolean dummy) {

        super(singlePaneUIArguments.instantiateSettings(), singlePaneUIArguments.instantiateAlternateUIInteraction());

        //the main thing this does in instantiate the SinglePaneUIInstance.
        SinglePaneUIInstance singlePaneUIInstance = new SinglePaneUIInstance();
        singlePaneUIInstance.initialize(settingsVersionWrapper, alternateUIInteractionVersionWrapper);
        initialize(singlePaneUIInstance);
    }

    public int getNumberOfOpenedOutputTabs() {
        return getGradleUI().getOutputUILord().getTabCount();
    }

    /**
     * Returns this panel as a Swing object suitable for inserting in your UI.
     *
     * @return the main component
     */
    public JComponent getComponent() {
        return getGradleUI().getComponent();
    }
}
