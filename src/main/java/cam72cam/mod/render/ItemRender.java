package cam72cam.mod.render;

import cam72cam.mod.Config;
import cam72cam.mod.event.ClientEvents;
import cam72cam.mod.gui.Progress;
import cam72cam.mod.item.ItemBase;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.world.World;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.model.ModelLoadingRegistry;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.model.*;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.render.model.json.ModelItemPropertyOverrideList;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.vecmath.Matrix4f;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ItemRender {
    private static final List<BakedQuad> EMPTY = new ArrayList<>();
    private static final SpriteSheet iconSheet = new SpriteSheet(Config.SpriteSize);

    public static void register(ItemBase item, Identifier tex) {

        ClientEvents.MODEL_BAKE.subscribe(() -> {
            Map<String, Either<SpriteIdentifier, String>> textures = new HashMap<>();
            textures.put("layer0", Either.right(tex.toString()));
            ModelLoadingRegistry.INSTANCE.registerVariantProvider(resourceManager -> (modelId, context) -> item.getRegistryName().internal.equals(modelId) ?
                    new JsonUnbakedModel(new net.minecraft.util.Identifier("item/generated"), Collections.emptyList(), textures, true, null, ModelTransformation.NONE, Collections.emptyList())
                    : null);
        });
    }

    public static void register(ItemBase item, IItemModel model) {
        ClientEvents.MODEL_BAKE.subscribe(() -> {
            ModelLoadingRegistry.INSTANCE.registerVariantProvider(manager -> (modelId, context) -> item.getRegistryName().internal.equals(modelId) ?
                    new UnbakedModel() {
                        @Override
                        public Collection<net.minecraft.util.Identifier> getModelDependencies() {
                            return Collections.emptyList();
                        }

                        @Override
                        public Collection<SpriteIdentifier> getTextureDependencies(Function<net.minecraft.util.Identifier, UnbakedModel> var1, Set<Pair<String, String>> var2) {
                            return Collections.emptyList();
                        }

                        @Nullable
                        @Override
                        public BakedModel bake(ModelLoader var1, Function<SpriteIdentifier, Sprite> var2, ModelBakeSettings var3, net.minecraft.util.Identifier var4) {
                            return new BakedItemModel(model);
                        }
                    } : null);
        });

        if (model instanceof ISpriteItemModel) {
            ClientEvents.RELOAD.subscribe(() -> {
                List<ItemStack> variants = item.getItemVariants(null);
                Progress.Bar bar = Progress.push(item.getClass().getSimpleName() + " Icon", variants.size());
                for (ItemStack stack : variants) {
                    String id = ((ISpriteItemModel) model).getSpriteKey(stack);
                    bar.step(id);
                    createSprite(id, ((ISpriteItemModel) model).getSpriteModel(stack));
                }
                Progress.pop(bar);
            });
        }
    }

    public enum ItemRenderType {
        NONE(ModelTransformation.Mode.NONE),
        THIRD_PERSON_LEFT_HAND(ModelTransformation.Mode.THIRD_PERSON_LEFT_HAND),
        THIRD_PERSON_RIGHT_HAND(ModelTransformation.Mode.THIRD_PERSON_RIGHT_HAND),
        FIRST_PERSON_LEFT_HAND(ModelTransformation.Mode.FIRST_PERSON_LEFT_HAND),
        FIRST_PERSON_RIGHT_HAND(ModelTransformation.Mode.FIRST_PERSON_RIGHT_HAND),
        HEAD(ModelTransformation.Mode.HEAD),
        GUI(ModelTransformation.Mode.GUI),
        ENTITY(ModelTransformation.Mode.GROUND),
        FRAME(ModelTransformation.Mode.FIXED);

        private final ModelTransformation.Mode type;

        ItemRenderType(ModelTransformation.Mode type) {
            this.type = type;
        }

        public static ItemRenderType from(ModelTransformation.Mode cameraTransformType) {
            for (ItemRenderType type : values()) {
                if (cameraTransformType == type.type) {
                    return type;
                }
            }
            return null;
        }
    }

    @FunctionalInterface
    public interface IItemModel {
        StandardModel getModel(World world, ItemStack stack);
        default void applyTransform(ItemRenderType type) {
            defaultTransform(type);
        }
        static void defaultTransform(ItemRenderType type) {
            switch (type) {
                case HEAD:
                    GL11.glTranslated(-0.5, 1, 0);
                    GL11.glScaled(2, 2, 2);
                    break;
                case FIRST_PERSON_LEFT_HAND:
                    GL11.glTranslated(0, 0, -0.5);
                    GL11.glTranslated(0, 0, -0.5);
                    break;
                case FRAME:
                    GL11.glTranslated(0, 0, 0.5);
                    GL11.glRotated(90, 0, 1, 0);
                    break;
            }
        }
    }

    public interface ISpriteItemModel extends IItemModel {
        String getSpriteKey(ItemStack stack);
        StandardModel getSpriteModel(ItemStack stack);
    }


    private static void createSprite(String id, StandardModel model) {
        int width = iconSheet.spriteSize;
        int height = iconSheet.spriteSize;
        File sprite = GLTexture.cacheFile(id.replace("/", ".") + "_" + "sprite" + iconSheet.spriteSize + ".raw");
        if (sprite.exists()) {
            try {
                ByteBuffer buff = ByteBuffer.allocateDirect(4 * width * height);
                buff.put(ByteBuffer.wrap(Files.readAllBytes(sprite.toPath())));
                buff.flip();
                iconSheet.setSprite(id, buff);
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Framebuffer fb = new Framebuffer(width, height, true, true);
        fb.setClearColor(0, 0, 0, 0);
        fb.clear(true);
        fb.beginWrite(true);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GLBoolTracker depth = new GLBoolTracker(GL11.GL_DEPTH_TEST, true);
        int oldDepth = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        GL11.glDepthFunc(GL11.GL_LESS);
        GL11.glClearDepth(1);

        model.renderCustom();

        ByteBuffer buff = ByteBuffer.allocateDirect(4 * width * height);
        fb.beginRead();
        GL11.glReadPixels(0, 0, width, height, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buff);
        fb.endRead();

        fb.endWrite();
        fb.delete();
        GL11.glDepthFunc(oldDepth);
        depth.restore();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        iconSheet.setSprite(id, buff);
        GL11.glDepthFunc(GL11.GL_LEQUAL);

        try {
            byte[] data = new byte[buff.capacity()];
            buff.get(data);
            Files.write(sprite.toPath(), data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class BakedItemModel implements FullBakedModel {
        private final IItemModel model;

        BakedItemModel(IItemModel model) {
            this.model = model;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, Random rand) {
            return EMPTY;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return true;
        }

        @Override
        public boolean hasDepth() {
            return true;
        }

        @Override
        public boolean isSideLit() {
            return true;
        }

        @Override
        public boolean isBuiltin() {
            return false;
        }

        @Override
        public Sprite getSprite() {
            return null;
        }

        @Override
        public ModelItemPropertyOverrideList getItemPropertyOverrides() {
            return ModelItemPropertyOverrideList.EMPTY;
        }

        @Override
        public ModelTransformation getTransformation() {
            return ModelTransformation.NONE;
        }

        @Override
        public boolean isVanillaAdapter() {
            return true;
        }

        @Override
        public void emitBlockQuads(BlockRenderView extendedBlockView, BlockState blockState, BlockPos blockPos, Supplier<Random> supplier, RenderContext renderContext) {
        }

        @Override
        public void emitItemQuads(net.minecraft.item.ItemStack itemStack, Supplier<Random> supplier, RenderContext renderContext) {
        }

        public void render(net.minecraft.item.ItemStack stackIn, ModelTransformation.Mode transformMode, MatrixStack matrixStack, int light, int overlay) {
            try {
                GL11.glPushMatrix();
                RenderLayer.getCutout().startDrawing();
                RenderSystem.multMatrix(matrixStack.peek().getModel());

                ItemStack stack = new ItemStack(stackIn);
                ItemRenderType type = ItemRenderType.from(transformMode);
                if (type == ItemRenderType.GUI && model instanceof ISpriteItemModel) {
                    GL11.glTranslated(-0.5, -0.5, 0);
                    iconSheet.renderSprite(((ISpriteItemModel) model).getSpriteKey(stack));
                    return;
                }

                StandardModel std = model.getModel(World.get(MinecraftClient.getInstance().world), stack);
                if (std == null) {
                    return;
                }

                if (type == ItemRenderType.GUI) {
                    GL11.glTranslated(-0.5, -0.5, 0);
                } else {
                    GL11.glTranslated(-0.5, -0.5, -0.5);
                }

                model.applyTransform(type);
                std.render();
            } finally {
                RenderLayer.getCutout().endDrawing();
                GL11.glPopMatrix();
            }
        }
    }
}
