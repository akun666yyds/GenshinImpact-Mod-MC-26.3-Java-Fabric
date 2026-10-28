package com.akun.xinkun.entity;

import com.akun.xinkun.GenshinCharacter;
import com.akun.xinkun.entity.ai.HuTaoFireMagicGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class HuTaoEntity extends GenshinNpcEntity {
    public HuTaoEntity(EntityType<? extends HuTaoEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public GenshinCharacter getCharacter() {
        return GenshinCharacter.HU_TAO;
    }

    @Override
    protected void registerCharacterGoals() {
        // Fire magic outranks the shared melee goal, so it is her main attack.
        this.goalSelector.addGoal(2, new HuTaoFireMagicGoal(this));
    }
}
