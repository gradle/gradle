/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.NonNullApi;
import org.gradle.api.tasks.testing.CompositeTestEventGenerator;
import org.gradle.api.tasks.testing.TestEventGenerator;
import org.gradle.internal.id.IdGenerator;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

@NonNullApi
public class DefaultCompositeTestEventGenerator extends DefaultTestEventGenerator implements CompositeTestEventGenerator {
    private final IdGenerator<?> idGenerator;
    private Set<DefaultTestEventGenerator> children;

    public DefaultCompositeTestEventGenerator(
        TestResultProcessor processor, IdGenerator<?> idGenerator, @Nullable TestDescriptorInternal parent, TestDescriptorInternal testDescriptor
    ) {
        super(processor, parent, testDescriptor);
        this.idGenerator = idGenerator;
    }

    private void addChild(DefaultTestEventGenerator child) {
        if (children == null) {
            children = Collections.newSetFromMap(new WeakHashMap<>());
        }
        children.add(child);
    }

    @Override
    protected void cleanup() {
        if (children != null) {
            List<Throwable> errors = null;
            for (DefaultTestEventGenerator child : children) {
                try {
                    child.close();
                } catch (Error e) {
                    // Let errors propagate
                    if (errors != null) {
                        errors.forEach(e::addSuppressed);
                    }
                    throw e;
                } catch (Throwable t) {
                    if (errors == null) {
                        errors = new ArrayList<>();
                    }
                    errors.add(t);
                }
            }
            if (errors != null) {
                RuntimeException e = new RuntimeException("Failed to close children");
                errors.forEach(e::addSuppressed);
                throw e;
            }
            children = null;
        }
        super.cleanup();
    }

    @Override
    public TestEventGenerator createAtomicNode(String name, String displayName) {
        requireRunning();
        DefaultTestEventGenerator child = new DefaultTestEventGenerator(
            processor, testDescriptor, new DefaultTestDescriptor(idGenerator.generateId(), null, name, null, displayName)
        );
        addChild(child);
        return child;
    }

    @Override
    public CompositeTestEventGenerator createCompositeNode(String name) {
        requireRunning();
        DefaultCompositeTestEventGenerator child = new DefaultCompositeTestEventGenerator(
            processor, idGenerator, testDescriptor, new DefaultTestSuiteDescriptor(idGenerator.generateId(), name)
        );
        addChild(child);
        return child;
    }
}
