# Adding new progress event

* project `daemon-messaging`
  * `org.gradle.internal.build.event.types` 
     * `public class Default*Event extends AbstractProgressEvent<Internal*Descriptor> implements Serializable, Internal*Event`
     * `public class Default*Descriptor implements Serializable, Internal*Descriptor`
* project `tooling-api`
  * `org.gradle.tooling.internal.protocol`
     * `public interface Internal*Event extends InternalProgressEvent`
  * `org.gradle.tooling.internal.protocol.events`
    * `public interface Internal*Descriptor extends InternalOperationDescriptor`
  * `org.gradle.tooling.events.problems`
    * `public interface *Descriptor extends OperationDescriptor`
    * `public interface *Event extends ProgressEvent`
  * `org.gradle.tooling.events.problems.internal`
    * `public class Default*Event implements *Event`
    * `public class Default*OperationDescriptor extends DefaultOperationDescriptor implements *Descriptor`
