package za.co.neroland.nerocompanion.client.render.compat;

// Stand-in for the 1.21.9 item render state below 1.21.9 (see LegacyBlockEntityRenderer): the item is captured at
// extract time and drawn through the item renderer at submit time. Compiled only below 1.21.9.
//? if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ItemStackRenderState {

    private ItemStack stack = ItemStack.EMPTY;
    private ItemDisplayContext context = ItemDisplayContext.FIXED;
    private Level level;
    private int seed;

    public void update(ItemStack stack, ItemDisplayContext context, Level level, int seed) {
        this.stack = stack;
        this.context = context;
        this.level = level;
        this.seed = seed;
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, int outlineColor) {
        if (this.stack.isEmpty()) {
            return;
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(this.stack, this.context, light, overlay, poseStack,
                collector.buffers(), this.level, this.seed);
    }
}
*///?}
