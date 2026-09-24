package smsk.smoothscroll.mixin.CreativeScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import smsk.smoothscroll.SmoothSc;
import smsk.smoothscroll.cfg.SmScCfg;

@Mixin(CreativeInventoryScreen.class)
public class CreativeScreenMixin {

    @Inject(method = "setSelectedTab", at = @At("TAIL"))
    private void setSelectedTabT(ItemGroup group, CallbackInfo ci) {
        SmoothSc.creativeScreenScrollOffset = 0;
    }

    @WrapOperation(
        method = "drawBackground",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIII)V"
        )
    )
    private void smoothscroll$drawBackground(
        DrawContext context,
        RenderPipeline renderPipeline,
        Identifier texture,
        int x,
        int y,
        float u,
        float v,
        int width,
        int height,
        int textureWidth,
        int textureHeight,
        Operation<Void> original
    ) {
        if (!smoothscroll$shouldAnimateCreativeBackground()) {
            original.call(context, renderPipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
            return;
        }

        SmoothSc.creativeScreenScrollOffset = (float) (
            SmoothSc.creativeScreenScrollOffset
                * Math.pow(SmScCfg.creativeScreenSmoothness, SmoothSc.getLastFrameDuration())
        );

        SmoothSc.creativeScreenScrollMixin = false;
        SmoothSc.creativeSH.scrollItems(
            ((CreativeScreenHandlerAccessor) SmoothSc.creativeSH)
                .getPos(SmoothSc.creativeScreenPrevRow - SmoothSc.getCreativeScrollOffset() / 18)
        );
        SmoothSc.creativeScreenScrollMixin = true;

        int inventoryX = x + 8;
        int inventoryY = y + 17;
        int inventoryWidth = 162;
        int inventoryHeight = 90;
        float inventoryU = u + 8;
        float inventoryV = v + 17;

        // Do not draw the static inventory viewport first. Drawing it again as the
        // moving viewport blends translucent resource-pack pixels twice.
        smoothscroll$drawBackgroundOutsideInventory(
            context,
            renderPipeline,
            texture,
            x,
            y,
            u,
            v,
            width,
            height,
            textureWidth,
            textureHeight,
            inventoryX,
            inventoryY,
            inventoryWidth,
            inventoryHeight,
            original
        );

        int drawOffset = SmoothSc.getCreativeDrawOffset();
        int repeatOffset = -inventoryHeight * Integer.signum(SmoothSc.getCreativeScrollOffset());

        context.enableScissor(
            inventoryX,
            inventoryY + 1,
            inventoryX + inventoryWidth,
            inventoryY + inventoryHeight - 1
        );
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0, drawOffset);

        if (SmScCfg.creativeUseScissorTexture) {
            // Preserve the original full GUI draw call. Resource packs that alter
            // GUI quad size or position in a shader can depend on those dimensions.
            // Each repeated frame is clipped to the part of its *inventory* region
            // that actually intersects the fixed viewport. This prevents both gaps
            // at the leading edge and double-blending of translucent pack pixels.
            smoothscroll$drawCompatibilityFrame(
                context,
                renderPipeline,
                texture,
                x,
                y,
                u,
                v,
                width,
                height,
                textureWidth,
                textureHeight,
                inventoryX,
                inventoryY,
                inventoryWidth,
                inventoryHeight,
                drawOffset,
                original
            );

            context.getMatrices().translate(0, repeatOffset);

            smoothscroll$drawCompatibilityFrame(
                context,
                renderPipeline,
                texture,
                x,
                y,
                u,
                v,
                width,
                height,
                textureWidth,
                textureHeight,
                inventoryX,
                inventoryY,
                inventoryWidth,
                inventoryHeight,
                drawOffset + repeatOffset,
                original
            );
        } else {
            original.call(
                context,
                renderPipeline,
                texture,
                inventoryX,
                inventoryY,
                inventoryU,
                inventoryV,
                inventoryWidth,
                inventoryHeight,
                textureWidth,
                textureHeight
            );

            // The 1.21.8 renderer's offset is signed. Repeat the texture on the
            // side from which a new row is entering, matching the original 2.3.1
            // implementation instead of always placing the repeat below.
            context.getMatrices().translate(0, repeatOffset);

            original.call(
                context,
                renderPipeline,
                texture,
                inventoryX,
                inventoryY,
                inventoryU,
                inventoryV,
                inventoryWidth,
                inventoryHeight,
                textureWidth,
                textureHeight
            );
        }

        context.getMatrices().popMatrix();

        if (SmScCfg.enableMaskDebug) {
            context.fill(
                -100,
                -100,
                context.getScaledWindowWidth(),
                context.getScaledWindowHeight(),
                ColorHelper.getArgb(50, 255, 255, 0)
            );
        }

        context.disableScissor();
    }

    @Unique
    private static boolean smoothscroll$shouldAnimateCreativeBackground() {
        if (SmoothSc.getCreativeScrollOffset() == 0
            || SmScCfg.creativeScreenSmoothness == 0
            || SmoothSc.creativeSH == null) {
            return false;
        }

        return !(FabricLoader.getInstance().getObjectShare().get("flow:is_caching_screen") instanceof Boolean isCaching)
            || !isCaching;
    }

    @Unique
    private static void smoothscroll$drawBackgroundOutsideInventory(
        DrawContext context,
        RenderPipeline renderPipeline,
        Identifier texture,
        int x,
        int y,
        float u,
        float v,
        int width,
        int height,
        int textureWidth,
        int textureHeight,
        int inventoryX,
        int inventoryY,
        int inventoryWidth,
        int inventoryHeight,
        Operation<Void> original
    ) {
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        smoothscroll$drawClipped(
            context, 0, 0, screenWidth, inventoryY + 1,
            renderPipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight, original
        );
        smoothscroll$drawClipped(
            context, 0, inventoryY + inventoryHeight - 1, screenWidth, screenHeight,
            renderPipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight, original
        );
        smoothscroll$drawClipped(
            context, 0, inventoryY + 1, inventoryX, inventoryY + inventoryHeight - 1,
            renderPipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight, original
        );
        smoothscroll$drawClipped(
            context, inventoryX + inventoryWidth, inventoryY + 1, screenWidth, inventoryY + inventoryHeight - 1,
            renderPipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight, original
        );
    }

    @Unique
    private static void smoothscroll$drawCompatibilityFrame(
        DrawContext context,
        RenderPipeline renderPipeline,
        Identifier texture,
        int x,
        int y,
        float u,
        float v,
        int width,
        int height,
        int textureWidth,
        int textureHeight,
        int inventoryX,
        int inventoryY,
        int inventoryWidth,
        int inventoryHeight,
        int frameOffsetY,
        Operation<Void> original
    ) {
        int viewportMinY = inventoryY + 1;
        int viewportMaxY = inventoryY + inventoryHeight - 1;
        int frameMinY = inventoryY + frameOffsetY;
        int frameMaxY = frameMinY + inventoryHeight;

        int minY = Math.max(viewportMinY, frameMinY);
        int maxY = Math.min(viewportMaxY, frameMaxY);

        if (maxY <= minY) {
            return;
        }

        context.enableScissor(inventoryX, minY, inventoryX + inventoryWidth, maxY);
        original.call(context, renderPipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        context.disableScissor();
    }

    @Unique
    private static void smoothscroll$drawClipped(
        DrawContext context,
        int minX,
        int minY,
        int maxX,
        int maxY,
        RenderPipeline renderPipeline,
        Identifier texture,
        int x,
        int y,
        float u,
        float v,
        int width,
        int height,
        int textureWidth,
        int textureHeight,
        Operation<Void> original
    ) {
        if (maxX <= minX || maxY <= minY) {
            return;
        }

        context.enableScissor(minX, minY, maxX, maxY);
        original.call(context, renderPipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        context.disableScissor();
    }
}
