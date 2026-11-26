package za.co.neroland.nerocompanion.registry;

//? if <1.20.5 {
/*import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import za.co.neroland.nerocompanion.NeroCompanionCommon;
import za.co.neroland.nerocompanion.platform.Services;

public final class LegacyCapsuleRecipe extends ShapelessRecipe {
    private static final RecipeSerializer<ShapelessRecipe> VANILLA = RecipeSerializer.SHAPELESS_RECIPE;
    private static final za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry<RecipeSerializer<LegacyCapsuleRecipe>> SERIALIZER = Services.REGISTRATION
            .provider(Registries.RECIPE_SERIALIZER, NeroCompanionCommon.MOD_ID)
            .register("capsule_blueprint", key -> new Serializer());

    private final ShapelessRecipe base;
    private final Identifier blueprint;

    private LegacyCapsuleRecipe(ShapelessRecipe base, Identifier blueprint) {
        //? if >=1.20.2 {
        super(base.getGroup(), base.category(), result(base, blueprint), base.getIngredients());
        //?} else {
        super(base.getId(), base.getGroup(), base.category(), result(base, blueprint), base.getIngredients());
        //?}
        this.base = base;
        this.blueprint = blueprint;
    }

    private static ItemStack result(ShapelessRecipe base, Identifier blueprint) {
        ItemStack result = base.getResultItem(RegistryAccess.EMPTY).copy();
        if (!result.is(ModItems.CAPSULE.get())) {
            throw new JsonSyntaxException("A capsule blueprint recipe must output a capsule");
        }
        ModDataComponents.BLUEPRINT.set(result, blueprint);
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }

    public static void init() {
    }

    private static final class Serializer implements RecipeSerializer<LegacyCapsuleRecipe> {
        //? if >=1.20.2 {
        @Override
        public Codec<LegacyCapsuleRecipe> codec() {
            return RecordCodecBuilder.create(instance -> instance.group(
                    VANILLA.codec().fieldOf("recipe").forGetter(recipe -> recipe.base),
                    Identifier.CODEC.fieldOf("blueprint").forGetter(recipe -> recipe.blueprint)
            ).apply(instance, LegacyCapsuleRecipe::new));
        }

        @Override
        public LegacyCapsuleRecipe fromNetwork(FriendlyByteBuf buf) {
            return new LegacyCapsuleRecipe(VANILLA.fromNetwork(buf), buf.readResourceLocation());
        }
        //?} else {
        @Override
        public LegacyCapsuleRecipe fromJson(Identifier id, JsonObject json) {
            ShapelessRecipe base = VANILLA.fromJson(id, GsonHelper.getAsJsonObject(json, "recipe"));
            Identifier blueprint = Identifier.parse(GsonHelper.getAsString(json, "blueprint"));
            return new LegacyCapsuleRecipe(base, blueprint);
        }

        @Override
        public LegacyCapsuleRecipe fromNetwork(Identifier id, FriendlyByteBuf buf) {
            return new LegacyCapsuleRecipe(VANILLA.fromNetwork(id, buf), buf.readResourceLocation());
        }
        //?}

        @Override
        public void toNetwork(FriendlyByteBuf buf, LegacyCapsuleRecipe recipe) {
            VANILLA.toNetwork(buf, recipe.base);
            buf.writeResourceLocation(recipe.blueprint);
        }
    }
}
*///?}
