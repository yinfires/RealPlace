package com.yinfires.realplace.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yinfires.realplace.RealPlace;
import com.yinfires.realplace.RealPlaceClientConfig;
import com.yinfires.realplace.RealPlaceClientState;
import com.yinfires.realplace.RealPlaceItemTransforms;
import com.yinfires.realplace.network.PickupRealObjectPayload;
import com.yinfires.realplace.network.PlaceRealObjectPayload;
import com.yinfires.realplace.network.RealPlacePlacementModePayload;
import com.yinfires.realplace.server.RealPlaceObject;
import com.yinfires.realplace.server.RealPlaceShape;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

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

    private static final ResourceLocation HINT_LAYER = ResourceLocation.fromNamespaceAndPath(RealPlace.MOD_ID, "placement_key_hints");

    private RealPlaceClient() {
    }

    public static void registerModBus(IEventBus modEventBus) {
        modEventBus.addListener(RealPlaceClient::registerKeys);
        modEventBus.addListener(RealPlaceClient::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(RealPlaceClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(RealPlaceClient::onRenderLevel);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(PLACEMENT_MODE);
        event.register(SWITCH_MODEL);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(HINT_LAYER, RealPlaceClient::renderHints);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            RealPlaceClientState.setPlacementMode(false);
            return;
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
        PacketDistributor.sendToServer(new RealPlacePlacementModePayload(active));
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
            PacketDistributor.sendToServer(new PickupRealObjectPayload(object.id(), hand));
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
            PacketDistributor.sendToServer(new PickupRealObjectPayload(object.id(), hand));
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
        if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, RealPlaceClientState.previewStack())) {
            return;
        }
        int modelMode = RealPlaceItemTransforms.clampModelMode(stack, RealPlaceClientState.modelMode());
        Preview preview = currentPreview();
        if (preview == null) {
            return;
        }
        PacketDistributor.sendToServer(new PlaceRealObjectPayload(
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

    private static void renderObject(RenderLevelStageEvent event, MultiBufferSource bufferSource, RealPlaceObject object) {
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
        minecraft.getItemRenderer().renderStatic(object.stack(), displayContext(object.stack(), object.modelMode()), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, event.getPoseStack(), bufferSource, minecraft.level, 0);
        event.getPoseStack().popPose();
    }

    private static boolean renderBlockModelObject(Minecraft minecraft, PoseStack poseStack, MultiBufferSource bufferSource, RealPlaceObject object) {
        if (object.modelMode() != 1 || !(object.stack().getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        poseStack.pushPose();
        poseStack.translate(-0.5D, -0.5D, -0.5D);
        minecraft.getBlockRenderer().renderSingleBlock(state, poseStack, bufferSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        return true;
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
            consumer.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, alpha).setNormal(poseStack.last(), normal.x(), normal.y(), normal.z());
            consumer.addVertex(matrix, x2, y2, z2).setColor(red, green, blue, alpha).setNormal(poseStack.last(), normal.x(), normal.y(), normal.z());
        }
    }

    private static ItemDisplayContext displayContext(ItemStack stack, int modelMode) {
        if (modelMode == 1) {
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
        double range = minecraft.player.blockInteractionRange();
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

    private static void renderHints(GuiGraphics graphics, DeltaTracker deltaTracker) {
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

    private record Preview(Vec3 position, RealPlaceShape shape, boolean valid) {
    }

    private record PreviewHit(Vec3 location, Direction direction, double distance) {
    }

    public record ObjectHit(RealPlaceObject object, Vec3 location, Direction direction, double distance) {
    }
}
