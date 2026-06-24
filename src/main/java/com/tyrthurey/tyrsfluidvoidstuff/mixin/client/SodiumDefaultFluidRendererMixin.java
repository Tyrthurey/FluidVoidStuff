package com.tyrthurey.tyrsfluidvoidstuff.mixin.client;

import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.api.util.ColorARGB;
import net.caffeinemc.mods.sodium.api.util.ColorU8;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.light.LightMode;
import net.caffeinemc.mods.sodium.client.model.light.LightPipeline;
import net.caffeinemc.mods.sodium.client.model.light.LightPipelineProvider;
import net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadViewMutable;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.services.PlatformBlockAccess;
import net.caffeinemc.mods.sodium.client.util.DirectionUtil;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium-compatible variant: renders fluids smoothly fading into the void at world bottom.
 * Ported from FluidVoidFading (1.21 Fabric branch) to NeoForge 1.21.1, Sodium 0.6.x.
 */
@Pseudo
@Mixin(value = DefaultFluidRenderer.class, remap = false)
public abstract class SodiumDefaultFluidRendererMixin {

    @Shadow @Final private BlockPos.MutableBlockPos scratchPos;

    @Shadow @Final private ModelQuadViewMutable quad;
    @Shadow @Final private int[] quadColors;

    @Shadow
    private static void setVertex(ModelQuadViewMutable quad, int i, float x, float y, float z, float u, float v) {
    }

    @Shadow @Final private LightPipelineProvider lighters;

    @Shadow
    protected abstract void writeQuad(ChunkModelBuilder builder, TranslucentGeometryCollector collector, Material material, BlockPos offset, ModelQuadView quad, ModelQuadFacing facing, boolean flip);

    @Shadow @Final public static float EPSILON;

    @Shadow @Final private float[] brightness;

    @Shadow @Final private QuadLightData quadLightData;

    @Inject(method = "render", at = @At("RETURN"))
    public void fluidVoidFading$render(LevelSlice level, BlockState blockState, FluidState fluidState, BlockPos blockPos, BlockPos offset, TranslucentGeometryCollector collector, ChunkModelBuilder meshBuilder, Material material, ColorProvider<FluidState> colorProvider, TextureAtlasSprite[] sprites, CallbackInfo ci) {
        if (fluidVoidFading$shouldFadeBelow(level, blockPos, fluidState)) {
            this.fluidVoidFading$renderFluidInVoid(level, fluidState, blockPos, offset, collector, meshBuilder, material, colorProvider, sprites);
        }
    }

    @Inject(method = "isSideExposed", at = @At("HEAD"), cancellable = true)
    private void fluidVoidFading$isSideExposed(BlockAndTintGetter world, int x, int y, int z, Direction dir, float height, CallbackInfoReturnable<Boolean> cir) {
        if (dir == Direction.DOWN && y == world.getMinBuildHeight()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Trigger for drawing the fade gradient below this fluid block. True when:
     *  - the block is the last one above the void (vanilla behaviour), OR
     *  - the block directly below is air AND this block holds a flowing
     *    (non-source) fluid -- i.e. the bottom of a falling fluid column that
     *    ends in mid-air, which happens when the server-side flow cap
     *    truncates a column.
     */
    @Unique
    private static boolean fluidVoidFading$shouldFadeBelow(LevelSlice level, BlockPos pos, FluidState fluidState) {
        BlockGetter bg = (BlockGetter) level;
        if (pos.getY() == bg.getMinBuildHeight()) {
            return true;
        }
        if (fluidState == null || fluidState.isEmpty() || fluidState.isSource()) {
            return false;
        }
        BlockState belowState = bg.getBlockState(pos.below());
        return belowState.isAir() && belowState.getFluidState().isEmpty();
    }

    @Unique
    private void fluidVoidFading$renderFluidInVoid(LevelSlice level, FluidState fluidState, BlockPos blockPos, BlockPos offset, TranslucentGeometryCollector collector, ChunkModelBuilder meshBuilder, Material material, ColorProvider<FluidState> colorProvider, TextureAtlasSprite[] sprites) {
        boolean isWater = fluidState.is(FluidTags.WATER);

        final ModelQuadViewMutable quad = this.quad;

        LightMode lightMode = isWater && Minecraft.useAmbientOcclusion() ? LightMode.SMOOTH : LightMode.FLAT;
        LightPipeline lighter = this.lighters.getLighter(lightMode);

        quad.setFlags(ModelQuadFlags.IS_PARALLEL | ModelQuadFlags.IS_ALIGNED);
        for (Direction dir : DirectionUtil.HORIZONTAL_DIRECTIONS) {
            BlockState adjBlock = ((BlockGetter) level).getBlockState(this.scratchPos.setWithOffset(blockPos, dir));
            if (!adjBlock.getFluidState().isEmpty()) {
                continue;
            }

            float x1;
            float z1;
            float x2;
            float z2;

            if (dir == Direction.NORTH) {
                x1 = 0.0f;
                x2 = 1.0F;
                z1 = EPSILON;
                z2 = z1;
            } else if (dir == Direction.SOUTH) {
                x1 = 1.0F;
                x2 = 0.0f;
                z1 = 1.0f - EPSILON;
                z2 = z1;
            } else if (dir == Direction.WEST) {
                x1 = EPSILON;
                x2 = x1;
                z1 = 1.0F;
                z2 = 0.0f;
            } else if (dir == Direction.EAST) {
                x1 = 1.0f - EPSILON;
                x2 = x1;
                z1 = 0.0f;
                z2 = 1.0F;
            } else {
                continue;
            }

            TextureAtlasSprite sprite = sprites[1];

            boolean isOverlay = false;

            if (sprites.length > 2 && sprites[2] != null
                    && PlatformBlockAccess.getInstance().shouldShowFluidOverlay(adjBlock, level, this.scratchPos, fluidState)) {
                sprite = sprites[2];
                isOverlay = true;
            }

            float u1 = sprite.getU(0.0F);
            float u2 = sprite.getU(0.5F);
            float v1 = sprite.getV(0.0F);
            float v2 = sprite.getV(0.5F);

            quad.setSprite(sprite);

            setVertex(quad, 0, x2, 1.0F, z2, u2, v1);
            setVertex(quad, 1, x2, EPSILON, z2, u2, v2);
            setVertex(quad, 2, x1, EPSILON, z1, u1, v2);
            setVertex(quad, 3, x1, 1.0F, z1, u1, v1);
            float br = dir.getAxis() == Direction.Axis.Z ? 0.8F : 0.6F;

            ModelQuadFacing facing = ModelQuadFacing.fromDirection(dir);

            lighter.calculate(quad, blockPos, this.quadLightData, null, dir, false, false);
            colorProvider.getColors(level, blockPos, this.scratchPos, fluidState, quad, this.quadColors, level.hasBiomeBlend());

            int[] original = new int[]{
                    ColorARGB.toABGR(this.quadColors[0]),
                    ColorARGB.toABGR(this.quadColors[1]),
                    ColorARGB.toABGR(this.quadColors[2]),
                    ColorARGB.toABGR(this.quadColors[3])
            };

            // Always emit the reverse-winding (back-facing) quads so the gradient
            // sides remain visible and identically shaded when the camera moves
            // below the fluid column, regardless of whether this is an overlay
            // sprite. Without this, when looking up from below the front quad is
            // back-face-culled, leaving the gradient looking far more transparent.
            BlockPos downPos1 = offset.below(1);
            this.fluidVoidFading$updateQuadWithAlpha(quad, facing, br, original, 1.0F, 0.3F);
            this.writeQuad(meshBuilder, collector, material, downPos1, quad, facing, false);
            this.writeQuad(meshBuilder, collector, material, downPos1, quad, facing.getOpposite(), true);

            BlockPos downPos2 = offset.below(2);
            this.fluidVoidFading$updateQuadWithAlpha(quad, facing, br, original, 0.3F, 0.0F);
            this.writeQuad(meshBuilder, collector, material, downPos2, quad, facing, false);
            this.writeQuad(meshBuilder, collector, material, downPos2, quad, facing.getOpposite(), true);
        }

        // Bottom cap: render a downward-facing quad with the still texture at the
        // bottom of the actual fluid block so viewers looking up from beneath the
        // void column see the fluid surface instead of an empty hollow column.
        TextureAtlasSprite still = sprites[0];
        quad.setSprite(still);
        float bu1 = still.getU(0.0F);
        float bu2 = still.getU(1.0F);
        float bv1 = still.getV(0.0F);
        float bv2 = still.getV(1.0F);
        // Quad lying on the bottom face (y = EPSILON inside the local 1x1x1 cell),
        // wound so the visible (front) face points DOWN (-Y): viewer below sees it,
        // viewer above (inside the column) does not.
        setVertex(quad, 0, 0.0F, EPSILON, 0.0F,        bu1, bv1);
        setVertex(quad, 1, 0.0F, EPSILON, 1.0F,        bu1, bv2);
        setVertex(quad, 2, 1.0F, EPSILON, 1.0F,        bu2, bv2);
        setVertex(quad, 3, 1.0F, EPSILON, 0.0F,        bu2, bv1);

        ModelQuadFacing downFacing = ModelQuadFacing.fromDirection(Direction.DOWN);
        LightPipeline downLighter = this.lighters.getLighter(isWater && Minecraft.useAmbientOcclusion() ? LightMode.SMOOTH : LightMode.FLAT);
        downLighter.calculate(quad, blockPos, this.quadLightData, null, Direction.DOWN, false, false);
        colorProvider.getColors(level, blockPos, this.scratchPos, fluidState, quad, this.quadColors, level.hasBiomeBlend());

        int[] capColors = new int[]{
                ColorARGB.toABGR(this.quadColors[0]),
                ColorARGB.toABGR(this.quadColors[1]),
                ColorARGB.toABGR(this.quadColors[2]),
                ColorARGB.toABGR(this.quadColors[3])
        };
        this.fluidVoidFading$updateQuadWithAlpha(quad, downFacing, 0.5F, capColors, 0.25F, 0.25F);
        this.writeQuad(meshBuilder, collector, material, offset, quad, downFacing, false);
    }

    @Unique
    private void fluidVoidFading$updateQuadWithAlpha(ModelQuadViewMutable quad, ModelQuadFacing facing, float brightness, int[] original, float alphaStart, float alphaEnd) {
        int normal;
        if (facing.isAligned()) {
            normal = facing.getPackedAlignedNormal();
        } else {
            normal = quad.calculateNormal();
        }
        quad.setFaceNormal(normal);
        for (int i = 0; i < 4; ++i) {
            float alpha = (i == 0 || i == 3) ? alphaStart : alphaEnd;
            alpha *= ColorU8.byteToNormalizedFloat(ColorABGR.unpackAlpha(original[i]));
            this.quadColors[i] = ColorABGR.withAlpha(original[i], alpha);
            this.brightness[i] = this.quadLightData.br[i] * brightness;
        }
    }
}
