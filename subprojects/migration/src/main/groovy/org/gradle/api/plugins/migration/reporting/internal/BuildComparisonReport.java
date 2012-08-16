/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.migration.reporting.internal;

import org.gradle.api.plugins.migration.model.render.internal.BuildComparisonResultRenderer;
import org.gradle.api.reporting.Report;

/*
    This is private right now, but we might publicise it and use it to allow configuring the renderer.
    That doesn't seem quite right though.
 */
public interface BuildComparisonReport<T extends BuildComparisonResultRenderer> extends Report {

    T getRenderer();

}
