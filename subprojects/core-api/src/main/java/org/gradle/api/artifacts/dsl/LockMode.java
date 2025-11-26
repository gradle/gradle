 *     <li>{@code DEFAULT} will load the lock state and verify resolution matches it</li>
 *     <li>{@code STRICT} in addition to the {@code DEFAULT} behavior, will fail resolution if a locked configuration does not have lock state defined</li>
 *     <li>{@code LENIENT} will load the lock state, to anchor dynamic versions, but otherwise be lenient about modifications of the dependency resolution,
 *     allowing versions to change and module to be added or removed</li>
