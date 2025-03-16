/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.compile;

import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.reflect.JavaPropertyReflectionUtil;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Map;

/**
 * Base class for compilation-related options.
 *
 * @deprecated This class will be removed in Gradle 9.0. All classes that extend this class will no longer extend it.
 */
@Deprecated
public abstract class AbstractOptions implements Serializable {
    private static final long serialVersionUID = 0;

    /**
     * Reflectively sets the properties of this object from the given map.
     *
     * @deprecated This method will be removed in Gradle 9.0
     */
    @Deprecated
    public void define(@Nullable Map<String, Object> args) {

        DeprecationLogger.deprecateMethod(AbstractOptions.class, "define(Map)")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "deprecated_abstract_options")
            .nagUser();

        if (args == null) {
            return;
        }
        for (Map.Entry<String, Object> arg: args.entrySet()) {
            setProperty(arg.getKey(), arg.getValue());
        }
    }

    private void setProperty(String property, Object value) {
        JavaPropertyReflectionUtil.writeableProperty(getClass(), property, value == null ? null : value.getClass()).setValue(this, value);
    }

}
