/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.dsl.internal.inputs;

import com.google.common.collect.Maps;
import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.core.ModelView;

import java.util.List;
import java.util.Map;

@ThreadSafe
public abstract class RuleInputAccessBacking {

    private RuleInputAccessBacking() {
    }

    private static final ThreadLocal<Map<String, Object>> INPUT = new ThreadLocal<Map<String, Object>>();

    public static void runWithContext(List<ModelView<?>> views, Runnable runnable) {
        Map<String, Object> map = Maps.newHashMap();
        int i = 0;
        for (ModelView<?> view : views) {
            map.put(view.getPath().toString(), views.get(i++).getInstance());
        }

        INPUT.set(map);
        try {
            runnable.run();
        } finally {
            INPUT.remove();
        }
    }

    public static RuleInputAccess getAccess() {
        final Map<String, Object> inputs = INPUT.get();
        return new RuleInputAccess() {
            public Object input(String modelPath) {
                return inputs.get(modelPath);
            }
        };
    }

}
