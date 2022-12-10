package org.gradle.internal.execution.model.impl;

import org.gradle.api.tasks.Input;
import org.gradle.internal.model.AbstractPropertyModelBuilder;
import org.gradle.internal.schema.PropertySchema;

import javax.annotation.Nullable;

public class InputPropertyModelBuilder extends AbstractPropertyModelBuilder<Input, InputModelBuilderVisitor> {
    public InputPropertyModelBuilder() {
        super(Input.class);
    }

    @Override
    protected void acceptVisitor(@Nullable Input type, PropertySchema schema, InputModelBuilderVisitor visitor) {
        visitor.visitInputPropertyModel(new DefaultInputPropertyModel(schema.getQualifiedName(), schema.getValue()));
    }
}

