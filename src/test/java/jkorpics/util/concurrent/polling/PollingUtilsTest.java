package jkorpics.util.concurrent.polling;

import org.junit.Test;

import java.util.function.Supplier;

import static org.junit.Assert.*;

public class PollingUtilsTest {

    private static class CountingSupplier implements Supplier<Boolean> {
        private int calledCount = 0;
        private int trueAfter;

        CountingSupplier(int trueAfter) {
            this.trueAfter = trueAfter;
        }

        public Boolean get() {
            return trueAfter == calledCount++;
        }

        int getCalledCount() {
            return calledCount;
        }
    }

    @Test
    public void testPredicateTrue() {
        CountingSupplier supplier =  new CountingSupplier(3);
        boolean result = PollingUtils.waitForPredicate(CountingSupplier::get, supplier, 2L, 100L);
        assertEquals(4, supplier.getCalledCount());
        assertTrue(result);
    }

    @Test
    public void testPredicateFalse() {
        CountingSupplier supplier =  new CountingSupplier(1000);
        boolean result = PollingUtils.waitForPredicate(CountingSupplier::get, supplier, 2L, 100L);
        assertEquals(51, supplier.getCalledCount());
        assertFalse(result);
    }

    @Test
    public void testSupplierTrue() {
        CountingSupplier supplier  = new CountingSupplier(3);
        boolean result = PollingUtils.waitForSupplier(supplier, 2L, 100L);
        assertEquals(4, supplier.getCalledCount());
        assertTrue(result);
    }

    @Test
    public void testSupplierFalse() {
        CountingSupplier supplier  = new CountingSupplier(1000);
        boolean result = PollingUtils.waitForSupplier(supplier, 2L, 100L);
        assertEquals(51, supplier.getCalledCount());
        assertFalse(result);
    }

}