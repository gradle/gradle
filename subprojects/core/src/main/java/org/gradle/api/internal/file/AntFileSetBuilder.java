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

import groovy.lang.Closure;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.tasks.AntBuilderAware;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.util.AntUtil;

import java.util.Collections;

public class AntFileSetBuilder implements AntBuilderAware {

    private final Iterable<DirectoryFileTree> trees;

    public AntFileSetBuilder(Iterable<DirectoryFileTree> trees) {
        this.trees = trees;
    }

    @Override
    public Object addToAntBuilder(final Object node, String nodeName) {
        final DynamicObject dynamicObject = new BeanDynamicObject(node);
        for (final DirectoryFileTree tree : trees) {
            if (!tree.getDir().exists()) {
                continue;
            }

            dynamicObject.invokeMethod(nodeName == null ? "fileset" : nodeName, Collections.singletonMap("dir", AntUtil.maskFilename(tree.getDir().getAbsolutePath())), new Closure<Void>(this) {
                public Object doCall(Object ignore) {
                    return tree.getPatternSet().addToAntBuilder(node, null);
                }
            });
        }
        return null;
    }
}
