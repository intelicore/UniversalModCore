package cam72cam.mod.render;

import cam72cam.mod.block.BlockEntity;
import cam72cam.mod.block.BlockType;
import cam72cam.mod.block.BlockTypeEntity;
import cam72cam.mod.block.tile.TileEntity;
import cam72cam.mod.event.ClientEvents;
import cam72cam.mod.render.opengl.RenderState;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.ColorizerGrass;
import net.minecraft.world.biome.BiomeColorHelper;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for block rendering (and internal implementation)
 *
 * Currently only supports TE's, not standard blocks
 */
public class BlockRender {
    // Don't need to return a *new* array list for no result
    private static final List<BakedQuad> EMPTY = Collections.emptyList();
    // Block coloring (grass) hooks
    private static final List<Runnable> colors = new ArrayList<>();
    // BlockEntity type -> BlockEntity Renderer
    private static final Map<Class<? extends BlockEntity>, Function<BlockEntity, StandardModel>> renderers = new HashMap<>();
    // Internal hack for globally rendered TE's
    private static List<net.minecraft.tileentity.TileEntity> prev = new ArrayList<>();

    static {
        ClientEvents.TICK.subscribe(() -> {
            if (Minecraft.getMinecraft().world == null) {
                return;
            }
            /*
            Find all UMC TEs
            Create new array to prevent CME's with poorly behaving mods
            TODO: Opt out of renderGlobal!
             */
            List<net.minecraft.tileentity.TileEntity> tes = new ArrayList<>(Minecraft.getMinecraft().world.loadedTileEntityList).stream()
                    .filter(x -> x instanceof TileEntity && ((TileEntity) x).isLoaded() && x.getMaxRenderDistanceSquared() > 0)
                    .collect(Collectors.toList());
            if (Minecraft.getMinecraft().world.getTotalWorldTime() % 20 == 1) {
                prev = new ArrayList<>(Minecraft.getMinecraft().world.loadedTileEntityList).stream()
                        .filter(x -> x instanceof TileEntity)
                        .collect(Collectors.toList());
            }
            Minecraft.getMinecraft().renderGlobal.updateTileEntities(prev, tes);
            prev = tes;
        });
    }

    /** Internal, do not use.  Is fired by UMC directly */
    public static void onPostColorSetup() {
        colors.forEach(Runnable::run);

        ClientRegistry.bindTileEntitySpecialRenderer(TileEntity.class, new TileEntitySpecialRenderer<TileEntity>() {
            public void render(TileEntity te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
                BlockEntity instance = te.instance();
                if (instance == null) {
                    return;
                }
                Class<? extends BlockEntity> cls = instance.getClass();
                Function<BlockEntity, StandardModel> renderer = renderers.get(cls);
                if (renderer == null) {
                    return;
                }

                StandardModel model = renderer.apply(instance);
                if (model == null) {
                    return;
                }

                if (!model.hasCustom()) {
                    return;
                }
                model.renderCustom(new RenderState().translate(x, y, z), partialTicks);
            }

            public boolean isGlobalRenderer(TileEntity te) {
                return true;
            }
        });
    }

    // TODO version for non TE blocks

    public static <T extends BlockEntity> void register(BlockType block, Function<T, StandardModel> model, Class<T> cls) {
        renderers.put(cls, (te) -> model.apply(cls.cast(te)));

        colors.add(() -> {
            BlockColors blockColors = Minecraft.getMinecraft().getBlockColors();
            blockColors.registerBlockColorHandler((state, worldIn, pos, tintIndex) -> worldIn != null && pos != null ? BiomeColorHelper.getGrassColorAtPos(worldIn, pos) : ColorizerGrass.getGrassColor(0.5D, 1.0D), block.internal);
        });

        ClientEvents.MODEL_BAKE.subscribe(event -> {
            event.getModelRegistry().putObject(new ModelResourceLocation(block.internal.getRegistryName(), ""), new IBakedModel() {
                @Override
                public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
                    if (block instanceof BlockTypeEntity) {
                        if (!(state instanceof IExtendedBlockState)) {
                            return EMPTY;
                        }
                        IExtendedBlockState extState = (IExtendedBlockState) state;
                        Object data = extState.getValue(BlockTypeEntity.BLOCK_DATA);
                        if (!cls.isInstance(data)) {
                            return EMPTY;
                        }
                        StandardModel out = model.apply(cls.cast(data));
                        if (out == null) {
                            return EMPTY;
                        }
                        return out.getQuads(side, rand);
                    } else {
                        // TODO
                        return EMPTY;
                    }
                }

                @Override
                public boolean isAmbientOcclusion() {
                    return true;
                }

                @Override
                public boolean isGui3d() {
                    return true;
                }

                @Override
                public boolean isBuiltInRenderer() {
                    return false;
                }

                @Override
                public TextureAtlasSprite getParticleTexture() {
                    if (block.internal.getMaterial(null) == Material.IRON) {
                        return Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelForState(Blocks.IRON_BLOCK.getDefaultState()).getParticleTexture();
                    }
                    return Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelForState(Blocks.STONE.getDefaultState()).getParticleTexture();
                }

                @Override
                public ItemOverrideList getOverrides() {
                    return null;
                }
            });
        });
    }
}
