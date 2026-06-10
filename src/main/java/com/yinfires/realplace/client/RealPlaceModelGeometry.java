package com.yinfires.realplace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yinfires.realplace.server.RealPlaceShape;
import net.minecraft.client.renderer.block.model.ItemTransform;

public final class RealPlaceModelGeometry {
    private final RealPlaceShape localShape;

    private RealPlaceModelGeometry(RealPlaceShape localShape) {
        this.localShape = localShape;
    }

    public static RealPlaceModelGeometry of(RealPlaceShape localShape) {
        return new RealPlaceModelGeometry(localShape);
    }

    public RealPlaceModelGeometry withDisplayTransform(ItemTransform transform) {
        if (transform == ItemTransform.NO_TRANSFORM || localShape.boxes().isEmpty()) {
            return new RealPlaceModelGeometry(localShape.withTransform(RealPlaceShape.Transform.renderIdentity()));
        }
        PoseStack poseStack = new PoseStack();
        transform.apply(false, poseStack);
        return new RealPlaceModelGeometry(localShape.withTransform(RealPlaceShape.Transform.fromRenderMatrix(poseStack.last().pose())));
    }

    public RealPlaceModelGeometry withCenteredDisplayTransform(ItemTransform transform) {
        if (transform == ItemTransform.NO_TRANSFORM || localShape.boxes().isEmpty()) {
            return this;
        }
        PoseStack poseStack = new PoseStack();
        transform.apply(false, poseStack);
        return new RealPlaceModelGeometry(localShape.withTransform(RealPlaceShape.Transform.fromMatrix(poseStack.last().pose())));
    }

    public RealPlaceShape shape() {
        return localShape;
    }
}
