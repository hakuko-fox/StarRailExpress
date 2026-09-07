package org.agmas.noellesroles.mixin;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrashReportCategoryMixinTest {

    @Test
    void acceptsMatchingNullFileNames() throws ReflectiveOperationException {
        assertTrue(fileNamesEqual(null, null));
    }

    @Test
    void rejectsDifferentFileNames() throws ReflectiveOperationException {
        assertFalse(fileNamesEqual(null, "Example.java"));
    }

    @Test
    void uniqueStaticHelperIsPrivateForMixinValidation() throws NoSuchMethodException {
        Method method = fileNamesEqualMethod();
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
    }

    private static boolean fileNamesEqual(String fileName, Object otherFileName)
            throws ReflectiveOperationException {
        Method method = fileNamesEqualMethod();
        method.setAccessible(true);
        return (boolean) method.invoke(null, fileName, otherFileName);
    }

    private static Method fileNamesEqualMethod() throws NoSuchMethodException {
        return CrashReportCategoryMixin.class.getDeclaredMethod(
                "starRailExpress$fileNamesEqual",
                String.class,
                Object.class
        );
    }
}
