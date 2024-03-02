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

package org.gradle.configurationcache.isolated;

import org.gradle.configurationcache.fixtures.SomeToolingModel;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;

import java.util.ArrayList;
import java.util.List;

public class FetchCustomModelInParallel implements BuildAction<List<SomeToolingModel>> {

    private final String blockingHttpServerUrl;

    public FetchCustomModelInParallel(String blockingHttpServerUrl) {
        this.blockingHttpServerUrl = blockingHttpServerUrl;
    }

    @Override
    public List<SomeToolingModel> execute(BuildController controller) {
        List<FetchModelForProject> actions = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            actions.add(new FetchModelForProject(blockingHttpServerUrl + "/nested-" + (i + 1)));
        }

        List<SomeToolingModel> models = controller.run(actions);
        awaitBlockingHttpServer(blockingHttpServerUrl + "/finished");
        return models;
    }

    private static class FetchModelForProject implements BuildAction<SomeToolingModel> {

        private final String url;

        private FetchModelForProject(String url) {
            this.url = url;
        }

        @Override
        public SomeToolingModel execute(BuildController controller) {
            awaitBlockingHttpServer(url); // Ensure each nested action is running in parallel
            return controller.findModel(SomeToolingModel.class);
        }
    }

    private static void awaitBlockingHttpServer(String url0) {
        System.out.println("[G] calling " + url0);
        try {
            java.net.URLConnection connection0 = new java.net.URL(url0).openConnection();
            connection0.setReadTimeout(0);
            connection0.connect();
            try (java.io.InputStream inputStream0 = connection0.getInputStream()) {
                while (inputStream0.read() >= 0) {
                }
            }
        } catch (Exception e) {
            System.out.println("[G] error response received for " + url0);
            throw new RuntimeException("Received error response from " + url0, e);
        }
        System.out.println("[G] response received for " + url0);
    }
}
