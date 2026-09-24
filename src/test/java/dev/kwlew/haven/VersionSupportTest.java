package dev.kwlew.haven;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionSupportTest {

    @Test
    void acceptsSupportedEndpointsAndIntermediates() {
        assertTrue(VersionSupport.supports("1.18.2-R0.1-SNAPSHOT"));
        assertTrue(VersionSupport.supports("1.21.11-R0.1-SNAPSHOT"));
        assertTrue(VersionSupport.supports("26.3.build.32-alpha"));
    }

    @Test
    void rejectsOlderOrUnrecognizableVersions() {
        assertFalse(VersionSupport.supports("1.18.1-R0.1-SNAPSHOT"));
        assertFalse(VersionSupport.supports("1.17.1-R0.1-SNAPSHOT"));
        assertFalse(VersionSupport.supports("unknown"));
    }

    @Test
    void selectsBrigadierOnlyFromPaper1206() {
        assertFalse(VersionSupport.supportsBrigadier("1.18.2-R0.1-SNAPSHOT"));
        assertFalse(VersionSupport.supportsBrigadier("1.20.4-R0.1-SNAPSHOT"));
        assertTrue(VersionSupport.supportsBrigadier("1.20.6-R0.1-SNAPSHOT"));
        assertTrue(VersionSupport.supportsBrigadier("1.21.11-R0.1-SNAPSHOT"));
        assertTrue(VersionSupport.supportsBrigadier("26.3.build.38-alpha"));
    }
}
