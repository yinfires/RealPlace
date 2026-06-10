package com.yinfires.realplace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yinfires.realplace.RealPlaceItemTransforms;
import com.yinfires.realplace.server.RealPlaceShape;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public final class RealPlaceShapeBuilder {
    private static final double PIXEL = 1.0D / 16.0D;
    private static final double GENERATED_THICKNESS = 1.0D / 16.0D;
    private static final double RENDER_FACE_THICKNESS = 1.0D / 32.0D;
    private static final double QUAD_EPSILON = 1.0E-5D;
    private static final RealPlaceShape.Transform ARMOR_STAND_MODEL_OFFSET = new RealPlaceShape.Transform(
            1.0D, 0.0D, 0.0D, 0.0D,
            0.0D, 1.0D, 0.0D, -0.9D,
            0.0D, 0.0D, 1.0D, 0.0D);
    private static final int MAX_PIXEL_BOXES = 96;
    private static final int MAX_PIXEL_SAMPLE_SIZE = 32;
    private static final int MAX_CACHE_SIZE = 256;
    private static final int[] PIXEL_SAMPLE_STEPS = new int[]{32, 24, 16, 12, 8};
    private static final Map<CacheKey, RealPlaceShape> SHAPE_CACHE = new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, RealPlaceShape> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    private RealPlaceShapeBuilder() {
    }

    public static RealPlaceShape create(ItemStack stack, int modelMode) {
        if (stack.isEmpty()) {
            return RealPlaceShape.fallback(stack, modelMode);
        }
        Minecraft minecraft = Minecraft.getInstance();
        int safeMode = RealPlaceItemTransforms.clampModelMode(stack, modelMode);
        CacheKey key = new CacheKey(
                BuiltInRegistries.ITEM.getKey(stack.getItem()),
                ItemStack.hashItemAndComponents(stack),
                safeMode,
                System.identityHashCode(minecraft.getModelManager()));
        synchronized (SHAPE_CACHE) {
            RealPlaceShape cached = SHAPE_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        RealPlaceShape shape = createUncached(stack.copyWithCount(1), safeMode, minecraft);
        synchronized (SHAPE_CACHE) {
            SHAPE_CACHE.put(key, shape);
        }
        return shape;
    }

    private static RealPlaceShape createUncached(ItemStack stack, int modelMode, Minecraft minecraft) {
        if (modelMode == 2 && stack.getItem() instanceof ArmorItem armorItem && armorItem.getEquipmentSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            return armorShape(stack, minecraft);
        }
        ResolvedItemModel resolved = resolveRenderedModel(stack, modelMode, minecraft);
        RealPlaceShape shape;
        if (stack.getItem() instanceof BlockItem blockItem && modelMode == 1) {
            return blockShape(blockItem, resolved.renderModels()).withTransform(RealPlaceShape.Transform.renderIdentity());
        }
        if (resolved.customRenderer()) {
            shape = captureCustomRendererShape(stack, modelMode).withTransform(resolved.transform());
            if (isUsableShape(shape) || !stack.is(Items.TRIDENT) || modelMode != 1) {
                return shape;
            }
        }
        if (stack.is(Items.TRIDENT) && modelMode == 1) {
            shape = geometryShape(resolved.renderModels(), false);
            if (!isUsableShape(shape)) {
                shape = tridentInHandShape();
            }
        } else if (stack.getItem() instanceof BlockItem && modelMode == 0) {
            shape = pixelShape(resolved.renderModels());
        } else {
            shape = itemShape(resolved);
        }
        return shape.withTransform(resolved.transform());
    }

    private static ResolvedItemModel resolveRenderedModel(ItemStack stack, int modelMode, Minecraft minecraft) {
        Level level = minecraft.level;
        ItemDisplayContext context = displayContext(modelMode);
        boolean groundLike = isGroundLikeContext(context);
        BakedModel model = minecraft.getItemRenderer().getModel(stack, level, minecraft.player, 0);
        if (groundLike) {
            if (stack.is(Items.TRIDENT)) {
                model = minecraft.getModelManager().getModel(ModelResourceLocation.inventory(ResourceLocation.withDefaultNamespace("trident")));
            } else if (stack.is(Items.SPYGLASS)) {
                model = minecraft.getModelManager().getModel(ModelResourceLocation.inventory(ResourceLocation.withDefaultNamespace("spyglass")));
            }
        }
        PoseStack poseStack = new PoseStack();
        model = ClientHooks.handleCameraTransforms(poseStack, model, context, false);
        boolean customRenderer = model.isCustomRenderer() || stack.is(Items.TRIDENT) && !groundLike;
        List<BakedModel> renderModels = customRenderer ? List.of(model) : renderPassModels(stack, context, model);
        return new ResolvedItemModel(model, renderModels, RealPlaceShape.Transform.fromRenderMatrix(poseStack.last().pose()), customRenderer);
    }

    private static RealPlaceShape captureCustomRendererShape(ItemStack stack, int modelMode) {
        CapturingBufferSource bufferSource = new CapturingBufferSource();
        try {
            IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(
                    stack,
                    displayContext(modelMode),
                    new PoseStack(),
                    bufferSource,
                    0xF000F0,
                    OverlayTexture.NO_OVERLAY);
        } catch (RuntimeException ignored) {
            return unsupportedModelShape();
        }
        return capturedBoundsShape(bufferSource.bounds());
    }

    private static ItemDisplayContext displayContext(int modelMode) {
        if (modelMode == 1) {
            return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        }
        if (modelMode == 2) {
            return ItemDisplayContext.HEAD;
        }
        return ItemDisplayContext.GROUND;
    }

    private static boolean isGroundLikeContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.GUI || context == ItemDisplayContext.GROUND || context == ItemDisplayContext.FIXED;
    }

    private static List<BakedModel> renderPassModels(ItemStack stack, ItemDisplayContext context, BakedModel model) {
        boolean directRenderType = true;
        if (context != ItemDisplayContext.GUI && !context.firstPerson() && stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            directRenderType = !(block instanceof HalfTransparentBlock) && !(block instanceof StainedGlassPaneBlock);
        }
        List<BakedModel> passes = model.getRenderPasses(stack, directRenderType);
        return passes.isEmpty() ? List.of(model) : passes;
    }

    private static RealPlaceShape itemShape(ResolvedItemModel resolved) {
        if (resolved.customRenderer()) {
            return unsupportedModelShape();
        }
        return hasGui3dModel(resolved.renderModels()) ? geometryShape(resolved.renderModels(), false) : pixelShape(resolved.renderModels());
    }

    private static boolean hasGui3dModel(List<BakedModel> models) {
        for (BakedModel model : models) {
            if (model.isGui3d()) {
                return true;
            }
        }
        return false;
    }

    private static RealPlaceShape unsupportedModelShape() {
        return RealPlaceShape.unplaceable(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
    }

    private static boolean isUsableShape(RealPlaceShape shape) {
        return shape.placeable() && !shape.boxes().isEmpty();
    }

    private static RealPlaceShape pixelShape(BakedModel model) {
        return pixelShape(List.of(model));
    }

    private static RealPlaceShape pixelShape(List<BakedModel> models) {
        Set<TextureAtlasSprite> sprites = collectSprites(models);
        if (sprites.isEmpty()) {
            for (BakedModel model : models) {
                sprites.add(model.getParticleIcon());
            }
        }
        int width = 1;
        int height = 1;
        for (TextureAtlasSprite sprite : sprites) {
            SpriteContents contents = sprite.contents();
            width = Math.max(width, contents.width());
            height = Math.max(height, contents.height());
        }
        PixelMask bestMask = null;
        for (int maxSampleSize : PIXEL_SAMPLE_STEPS) {
            PixelMask mask = sampleSprites(sprites, width, height, maxSampleSize);
            if (mask.solidPixels() == 0) {
                continue;
            }
            List<RealPlaceShape.Box> boxes = boxesFromMask(mask);
            if (boxes.size() <= MAX_PIXEL_BOXES) {
                return normalizeModelSpace(new RealPlaceShape(boxes));
            }
            List<RealPlaceShape.Box> rowBoxes = rowEnvelopeBoxes(mask);
            if (rowBoxes.size() <= MAX_PIXEL_BOXES) {
                return normalizeModelSpace(new RealPlaceShape(rowBoxes));
            }
            bestMask = mask;
        }
        return bestMask == null ? new RealPlaceShape(List.of()) : normalizeModelSpace(new RealPlaceShape(rowEnvelopeBoxes(bestMask)));
    }

    private static Set<TextureAtlasSprite> collectSprites(List<BakedModel> models) {
        Set<TextureAtlasSprite> sprites = new LinkedHashSet<>();
        for (BakedModel model : models) {
            sprites.addAll(collectSprites(model));
        }
        return sprites;
    }

    private static Set<TextureAtlasSprite> collectSprites(BakedModel model) {
        Set<TextureAtlasSprite> sprites = new LinkedHashSet<>();
        RandomSource random = RandomSource.create();
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
        return sprites;
    }

    private static PixelMask sampleSprites(Set<TextureAtlasSprite> sprites, int originalWidth, int originalHeight, int maxSampleSize) {
        double scale = Math.min(1.0D, (double)Math.min(MAX_PIXEL_SAMPLE_SIZE, maxSampleSize) / (double)Math.max(originalWidth, originalHeight));
        int width = Math.max(1, Math.min(originalWidth, (int)Math.round(originalWidth * scale)));
        int height = Math.max(1, Math.min(originalHeight, (int)Math.round(originalHeight * scale)));
        boolean[][] solid = new boolean[height][width];
        int solidPixels = 0;
        for (TextureAtlasSprite sprite : sprites) {
            SpriteContents contents = sprite.contents();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int spriteX = Math.min(contents.width() - 1, (int)((x + 0.5D) * contents.width() / width));
                    int spriteY = Math.min(contents.height() - 1, (int)((y + 0.5D) * contents.height() / height));
                    if (!solid[y][x] && !contents.isTransparent(0, spriteX, spriteY)) {
                        solid[y][x] = true;
                        solidPixels++;
                    }
                }
            }
        }
        return new PixelMask(width, height, solid, solidPixels);
    }

    private static List<RealPlaceShape.Box> boxesFromMask(PixelMask mask) {
        List<RealPlaceShape.Box> boxes = new ArrayList<>();
        boolean[][] consumed = new boolean[mask.height()][mask.width()];
        for (int y = 0; y < mask.height(); y++) {
            for (int x = 0; x < mask.width(); x++) {
                if (!mask.solid()[y][x] || consumed[y][x]) {
                    continue;
                }
                int runWidth = 1;
                while (x + runWidth < mask.width() && mask.solid()[y][x + runWidth] && !consumed[y][x + runWidth]) {
                    runWidth++;
                }
                int runHeight = 1;
                boolean canGrow = true;
                while (y + runHeight < mask.height() && canGrow) {
                    for (int dx = 0; dx < runWidth; dx++) {
                        if (!mask.solid()[y + runHeight][x + dx] || consumed[y + runHeight][x + dx]) {
                            canGrow = false;
                            break;
                        }
                    }
                    if (canGrow) {
                        runHeight++;
                    }
                }
                for (int yy = y; yy < y + runHeight; yy++) {
                    for (int xx = x; xx < x + runWidth; xx++) {
                        consumed[yy][xx] = true;
                    }
                }
                boxes.add(pixelBox(mask.width(), mask.height(), x, y, runWidth, runHeight));
            }
        }
        return boxes;
    }

    private static List<RealPlaceShape.Box> rowEnvelopeBoxes(PixelMask mask) {
        List<RealPlaceShape.Box> boxes = new ArrayList<>();
        for (int y = 0; y < mask.height(); y++) {
            int minX = mask.width();
            int maxX = -1;
            for (int x = 0; x < mask.width(); x++) {
                if (mask.solid()[y][x]) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                }
            }
            if (maxX >= minX) {
                boxes.add(pixelBox(mask.width(), mask.height(), minX, y, maxX - minX + 1, 1));
            }
        }
        return boxes;
    }

    private static RealPlaceShape.Box pixelBox(int width, int height, int x, int y, int runWidth, int runHeight) {
        double minX = (double)x / (double)width - 0.5D;
        double maxX = (double)(x + runWidth) / (double)width - 0.5D;
        double minY = 0.5D - (double)(y + runHeight) / (double)height;
        double maxY = 0.5D - (double)y / (double)height;
        return new RealPlaceShape.Box(minX, minY, -GENERATED_THICKNESS * 0.5D, maxX, maxY, GENERATED_THICKNESS * 0.5D);
    }

    private static RealPlaceShape blockShape(BlockItem blockItem, List<BakedModel> renderModels) {
        VoxelShape voxelShape = blockItem.getBlock().defaultBlockState().getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        if (!voxelShape.isEmpty()) {
            List<RealPlaceShape.Box> boxes = new ArrayList<>();
            for (AABB box : voxelShape.toAabbs()) {
                if (box.getXsize() >= 1.0E-4D && box.getYsize() >= 1.0E-4D && box.getZsize() >= 1.0E-4D) {
                    boxes.add(RealPlaceShape.Box.from(box));
                }
            }
            if (!boxes.isEmpty()) {
                return new RealPlaceShape(boxes);
            }
        }
        return geometryShape(renderModels, false);
    }

    private static RealPlaceShape geometryShape(BakedModel model, boolean mergeToBounds) {
        return geometryShape(List.of(model), mergeToBounds);
    }

    private static RealPlaceShape geometryShape(List<BakedModel> models, boolean mergeToBounds) {
        List<AABB> bounds = new ArrayList<>();
        RandomSource random = RandomSource.create();
        for (BakedModel model : models) {
            for (Direction direction : Direction.values()) {
                random.setSeed(42L);
                addQuadBounds(model.getQuads(null, direction, random), bounds);
            }
            random.setSeed(42L);
            addQuadBounds(model.getQuads(null, null, random), bounds);
        }
        if (bounds.isEmpty()) {
            return RealPlaceShape.unplaceable(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
        }
        bounds = compactQuadBounds(bounds);
        if (bounds.size() > RealPlaceShape.MAX_BOXES) {
            AABB merged = null;
            for (AABB box : bounds) {
                merged = merged == null ? box : merged.minmax(box);
            }
            return merged == null ? new RealPlaceShape(List.of()) : RealPlaceShape.unplaceable(merged);
        }
        if (mergeToBounds) {
            AABB merged = null;
            for (AABB box : bounds) {
                merged = merged == null ? box : merged.minmax(box);
            }
            return merged == null ? RealPlaceShape.unplaceable(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)) : RealPlaceShape.single(merged);
        }
        List<RealPlaceShape.Box> boxes = new ArrayList<>(bounds.size());
        for (AABB box : bounds) {
            if (box.getXsize() >= 1.0E-4D && box.getYsize() >= 1.0E-4D && box.getZsize() >= 1.0E-4D) {
                boxes.add(RealPlaceShape.Box.from(box));
            }
        }
        if (!boxes.isEmpty()) {
            return new RealPlaceShape(boxes);
        }
        AABB merged = null;
        for (AABB box : bounds) {
            merged = merged == null ? box : merged.minmax(box);
        }
        return merged == null ? RealPlaceShape.unplaceable(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D)) : RealPlaceShape.unplaceable(merged);
    }

    private static RealPlaceShape capturedBoundsShape(List<AABB> bounds) {
        return capturedBoundsShape(bounds, true);
    }

    private static RealPlaceShape capturedBoundsShape(List<AABB> bounds, boolean rebuildCuboids) {
        bounds = rebuildCuboids ? compactQuadBounds(bounds) : paddedFaceBounds(bounds);
        if (bounds.isEmpty()) {
            return unsupportedModelShape();
        }
        if (bounds.size() > RealPlaceShape.MAX_BOXES) {
            AABB merged = null;
            for (AABB box : bounds) {
                merged = merged == null ? box : merged.minmax(box);
            }
            return merged == null ? unsupportedModelShape() : RealPlaceShape.unplaceable(merged);
        }
        List<RealPlaceShape.Box> boxes = new ArrayList<>(bounds.size());
        for (AABB box : bounds) {
            if (box.getXsize() >= 1.0E-4D && box.getYsize() >= 1.0E-4D && box.getZsize() >= 1.0E-4D) {
                boxes.add(RealPlaceShape.Box.from(box));
            }
        }
        return boxes.isEmpty() ? unsupportedModelShape() : new RealPlaceShape(boxes);
    }

    private static List<AABB> paddedFaceBounds(List<AABB> rawBounds) {
        List<AABB> deduped = dedupeBounds(rawBounds);
        List<AABB> padded = new ArrayList<>(deduped.size());
        for (AABB box : deduped) {
            padded.add(paddedBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
        }
        return padded;
    }

    private static List<AABB> compactQuadBounds(List<AABB> rawBounds) {
        List<AABB> bounds = dedupeBounds(rawBounds);
        List<AABB> compacted = new ArrayList<>(bounds.size());
        boolean[] used = new boolean[bounds.size()];
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < bounds.size(); i++) {
                if (used[i]) {
                    continue;
                }
                CuboidMatch match = findCuboid(bounds, used, i);
                if (match != null) {
                    compacted.add(match.bounds());
                    for (int index : match.faceIndexes()) {
                        used[index] = true;
                    }
                    changed = true;
                    break;
                }
            }
        } while (changed);
        for (int i = 0; i < bounds.size(); i++) {
            if (!used[i]) {
                AABB box = bounds.get(i);
                compacted.add(paddedBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
            }
        }
        return compacted;
    }

    private static List<AABB> dedupeBounds(List<AABB> rawBounds) {
        List<AABB> bounds = new ArrayList<>(rawBounds.size());
        Set<String> seen = new LinkedHashSet<>();
        for (AABB box : rawBounds) {
            String key = quantize(box.minX) + "," + quantize(box.minY) + "," + quantize(box.minZ) + ","
                    + quantize(box.maxX) + "," + quantize(box.maxY) + "," + quantize(box.maxZ);
            if (seen.add(key)) {
                bounds.add(box);
            }
        }
        return bounds;
    }

    private static CuboidMatch findCuboid(List<AABB> bounds, boolean[] used, int seedIndex) {
        AABB seed = bounds.get(seedIndex);
        Direction.Axis seedAxis = thinAxis(seed);
        if (seedAxis == null) {
            return null;
        }
        for (int oppositeIndex = 0; oppositeIndex < bounds.size(); oppositeIndex++) {
            if (oppositeIndex == seedIndex || used[oppositeIndex]) {
                continue;
            }
            AABB opposite = bounds.get(oppositeIndex);
            if (thinAxis(opposite) != seedAxis || near(axisMin(seed, seedAxis), axisMin(opposite, seedAxis))) {
                continue;
            }
            AABB candidate = seed.minmax(opposite);
            if (candidate.getXsize() <= QUAD_EPSILON || candidate.getYsize() <= QUAD_EPSILON || candidate.getZsize() <= QUAD_EPSILON) {
                continue;
            }
            int[] faces = new int[6];
            int size = 0;
            size = addFaceIndex(faces, size, findFace(bounds, used, candidate, Direction.Axis.X, candidate.minX, faces, size));
            size = addFaceIndex(faces, size, findFace(bounds, used, candidate, Direction.Axis.X, candidate.maxX, faces, size));
            size = addFaceIndex(faces, size, findFace(bounds, used, candidate, Direction.Axis.Y, candidate.minY, faces, size));
            size = addFaceIndex(faces, size, findFace(bounds, used, candidate, Direction.Axis.Y, candidate.maxY, faces, size));
            size = addFaceIndex(faces, size, findFace(bounds, used, candidate, Direction.Axis.Z, candidate.minZ, faces, size));
            size = addFaceIndex(faces, size, findFace(bounds, used, candidate, Direction.Axis.Z, candidate.maxZ, faces, size));
            if (size == 6) {
                return new CuboidMatch(candidate, faces);
            }
        }
        return null;
    }

    private static int findFace(List<AABB> bounds, boolean[] used, AABB cuboid, Direction.Axis axis, double plane, int[] existing, int existingSize) {
        for (int i = 0; i < bounds.size(); i++) {
            if (used[i] || contains(existing, existingSize, i)) {
                continue;
            }
            if (matchesFace(bounds.get(i), cuboid, axis, plane)) {
                return i;
            }
        }
        return -1;
    }

    private static int addFaceIndex(int[] faces, int size, int index) {
        if (index < 0 || contains(faces, size, index)) {
            return size;
        }
        faces[size] = index;
        return size + 1;
    }

    private static boolean contains(int[] values, int size, int value) {
        for (int i = 0; i < size; i++) {
            if (values[i] == value) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesFace(AABB face, AABB cuboid, Direction.Axis axis, double plane) {
        if (thinAxis(face) != axis || !near(axisMin(face, axis), plane) || !near(axisMax(face, axis), plane)) {
            return false;
        }
        for (Direction.Axis otherAxis : Direction.Axis.values()) {
            if (otherAxis != axis && (!near(axisMin(face, otherAxis), axisMin(cuboid, otherAxis)) || !near(axisMax(face, otherAxis), axisMax(cuboid, otherAxis)))) {
                return false;
            }
        }
        return true;
    }

    private static Direction.Axis thinAxis(AABB box) {
        if (thin(box.getXsize())) {
            return Direction.Axis.X;
        }
        if (thin(box.getYsize())) {
            return Direction.Axis.Y;
        }
        if (thin(box.getZsize())) {
            return Direction.Axis.Z;
        }
        return null;
    }

    private static double axisMin(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.minX;
            case Y -> box.minY;
            case Z -> box.minZ;
        };
    }

    private static double axisMax(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.maxX;
            case Y -> box.maxY;
            case Z -> box.maxZ;
        };
    }

    private static boolean thin(double size) {
        return Math.abs(size) <= QUAD_EPSILON;
    }

    private static boolean near(double first, double second) {
        return Math.abs(first - second) <= QUAD_EPSILON;
    }

    private static long quantize(double value) {
        return Math.round(value / QUAD_EPSILON);
    }

    private static void addQuadBounds(List<BakedQuad> quads, List<AABB> bounds) {
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 4; i++) {
                int base = i * stride;
                double x = Float.intBitsToFloat(vertices[base]);
                double y = Float.intBitsToFloat(vertices[base + 1]);
                double z = Float.intBitsToFloat(vertices[base + 2]);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
            bounds.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }

    private static AABB paddedBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (maxX - minX < QUAD_EPSILON) {
            minX -= RENDER_FACE_THICKNESS * 0.5D;
            maxX += RENDER_FACE_THICKNESS * 0.5D;
        }
        if (maxY - minY < QUAD_EPSILON) {
            minY -= RENDER_FACE_THICKNESS * 0.5D;
            maxY += RENDER_FACE_THICKNESS * 0.5D;
        }
        if (maxZ - minZ < QUAD_EPSILON) {
            minZ -= RENDER_FACE_THICKNESS * 0.5D;
            maxZ += RENDER_FACE_THICKNESS * 0.5D;
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static RealPlaceShape armorShape(ItemStack stack, Minecraft minecraft) {
        RealPlaceShape captured = captureArmorRendererShape(stack, minecraft);
        if (isUsableShape(captured)) {
            return captured;
        }
        if (stack.getItem() instanceof ArmorItem armorItem) {
            return fallbackArmorShape(armorItem.getEquipmentSlot());
        }
        return unsupportedModelShape();
    }

    private static RealPlaceShape captureArmorRendererShape(ItemStack stack, Minecraft minecraft) {
        if (minecraft.level == null || !(stack.getItem() instanceof ArmorItem armorItem)) {
            return unsupportedModelShape();
        }
        ArmorStand stand = new ArmorStand(minecraft.level, 0.0D, 0.0D, 0.0D);
        stand.setNoBasePlate(true);
        stand.setShowArms(false);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                stand.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        stand.setItemSlot(armorItem.getEquipmentSlot(), stack);
        CapturingBufferSource bufferSource = new CapturingBufferSource();
        try {
            minecraft.getEntityRenderDispatcher().render(
                    stand,
                    0.0D,
                    -0.9D,
                    0.0D,
                    0.0F,
                    1.0F,
                    new PoseStack(),
                    bufferSource,
                    LightTexture.FULL_BRIGHT);
        } catch (RuntimeException ignored) {
            return unsupportedModelShape();
        }
        return capturedBoundsShape(bufferSource.bounds(), false);
    }

    private static RealPlaceShape fallbackArmorShape(EquipmentSlot slot) {
        List<RealPlaceShape.Box> boxes = new ArrayList<>();
        switch (slot) {
            case HEAD -> boxes.add(new RealPlaceShape.Box(-0.32D, 0.5D, -0.32D, 0.32D, 0.92D, 0.32D));
            case CHEST -> {
                boxes.add(new RealPlaceShape.Box(-0.32D, -0.08D, -0.18D, 0.32D, 0.58D, 0.18D));
                boxes.add(new RealPlaceShape.Box(-0.5D, -0.08D, -0.14D, -0.32D, 0.48D, 0.14D));
                boxes.add(new RealPlaceShape.Box(0.32D, -0.08D, -0.14D, 0.5D, 0.48D, 0.14D));
            }
            case LEGS -> {
                boxes.add(new RealPlaceShape.Box(-0.28D, -0.18D, -0.16D, 0.28D, 0.28D, 0.16D));
                boxes.add(new RealPlaceShape.Box(-0.24D, -0.68D, -0.14D, -0.04D, -0.18D, 0.14D));
                boxes.add(new RealPlaceShape.Box(0.04D, -0.68D, -0.14D, 0.24D, -0.18D, 0.14D));
            }
            case FEET -> {
                boxes.add(new RealPlaceShape.Box(-0.24D, -0.88D, -0.22D, -0.02D, -0.68D, 0.16D));
                boxes.add(new RealPlaceShape.Box(0.02D, -0.88D, -0.22D, 0.24D, -0.68D, 0.16D));
            }
            default -> boxes.add(new RealPlaceShape.Box(-0.32D, 0.05D, -0.18D, 0.32D, 0.58D, 0.18D));
        }
        return new RealPlaceShape(boxes, ARMOR_STAND_MODEL_OFFSET);
    }

    private static RealPlaceShape tridentInHandShape() {
        List<RealPlaceShape.Box> boxes = new ArrayList<>();
        boxes.add(new RealPlaceShape.Box(0.465D, -0.32D, 0.465D, 0.535D, 0.98D, 0.535D));
        boxes.add(new RealPlaceShape.Box(0.445D, 0.92D, 0.445D, 0.555D, 1.32D, 0.555D));
        boxes.add(new RealPlaceShape.Box(0.28D, 0.92D, 0.47D, 0.36D, 1.22D, 0.53D));
        boxes.add(new RealPlaceShape.Box(0.64D, 0.92D, 0.47D, 0.72D, 1.22D, 0.53D));
        boxes.add(new RealPlaceShape.Box(0.32D, 0.88D, 0.475D, 0.68D, 0.98D, 0.525D));
        return new RealPlaceShape(boxes);
    }

    private static RealPlaceShape normalizeModelSpace(RealPlaceShape shape) {
        List<RealPlaceShape.Box> boxes = new ArrayList<>(shape.boxes().size());
        for (RealPlaceShape.Box box : shape.boxes()) {
            boxes.add(new RealPlaceShape.Box(
                    box.minX() + 0.5D,
                    box.minY() + 0.5D,
                    box.minZ() + 0.5D,
                    box.maxX() + 0.5D,
                    box.maxY() + 0.5D,
                    box.maxZ() + 0.5D));
        }
        return new RealPlaceShape(boxes, shape.modelTransform(), shape.placeable());
    }

    private record ResolvedItemModel(BakedModel model, List<BakedModel> renderModels, RealPlaceShape.Transform transform, boolean customRenderer) {
    }

    private static final class CapturingBufferSource implements MultiBufferSource {
        private final CapturingVertexConsumer consumer = new CapturingVertexConsumer();
        private final VertexConsumer noop = new NoopVertexConsumer();

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            String name = renderType.toString().toLowerCase(Locale.ROOT);
            return name.contains("glint") ? noop : consumer;
        }

        List<AABB> bounds() {
            return consumer.bounds();
        }
    }

    private static final class CapturingVertexConsumer implements VertexConsumer {
        private final List<AABB> bounds = new ArrayList<>();
        private final double[] quad = new double[12];
        private int vertices;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            int base = vertices * 3;
            quad[base] = x;
            quad[base + 1] = y;
            quad[base + 2] = z;
            vertices++;
            if (vertices == 4) {
                addQuadBounds();
                vertices = 0;
            }
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int packedOverlay, int packedLight, float normalX, float normalY, float normalZ) {
            addVertex(x, y, z);
        }

        private void addQuadBounds() {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 4; i++) {
                int base = i * 3;
                minX = Math.min(minX, quad[base]);
                minY = Math.min(minY, quad[base + 1]);
                minZ = Math.min(minZ, quad[base + 2]);
                maxX = Math.max(maxX, quad[base]);
                maxY = Math.max(maxY, quad[base + 1]);
                maxZ = Math.max(maxZ, quad[base + 2]);
            }
            bounds.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        }

        List<AABB> bounds() {
            return bounds;
        }
    }

    private static final class NoopVertexConsumer implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int packedOverlay, int packedLight, float normalX, float normalY, float normalZ) {
        }
    }

    private record CacheKey(ResourceLocation itemId, int stackHash, int modelMode, int modelManagerHash) {
    }

    private record PixelMask(int width, int height, boolean[][] solid, int solidPixels) {
    }

    private record CuboidMatch(AABB bounds, int[] faceIndexes) {
    }
}
