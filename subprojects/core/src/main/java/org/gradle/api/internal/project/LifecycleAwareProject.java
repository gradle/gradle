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

package org.gradle.api.internal.project;

import org.gradle.api.IsolatedAction;
import org.gradle.internal.Cast;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.metaobject.DynamicObjectUtil;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.invocation.GradleLifecycleActionExecutor;

import javax.annotation.Nullable;

/**
 * Wrapper for {@link ProjectInternal} that has been accessed across projects, even in vintage mode.
 * <p>
 * When mutable state accessed - executes previously registered {@link org.gradle.api.invocation.GradleLifecycle#beforeProject(IsolatedAction)}
 * actions <b>eagerly</b>.
 */
public class LifecycleAwareProject extends MutableStateAccessAwareProject {

    public static ProjectInternal from(
        ProjectInternal target,
        ProjectInternal referrer,
        GradleLifecycleActionExecutor gradleLifecycleActionExecutor,
        Instantiator instantiator
    ) {
        return MutableStateAccessAwareProject.wrap(
            target,
            referrer,
            project -> instantiator.newInstance(LifecycleAwareProject.class, target, referrer, gradleLifecycleActionExecutor)
        );
    }

    private final GradleLifecycleActionExecutor gradleLifecycleActionExecutor;

    public LifecycleAwareProject(ProjectInternal delegate, ProjectInternal referrer, GradleLifecycleActionExecutor gradleLifecycleActionExecutor) {
        super(delegate, referrer);
        this.gradleLifecycleActionExecutor = gradleLifecycleActionExecutor;
    }

    @Override
    protected void onMutableStateAccess(String what) {
        gradleLifecycleActionExecutor.executeBeforeProjectFor(delegate);
    }

    @Override
    protected Object propertyMissing(String name) {
        onMutableStateAccess("getProperty");
        // TODO just delegate.getProperty?
        DynamicObject dynamicDelegate = DynamicObjectUtil.asDynamicObject(delegate);
        DynamicInvokeResult delegateResult = dynamicDelegate.tryGetProperty(name);

        if (delegateResult.isFound()) {
            return delegateResult.getValue();
        }

        throw dynamicDelegate.getMissingProperty(name);
    }

    @Nullable
    @Override
    protected Object methodMissing(String name, Object args) {
        onMutableStateAccess("invokeMethod");
        // TODO just delegate.invokeMethod?
        Object[] varargs = Cast.uncheckedNonnullCast(args);
        DynamicObject dynamicDelegate = DynamicObjectUtil.asDynamicObject(delegate);
        DynamicInvokeResult delegateResult = dynamicDelegate.tryInvokeMethod(name, varargs);

        if (delegateResult.isFound()) {
            return delegateResult.getValue();
        }

        throw dynamicDelegate.methodMissingException(name, varargs);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (other instanceof LifecycleAwareProject) {
            LifecycleAwareProject lifecycleAwareProject = (LifecycleAwareProject) other;
            return delegate.equals(lifecycleAwareProject.delegate) && referrer.equals(lifecycleAwareProject.referrer);
        } else {
            return delegate.equals(other);
        }
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }
}
