/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.platform.base.internal.toolchain;

import org.gradle.internal.text.TreeFormatter;
import org.gradle.util.TreeVisitor;

public class ToolChainAvailability implements ToolSearchResult {
    private ToolSearchResult reason;

    @Override
    public boolean isAvailable() {
        return reason == null;
    }

    public String getUnavailableMessage() {
        TreeFormatter formatter = new TreeFormatter();
        this.explain(formatter);
        return formatter.toString();
    }

    @Override
    public void explain(TreeVisitor<? super String> visitor) {
        reason.explain(visitor);
    }

    public ToolChainAvailability unavailable(String unavailableMessage) {
        if (reason == null) {
            reason = new FixedMessageToolSearchResult(unavailableMessage);
        }
        return this;
    }

    public ToolChainAvailability mustBeAvailable(ToolSearchResult tool) {
        if (!tool.isAvailable() && reason == null) {
            reason = tool;
        }
        return this;
    }

    private static class FixedMessageToolSearchResult implements ToolSearchResult {
        private final String message;

        private FixedMessageToolSearchResult(String message) {
            this.message = message;
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(message);
        }
    }
}
