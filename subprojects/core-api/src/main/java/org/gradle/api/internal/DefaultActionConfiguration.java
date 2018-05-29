/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal;

import com.google.common.collect.Lists;
import org.gradle.api.ActionConfiguration;

import java.util.Collections;
import java.util.List;

public class DefaultActionConfiguration implements ActionConfiguration {
    private final List<Object> params = Lists.newArrayList();

    @Override
    public void params(Object... params) {
        Collections.addAll(this.params, params);
    }

    @Override
    public void setParams(Object... params) {
        this.params.clear();
        Collections.addAll(this.params, params);
    }

    @Override
    public Object[] getParams() {
        return this.params.toArray();
    }
}
