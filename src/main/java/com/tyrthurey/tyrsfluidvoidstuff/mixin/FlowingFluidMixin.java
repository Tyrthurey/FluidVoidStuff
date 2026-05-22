package com.tyrthurey.tyrsfluidvoidstuff.mixin;

import com.tyrthurey.tyrsfluidvoidstuff.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-side mixin that caps how many falling fluid blocks may hang below a
 * source block. When {@link Config#MAX_FLUID_COLUMN_LENGTH} is 0 (default),
 * behaviour is vanilla. When it is N > 0, downward spread is cancelled once
 * the column of same-fluid blocks above the would-be target already contains
 * N falling blocks (i.e. the source + N falling blocks have already been
 * placed). The truncated column ends with air below, which is what the client
 * renderer uses as the trigger for the fade-into-void gradient.
 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {

    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void tyrsfluidvoidstuff$capDownwardSpread(LevelAccessor level, BlockPos pos, BlockState state,
                                                      Direction direction, FluidState fluidState, CallbackInfo ci) {
        if (direction != Direction.DOWN) {
            return;
        }
        // Per-fluid opt-out: if this fluid's id (or its source/flowing twin's id)
        // is in the server blacklist, skip ALL custom logic and let vanilla
        // handle the spread untouched.
        Fluid spreadingType = fluidState.getType();
        try {
            java.util.List<? extends String> blacklist = Config.FLUID_COLUMN_LENGTH_BLACKLIST.get();
            if (!blacklist.isEmpty()) {
                ResourceLocation id = BuiltInRegistries.FLUID.getKey(spreadingType);
                if (id != null && blacklist.contains(id.toString())) {
                    return;
                }
            }
        } catch (IllegalStateException ignored) {
            // Config not loaded yet -> behave vanilla.
            return;
        }
        // Disallow orphaned falling fluid from creating new falling fluid below it.
        // The block "doing the spreading" sits at pos.above() for a DOWN spread.
        // Walk upward through contiguous same-fluid blocks. The column is
        // considered source-fed if we encounter EITHER:
        //   - a source block (amount == 8, isSource() == true), OR
        //   - a horizontally-flowing fluid (amount < 8): vanilla only assigns a
        //     positive flowing level after verifying a nearby source via
        //     getNewLiquid(), so this position is fed from a source sideways
        //     (e.g. source on an island flowing one block over before falling).
        // Only a chain of pure falling fluid (amount == 8 && !isSource) that
        // terminates in air/non-fluid is considered an orphaned tail and gets
        // cancelled.
        // Walk upward through contiguous same-fluid blocks. The column is
        // source-fed only if we find EITHER:
        //   - an actual source block (isSource()) somewhere in the chain, OR
        //   - a horizontally-flowing fluid (amount < 8) that has a same-fluid
        //     SOURCE as one of its horizontal neighbors (the canonical
        //     "source on an island, flows one block over, falls" shape).
        // A bare horizontally-flowing block without a true source neighbor is
        // NOT considered source-fed: that case happens transiently when two
        // cut-off falling columns sideways-flow into each other's airspace,
        // and previously caused the spread to be allowed and effectively
        // re-extend the truncated column by `maxFluidColumnLength` blocks.
        // Generalised source-fed check: BFS through any contiguous same-fluid
        // blocks reachable from the block that is doing the spreading
        // (pos.above() for a DOWN spread). Edges in the graph:
        //   - From a horizontal flow (amount < 8): step to horizontal neighbors
        //     AND step DOWN (into the falling column it pours into).
        //   - From a falling block (amount == 8 && !isSource): step UP only
        //     (back toward whatever is feeding it).
        //   - A source block (isSource()) found anywhere in the reachable set
        //     means the column we're trying to extend IS source-fed.
        // This handles staircases (source -> flow -> fall -> flow -> fall ...)
        // where the feeding path zig-zags through alternating horizontal
        // flow segments and short falling drops. Two cut-off tails that
        // sideways-touch do NOT re-qualify each other because neither tail
        // has any reachable source via this graph (their original sources
        // are gone, and falling blocks only walk UP, never sideways).
        Fluid spreading = fluidState.getType();
        BlockPos startPos = pos.above();
        FluidState startState = level.getFluidState(startPos);
        boolean sourceFed = false;
        int sourceY = Integer.MIN_VALUE; // Y of the reachable source block (lowest if multiple)
        if (!startState.isEmpty() && startState.getType().isSame(spreading)) {
            if (startState.isSource()) {
                sourceFed = true;
                sourceY = startPos.getY();
            } else {
                java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
                java.util.HashSet<Long> visited = new java.util.HashSet<>();
                queue.add(startPos);
                visited.add(startPos.asLong());
                int budget = 4096;
                while (!queue.isEmpty() && budget-- > 0) {
                    BlockPos cur = queue.poll();
                    FluidState curState = level.getFluidState(cur);
                    if (curState.isEmpty() || !curState.getType().isSame(spreading)) {
                        continue;
                    }
                    if (curState.isSource()) {
                        sourceFed = true;
                        sourceY = cur.getY();
                        break;
                    }
                    boolean falling = curState.getAmount() == 8; // non-source falling
                    if (falling) {
                        // Falling block: only walk UP toward its feeder.
                        BlockPos up = cur.above();
                        if (visited.add(up.asLong())) {
                            FluidState us = level.getFluidState(up);
                            if (!us.isEmpty() && us.getType().isSame(spreading)) {
                                queue.add(up);
                            }
                        }
                    } else {
                        // Horizontal flow: walk to horizontal neighbors and
                        // also DOWN into a falling column it might be feeding
                        // INTO -- but we want to walk toward the SOURCE, so
                        // only follow DOWN if the block below is itself a
                        // falling block of the same fluid (rare, but covers
                        // the case where we entered via that falling block).
                        for (Direction h : Direction.Plane.HORIZONTAL) {
                            BlockPos np = cur.relative(h);
                            if (!visited.add(np.asLong())) continue;
                            FluidState n = level.getFluidState(np);
                            if (!n.isEmpty() && n.getType().isSame(spreading)) {
                                queue.add(np);
                            }
                        }
                        // Also allow walking UP from a horizontal flow: in a
                        // staircase, a horizontal flow may sit directly below
                        // another horizontal flow segment that is closer to
                        // the source. Vanilla doesn't stack flow vertically
                        // without a falling block between, but be defensive.
                        BlockPos up = cur.above();
                        if (visited.add(up.asLong())) {
                            FluidState us = level.getFluidState(up);
                            if (!us.isEmpty() && us.getType().isSame(spreading)) {
                                queue.add(up);
                            }
                        }
                    }
                }
            }
        }
        if (!sourceFed) {
            ci.cancel();
            return;
        }

        // Vertical-drop cap (when configured). The cap is expressed as a
        // VERTICAL DISTANCE from the source block, not as a count of
        // contiguous falling blocks. This makes staircases obey the same
        // rule: a source at Y=64 with maxFluidColumnLength=3 can extend
        // downward through any path (straight column or zig-zag staircase)
        // but only as far as Y=61. The horizontal aspect of flow is left
        // entirely to vanilla.
        int maxLen;
        try {
            maxLen = Config.MAX_FLUID_COLUMN_LENGTH.getAsInt();
        } catch (IllegalStateException ignored) {
            return;
        }
        if (maxLen <= 0) {
            return;
        }
        if (sourceY == Integer.MIN_VALUE) {
            // sourceFed was true but BFS didn't record a Y (shouldn't happen).
            return;
        }

        int verticalDrop = sourceY - pos.getY(); // pos is the would-be new block
        if (verticalDrop > maxLen) {
            ci.cancel();
        }
    }
}
