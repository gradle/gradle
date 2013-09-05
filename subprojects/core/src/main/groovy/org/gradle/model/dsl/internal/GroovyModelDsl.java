package org.gradle.model.dsl.internal;

import groovy.lang.Closure;
import groovy.lang.MissingMethodException;
import org.gradle.api.Action;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.model.ModelPath;
import org.gradle.model.ModelRules;
import org.gradle.model.dsl.ModelDsl;

public class GroovyModelDsl implements ModelDsl {

    private final ModelPath modelPath;
    private final ModelRules modelRules;

    public GroovyModelDsl(ModelRules modelRules) {
        this(null, modelRules);
    }

    private GroovyModelDsl(ModelPath modelPath, ModelRules modelRules) {
        this.modelPath = modelPath;
        this.modelRules = modelRules;
    }

    public ModelDsl get(String name) {
        return new GroovyModelDsl(modelPath == null ? ModelPath.path(name) : modelPath.child(name), modelRules);
    }

    public void configure(Action<?> action) {
        modelRules.config(modelPath.toString(), action);
    }

    public ModelDsl propertyMissing(String name) {
        return get(name);
    }

    public Void methodMissing(String name, Object argsObj) {
        Object[] args = (Object[]) argsObj;

        if (args.length != 1 || !(args[0] instanceof Closure)) {
            throw new MissingMethodException(name, getClass(), args);
        }

        Closure closure = (Closure) args[0];

        get(name).configure(new ClosureBackedAction<Object>(closure));

        return null;
    }

}
