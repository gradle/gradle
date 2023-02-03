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

package org.gradle.api.internal.artifacts.verification.exceptions;

import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.function.Consumer;

public class ComponentVerificationException extends GradleException {

    private final ModuleComponentIdentifier component;
    private final Consumer<TreeFormatter> causeErrorFormatter;

    /**
     * Creates a new exception when a component cannot be verified - because of some reason.
     *
     * @param component the component which failed the verification
     */
    public ComponentVerificationException(String message, ModuleComponentIdentifier component) {
        super(message);
        this.component = component;
        this.causeErrorFormatter = null;
    }

    /**
     * Creates a new exception when a component cannot be verified - because of some reason.
     *
     * @param component the component which failed the verification
     * @param causeErrorFormatter a consumer, which will be called with a {@link TreeFormatter}, and can put extra details what happened
     */
    public ComponentVerificationException(ModuleComponentIdentifier component, Consumer<TreeFormatter> causeErrorFormatter) {
        this.component = component;
        this.causeErrorFormatter = causeErrorFormatter;
    }

    @Override
    public String getMessage() {
        final TreeFormatter treeFormatter = new TreeFormatter();
        // Add our header first
        treeFormatter.node(
            String.format(
                "An error happened meanwhile verifying '%s:%s:%s':",
                component.getGroup(), component.getModule(), component.getVersion()
            )
        );

        if (this.causeErrorFormatter != null) {
            treeFormatter.startChildren();
            // Let the underlying exception explain the situation
            causeErrorFormatter.accept(treeFormatter);
            treeFormatter.endChildren();

        }
        return treeFormatter.toString();
    }
}
