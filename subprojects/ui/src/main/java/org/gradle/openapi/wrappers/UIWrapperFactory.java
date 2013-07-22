/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.openapi.wrappers;

import org.gradle.openapi.external.ui.DualPaneUIInteractionVersion1;
import org.gradle.openapi.external.ui.DualPaneUIVersion1;
import org.gradle.openapi.external.ui.SinglePaneUIInteractionVersion1;
import org.gradle.openapi.external.ui.SinglePaneUIVersion1;
import org.gradle.openapi.wrappers.ui.DualPaneUIWrapper;
import org.gradle.openapi.wrappers.ui.SinglePaneUIWrapper;

/**
 * This factory instantiates Gradle UIs used in IDE plugins. It is meant to be called via the Open API UIFactory class using reflection. This is because it is called dynamically. It is also meant to
 * help shield a Gradle user from changes to different versions of UI. It does so by using wrappers that can dynamically choose what/how to implement. The wrappers usually use the latest, however,
 * some of the functionality requires a matching Open API jar (which will be included with the plugin trying to use this). If the matching functionality is not found (a NoClassDefFoundError is
 * thrown), it will fall back to earlier versions.
 *
 * This class should not be moved or renamed, nor should its functions be renamed or have arguments added to/removed from them. This is to ensure forward/backward compatibility with multiple versions
 * of IDE plugins. Instead, consider changing the interaction that is passed to the functions as a means of having the caller provide different functionality.
 */
public class UIWrapperFactory {

    /**
     * Creates a single-pane Gradle UI. The main UI and output panes are self-contained.
     *
     * @param interaction this is how we interact with the caller.
     * @param showDebugInfo true to show some additional information that may be helpful diagnosing problems is this fails
     * @return a single pane UI.
     */
    public static SinglePaneUIVersion1 createSinglePaneUI(final SinglePaneUIInteractionVersion1 interaction, boolean showDebugInfo) throws Exception {
        return new SinglePaneUIWrapper(interaction);
    }

    /**
     * Creates a dual-pane Gradle UI, consisting of a main panel (containing task tree, favorites, etc) and a separate panel containing the output.
     *
     * @param interaction this is how we interact with the caller.
     * @param showDebugInfo true to show some additional information that may be helpful diagnosing problems is this fails
     * @return a dual pane UI.
     */
    public static DualPaneUIVersion1 createDualPaneUI(final DualPaneUIInteractionVersion1 interaction, boolean showDebugInfo) throws Exception {
        return new DualPaneUIWrapper(interaction);
    }
}
