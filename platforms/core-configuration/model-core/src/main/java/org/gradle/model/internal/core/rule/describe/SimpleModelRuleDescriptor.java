/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model.internal.core.rule.describe;

import com.google.common.base.Objects;
import javax.annotation.concurrent.ThreadSafe;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;

import java.io.IOException;

@ThreadSafe
public class SimpleModelRuleDescriptor extends AbstractModelRuleDescriptor {

    private final Factory<String> factory;

    public SimpleModelRuleDescriptor(final Factory<String> descriptor) {
        this.factory = new Factory<String>() {
            String cachedValue;
            @Override
            public String create() {
                if (cachedValue == null) {
                    cachedValue = STRING_INTERNER.intern(descriptor.create());
                }
                return cachedValue;
            }
        };
    }

    public SimpleModelRuleDescriptor(String descriptor) {
        this(Factories.constant(STRING_INTERNER.intern(descriptor)));
    }

    public SimpleModelRuleDescriptor(final String descriptor, final Object... args) {
        this(new Factory<String>() {
            @Override
            public String create() {
                return String.format(descriptor, args);
            }
        });
    }

    private String getDescriptor() {
        return factory.create();
    }

    @Override
    public void describeTo(Appendable appendable) {
        try {
            appendable.append(getDescriptor());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SimpleModelRuleDescriptor that = (SimpleModelRuleDescriptor) o;
        return Objects.equal(getDescriptor(), that.getDescriptor());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getDescriptor());
    }
}
