# Improvements to code coverage tools

## Common metrics as quality gates across different tools

Metrics produced by code coverage tools often act as quality gates for Continuous Integration pipelines. As such, these metrics are invaluable for controlling if a code change should continue
to travel further along in the pipeline. Exposed configuration options for code coverage tools enable users to conveniently set coverage thresholds based on specific criteria. Optimally, 
these configuration options define a common language based on terminology agreed upon in the industry and can be applied to a wide range of tools e.g. JaCoCo, Clover 
or Cobertura. 

The following aspects need to be taken under consideration to determine if an abstracted, common language makes sense:

### Criteria

There's a wide range of support for declaring metrics used as quality gates. Atlassian provides an [well-arranged overview](https://confluence.atlassian.com/display/CLOVER/Comparison+of+code+coverage+tools). 

- Class
- Method
- Statement
- Line
- Branch
- Complexity
- Instruction
- Total (include all criteria)

Most code coverage tools only support a limited set of those criteria.

### Threshold

Metrics thresholds are defined by percentage (0-100%) or by ratio (0.0-1.0). Some but not all tools allow for defining a minimum and maximum boundary.  

### Scope of threshold

Thresholds can be applied by a specific scope e.g. based on a missed or covered ratio. Most tools do not support defining a scope.

### Filter pattern

Some tools provide support for including and excluding instrumented code by filter pattern e.g. all classes matching `**/*Important.class`.

### Conclusion

Existing code coverage tools in the Java space differ widely by supported metric criteria and their configuration options. It would be very hard to find a common denominator across all
tools without introducing additional complexity. Furthermore, there are no plans to support other code coverage tools in addition to JaCoCo. As a result, a common DSL for modeling the code 
coverage domain is **not** going to be developed based on the pros and cons listed below.

**Benefits**

- Easy to introduce new rules or modifying existing rules without potentially affecting other code coverage plugins
- Ability to fully support all configuration options specific to one tool
- Isolated code base for a specific code coverage plugin (supports modularity without additional dependencies)
- Avoidance of a overly complex code base by allowing composable rules
- Less confusion as criteria terminology isn't necessarily standardized
- Users can easily refer to configuration options and what they mean by looking at the documentation of the code coverage tool (not the Gradle plugin)

**Drawbacks**

- No established, common language for the code coverage domain (it seems that it is only the case for the method, line and branch criteria)
- Plugin developers have no modeling guidance by looking at an established DSL for domain 

## Story: Expose configuration options for enforcing Jacoco code coverage metrics

[JaCoCo](https://docs.gradle.org/current/userguide/jacoco_plugin.html) is the only code coverage tool currently provided by Gradle core out-of-the-box. The Gradle plugin calls off to the Jacoco Ant 
tasks under the covers. The JaCoCo report Ant task provides configuration options for failing the build if certain metric threshold are not met by the instrumented code. The goal of this story
 is to extend the existing Gradle task by exposing configuration options for controlling coverage thresholds.

### User visible changes

- Introduce the following enums to represent data types exposed by JaCoCo.

<!-- -->

    public enum JacocoRuleScope {
        BUNDLE, PACKAGE, CLASS, SOURCEFILE, METHOD;
    }

    public enum JacocoThresholdMetric {
        INSTRUCTION, LINE, BRANCH, COMPLEXITY, METHOD, CLASS;
    }
    
    public enum JacocoThresholdValue {
        TOTALCOUNT, MISSEDCOUNT, COVEREDCOUNT, MISSEDRATIO, COVEREDRATIO;
    }

- Introduce new interfaces for configuring coverage rules.

<!-- -->

    public interface JacocoThreshold {
        void setMetric(JacocoThresholdMetric metric);
        void setValue(JacocoThresholdType type);
        void setMinimum(Double minimum);
        void setMaximum(Double maximum);
    }

    public interface JacocoViolationRule {
        void setEnabled(boolean enabled);
        boolean getEnabled();
        void setScope(JacocoRuleScope scope);
        void setIncludes(Collection<String> includes);
        void setExcludes(Collection<String> excludes);
        
        void threshold(Action<? super JacocoThreshold> configureAction) {
            ...
        }
    }

    public interface JacocoViolationRulesContainer {
        void setFailOnViolation(boolean failOnViolation);
        boolean getFailOnViolation();
        
        void rule(Action<? super JacocoViolationRule> configureAction) {
            ...
        }
    }

- Metric thresholds can be configured for any task of type `JacocoReport`.

<!-- -->

    tasks.withType(JacocoReport) {
        violationRules {
            failOnViolation = true
        
            rule {
                scope = JacocoRuleScope.PACKAGE
                includes = ['**/important/*']
                excludes = ['**/ignore/*']
                
                threshold {
                    metric = JacocoThresholdMetric.LINE
                    value = JacocoThresholdType.COVERED_RATIO
                    minimum = 0.3
                    maximum = 0.8
                }
                
                threshold {
                    ...
                }
            }
            
            rule {
                ...
            }
        }
    }

### Implementation

- Metric thresholds can be declared on a "per Test source set" basis. The `JacocoExtension` will not expose any global configuration options.
    - Zero or n rules can be defined.
    - Defined thresholds are propagated toward the JaCoCo Ant task ([see `check` element](http://www.eclemma.org/jacoco/trunk/doc/ant.html)). No further transformation or processing is needed.
- Introduce configuration option for enabling/disabling metric enforcement.
    - By default the build fails if metric thresholds have been defined but not met. 
    - Introduce a configuration option for disabling the default behavior e.g. `failOnViolation`. The default value is `false`.
    - Setting the property to `true` will continue the build and not fail the task.
    - Introduce a configuration option for disabling threshold check e.g. `enabled` for a rule. The default value is `true`.
- The JaCoCo report task observes the following runtime behavior:
    - Threshold checks will not have to be performed if no thresholds are defined.
    - If threshold(s) are defined but met then the task will execute successfully. No additional output is rendered.
    - If threshold(s) are defined but not met then the task will fail. The task renders threshold that have not been met including the their runtime value.
- Enhance the existing JaCoCo plugin user guide documentation by a section that explain the usage and runtime behavior of metric thresholds.
- Add a sample project to distribution that demonstrates the use of thresholds with the JaCoCo plugin.
- Enhance existing test coverage.

### Test coverage

- A JaCoCo report task does not define any thresholds. The task finishes successfully.
- A JaCoCo report task defines a single or multiple thresholds.
    - Multiple Threshold can be defined for the task in any order.
    - Thresholds can be defined with minimum and maximum boundary.
    - Threshold is met based on defined metrics. The task finishes successfully.
    - Threshold is not met and failure is not ignored. The task fails.
    - Threshold is not met but failure is ignored. The task finishes successfully.
    - All rules are disabled. The task finishes successfully even if threshold is not met.
    - Include/exclude filter patterns for rules apply as defined.
- Multiple JaCoCo report tasks defined for a project can use different thresholds.

### Open issues

- Usage of exact same configuration options used by Ant task to make it easier for users to find their way. The Maven plugin does the same.
