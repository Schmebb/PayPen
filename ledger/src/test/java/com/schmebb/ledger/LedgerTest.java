package com.schmebb.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LedgerTest {

    @Test
    void hasName() {
        assertEquals("ledger", new Ledger().name());
    }
}
