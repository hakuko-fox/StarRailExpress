package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.api.RoleSkill;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillCooldownAccountingTest {
    private RoleSkill.Builder skill() {
        return RoleSkill.Definition.builder(ResourceLocation.parse("test:form"), "test.form", context -> true)
                .cooldownSeconds(10);
    }

    @Test
    void ordinarySkillsStillStartCooldownAndConsumeCharges() {
        var state = new SREAbilityPlayerComponent.SkillState();
        state.markUsed(skill().charges(2).build());
        assertEquals(200, state.cooldown);
        assertEquals(1, state.charges);
        assertEquals(1, state.castCount);
    }

    @Test
    void formActivationDoesNotStartCooldown() {
        var state = new SREAbilityPlayerComponent.SkillState();
        state.markUsed(skill().toggleable(true).manualCooldown().build());
        assertEquals(0, state.cooldown);
        assertEquals(1, state.castCount);
    }

    @Test
    void formDeactivationKeepsTheHandlersCooldownAndChargeAccounting() {
        var state = new SREAbilityPlayerComponent.SkillState();
        state.cooldown = 400;
        state.markUsed(skill().manualCooldown().charges(1).build());
        assertEquals(400, state.cooldown);
        assertEquals(0, state.charges);
    }
}
