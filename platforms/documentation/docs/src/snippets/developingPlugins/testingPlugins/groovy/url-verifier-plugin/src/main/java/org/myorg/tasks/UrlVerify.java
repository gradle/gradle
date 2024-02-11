package org.myorg.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.myorg.http.DefaultHttpCaller;
import org.myorg.http.HttpCallException;
import org.myorg.http.HttpCaller;
import org.myorg.http.HttpResponse;

abstract public class UrlVerify extends DefaultTask {
    private HttpCaller httpCaller = new DefaultHttpCaller();

    @Input
    abstract public Property<String> getUrl();

    @TaskAction
    public void verify() {
        String url = getUrl().get();
        try {
            HttpResponse httpResponse = httpCaller.get(url);

            if (httpResponse.getCode() != 200) {
                throw new GradleException(String.format("Failed to resolve url '%s' (%s)", url, httpResponse.toString()));
            }
        } catch (HttpCallException e) {
            throw new GradleException(String.format("Failed to resolve url '%s'", url, e));
        }

        getLogger().quiet(String.format("Successfully resolved URL '%s'", url));
    }

    void setHttpCaller(HttpCaller httpCaller) {
        this.httpCaller = httpCaller;
    }
}
