package jkorpics.util.concurrent.polling;

import java.util.function.Predicate;
import java.util.function.Supplier;

public class PollingUtils {

    /**
     * Poll condition at regular intervals until the Predicate condition is true
     * or until a max time limit is reached. This method can help in unit testing rather
     * than just sleeping, which can cause random test failures.
     * @param condition - the condition function to call at regular intervals
     * @param object - object for which a condition will be tested at regular intervals
     * @param intervalMs - interval in ms between each condition check.
     * @param maxTimeMs - max amount of time in ms to wait for the condition
     * @param <T> - type of object for which we are checking the condition
     * @return - True if the condition is met within the given timeframe, False otherwise.
     */
    public static <T> boolean waitForCondition(final Predicate<T> condition,
                                               T object,
                                               long intervalMs,
                                               long maxTimeMs) {
        long totalWaitTime = 0L;
        boolean conditionMet = condition.test(object);
        while(totalWaitTime < maxTimeMs && ! conditionMet) {
            try {
                Thread.sleep(intervalMs);
                totalWaitTime += intervalMs;
                conditionMet = condition.test(object);
            } catch(InterruptedException e) {
                totalWaitTime = maxTimeMs;
            }
        }
        return conditionMet;
    }

    /**
     * Poll condition at regular intervals until the Supplier condition returns true
     * or until a max time limit is reached. This method can help in unit testing rather
     * than just sleeping, which can cause random test failures.
     * @param condition - the condition function to call at regular intervals
     * @param intervalMs - the interval in ms between each condition check.
     * @param maxTimeMs - max amount of time in ms to wait for the condition\
     * @return - True if the condition is met within the given timeframe, False otherwise.
     */
    public static <T> boolean waitForCondition(final Supplier<Boolean> condition,
                                               long intervalMs,
                                               long maxTimeMs) {
        long totalWaitTime = 0L;
        boolean conditionMet = condition.get();
        while (totalWaitTime < maxTimeMs && !conditionMet) {
            try {
                Thread.sleep(intervalMs);
                totalWaitTime += intervalMs;
                conditionMet = condition.get();
            } catch (InterruptedException e) {
                totalWaitTime = maxTimeMs;
            }
        }
        return conditionMet;
    }
}
