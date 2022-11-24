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

package org.gradle.api;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.serialization.Cached;

import java.io.Serializable;

public abstract class SeparatedTask<P extends SeparatedTask.TaskParams> extends DefaultTask {
    public interface TaskParams {}

    public interface TaskActionRunnable extends Runnable, Serializable {}

    private final Cached<Runnable> taskAction = Cached.of(this::createTaskAction);

    private ObjectFactory getObjectFactory() {
        return getServices().get(ObjectFactory.class);
    }

    @TaskAction
    public final void runTaskAction() {
        taskAction.get().run();
    }

    private Runnable createTaskAction() {
        P taskParams = getObjectFactory().newInstance(getTaskParamType());
        return configureTaskAction(taskParams);
    }

//    interface Params {
//    }
//
//    interface AcType<P extends Params> {
//        P getParameters();
//    }
//
//    protected <P extends Params, A extends AcType<P>> void addAction(Class<A> actionClass, Action<P> configureAction) {
//
//    }
//
//    class Builder<P extends Params> {
//        Builder<P> configure(Action<P> configureAction) {
//            return this;
//        }
//
//        <A extends AcType<P>> Builder<P> addAction(Class<A> actionClass) {
//            return this;
//        }
//    }
//
//    protected <P extends Params> Builder<P> actionBuilder(Class<P> parameters) {
//        return new Builder<>();
//    }

    @Internal
    protected abstract Class<P> getTaskParamType();

    protected abstract TaskActionRunnable configureTaskAction(P a);
}
