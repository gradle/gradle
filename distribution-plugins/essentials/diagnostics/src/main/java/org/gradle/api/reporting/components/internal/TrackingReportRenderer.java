/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.reporting.components.internal;

import com.google.common.collect.Sets;
import org.gradle.reporting.ReportRenderer;

import java.io.IOException;
import java.util.Set;

public class TrackingReportRenderer<T, E> extends ReportRenderer<T, E> {
    private final Set<T> items = Sets.newHashSet();
    private final ReportRenderer<? super T, ? super E> delegate;

    public TrackingReportRenderer(ReportRenderer<? super T, ? super E> delegate) {
        this.delegate = delegate;
    }

    public Set<T> getItems() {
        return items;
    }

    @Override
    public void render(T model, E output) throws IOException {
        if (items.add(model)) {
            delegate.render(model, output);
        }
    }
}
