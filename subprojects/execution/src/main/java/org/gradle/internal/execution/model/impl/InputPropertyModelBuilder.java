package org.gradle.internal.execution.model.impl;

import org.gradle.api.tasks.Input;
import org.gradle.internal.execution.model.InputPropertyModel;
import org.gradle.internal.model.PropertyModelBuilder;
import org.gradle.internal.schema.PropertySchema;

public class InputPropertyModelBuilder implements PropertyModelBuilder<Input, InputPropertyModel> {

    @Override
    public Class<Input> getHandledPropertyType() {
        return Input.class;
    }

    @Override
    public InputPropertyModel getModel(PropertySchema schema) {
        return new DefaultInputPropertyModel(schema.getQualifiedName(), schema.getValue());
    }
}

