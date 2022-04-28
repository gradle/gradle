/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.scala;

import com.google.common.collect.ImmutableList;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.upgrade.ApiUpgradeManager;
import org.gradle.language.scala.tasks.AbstractScalaCompile;

public class ScalaServices  extends AbstractPluginServiceRegistry {
    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.addProvider(new ProviderApiMigrationAction());
    }

    private static class ProviderApiMigrationAction {
        public void configure(ApiUpgradeManager upgradeManager) {
            // It seems like the bytecode references the subclass as an owner
            for (Class<? extends AbstractCompile> compileClass : ImmutableList.of(AbstractScalaCompile.class, ScalaCompile.class)) {
                upgradeManager
                    .matchProperty(compileClass, String.class, "targetCompatibility")
                    .replaceWith(
                        abstractCompile -> abstractCompile.getTargetCompatibility().get(),
                        (abstractCompile, value) -> abstractCompile.getTargetCompatibility().set(value)
                    );
                upgradeManager
                    .matchProperty(compileClass, String.class, "sourceCompatibility")
                    .replaceWith(
                        abstractCompile -> abstractCompile.getSourceCompatibility().get(),
                        (abstractCompile, value) -> abstractCompile.getSourceCompatibility().set(value)
                    );
            }
        }
    }
}
