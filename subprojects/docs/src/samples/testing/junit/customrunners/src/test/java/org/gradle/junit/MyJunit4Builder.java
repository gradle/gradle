package org.gradle.junit;

import org.junit.internal.builders.JUnit4Builder;
import org.junit.runner.Runner;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;

class MyJunit4Builder extends JUnit4Builder {
	@Override
	public Runner runnerForClass(Class<?> testClass) throws Throwable {
		return new BlockJUnit4ClassRunner(testClass) {
			@Override
			protected boolean isIgnored(FrameworkMethod child) {
				return super.isIgnored(child) || child.getName().startsWith("ignore");
			}
		};

	}
}
