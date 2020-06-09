/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.enterprise.core;

import javax.annotation.Nullable;

public class GradleEnterprisePluginEndOfBuildNotifier {

    private Listener listener;

    public interface Listener {
        void buildFinished(@Nullable Throwable buildFailure);
    }

    public void registerOnlyListener(Listener listener) {
        if (this.listener == null) {
            this.listener = listener;
        } else {
            throw new IllegalStateException("listener already set to: " + listener);
        }
    }

    public void buildComplete(@Nullable Throwable buildFailure) {
        Listener listener = this.listener;
        if (listener != null) {
            listener.buildFinished(buildFailure);
        }
    }

}
