package com.vladosiwe.solostatus.client.ui;

import com.vladosiwe.solostatus.SoloStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Smooth text for the System's Exo 2 fonts.
 *
 * Minecraft rasterises a TTF glyph at size × oversample pixels and draws it at "size" GUI pixels. Its glyph sheets
 * are sampled without filtering when shrunk, so at GUI scale 2–3 (below the oversample) the letters lose pixels
 * and look ragged. Here every glyph sheet of the System's fonts gets linear filtering, so text is antialiased
 * at any GUI scale. Sheets are created lazily as new characters appear, so new ones are picked up every frame.
 */
@EventBusSubscriber(modid = SoloStatus.MODID, value = Dist.CLIENT)
public final class FontSmoothing {
    private static final ResourceLocation[] FONTS = {SystemFonts.BODY, SystemFonts.BOLD, SystemFonts.TITLE};
    private static final Set<AbstractTexture> SMOOTHED = Collections.newSetFromMap(new IdentityHashMap<>());

    private FontSmoothing() {}

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation font : FONTS) {
            for (int page = 0; page < 256; page++) {
                AbstractTexture sheet = textures.getTexture(font.withSuffix("/" + page), null);
                if (sheet == null) break; // sheets are numbered without gaps
                if (SMOOTHED.add(sheet)) sheet.setFilter(true, false);
            }
        }
        if (SMOOTHED.size() > 4096) SMOOTHED.clear(); // fonts reloaded many times: forget closed sheets
    }
}
