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
package org.gradle.api.internal.file;

import com.google.common.collect.ImmutableMap;
import groovy.lang.Closure;
import org.gradle.api.tasks.AntBuilderAware;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;

import java.io.File;
import java.util.Map;

public class AntFileTreeBuilder implements AntBuilderAware {

    private final Map<String, File> files;

    public AntFileTreeBuilder(Map<String, File> files) {
        this.files = files;
    }

    @Override
    public Object addToAntBuilder(Object node, String childNodeName) {
        final DynamicObject dynamicObject = new BeanDynamicObject(node);
        dynamicObject.invokeMethod(childNodeName == null ? "resources" : childNodeName, new Closure(this) {
            public Object doCall(Object ignore) {
                for (Map.Entry<String, File> entry : files.entrySet()) {
                    String name = entry.getKey();
                    File file = entry.getValue();
                    // gradleFileResource type is mapped to AntFileResource
                    dynamicObject.invokeMethod("gradleFileResource", ImmutableMap.of(
                        "file", file.getAbsolutePath(),
                        "name", name
                    ));
                }
                return null;
            }
        });
        return node;
    }
}
