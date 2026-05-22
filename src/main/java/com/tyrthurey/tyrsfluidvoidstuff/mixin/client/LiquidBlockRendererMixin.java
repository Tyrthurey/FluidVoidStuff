package com.tyrthurey.tyrsfluidvoidstuff.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Renders fluids smoothly fading into the void at world bottom.
 * Ported from FluidVoidFading (1.21 Fabric branch) to NeoForge 1.21.1.
 */
@Mixin(LiquidBlockRenderer.class)
public abstract class LiquidBlockRendererMixin {

    @Shadow
    private TextureAtlasSprite waterOverlay;

    @Shadow
    private static boolean isNeighborSameFluid(FluidState firstState, FluidState secondState) {
        throw new AssertionError();
    }

    @Shadow
    protected abstract int getLightColor(BlockAndTintGetter level, BlockPos pos);

    @Inject(method = "tesselate", at = @At("HEAD"))
    public void fluidVoidFading$render(BlockAndTintGetter level, BlockPos pos, VertexConsumer consumer, BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        if (fluidVoidFading$shouldFadeBelow(level, pos, fluidState)) {
            fluidVoidFading$renderFluidInVoid(level, pos, consumer, fluidState);
        }
    }

    @Unique
    private static boolean fluidVoidFading$isDirectlyAboveVoid(BlockGetter level, BlockPos pos) {
        return pos.getY() == level.getMinBuildHeight();
    }

    /**
     * Trigger for drawing the fade gradient below this fluid block. True when:
     *  - the block is the last one above the void (vanilla behaviour), OR
     *  - the block directly below is air AND this block holds a flowing (non-source)
     *    fluid -- i.e. the bottom of a falling fluid column that ends in mid-air,
     *    which is what happens when the server-side flow cap truncates a column.
     */
    @Unique
    private static boolean fluidVoidFading$shouldFadeBelow(BlockGetter level, BlockPos pos, FluidState fluidState) {
        if (fluidVoidFading$isDirectlyAboveVoid(level, pos)) {
            return true;
        }
        if (fluidState == null || fluidState.isEmpty() || fluidState.isSource()) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        return belowState.isAir() && belowState.getFluidState().isEmpty();
    }

    @Inject(method = "isFaceOccludedByNeighbor", at = @At("HEAD"), cancellable = true)
    private static void fluidVoidFading$isFaceOccludedByNeighbor(BlockGetter level, BlockPos pos, Direction direction, float maxDeviation, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (direction == Direction.DOWN && fluidVoidFading$isDirectlyAboveVoid(level, pos)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void fluidVoidFading$renderFluidInVoid(BlockAndTintGetter level, BlockPos pos, VertexConsumer consumer, FluidState fluidState) {
        Fluid fluid = fluidState.getType();
        if (fluid == Fluids.EMPTY) {
            return;
        }

        BlockState northBlockState = level.getBlockState(pos.relative(Direction.NORTH));
        FluidState northFluidState = northBlockState.getFluidState();
        BlockState southBlockState = level.getBlockState(pos.relative(Direction.SOUTH));
        FluidState southFluidState = southBlockState.getFluidState();
        BlockState westBlockState = level.getBlockState(pos.relative(Direction.WEST));
        FluidState westFluidState = westBlockState.getFluidState();
        BlockState eastBlockState = level.getBlockState(pos.relative(Direction.EAST));
        FluidState eastFluidState = eastBlockState.getFluidState();

        boolean sameFluidNorth = isNeighborSameFluid(fluidState, northFluidState);
        boolean sameFluidSouth = isNeighborSameFluid(fluidState, southFluidState);
        boolean sameFluidWest = isNeighborSameFluid(fluidState, westFluidState);
        boolean sameFluidEast = isNeighborSameFluid(fluidState, eastFluidState);

        float brightnessUp = level.getShade(Direction.UP, true);
        float brightnessNorth = level.getShade(Direction.NORTH, true);
        float brightnessWest = level.getShade(Direction.WEST, true);

        float n = 1.0F;
        float o = 1.0F;
        float p = 1.0F;
        float q = 1.0F;
        float d = (pos.getX() & 15);
        float e = (pos.getY() & 15);
        float r = (pos.getZ() & 15);
        float t = 0.0F;
        float ca = 0;
        float cb;
        float u1;
        float u2;

        int light = this.getLightColor(level, pos);

        // NeoForge: get sprite + tint via IClientFluidTypeExtensions
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluidState);
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getModelManager()
                .getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(ext.getFlowingTexture(fluidState, level, pos));
        TextureAtlasSprite stillSprite = Minecraft.getInstance()
                .getModelManager()
                .getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(ext.getStillTexture(fluidState, level, pos));
        int color = ext.getTintColor(fluidState, level, pos);
        int[] colors = fluidVoidFading$unpackColor(color);

        float redF = colors[1] / 255F;
        float greenF = colors[2] / 255F;
        float blueF = colors[3] / 255F;

        float alpha1 = colors[0] / 255F;
        float alpha2 = 0.3F * (colors[0] / 255F);
        float alpha3 = 0.0F;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            float x1;
            float z1;
            float x2;
            float z2;
            boolean shouldRender;
            if (direction == Direction.NORTH) {
                ca = n;
                cb = q;
                x1 = d;
                x2 = d + 1.0F;
                z1 = r + 0.001F;
                z2 = r + 0.001F;
                shouldRender = sameFluidNorth;
            } else if (direction == Direction.SOUTH) {
                cb = o;
                x1 = d + 1.0F;
                x2 = d;
                z1 = r + 1.0F - 0.001F;
                z2 = r + 1.0F - 0.001F;
                shouldRender = sameFluidSouth;
            } else if (direction == Direction.WEST) {
                ca = o;
                cb = n;
                x1 = d + 0.001F;
                x2 = d + 0.001F;
                z1 = r + 1.0F;
                z2 = r;
                shouldRender = sameFluidWest;
            } else {
                ca = q;
                cb = p;
                x1 = d + 1.0F - 0.001F;
                x2 = d + 1.0F - 0.001F;
                z1 = r;
                z2 = r + 1.0F;
                shouldRender = sameFluidEast;
            }

            if (shouldRender) {
                continue;
            }

            u1 = sprite.getU(0.0F);
            u2 = sprite.getU(0.5F);
            float v1 = sprite.getV((1.0F - ca) * 0.5F);
            float v2 = sprite.getV((1.0F - cb) * 0.5F);
            float v3 = sprite.getV(0.5F);

            float sidedBrightness = direction.getAxis() == Direction.Axis.Z ? brightnessNorth : brightnessWest;
            float red = brightnessUp * sidedBrightness * redF;
            float green = brightnessUp * sidedBrightness * greenF;
            float blue = brightnessUp * sidedBrightness * blueF;

            // Normal pointing outward from the block, per face direction. Using the
            // correct horizontal normal (instead of +Y) ensures the chunk shader's
            // directional/diffuse lighting treats these as vertical side faces, so
            // they shade identically when viewed from above and from below.
            float nx = direction.getStepX();
            float ny = 0.0F;
            float nz = direction.getStepZ();

            fluidVoidFading$vertex(consumer, x1, e + ca - 1, z1, red, green, blue, u1, v1, light, alpha1, nx, ny, nz);
            fluidVoidFading$vertex(consumer, x2, e + cb - 1, z2, red, green, blue, u2, v2, light, alpha1, nx, ny, nz);
            fluidVoidFading$vertex(consumer, x2, e + t - 1, z2, red, green, blue, u2, v3, light, alpha2, nx, ny, nz);
            fluidVoidFading$vertex(consumer, x1, e + t - 1, z1, red, green, blue, u1, v3, light, alpha2, nx, ny, nz);

            fluidVoidFading$vertex(consumer, x1, e + ca - 2, z1, red, green, blue, u1, v1, light, alpha2, nx, ny, nz);
            fluidVoidFading$vertex(consumer, x2, e + cb - 2, z2, red, green, blue, u2, v2, light, alpha2, nx, ny, nz);
            fluidVoidFading$vertex(consumer, x2, e + t - 2, z2, red, green, blue, u2, v3, light, alpha3, nx, ny, nz);
            fluidVoidFading$vertex(consumer, x1, e + t - 2, z1, red, green, blue, u1, v3, light, alpha3, nx, ny, nz);

            // Always emit the reverse-winding (back-facing) quads so the gradient
            // remains visible and identically shaded when viewed from below the
            // fluid column, even when the front face would be back-face culled.
            // (Previously this was gated behind `sprite != waterOverlay`, leaving
            // the gradient effectively invisible / much more transparent from
            // below in the overlay case, and the back side relied on a +Y normal
            // that disagreed with the front face's lighting.)
            fluidVoidFading$vertex(consumer, x1, e + t - 1, z1, red, green, blue, u1, v3, light, alpha2, -nx, ny, -nz);
            fluidVoidFading$vertex(consumer, x2, e + t - 1, z2, red, green, blue, u2, v3, light, alpha2, -nx, ny, -nz);
            fluidVoidFading$vertex(consumer, x2, e + cb - 1, z2, red, green, blue, u2, v2, light, alpha1, -nx, ny, -nz);
            fluidVoidFading$vertex(consumer, x1, e + ca - 1, z1, red, green, blue, u1, v1, light, alpha1, -nx, ny, -nz);

            fluidVoidFading$vertex(consumer, x1, e + t - 2, z1, red, green, blue, u1, v3, light, alpha3, -nx, ny, -nz);
            fluidVoidFading$vertex(consumer, x2, e + t - 2, z2, red, green, blue, u2, v3, light, alpha3, -nx, ny, -nz);
            fluidVoidFading$vertex(consumer, x2, e + cb - 2, z2, red, green, blue, u2, v2, light, alpha2, -nx, ny, -nz);
            fluidVoidFading$vertex(consumer, x1, e + ca - 2, z1, red, green, blue, u1, v1, light, alpha2, -nx, ny, -nz);
        }

        // Bottom cap: a downward-facing quad at the bottom of the actual fluid block
        // (y == e) so that a viewer looking up from below the void column sees the
        // fluid's still surface texture instead of an empty hollow column.
        float bu1 = stillSprite.getU(0.0F);
        float bu2 = stillSprite.getU(1.0F);
        float bv1 = stillSprite.getV(0.0F);
        float bv2 = stillSprite.getV(1.0F);
        float bRed = brightnessUp * redF;
        float bGreen = brightnessUp * greenF;
        float bBlue = brightnessUp * blueF;
        float capAlpha = alpha1 * 0.25F;
        // Wound so the visible (front) face points DOWN (-Y): viewer below sees it,
        // viewer above (inside the column) does not.
        fluidVoidFading$vertexDown(consumer, d,        e, r,        bRed, bGreen, bBlue, bu1, bv1, light, capAlpha);
        fluidVoidFading$vertexDown(consumer, d + 1.0F, e, r,        bRed, bGreen, bBlue, bu2, bv1, light, capAlpha);
        fluidVoidFading$vertexDown(consumer, d + 1.0F, e, r + 1.0F, bRed, bGreen, bBlue, bu2, bv2, light, capAlpha);
        fluidVoidFading$vertexDown(consumer, d,        e, r + 1.0F, bRed, bGreen, bBlue, bu1, bv2, light, capAlpha);
    }

    @Unique
    private void fluidVoidFading$vertexDown(VertexConsumer consumer, float x, float y, float z, float red, float green, float blue, float u, float v, int light, float alpha) {
        consumer.addVertex(x, y, z).setColor(red, green, blue, alpha).setUv(u, v).setLight(light).setNormal(0.0F, -1.0F, 0.0F);
    }

    @Unique
    private static int[] fluidVoidFading$unpackColor(int color) {
        final int[] colors = new int[4];
        colors[0] = color >> 24 & 0xff; // alpha
        colors[1] = color >> 16 & 0xff; // red
        colors[2] = color >> 8 & 0xff; // green
        colors[3] = color & 0xff; // blue
        return colors;
    }

    @Unique
    private void fluidVoidFading$vertex(VertexConsumer consumer, float x, float y, float z, float red, float green, float blue, float u, float v, int light, float alpha, float nx, float ny, float nz) {
        consumer.addVertex(x, y, z).setColor(red, green, blue, alpha).setUv(u, v).setLight(light).setNormal(nx, ny, nz);
    }
}
