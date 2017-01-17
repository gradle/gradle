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

package org.gradle.api.internal.changedetection.rules;

import com.google.common.base.Objects;
import org.gradle.api.GradleException;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.DefaultInputProperty;
import org.gradle.api.internal.changedetection.state.InputProperty;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.util.ChangeListener;
import org.gradle.util.CollectionUtils;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.DiffUtil;
import org.gradle.util.Equalizer;
import org.gradle.util.internal.DefaultEqualizer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

class InputPropertiesTaskStateChanges extends SimpleTaskStateChanges {
    private final Map<String, InputProperty> properties;
    private final TaskExecution previousExecution;
    private final TaskInternal task;

    public InputPropertiesTaskStateChanges(TaskExecution previousExecution, TaskExecution currentExecution, TaskInternal task) {
        currentExecution.setInputProperties(process(task.getInputs().getProperties()));
        this.properties = currentExecution.getInputProperties();
        this.previousExecution = previousExecution;
        this.task = task;
    }

    @Override
    protected void addAllChanges(final List<TaskStateChange> changes) {
        DiffUtil.diff(properties, previousExecution.getInputProperties(), new ChangeListener<Map.Entry<String, InputProperty>>() {
            public void added(Map.Entry<String, InputProperty> element) {
                changes.add(new DescriptiveChange("Input property '%s' has been added for %s", element.getKey(), task));
            }

            public void removed(Map.Entry<String, InputProperty> element) {
                changes.add(new DescriptiveChange("Input property '%s' has been removed for %s", element.getKey(), task));
            }

            public void changed(Map.Entry<String, InputProperty> element) {
                changes.add(new DescriptiveChange("Value of input property '%s' has changed for %s", element.getKey(), task));
            }
        }, new InputPropertyEqualizer(changes));
    }

    public static Map<String, InputProperty> process(final Map<String, Object> inputProperties) {
        return CollectionUtils.collectMapValues(inputProperties.keySet(), new Transformer<InputProperty, String>() {
            @Override
            public InputProperty transform(String key) {
                Object value = inputProperties.get(key);
                try {
                    return DefaultInputProperty.create(value);
                } catch (NotSerializableException e) {
                    throw new GradleException(format("Unable to hash task input properties. Property '%s' with value '%s' cannot be serialized.", key, value), e);
                }
            }
        });
    }

    private static class InputPropertyEqualizer implements Equalizer<InputProperty> {
        private static final Equalizer<Object> DEFAULT_EQUALIZER = new DefaultEqualizer();
        private final List<TaskStateChange> changes;

        InputPropertyEqualizer(List<TaskStateChange> changes) {
            this.changes = changes;
        }

        @Override
        public boolean equals(@Nullable InputProperty obj1, @Nullable InputProperty obj2) {
            if (obj1 == obj2) {
                return true;
            }
            if (obj1 == null || obj2 == null) {
                return false;
            }

            ClassLoader classLoader = selectClassLoader(obj1.getRawValue(), obj2.getRawValue());
            Object raw1;
            Object raw2;
            try {
                raw1 = tryDeserialize(obj1, classLoader);
                raw2 = tryDeserialize(obj2, classLoader);
            } catch (IOException e) {
                // In presence of corruption, both object cannot assert to be equal
                changes.add(new DescriptiveChange("Exception while deserializing the input property: %s", e.getMessage()));
                return false;
            } catch (ClassNotFoundException e) {
                // In presence of complete change of property type, both object cannot assert to be equal
                changes.add(new DescriptiveChange("Class definition couldn't be found while deserializing the input property: %s", e.getMessage()));
                return false;
            }

            boolean isInputPropertyEquals = DEFAULT_EQUALIZER.equals(raw1, raw2);
            if (!isEqualsMethodOverriden(raw1) || !isEqualsMethodOverriden(raw2)) {
                changes.add(new DescriptiveChange("Input property is using the default equals method implementation which result in always detecting a change"));
            } else {
                maybeNagAboutCustomEqualsInTaskInputPropertyDeprecation(isInputPropertyEquals, Objects.equal(obj1.getHash(), obj2.getHash()));
            }

            return isInputPropertyEquals;
        }

        private static ClassLoader selectClassLoader(Object lhs, Object rhs) {
            if (lhs == null && rhs == null) {
                return InputProperty.class.getClassLoader();
            } else if (lhs == null) {
                return rhs.getClass().getClassLoader();
            } else {
                return lhs.getClass().getClassLoader();
            }
        }

        private static Object tryDeserialize(InputProperty inputProperty, ClassLoader classLoader) throws IOException, ClassNotFoundException {
            if (inputProperty.getRawValue() == null && inputProperty.getSerializedBytes() != null) {
                return new ClassLoaderObjectInputStream(new ByteArrayInputStream(inputProperty.getSerializedBytes()), classLoader).readObject();
            }

            return inputProperty.getRawValue();
        }

        private static boolean isEqualsMethodOverriden(Object obj) {
            Method equalsMethod = getEqualsMethod(obj);
            return equalsMethod != null && isMethodOverridden(equalsMethod, Object.class);
        }

        @Nullable
        private static Method getEqualsMethod(Object obj) {
            if (obj == null) {
                return null;
            }
            return getEqualsMethod(obj.getClass());
        }


        @Nullable
        private static Method getEqualsMethod(Class cls) {
            try {
                return cls.getMethod("equals", Object.class);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        private static boolean isMethodOverridden(Method method, Class declaringClass) {
            return !method.getDeclaringClass().equals(declaringClass);
        }

        private static void maybeNagAboutCustomEqualsInTaskInputPropertyDeprecation(boolean isObjectEquals, boolean isHashEquals) {
            // In presence of a mismatch between the Object.equals implementation and hashed property, a deprecation warning is shown
            if ((isObjectEquals && !isHashEquals) || (!isObjectEquals && isHashEquals)) {
                DeprecationLogger.nagUserOfDeprecated("Custom equals implementation on task input properties");
            }
        }
    }
}
