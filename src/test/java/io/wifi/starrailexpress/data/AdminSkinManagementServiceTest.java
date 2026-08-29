package io.wifi.starrailexpress.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.data.AdminSkinManagementService.Change;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminSkinManagementServiceTest {

    @Test
    void unlockAllPreservesExistingAndUnknownFields() {
        String source = """
                {
                  "equipped": {"knife": "ruby"},
                  "unlocked": {"knife": {"ruby": true}},
                  "coinNum": 17,
                  "websiteMetadata": {"source": "redeem_code"},
                  "version": 5
                }
                """;

        JsonObject merged = parse(AdminSkinManagementService.mergeChange(
                source, "knife", List.of("sapphire", "emerald"), Change.UNLOCK, 99L));

        assertEquals("ruby", merged.getAsJsonObject("equipped").get("knife").getAsString());
        assertTrue(merged.getAsJsonObject("unlocked").getAsJsonObject("knife").get("ruby").getAsBoolean());
        assertTrue(merged.getAsJsonObject("unlocked").getAsJsonObject("knife").get("sapphire").getAsBoolean());
        assertTrue(merged.getAsJsonObject("unlocked").getAsJsonObject("knife").get("emerald").getAsBoolean());
        assertEquals(17, merged.get("coinNum").getAsInt());
        assertEquals("redeem_code",
                merged.getAsJsonObject("websiteMetadata").get("source").getAsString());
        assertEquals(99L, merged.get("version").getAsLong());
    }

    @Test
    void unlockCreatesMissingUnlockedObjectsInFullSnapshot() {
        JsonObject merged = parse(AdminSkinManagementService.mergeChange(
                "{\"equipped\":{},\"lootChance\":2,\"coinNum\":3}",
                "hat", List.of("conductor"), Change.UNLOCK, 12L));

        assertTrue(merged.getAsJsonObject("unlocked").getAsJsonObject("hat").get("conductor").getAsBoolean());
        assertEquals(2, merged.get("lootChance").getAsInt());
        assertEquals(3, merged.get("coinNum").getAsInt());
    }

    @Test
    void lockAllRemovesOnlyRegisteredSkinsAndResetsMatchingEquippedSkin() {
        String source = """
                {
                  "equipped": {"knife": "ruby", "hat": "conductor"},
                  "unlocked": {
                    "knife": {"ruby": true, "sapphire": true, "website_only": true},
                    "hat": {"conductor": true}
                  },
                  "coinNum": 17,
                  "websiteMetadata": true
                }
                """;

        JsonObject merged = parse(AdminSkinManagementService.mergeChange(
                source, "knife", List.of("ruby", "sapphire"), Change.LOCK, 101L));

        JsonObject unlocked = merged.getAsJsonObject("unlocked");
        assertFalse(unlocked.getAsJsonObject("knife").has("ruby"));
        assertFalse(unlocked.getAsJsonObject("knife").has("sapphire"));
        assertTrue(unlocked.getAsJsonObject("knife").get("website_only").getAsBoolean());
        assertTrue(unlocked.getAsJsonObject("hat").get("conductor").getAsBoolean());
        assertEquals("default", merged.getAsJsonObject("equipped").get("knife").getAsString());
        assertEquals("conductor", merged.getAsJsonObject("equipped").get("hat").getAsString());
        assertEquals(17, merged.get("coinNum").getAsInt());
        assertTrue(merged.get("websiteMetadata").getAsBoolean());
        assertEquals(101L, merged.get("version").getAsLong());
    }

    @Test
    void lockAllPreservesUnknownEquippedSkinAndRemovesEmptyTypeEntry() {
        JsonObject merged = parse(AdminSkinManagementService.mergeChange(
                "{\"equipped\":{\"knife\":\"website_only\"},\"unlocked\":{\"knife\":{\"ruby\":true}}}",
                "knife", List.of("ruby"), Change.LOCK, 102L));

        assertEquals("website_only", merged.getAsJsonObject("equipped").get("knife").getAsString());
        assertFalse(merged.getAsJsonObject("unlocked").has("knife"));
    }

    @Test
    void equippedPartitionMergePreservesUnknownFieldsAndResetsOnlyRequestedType() {
        JsonObject merged = parse(AdminSkinManagementService.mergeEquippedPayload(
                "{\"equipped\":{\"knife\":\"ruby\",\"hat\":\"conductor\"},\"websiteMetadata\":true}",
                new JsonObject(), "knife", 103L, "Steve"));

        assertEquals("default", merged.getAsJsonObject("equipped").get("knife").getAsString());
        assertEquals("conductor", merged.getAsJsonObject("equipped").get("hat").getAsString());
        assertTrue(merged.get("websiteMetadata").getAsBoolean());
        assertEquals(103L, merged.get("updatedAt").getAsLong());
        assertEquals("Steve", merged.get("playerName").getAsString());
    }

    @Test
    void missingEquippedPartitionStartsFromCurrentFullEquippedSnapshot() {
        JsonObject currentEquipped = parse("{\"knife\":\"default\",\"hat\":\"conductor\"}");

        JsonObject merged = parse(AdminSkinManagementService.mergeEquippedPayload(
                null, currentEquipped, "knife", 104L, "Steve"));

        assertEquals("default", merged.getAsJsonObject("equipped").get("knife").getAsString());
        assertEquals("conductor", merged.getAsJsonObject("equipped").get("hat").getAsString());
    }

    @Test
    void mergeRejectsMalformedOrStructurallyUnsafePayloads() {
        assertThrows(RuntimeException.class,
                () -> AdminSkinManagementService.mergeChange(
                        "not-json", "knife", List.of("ruby"), Change.UNLOCK, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> AdminSkinManagementService.mergeChange(
                        "{\"unlocked\":[]}", "knife", List.of("ruby"), Change.UNLOCK, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> AdminSkinManagementService.mergeChange(
                        "{\"unlocked\":{\"knife\":true}}", "knife", List.of("ruby"), Change.LOCK, 1L));
        assertThrows(IllegalArgumentException.class,
                () -> AdminSkinManagementService.mergeChange(
                        "{\"equipped\":{\"knife\":[]}}", "knife", List.of("ruby"), Change.LOCK, 1L));
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
