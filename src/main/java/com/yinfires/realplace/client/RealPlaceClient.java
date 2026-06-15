package com.yinfires.realplace.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.yinfires.realplace.RealPlace;
import com.yinfires.realplace.RealPlaceClientConfig;
import com.yinfires.realplace.RealPlaceClientState;
import com.yinfires.realplace.RealPlaceItemTransforms;
import com.yinfires.realplace.network.PickupRealObjectPayload;
import com.yinfires.realplace.network.PlaceRealObjectPayload;
import com.yinfires.realplace.network.RealPlaceNetworking;
import com.yinfires.realplace.network.RealPlacePlacementModePayload;
import com.yinfires.realplace.server.RealPlaceObject;
import com.yinfires.realplace.server.RealPlaceShape;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

public final class RealPlaceClient {
    public static final KeyMapping PLACEMENT_MODE = new KeyMapping(
            "key.realplace.placement_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.realplace");
    public static final KeyMapping SWITCH_MODEL = new KeyMapping(
            "key.realplace.switch_model",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.realplace");

    private static final String HINT_LAYER = "placement_key_hints";
    private static final int MAX_GLINT_MASK_CACHE_SIZE = 128;
    private static final int MAX_GLINT_MASK_PIXELS = 1024;
    private static final double FLAT_ITEM_MASK_FRONT_Z = 0.5D - 1.0D / 32.0D;
    private static final double FLAT_ITEM_MASK_BACK_Z = 0.5D + 1.0D / 32.0D;
    private static final Map<GlintMaskKey, FlatItemAlphaMask> GLINT_MASK_CACHE = new LinkedHashMap<>(MAX_GLINT_MASK_CACHE_SIZE, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<GlintMaskKey, FlatItemAlphaMask> eldest) {
            return size() > MAX_GLINT_MASK_CACHE_SIZE;
        }
    };

    private RealPlaceClient() {
    }

    public static void registerModBus(IEventBus modEventBus) {
        modEventBus.addListener(RealPlaceClient::clientSetup);
        modEventBus.addListener(RealPlaceClient::registerKeys);
        modEventBus.addListener(RealPlaceClient::registerGuiOverlays);
        MinecraftForge.EVENT_BUS.addListener(RealPlaceClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(RealPlaceClient::onRenderLevel);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                Minecraft.getInstance().getMainRenderTarget().enableStencil();
            } catch (RuntimeException exception) {
                RealPlace.LOGGER.warn("Failed to enable stencil buffer for RealPlace flat item glint masking.", exception);
            }
        });
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(PLACEMENT_MODE);
        event.register(SWITCH_MODEL);
    }

    private static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(HINT_LAYER, RealPlaceClient::renderHints);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            RealPlaceClientState.setPlacementMode(false);
            return;
        }
        while (PLACEMENT_MODE.consumeClick()) {
            togglePlacementMode();
        }
        if (RealPlaceClientState.placementMode()) {
            while (SWITCH_MODEL.consumeClick()) {
                switchModelMode();
            }
        }
        if (RealPlaceClientState.placementMode()) {
            ItemStack stack = minecraft.player.getMainHandItem();
            RealPlaceClientState.setPreviewStack(stack);
            if (stack.isEmpty()) {
                setPlacementMode(false);
            }
        }
    }

    public static void togglePlacementMode() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        boolean active = !RealPlaceClientState.placementMode();
        if (active && minecraft.player.getMainHandItem().isEmpty()) {
            return;
        }
        setPlacementMode(active);
    }

    public static void setPlacementMode(boolean active) {
        RealPlaceClientState.setPlacementMode(active);
        Minecraft minecraft = Minecraft.getInstance();
        if (active && minecraft.player != null) {
            RealPlaceClientState.setPreviewStack(minecraft.player.getMainHandItem());
        }
        RealPlaceNetworking.sendToServer(new RealPlacePlacementModePayload(active));
    }

    public static void switchModelMode() {
        RealPlaceClientState.toggleModelMode();
    }

    public static boolean handleScroll(double scrollY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!RealPlaceClientState.placementMode() || minecraft.screen != null) {
            return false;
        }
        long window = minecraft.getWindow().getWindow();
        int steps = scrollY > 0.0D ? 1 : -1;
        if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            RealPlaceClientState.addScale(steps * 0.1F);
        } else if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT)) {
            RealPlaceClientState.addPitch(steps * 22.5F);
        } else {
            RealPlaceClientState.addYaw(steps * 22.5F);
        }
        return true;
    }

    public static boolean handleUseOn(BlockHitResult hit, InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        if (RealPlaceClientState.placementMode()) {
            placeCurrentPreview(hand);
            return true;
        }
        RealPlaceObject object = findLookedAtObject();
        if (object != null) {
            RealPlaceNetworking.sendToServer(new PickupRealObjectPayload(object.id(), hand));
            return true;
        }
        return false;
    }

    public static boolean handleUse(Player player, InteractionHand hand) {
        if (RealPlaceClientState.placementMode()) {
            placeCurrentPreview(hand);
            return true;
        }
        RealPlaceObject object = findLookedAtObject();
        if (object != null) {
            RealPlaceNetworking.sendToServer(new PickupRealObjectPayload(object.id(), hand));
            return true;
        }
        return false;
    }

    public static boolean isLookingAtObject() {
        return findLookedAtObject() != null;
    }

    public static boolean blocksPlacement(BlockHitResult hit, InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || RealPlaceClientState.placementMode()) {
            return false;
        }
        ItemStack stack = minecraft.player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem)) {
            return false;
        }
        BlockPlaceContext context = new BlockPlaceContext(minecraft.player, hand, stack, hit);
        if (!context.canPlace()) {
            return false;
        }
        AABB targetBox = new AABB(context.getClickedPos()).inflate(1.0E-4D);
        for (RealPlaceObject object : RealPlaceClientState.objects()) {
            if (object.bounds().intersects(targetBox)
                    && object.shape().intersectsAabb(object.position(), object.yaw(), object.pitch(), object.scale(), targetBox)) {
                return true;
            }
        }
        return false;
    }

    private static void placeCurrentPreview(InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ItemStack stack = minecraft.player.getItemInHand(hand);
        if (stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, RealPlaceClientState.previewStack())) {
            return;
        }
        int modelMode = RealPlaceItemTransforms.clampModelMode(stack, RealPlaceClientState.modelMode());
        Preview preview = currentPreview();
        if (preview == null) {
            return;
        }
        RealPlaceNetworking.sendToServer(new PlaceRealObjectPayload(
                preview.position,
                RealPlaceClientState.yaw(),
                RealPlaceClientState.pitch(),
                RealPlaceClientState.scale(),
                modelMode,
                hand,
                preview.shape));
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        for (RealPlaceObject object : RealPlaceClientState.objects()) {
            renderObject(event, bufferSource, object);
        }
        renderSelection(event, bufferSource);
        renderPreview(event, bufferSource);
        bufferSource.endBatch();
    }

    private static void renderObject(RenderLevelStageEvent event, MultiBufferSource.BufferSource bufferSource, RealPlaceObject object) {
        if (RealPlaceArmorPreviewRenderer.render(event, bufferSource, object)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        double camX = event.getCamera().getPosition().x;
        double camY = event.getCamera().getPosition().y;
        double camZ = event.getCamera().getPosition().z;
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(object.position().x - camX, object.position().y - camY, object.position().z - camZ);
        event.getPoseStack().mulPose(new Quaternionf().rotationY((float)Math.toRadians(object.yaw())));
        event.getPoseStack().mulPose(new Quaternionf().rotationX((float)Math.toRadians(object.pitch())));
        event.getPoseStack().scale(object.scale(), object.scale(), object.scale());
        if (renderBlockModelObject(minecraft, event.getPoseStack(), bufferSource, object)) {
            event.getPoseStack().popPose();
            return;
        }
        FlatItemAlphaMask glintMask = flatItemGlintMask(minecraft, object);
        if (glintMask != null && renderFlatItemWithStencilGlint(minecraft, event.getPoseStack(), bufferSource, object, glintMask)) {
            event.getPoseStack().popPose();
            return;
        }
        minecraft.getItemRenderer().renderStatic(object.stack(), displayContext(object.stack(), object.modelMode()), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, event.getPoseStack(), bufferSource, minecraft.level, 0);
        event.getPoseStack().popPose();
    }

    private static FlatItemAlphaMask flatItemGlintMask(Minecraft minecraft, RealPlaceObject object) {
        if (object.modelMode() != 0 || !object.stack().hasFoil() || minecraft.level == null) {
            return null;
        }
        if (object.stack().is(Items.TRIDENT) || object.stack().is(Items.SPYGLASS)) {
            return null;
        }
        BakedModel model = minecraft.getItemRenderer().getModel(object.stack(), minecraft.level, minecraft.player, 0);
        if (model.isGui3d() || model.isCustomRenderer()) {
            return null;
        }
        boolean directRenderType = true;
        if (object.stack().getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            directRenderType = !(block instanceof HalfTransparentBlock) && !(block instanceof StainedGlassPaneBlock);
        }
        GlintMaskKey key = new GlintMaskKey(
                BuiltInRegistries.ITEM.getKey(object.stack().getItem()),
                stackTagHash(object.stack()),
                directRenderType,
                System.identityHashCode(minecraft.getModelManager()));
        synchronized (GLINT_MASK_CACHE) {
            FlatItemAlphaMask cached = GLINT_MASK_CACHE.get(key);
            if (cached != null) {
                return cached.placeable() ? cached : null;
            }
        }
        FlatItemAlphaMask mask = createFlatItemAlphaMask(List.of(model));
        synchronized (GLINT_MASK_CACHE) {
            GLINT_MASK_CACHE.put(key, mask);
        }
        return mask.placeable() ? mask : null;
    }

    private static boolean renderFlatItemWithStencilGlint(Minecraft minecraft, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, RealPlaceObject object, FlatItemAlphaMask mask) {
        RenderTarget renderTarget = minecraft.getMainRenderTarget();
        if (!renderTarget.isStencilEnabled() || mask.sprites().isEmpty()) {
            return false;
        }
        ItemDisplayContext context = displayContext(object.stack(), object.modelMode());
        minecraft.getItemRenderer().renderStatic(
                object.stack(),
                context,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                new FilteredGlintBufferSource(bufferSource, false),
                minecraft.level,
                0);
        bufferSource.endBatch();

        boolean stencilWasEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean[] colorMask = currentColorMask();
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
        int stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        int stencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
        int stencilWriteMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        int stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
        int stencilPassDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
        int stencilPassDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
        GL11.glEnable(GL11.GL_STENCIL_TEST);
        try {
            RenderSystem.clearStencil(0);
            RenderSystem.stencilMask(0xFF);
            RenderSystem.clear(GL11.GL_STENCIL_BUFFER_BIT, false);
            RenderSystem.stencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.depthMask(false);
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            writeFlatItemGlintMask(poseStack, object.shape().modelTransform(), mask);

            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.stencilMask(0x00);
            RenderSystem.stencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            RenderSystem.stencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
            minecraft.getItemRenderer().renderStatic(
                    object.stack(),
                    context,
                    LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    new FilteredGlintBufferSource(bufferSource, true),
                    minecraft.level,
                    0);
            bufferSource.endBatch();
        } finally {
            RenderSystem.colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunc);
            if (depthWasEnabled) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            if (cullWasEnabled) {
                RenderSystem.enableCull();
            } else {
                RenderSystem.disableCull();
            }
            RenderSystem.stencilMask(stencilWriteMask);
            RenderSystem.stencilFunc(stencilFunc, stencilRef, stencilValueMask);
            RenderSystem.stencilOp(stencilFail, stencilPassDepthFail, stencilPassDepthPass);
            if (stencilWasEnabled) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
            } else {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
        }
        return true;
    }

    private static boolean[] currentColorMask() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buffer = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, buffer);
            return new boolean[]{
                    buffer.get(0) != 0,
                    buffer.get(1) != 0,
                    buffer.get(2) != 0,
                    buffer.get(3) != 0};
        }
    }

    private static void writeFlatItemGlintMask(PoseStack poseStack, RealPlaceShape.Transform transform, FlatItemAlphaMask mask) {
        RenderSystem.setShader(GameRenderer::getPositionShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        Matrix4f matrix = poseStack.last().pose();
        for (SpriteAlphaMask spriteMask : mask.sprites()) {
            for (int y = 0; y < spriteMask.height(); y++) {
                int x = 0;
                while (x < spriteMask.width()) {
                    while (x < spriteMask.width() && !spriteMask.solid()[y][x]) {
                        x++;
                    }
                    if (x >= spriteMask.width()) {
                        break;
                    }
                    int startX = x;
                    while (x < spriteMask.width() && spriteMask.solid()[y][x]) {
                        x++;
                    }
                    double minX = (double)startX / (double)spriteMask.width();
                    double maxX = (double)x / (double)spriteMask.width();
                    double minY = 1.0D - (double)(y + 1) / (double)spriteMask.height();
                    double maxY = 1.0D - (double)y / (double)spriteMask.height();
                    addMaskRect(builder, matrix, transform, minX, minY, maxX, maxY, FLAT_ITEM_MASK_FRONT_Z);
                    addMaskRect(builder, matrix, transform, minX, minY, maxX, maxY, FLAT_ITEM_MASK_BACK_Z);
                }
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void addMaskRect(BufferBuilder builder, Matrix4f matrix, RealPlaceShape.Transform transform, double minX, double minY, double maxX, double maxY, double z) {
        addMaskVertex(builder, matrix, transform, minX, minY, z);
        addMaskVertex(builder, matrix, transform, maxX, minY, z);
        addMaskVertex(builder, matrix, transform, maxX, maxY, z);
        addMaskVertex(builder, matrix, transform, minX, maxY, z);
    }

    private static void addMaskVertex(BufferBuilder builder, Matrix4f matrix, RealPlaceShape.Transform transform, double x, double y, double z) {
        Vec3 transformed = transform.apply(x, y, z);
        builder.vertex(matrix, (float)transformed.x, (float)transformed.y, (float)transformed.z).endVertex();
    }

    private static FlatItemAlphaMask createFlatItemAlphaMask(List<BakedModel> models) {
        Set<TextureAtlasSprite> sprites = new LinkedHashSet<>();
        RandomSource random = RandomSource.create();
        for (BakedModel model : models) {
            for (Direction direction : Direction.values()) {
                random.setSeed(42L);
                for (BakedQuad quad : model.getQuads(null, direction, random)) {
                    sprites.add(quad.getSprite());
                }
            }
            random.setSeed(42L);
            for (BakedQuad quad : model.getQuads(null, null, random)) {
                sprites.add(quad.getSprite());
            }
        }
        if (sprites.isEmpty()) {
            for (BakedModel model : models) {
                sprites.add(model.getParticleIcon());
            }
        }
        List<SpriteAlphaMask> masks = new ArrayList<>();
        int totalPixels = 0;
        for (TextureAtlasSprite sprite : sprites) {
            SpriteContents contents = sprite.contents();
            int width = contents.width();
            int height = contents.height();
            boolean[][] solid = new boolean[height][width];
            int solidPixels = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (!contents.isTransparent(0, x, y)) {
                        solid[y][x] = true;
                        solidPixels++;
                    }
                }
            }
            if (solidPixels > 0) {
                totalPixels += solidPixels;
                if (totalPixels > MAX_GLINT_MASK_PIXELS) {
                    return FlatItemAlphaMask.UNPLACEABLE;
                }
                masks.add(new SpriteAlphaMask(sprite, width, height, solid));
            }
        }
        return masks.isEmpty() ? FlatItemAlphaMask.UNPLACEABLE : new FlatItemAlphaMask(masks, true);
    }

    private static boolean renderBlockModelObject(Minecraft minecraft, PoseStack poseStack, MultiBufferSource bufferSource, RealPlaceObject object) {
        if ((object.modelMode() != 1 && !(object.modelMode() == 0 && RealPlaceBlockModeModels.isBed(object.stack())))
                || !(object.stack().getItem() instanceof BlockItem)) {
            return false;
        }
        RealPlaceBlockModeModels.Model model = RealPlaceBlockModeModels.resolve(object.stack());
        if (model.fixedItemFallback() || model.renderParts().isEmpty()) {
            return false;
        }
        for (RealPlaceBlockModeModels.Part part : model.renderParts()) {
            poseStack.pushPose();
            Vec3 offset = part.offset();
            poseStack.translate(offset.x - 0.5D, offset.y - 0.5D, offset.z - 0.5D);
            minecraft.getBlockRenderer().renderSingleBlock(part.state(), poseStack, bufferSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            renderSpecialBlockEntityPart(minecraft, poseStack, bufferSource, part.state());
            poseStack.popPose();
        }
        return true;
    }

    private static void renderSpecialBlockEntityPart(Minecraft minecraft, PoseStack poseStack, MultiBufferSource bufferSource, BlockState state) {
        if (state.getBlock() instanceof BellBlock) {
            BellBlockEntity bell = new BellBlockEntity(BlockPos.ZERO, state);
            minecraft.getBlockEntityRenderDispatcher().renderItem(bell, poseStack, bufferSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    private static void renderPreview(RenderLevelStageEvent event, MultiBufferSource.BufferSource bufferSource) {
        Preview preview = currentPreview();
        if (preview == null) {
            return;
        }
        double camX = event.getCamera().getPosition().x;
        double camY = event.getCamera().getPosition().y;
        double camZ = event.getCamera().getPosition().z;
        float red = preview.valid ? 0.1F : 1.0F;
        float green = preview.valid ? 0.45F : 0.1F;
        float blue = preview.valid ? 1.0F : 0.1F;
        renderOutline(
                event.getPoseStack(),
                bufferSource.getBuffer(RenderType.lines()),
                preview.shape,
                preview.position,
                RealPlaceClientState.yaw(),
                RealPlaceClientState.pitch(),
                RealPlaceClientState.scale(),
                camX,
                camY,
                camZ,
                red,
                green,
                blue,
                1.0F);
    }

    private static void renderSelection(RenderLevelStageEvent event, MultiBufferSource.BufferSource bufferSource) {
        if (RealPlaceClientState.placementMode()) {
            return;
        }
        ObjectHit hit = findLookedAtObjectHit();
        if (hit == null) {
            return;
        }
        double camX = event.getCamera().getPosition().x;
        double camY = event.getCamera().getPosition().y;
        double camZ = event.getCamera().getPosition().z;
        renderOutline(
                event.getPoseStack(),
                bufferSource.getBuffer(RenderType.lines()),
                hit.object.shape(),
                hit.object.position(),
                hit.object.yaw(),
                hit.object.pitch(),
                hit.object.scale(),
                camX,
                camY,
                camZ,
                0.0F,
                0.0F,
                0.0F,
                0.45F);
    }

    private static void renderOutline(PoseStack poseStack, VertexConsumer consumer, RealPlaceShape shape, Vec3 position, float yaw, float pitch, float scale, double camX, double camY, double camZ, float red, float green, float blue, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        for (RealPlaceShape.Line line : shape.outlineLines(position, yaw, pitch, scale)) {
            float x1 = (float)(line.start().x - camX);
            float y1 = (float)(line.start().y - camY);
            float z1 = (float)(line.start().z - camZ);
            float x2 = (float)(line.end().x - camX);
            float y2 = (float)(line.end().y - camY);
            float z2 = (float)(line.end().z - camZ);
            Vector3f normal = new Vector3f(x2 - x1, y2 - y1, z2 - z1);
            if (normal.lengthSquared() < 1.0E-8F) {
                normal.set(0.0F, 1.0F, 0.0F);
            } else {
                normal.normalize();
            }
            consumer.vertex(matrix, x1, y1, z1).color(red, green, blue, alpha).normal(poseStack.last().normal(), normal.x(), normal.y(), normal.z()).endVertex();
            consumer.vertex(matrix, x2, y2, z2).color(red, green, blue, alpha).normal(poseStack.last().normal(), normal.x(), normal.y(), normal.z()).endVertex();
        }
    }

    private static ItemDisplayContext displayContext(ItemStack stack, int modelMode) {
        if (modelMode == 1) {
            if (stack.getItem() instanceof BlockItem && RealPlaceBlockModeModels.useFixedItemFallback(stack)) {
                return ItemDisplayContext.FIXED;
            }
            return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        }
        if (modelMode == 2) {
            return ItemDisplayContext.HEAD;
        }
        return ItemDisplayContext.GROUND;
    }

    private static Preview currentPreview() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!RealPlaceClientState.placementMode()) {
            return null;
        }
        PreviewHit hit = currentPreviewHit();
        if (hit == null) {
            return null;
        }
        ItemStack stack = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        RealPlaceShape shape = RealPlaceShapeBuilder.create(stack, RealPlaceClientState.modelMode());
        Vec3 position = previewPosition(hit, shape);
        AABB box = shape.bounds(position, RealPlaceClientState.yaw(), RealPlaceClientState.pitch(), RealPlaceClientState.scale());
        boolean valid = shape.placeable() && !shape.boxes().isEmpty();
        if (minecraft.level != null) {
            for (net.minecraft.world.phys.shapes.VoxelShape blockShape : minecraft.level.getBlockCollisions(null, box)) {
                if (shape.intersectsVoxelShape(position, RealPlaceClientState.yaw(), RealPlaceClientState.pitch(), RealPlaceClientState.scale(), blockShape)) {
                    valid = false;
                    break;
                }
            }
        }
        if (valid) {
            for (RealPlaceObject object : RealPlaceClientState.objects()) {
                if (object.bounds().intersects(box)
                        && shape.intersectsShape(
                                position,
                                RealPlaceClientState.yaw(),
                                RealPlaceClientState.pitch(),
                                RealPlaceClientState.scale(),
                                object.shape(),
                                object.position(),
                                object.yaw(),
                                object.pitch(),
                                object.scale())) {
                    valid = false;
                    break;
                }
            }
        }
        return new Preview(position, shape, valid);
    }

    private static RealPlaceObject findLookedAtObject() {
        ObjectHit hit = findLookedAtObjectHit();
        return hit == null ? null : hit.object;
    }

    private static ObjectHit findLookedAtObjectHit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        Vec3 start = minecraft.player.getEyePosition();
        double range = minecraft.player.getBlockReach();
        Vec3 end = start.add(minecraft.player.getViewVector(1.0F).scale(range));
        RealPlaceObject nearest = null;
        Vec3 nearestLocation = null;
        Direction nearestDirection = Direction.UP;
        double nearestDistance = Double.MAX_VALUE;
        for (RealPlaceObject object : RealPlaceClientState.objects()) {
            java.util.Optional<RealPlaceShape.ShapeHit> hit = object.shape().clipExact(object.position(), object.yaw(), object.pitch(), object.scale(), start, end);
            if (hit.isPresent()) {
                double distance = hit.get().distance();
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = object;
                    nearestLocation = hit.get().location();
                    nearestDirection = hit.get().direction();
                }
            }
        }
        if (nearest != null && minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS) {
            double vanillaDistance = start.distanceToSqr(minecraft.hitResult.getLocation());
            if (vanillaDistance + 1.0E-5D < nearestDistance) {
                return null;
            }
        }
        return nearest == null ? null : new ObjectHit(nearest, nearestLocation, nearestDirection, nearestDistance);
    }

    private static PreviewHit currentPreviewHit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        Vec3 start = minecraft.player.getEyePosition();
        PreviewHit nearest = null;
        if (minecraft.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            nearest = new PreviewHit(blockHit.getLocation(), blockHit.getDirection(), start.distanceToSqr(blockHit.getLocation()));
        }
        ObjectHit objectHit = findLookedAtObjectHit();
        if (objectHit != null && (nearest == null || objectHit.distance < nearest.distance)) {
            nearest = new PreviewHit(objectHit.location, objectHit.direction, objectHit.distance);
        }
        return nearest;
    }

    private static Vec3 previewPosition(PreviewHit hit) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack stack = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        return previewPosition(hit, RealPlaceShapeBuilder.create(stack, RealPlaceClientState.modelMode()));
    }

    private static Vec3 previewPosition(PreviewHit hit, RealPlaceShape shape) {
        return shape.placementPosition(hit.location, hit.direction, RealPlaceClientState.yaw(), RealPlaceClientState.pitch(), RealPlaceClientState.scale());
    }

    private static void renderHints(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        if (!RealPlaceClientState.placementMode() || !RealPlaceClientConfig.INSTANCE.showPlacementKeyHints()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        List<Component> parts = new ArrayList<>();
        parts.add(Component.translatable("realplace.hint.horizontal_rotate"));
        parts.add(Component.translatable("realplace.hint.vertical_rotate"));
        parts.add(Component.translatable("realplace.hint.scale"));
        parts.add(Component.translatable("realplace.hint.switch_model", SWITCH_MODEL.getTranslatedKeyMessage()));
        parts.add(Component.translatable("realplace.hint.place"));
        int gap = 12;
        int width = -gap;
        for (Component part : parts) {
            width += minecraft.font.width(part) + gap;
        }
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 58;
        graphics.fill(x - 8, y - 5, x + width + 8, y + 15, 0xAA111111);
        int cursor = x;
        for (Component part : parts) {
            graphics.drawString(minecraft.font, part, cursor, y, 0xFFE6E6E6, true);
            cursor += minecraft.font.width(part) + gap;
        }
    }

    public static ObjectHit lookedAtObjectHit() {
        return findLookedAtObjectHit();
    }

    public static RealPlaceObject lookedAtObject() {
        return findLookedAtObject();
    }

    private static int stackTagHash(ItemStack stack) {
        return stack.getTag() == null ? 0 : stack.getTag().hashCode();
    }

    private record Preview(Vec3 position, RealPlaceShape shape, boolean valid) {
    }

    private record PreviewHit(Vec3 location, Direction direction, double distance) {
    }

    public record ObjectHit(RealPlaceObject object, Vec3 location, Direction direction, double distance) {
    }

    private static final class FilteredGlintBufferSource implements MultiBufferSource {
        private final MultiBufferSource delegate;
        private final boolean glintOnly;
        private final VertexConsumer noop = new NoopVertexConsumer();

        private FilteredGlintBufferSource(MultiBufferSource delegate, boolean glintOnly) {
            this.delegate = delegate;
            this.glintOnly = glintOnly;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            boolean glint = isGlint(renderType);
            return glint == glintOnly ? delegate.getBuffer(renderType) : noop;
        }

        private static boolean isGlint(RenderType renderType) {
            return renderType.toString().toLowerCase(java.util.Locale.ROOT).contains("glint");
        }
    }

    private static final class NoopVertexConsumer implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float normalX, float normalY, float normalZ) {
            return this;
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }

        @Override
        public void endVertex() {
        }
    }

    private record FlatItemAlphaMask(List<SpriteAlphaMask> sprites, boolean placeable) {
        private static final FlatItemAlphaMask UNPLACEABLE = new FlatItemAlphaMask(List.of(), false);
    }

    private record SpriteAlphaMask(TextureAtlasSprite sprite, int width, int height, boolean[][] solid) {
    }

    private record GlintMaskKey(ResourceLocation itemId, int stackHash, boolean directRenderType, int modelManagerHash) {
    }
}
