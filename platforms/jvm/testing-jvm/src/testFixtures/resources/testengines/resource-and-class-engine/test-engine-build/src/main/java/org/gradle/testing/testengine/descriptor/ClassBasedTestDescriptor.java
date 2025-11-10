package org.gradle.testing.testengine.descriptor;

import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;

public final class ClassBasedTestDescriptor extends AbstractTestDescriptor {
    private final Class clazz;

    public ClassBasedTestDescriptor(UniqueId parentId, Class clazz) {
        super(parentId.append("class", clazz.getName()), clazz.getName());
        this.clazz = clazz;
    }

    @Override
    public Type getType() {
        return Type.TEST;
    }

    public String getName() {
        return clazz.getName();
    }

    @Override
    public String toString() {
        return "Test [class=" + clazz.getName() + "]";
    }
}
