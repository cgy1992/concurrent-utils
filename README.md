# concurrent-utils
Utility classes for use in concurrent java programming.

BoundedReadWriteLock is a wrapper around ReentrantLock, which allows for limiting the number of outstanding read locks for throttling or load management.
