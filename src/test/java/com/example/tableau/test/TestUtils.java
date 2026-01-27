package com.example.tableau.test;

/**
 * Utility class for test helpers.
 */
public class TestUtils {

    /**
     * Set a private field value using reflection.
     * Useful for testing when direct access to private fields is needed.
     * 
     * @param target The object containing the field
     * @param fieldName The name of the field to set
     * @param value The value to set
     * @throws RuntimeException if the field cannot be accessed or set
     */
    public static void setPrivateField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    /**
     * Get a private field value using reflection.
     * Useful for testing when direct access to private fields is needed.
     * 
     * @param target The object containing the field
     * @param fieldName The name of the field to get
     * @return The value of the field
     * @throws RuntimeException if the field cannot be accessed
     */
    public static Object getPrivateField(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field " + fieldName, e);
        }
    }

    private TestUtils() {
        // Utility class - prevent instantiation
    }
}
