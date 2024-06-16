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

package org.gradle.api.internal.initialization;

import org.gradle.api.plugins.ExtensionAware;
import org.gradle.internal.Cast;
import org.gradle.plugin.software.internal.ConventionHandler;
import org.gradle.plugin.software.internal.SoftwareTypeImplementation;
import org.gradle.plugin.software.internal.SoftwareTypeRegistry;

public class ActionConventionHandler implements ConventionHandler {
    private final SoftwareTypeRegistry softwareTypeRegistry;

    public ActionConventionHandler(SoftwareTypeRegistry softwareTypeRegistry) {
        this.softwareTypeRegistry = softwareTypeRegistry;
    }

    @Override
    public void apply(Object target, String softwareTypeName) {
        SoftwareTypeImplementation<?> softwareType = softwareTypeRegistry.getSoftwareTypeImplementations().get(softwareTypeName);
        softwareType.getConventions().stream()
            .filter(convention -> convention instanceof ActionConvention)
            .forEach(convention -> convention.apply(Cast.uncheckedCast(receiverFor(getSoftwareTypeModel(target, softwareType.getModelPublicType())))));
    }

    private static <T> T getSoftwareTypeModel(Object target, Class<T> extensionType) {
        if (target instanceof ExtensionAware) {
            return ((ExtensionAware) target).getExtensions().getByType(extensionType);
        }
        throw new IllegalStateException("'%s' is not ExtensionAware" + target.getClass());
    }

    private static <T> ActionConventionReceiver<T> receiverFor(T softwareTypeModel) {
        return convention -> convention.execute(softwareTypeModel);
    }
}
