package com.example.tableau;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "tableau.base-url=https://test.tableau.com",
    "tableau.pat.name=test-token",
    "tableau.pat.secret=test-secret"
})
class TableauLineageSyncApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the Spring application context loads successfully
    }
}
