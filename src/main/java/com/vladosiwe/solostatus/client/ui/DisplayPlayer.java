package com.vladosiwe.solostatus.client.ui;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Копия охотника для окна Системы в режиме «без брони»: тот же профиль (а значит, тот же скин и модель рук),
 * те же видимые слои скина и предметы в руках, но без брони. Настоящий инвентарь не изменяется.
 * Копия не добавляется в мир; её позиция далеко от камеры, чтобы не рисовалась табличка с ником.
 */
public class DisplayPlayer extends RemotePlayer {
    public DisplayPlayer(ClientLevel level, AbstractClientPlayer source) {
        super(level, source.getGameProfile());
        this.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, source.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION));
        this.setItemSlot(EquipmentSlot.MAINHAND, source.getMainHandItem().copy());
        this.setItemSlot(EquipmentSlot.OFFHAND, source.getOffhandItem().copy());
        this.setPos(source.getX(), source.getY() - 4096, source.getZ());
    }
}
