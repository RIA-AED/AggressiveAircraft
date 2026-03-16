package dev.ignis.aggressiveaircraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ignis.aggressiveaircraft.AggressiveAircraft;
import dev.ignis.aggressiveaircraft.entities.ClusterDispenserEntity;
import immersive_aircraft.resources.BBModelLoader;
import immersive_aircraft.resources.bbmodel.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.*;
import net.minecraft.util.Mth;
import org.joml.Math;

public class ClusterDispenserRenderer extends EntityRenderer<ClusterDispenserEntity> {
    // 模型ID：命名空间:路径（不含.bbmodel后缀）
    // 实际路径：assets/aggressiveaircraft/objects/cluster_dispenser_entity.bbmodel
    private static final ResourceLocation MODEL_ID = ResourceLocation.tryBuild(AggressiveAircraft.MODID, "cluster_dispenser_entity");

    public ClusterDispenserRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ClusterDispenserEntity entity, float entityYaw, float partialTicks, PoseStack matrixStack, MultiBufferSource buffer, int packedLight) {
        BBModel bbModel = BBModelLoader.MODELS.get(MODEL_ID);
        if (bbModel == null) {
            // 模型未加载，渲染一个调试立方体
            renderDebugCube(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
            return;
        }

        matrixStack.pushPose();

        // 使用实体自身的旋转角度（标准 Minecraft 实体渲染方式）
        matrixStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        matrixStack.mulPose(Axis.XP.rotationDegrees(entity.getViewXRot(partialTicks)));
        matrixStack.mulPose(Axis.ZP.rotationDegrees(entity.getRoll(partialTicks)));

        // 手动渲染模型（不依赖 VehicleEntity 泛型）
        float time = (entity.level().getGameTime() % 24000 + partialTicks) / 20.0f;
        bbModel.root.forEach(object -> renderObject(bbModel, object, matrixStack, buffer, packedLight, time));

        matrixStack.popPose();
        super.render(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
    }

    private void renderObject(BBModel model, BBObject object, PoseStack matrixStack, MultiBufferSource buffer, int light, float time) {
        matrixStack.pushPose();
        matrixStack.translate(object.origin.x(), object.origin.y(), object.origin.z());

        // 应用动画
        if (!model.animations.isEmpty()) {
            BBAnimation animation = model.animations.get(0);
            if (animation.hasAnimator(object.uuid)) {
                Vector3f position = animation.sample(object.uuid, BBAnimator.Channel.POSITION, time);
                position.mul(1.0f / 16.0f);
                matrixStack.translate(position.x(), position.y(), position.z());

                Vector3f rotation = animation.sample(object.uuid, BBAnimator.Channel.ROTATION, time);
                rotation.mul(1.0f / 180.0f * (float) Math.PI);
                matrixStack.mulPose(new Quaternionf().rotationXYZ(rotation.x(), rotation.y(), rotation.z()));

                Vector3f scale = animation.sample(object.uuid, BBAnimator.Channel.SCALE, time);
                matrixStack.scale(scale.x(), scale.y(), scale.z());
            }
        }

        // 应用对象旋转
        matrixStack.mulPose(new Quaternionf().rotationXYZ(object.rotation.x(), object.rotation.y(), object.rotation.z()));

        // 骨骼处理
        if (object instanceof BBBone bone) {
            matrixStack.translate(-object.origin.x(), -object.origin.y(), -object.origin.z());
            if (bone.visibility) {
                bone.children.forEach(child -> renderObject(model, child, matrixStack, buffer, light, time));
            }
        }

        // 渲染立方体
        if (object instanceof BBFaceContainer cube) {
            renderFaces(cube, matrixStack, buffer, light);
        }

        matrixStack.popPose();
    }

    private void renderFaces(BBFaceContainer cube, PoseStack matrixStack, MultiBufferSource buffer, int light) {
        PoseStack.Pose pose = matrixStack.last();
        Matrix4f positionMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();

        for (BBFace face : cube.getFaces()) {
            VertexConsumer vertexConsumer = buffer.getBuffer(
                cube.enableCulling() ? RenderType.entityCutout(face.texture.location) : RenderType.entityCutoutNoCull(face.texture.location)
            );
            for (int i = 0; i < 4; i++) {
                BBFace.BBVertex v = face.vertices[i];
                vertexConsumer.vertex(positionMatrix, v.x, v.y, v.z);
                vertexConsumer.color(255, 255, 255, 255);
                vertexConsumer.uv(v.u, v.v);
                vertexConsumer.overlayCoords(OverlayTexture.NO_OVERLAY);
                vertexConsumer.uv2(light);
                vertexConsumer.normal(normalMatrix, v.nx, v.ny, v.nz);
                vertexConsumer.endVertex();
            }
        }
    }

    private void renderDebugCube(ClusterDispenserEntity entity, float entityYaw, float partialTicks, PoseStack matrixStack, MultiBufferSource buffer, int packedLight) {
        matrixStack.pushPose();
        
        // 位置和旋转
        matrixStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        matrixStack.mulPose(Axis.XP.rotationDegrees(entity.getViewXRot(partialTicks)));
        matrixStack.mulPose(Axis.ZP.rotationDegrees(entity.getRoll(partialTicks)));
        
        // 渲染一个红色立方体作为调试
        PoseStack.Pose pose = matrixStack.last();
        Matrix4f matrix4f = pose.pose();
        Matrix3f matrix3f = pose.normal();

        @SuppressWarnings("removal")
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(
            new ResourceLocation("minecraft", "textures/block/red_wool.png")
        ));
        
        // 简单的立方体渲染
        float size = 0.5f;
        renderBoxFace(vertexConsumer, matrix4f, matrix3f, packedLight, size, 1.0f, 0.0f, 0.0f);
        
        matrixStack.popPose();
        super.render(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
    }
    
    private void renderBoxFace(VertexConsumer vc, Matrix4f pm, Matrix3f nm, int light, float s, float r, float g, float b) {
        // 前面
        vertex(vc, pm, nm, light, -s, -s, s, r, g, b);
        vertex(vc, pm, nm, light, s, -s, s, r, g, b);
        vertex(vc, pm, nm, light, s, s, s, r, g, b);
        vertex(vc, pm, nm, light, -s, s, s, r, g, b);
    }
    
    private void vertex(VertexConsumer vc, Matrix4f pm, Matrix3f nm, int light, float x, float y, float z, float r, float g, float b) {
        vc.vertex(pm, x, y, z)
            .color((int)(r*255), (int)(g*255), (int)(b*255), 255)
            .uv(0, 0)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(nm, 0, 0, 1)
            .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(ClusterDispenserEntity entity) {
        return MODEL_ID;
    }
}
