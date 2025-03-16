/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.internal.tasks.testing.operations.TestListenerBuildOperationAdapter;
import org.gradle.api.internal.tasks.testing.report.generic.MetadataRendererRegistry;
import org.gradle.api.internal.tasks.testing.results.AggregateTestEventReporter;
import org.gradle.api.internal.tasks.testing.results.HtmlTestReportGenerator;
import org.gradle.api.tasks.testing.TestEventReporterFactory;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class TestingBasePluginServices extends AbstractGradleModuleServices {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new TestingGlobalScopeServices());
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new TestingBuildSessionScopeServices());
    }

    @Override
    public void registerBuildTreeServices(ServiceRegistration registration) {
        registration.addProvider(new TestingBuildTreeScopeServices());
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new TestingBuildScopeServices());
    }

    @NullMarked
    public static class TestingGlobalScopeServices implements ServiceRegistrationProvider {
        void configure(ServiceRegistration serviceRegistration) {
            serviceRegistration.add(MetadataRendererRegistry.class);
        }
    }

    @NullMarked
    public static class TestingBuildSessionScopeServices implements ServiceRegistrationProvider {
        void configure(ServiceRegistration serviceRegistration) {
            serviceRegistration.add(TestListenerBuildOperationAdapter.class);
            serviceRegistration.add(HtmlTestReportGenerator.class);
        }
    }

    @NullMarked
    public static class TestingBuildTreeScopeServices implements ServiceRegistrationProvider {
        void configure(ServiceRegistration serviceRegistration) {
            serviceRegistration.add(AggregateTestEventReporter.class);
        }
    }

    @NullMarked
    public static class TestingBuildScopeServices implements ServiceRegistrationProvider {
        void configure(ServiceRegistration serviceRegistration) {
            serviceRegistration.add(TestEventReporterFactory.class, DefaultTestEventReporterFactory.class);
        }
    }
}
