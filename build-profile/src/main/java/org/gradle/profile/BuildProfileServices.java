/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.profile;

import org.gradle.StartParameter;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.service.scopes.BuildScopeListenerManagerAction;

public class BuildProfileServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new Object() {
            public void configure(ServiceRegistration serviceRegistration, StartParameter startParameter) {
                if (startParameter.isProfile()) {
                    serviceRegistration.add(BuildProfile.class);
                    serviceRegistration.add(ReportGeneratingProfileListener.class);
                    serviceRegistration.add(ProfileCoordinator.class);
                }
            }
        });
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new Object() {
            public void configure(ServiceRegistration serviceRegistration, StartParameter startParameter) {
                if (startParameter.isProfile()) {
                    serviceRegistration.add(ProfileEventAdapter.class);
                    serviceRegistration.add(ProfileRegistrationAction.class);
                }
            }
        });
    }

    public static class ProfileRegistrationAction implements BuildScopeListenerManagerAction {
        private final ProfileEventAdapter eventAdapter;

        public ProfileRegistrationAction(ProfileEventAdapter eventAdapter) {
            this.eventAdapter = eventAdapter;
        }

        @Override
        public void execute(ListenerManager listenerManager) {
            listenerManager.addListener(eventAdapter);
        }
    }
}
