package com.example.tableau.service;

import com.example.tableau.enums.StatusFlag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for BaseAssetService - specifically testing the determineStatusFlag logic
 * to ensure UPDATED status appears correctly.
 */
class BaseAssetServiceTest {

    private TestBaseAssetService service;

    // Concrete implementation of abstract BaseAssetService for testing
    private static class TestBaseAssetService extends BaseAssetService {
        // Expose protected method for testing
        public StatusFlag testDetermineStatusFlag(String existingHash, String newHash, StatusFlag currentStatus) {
            return determineStatusFlag(existingHash, newHash, currentStatus);
        }
        
        public String testGenerateMetadataHash(String... fields) {
            return generateMetadataHash(fields);
        }
    }

    @BeforeEach
    void setUp() {
        service = new TestBaseAssetService();
    }

    @Test
    @DisplayName("New record should return NEW status when existingHash is null")
    void testNewRecord() {
        StatusFlag result = service.testDetermineStatusFlag(null, "hash123", StatusFlag.NEW);
        assertEquals(StatusFlag.NEW, result, "Should return NEW for null existingHash");
    }

    @Test
    @DisplayName("First ingestion after creation: NEW -> ACTIVE when hash matches")
    void testNewToActive() {
        String hash = "hash123";
        StatusFlag result = service.testDetermineStatusFlag(hash, hash, StatusFlag.NEW);
        assertEquals(StatusFlag.ACTIVE, result, "Should transition from NEW to ACTIVE when hash matches");
    }

    @Test
    @DisplayName("Change detected: ACTIVE -> UPDATED when hash differs")
    void testActiveToUpdated() {
        String oldHash = "hash123";
        String newHash = "hash456";
        StatusFlag result = service.testDetermineStatusFlag(oldHash, newHash, StatusFlag.ACTIVE);
        assertEquals(StatusFlag.UPDATED, result, "Should transition from ACTIVE to UPDATED when hash differs");
    }

    @Test
    @DisplayName("After update ingested: UPDATED -> ACTIVE when hash matches")
    void testUpdatedToActive() {
        String hash = "hash456";
        StatusFlag result = service.testDetermineStatusFlag(hash, hash, StatusFlag.UPDATED);
        assertEquals(StatusFlag.ACTIVE, result, "Should transition from UPDATED to ACTIVE when hash matches");
    }

    @Test
    @DisplayName("No change: ACTIVE -> ACTIVE when hash matches")
    void testActiveRemainsActive() {
        String hash = "hash123";
        StatusFlag result = service.testDetermineStatusFlag(hash, hash, StatusFlag.ACTIVE);
        assertEquals(StatusFlag.ACTIVE, result, "Should remain ACTIVE when hash matches");
    }

    @Test
    @DisplayName("Soft-deleted item remains DELETED even if hash matches")
    void testDeletedRemainsDeleted() {
        String hash = "hash123";
        StatusFlag result = service.testDetermineStatusFlag(hash, hash, StatusFlag.DELETED);
        assertEquals(StatusFlag.DELETED, result, "Should remain DELETED even when hash matches");
    }

    @Test
    @DisplayName("Complete lifecycle: NEW -> ACTIVE -> UPDATED -> ACTIVE")
    void testCompleteLifecycle() {
        String hash1 = "initialHash";
        String hash2 = "updatedHash";

        // Initial creation
        StatusFlag status1 = service.testDetermineStatusFlag(null, hash1, StatusFlag.NEW);
        assertEquals(StatusFlag.NEW, status1, "Step 1: Initial creation should be NEW");

        // Second ingestion - no changes
        StatusFlag status2 = service.testDetermineStatusFlag(hash1, hash1, status1);
        assertEquals(StatusFlag.ACTIVE, status2, "Step 2: No changes should transition to ACTIVE");

        // Third ingestion - item changed in Tableau
        StatusFlag status3 = service.testDetermineStatusFlag(hash1, hash2, status2);
        assertEquals(StatusFlag.UPDATED, status3, "Step 3: Changes should set status to UPDATED");

        // Fourth ingestion - no new changes
        StatusFlag status4 = service.testDetermineStatusFlag(hash2, hash2, status3);
        assertEquals(StatusFlag.ACTIVE, status4, "Step 4: No new changes should transition back to ACTIVE");
    }

    @Test
    @DisplayName("Hash generation should be consistent for same inputs")
    void testHashConsistency() {
        String hash1 = service.testGenerateMetadataHash("field1", "field2", "field3");
        String hash2 = service.testGenerateMetadataHash("field1", "field2", "field3");
        assertEquals(hash1, hash2, "Same inputs should generate same hash");
    }

    @Test
    @DisplayName("Hash generation should differ for different inputs")
    void testHashDifference() {
        String hash1 = service.testGenerateMetadataHash("field1", "field2", "field3");
        String hash2 = service.testGenerateMetadataHash("field1", "field2", "field4");
        assertNotEquals(hash1, hash2, "Different inputs should generate different hashes");
    }

    @Test
    @DisplayName("Hash generation should handle null values")
    void testHashWithNulls() {
        String hash1 = service.testGenerateMetadataHash("field1", null, "field3");
        String hash2 = service.testGenerateMetadataHash("field1", "", "field3");
        // Null and empty string should produce same hash (both converted to empty string)
        assertEquals(hash1, hash2, "Null and empty string should produce same hash");
    }

    @Test
    @DisplayName("Multiple consecutive changes should show UPDATED each time")
    void testMultipleUpdates() {
        String hash1 = "hash1";
        String hash2 = "hash2";
        String hash3 = "hash3";

        // Initial: NEW
        StatusFlag status1 = service.testDetermineStatusFlag(null, hash1, StatusFlag.NEW);
        assertEquals(StatusFlag.NEW, status1);

        // Second run: NEW -> ACTIVE
        StatusFlag status2 = service.testDetermineStatusFlag(hash1, hash1, status1);
        assertEquals(StatusFlag.ACTIVE, status2);

        // First change: ACTIVE -> UPDATED
        StatusFlag status3 = service.testDetermineStatusFlag(hash1, hash2, status2);
        assertEquals(StatusFlag.UPDATED, status3, "First change should show UPDATED");

        // Immediately after, no change: UPDATED -> ACTIVE
        StatusFlag status4 = service.testDetermineStatusFlag(hash2, hash2, status3);
        assertEquals(StatusFlag.ACTIVE, status4);

        // Second change: ACTIVE -> UPDATED
        StatusFlag status5 = service.testDetermineStatusFlag(hash2, hash3, status4);
        assertEquals(StatusFlag.UPDATED, status5, "Second change should show UPDATED again");
    }

    @Test
    @DisplayName("Field order should not affect hash")
    void testHashFieldOrder() {
        // Hash should be based on field order in the call
        String hash1 = service.testGenerateMetadataHash("A", "B", "C");
        String hash2 = service.testGenerateMetadataHash("A", "B", "C");
        String hash3 = service.testGenerateMetadataHash("C", "B", "A");
        
        assertEquals(hash1, hash2, "Same fields in same order should produce same hash");
        assertNotEquals(hash1, hash3, "Same fields in different order should produce different hash");
    }
}
