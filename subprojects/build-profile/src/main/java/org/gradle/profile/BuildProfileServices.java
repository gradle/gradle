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
import org.gradle.api.internal.BuildDefinition;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.internal.service.scopes.BuildScopeListenerManagerAction;
import org.gradle.internal.time.Clock;

public class BuildProfileServices extends AbstractPluginServiceRegistry {

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new Object() {
            public BuildScopeListenerManagerAction createBuildProfileListeners(ServiceRegistry services, BuildDefinition buildDefinition) {
                return new BuildScopeListenerManagerAction() {
                    @Override
                    public void execute(ListenerManager listenerManager) {
                        if (buildDefinition.getStartParameter().isProfile()) {
                            listenerManager.addListener(services.get(ProfileEventAdapter.class));
                            listenerManager.addListener(services.get(ReportGeneratingProfileListener.class));
                        }
                    }
                };
            }

            public ReportGeneratingProfileListener createReportGeneratingProfileListener(StyledTextOutputFactory styledTextOutputFactory) {
                return new ReportGeneratingProfileListener(styledTextOutputFactory);
            }

            public ProfileEventAdapter createProfileEventAdapter(BuildStartedTime buildStartedTime, Clock clock, ListenerManager listenerManager, StartParameter startParameter) {
                return new ProfileEventAdapter(buildStartedTime, clock, listenerManager.getBroadcaster(ProfileListener.class), startParameter);
            }
        });
    }

}
