# concurrent-utils
Utility classes for use in concurrent java programming.

BoundedReadWriteLock is a wrapper around ReentrantLock, which allows for limiting the number of outstanding read locks for throttling or load management.

PollingUtils is a utility class that provide convenient methods to wait for a condition to be true. Particularly useful for unit tests that have to wait for the side effects of a concurrent Thread.
