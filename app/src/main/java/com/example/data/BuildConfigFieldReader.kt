package com.example.data

object BuildConfigFieldReader {
    /**
     * Reflectively reads a String field from BuildConfig.
     * Prevents JVM Verifier / LinkageErrors (e.g. NoSuchFieldError) if the field
     * is not compiled into the BuildConfig class.
     */
    fun getFieldString(fieldName: String): String {
        return try {
            val clazz = Class.forName("com.example.BuildConfig")
            val field = clazz.getField(fieldName)
            val value = field.get(null)
            value as? String ?: ""
        } catch (e: Throwable) {
            ""
        }
    }
}
