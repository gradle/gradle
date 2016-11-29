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

package org.gradle.api.internal.component;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.component.CompositeSoftwareComponent;
import org.gradle.api.component.ConsumableSoftwareComponent;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.component.SoftwareComponentContainer;

class DefaultCompositeSoftwareComponent extends AbstractSoftwareComponent implements CompositeSoftwareComponent {
    private final SoftwareComponentContainer children;

    public DefaultCompositeSoftwareComponent(String name, SoftwareComponentContainer children) {
        // TODO:ADAM: children should inherit attributes
        super(name);
        this.children = children;
        // TODO:ADAM - constructor injection, decorate
        children.whenObjectAdded(new Action<SoftwareComponent>() {
            @Override
            public void execute(SoftwareComponent softwareComponent) {
                if (softwareComponent instanceof DefaultConsumableSoftwareComponent) {
                    DefaultConsumableSoftwareComponent component = (DefaultConsumableSoftwareComponent) softwareComponent;
                    component.setParent(DefaultCompositeSoftwareComponent.this);
                }
            }
        });
    }

    @Override
    public void composite(String name, Action<? super CompositeSoftwareComponent> configureAction) {
        children.create(name, CompositeSoftwareComponent.class, configureAction);
    }

    @Override
    public void child(String name, Action<? super ConsumableSoftwareComponent> configureAction) {
        children.create(name, ConsumableSoftwareComponent.class, configureAction);
    }

    @Override
    public void fromConfiguration(String name, final Configuration configuration, Action<? super ConsumableSoftwareComponent> configureAction) {
        // TODO:ADAM - decorate
        ConfigurationBackedConsumableSoftwareComponent component = new ConfigurationBackedConsumableSoftwareComponent(name, this, configuration);
        children.add(component);
        configureAction.execute(component);
    }

    @Override
    public SoftwareComponentContainer getChildren() {
        return children;
    }

    @Override
    public void children(Action<? super SoftwareComponentContainer> action) {
        action.execute(children);
    }
}
