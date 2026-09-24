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

    @Unique
    private static final int SMOOTHSCROLL_ROW_HEIGHT = 18;

    @Unique
    private static final int SMOOTHSCROLL_ROW_COUNT = 5;

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
                .getPos(SmoothSc.creativeScreenPrevRow - SmoothSc.getCreativeScrollOffset() / SMOOTHSCROLL_ROW_HEIGHT)
        );
        SmoothSc.creativeScreenScrollMixin = true;

        int inventoryX = x + 8;
        int inventoryY = y + 17;
        int inventoryWidth = SMOOTHSCROLL_ROW_HEIGHT * 9;
        int inventoryHeight = SMOOTHSCROLL_ROW_HEIGHT * SMOOTHSCROLL_ROW_COUNT;
        float inventoryU = u + 8;
        float inventoryV = v + 17;

        // The vanilla/background resource-pack draw still owns everything outside
        // the moving five-row slot viewport. Keeping the viewport out of this pass
        // avoids blending translucent pack pixels twice.
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

        if (SmScCfg.creativeUseScissorTexture) {
            /*
             * Resource-pack compatibility mode deliberately keeps the original
             * full-GUI draw call. Some packs use shaders that depend on the full
             * Creative GUI quad dimensions and break when Smooth Scrolling draws
             * only a 162x90 texture crop.
             *
             * Instead of moving two whole 90-pixel copies, render the five slot
             * rows as cyclic 18-pixel strips. Each strip is clipped to its own
             * destination band, and a wrapped copy is drawn when the strip crosses
             * the top or bottom edge. The strips therefore tile the viewport
             * without exposing adjacent GUI artwork or leaving an uncovered seam.
             */
            smoothscroll$drawCompatibilityRows(
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
        } else {
            context.enableScissor(
                inventoryX,
                inventoryY + 1,
                inventoryX + inventoryWidth,
                inventoryY + inventoryHeight - 1
            );
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0, drawOffset);

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

            // The 1.21.8 renderer has a signed offset. Repeat the cropped slot
            // texture on the side from which the next row is entering.
            int repeatOffset = -inventoryHeight * Integer.signum(SmoothSc.getCreativeScrollOffset());
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

            context.getMatrices().popMatrix();
            context.disableScissor();
        }

        if (SmScCfg.enableMaskDebug) {
            context.fill(
                -100,
                -100,
                context.getScaledWindowWidth(),
                context.getScaledWindowHeight(),
                ColorHelper.getArgb(50, 255, 255, 0)
            );
        }
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
    private static void smoothscroll$drawCompatibilityRows(
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
        int drawOffset,
        Operation<Void> original
    ) {
        int viewportMinY = inventoryY + 1;
        int viewportMaxY = inventoryY + inventoryHeight - 1;

        for (int row = 0; row < SMOOTHSCROLL_ROW_COUNT; row++) {
            int sourceRowY = inventoryY + row * SMOOTHSCROLL_ROW_HEIGHT;
            int destinationY = sourceRowY + drawOffset;

            // Normal position plus both cyclic neighbours. Because drawOffset is
            // modulo one slot row, only one neighbour can intersect the viewport.
            smoothscroll$drawCompatibilityRowCandidate(
                context, renderPipeline, texture,
                x, y, u, v, width, height, textureWidth, textureHeight,
                inventoryX, inventoryWidth,
                viewportMinY, viewportMaxY,
                sourceRowY, destinationY,
                original
            );
            smoothscroll$drawCompatibilityRowCandidate(
                context, renderPipeline, texture,
                x, y, u, v, width, height, textureWidth, textureHeight,
                inventoryX, inventoryWidth,
                viewportMinY, viewportMaxY,
                sourceRowY, destinationY - inventoryHeight,
                original
            );
            smoothscroll$drawCompatibilityRowCandidate(
                context, renderPipeline, texture,
                x, y, u, v, width, height, textureWidth, textureHeight,
                inventoryX, inventoryWidth,
                viewportMinY, viewportMaxY,
                sourceRowY, destinationY + inventoryHeight,
                original
            );
        }
    }

    @Unique
    private static void smoothscroll$drawCompatibilityRowCandidate(
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
        int inventoryWidth,
        int viewportMinY,
        int viewportMaxY,
        int sourceRowY,
        int destinationY,
        Operation<Void> original
    ) {
        int minY = Math.max(viewportMinY, destinationY);
        int maxY = Math.min(viewportMaxY, destinationY + SMOOTHSCROLL_ROW_HEIGHT);

        if (maxY <= minY) {
            return;
        }

        context.enableScissor(inventoryX, minY, inventoryX + inventoryWidth, maxY);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0, destinationY - sourceRowY);
        original.call(context, renderPipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        context.getMatrices().popMatrix();
        context.disableScissor();
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
