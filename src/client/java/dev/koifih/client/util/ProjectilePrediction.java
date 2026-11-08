package dev.koifih.client.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProjectilePrediction {
    public static final float ARROW_SPEED = 3f;
    private static final float ARROW_DRAG = 0.99f;
    private static final float ARROW_GRAVITY = 0.05f;
    private static final double THROWN_DRAG = 0.99;
    private static final double MARGIN_DELAY_TICKS = 2;
    private static final double MARGIN_RAMP_TICKS = 20;
    private static final double SPAWN_MATCH_SQR = 0.01;
    private static final double EYE_DROP = 0.1;
    private static final int FLIGHT_TICKS = 100;

    public record Launch(Vec3 position, Vec3 velocity, double gravity) {}

    public record Intercept(Vec3 direction, Vec3 point, double error) {}

    public static BlockHitResult arrow(LocalPlayer player, float speed) {
        return landing(player, throwOrigin(player),
                Vec3.directionFromRotation(player.getXRot(), player.getYRot()).scale(speed), ARROW_DRAG, ARROW_GRAVITY);
    }

    public static BlockHitResult arrow(LocalPlayer player, AbstractArrow arrow) {
        return landing(player, arrow.position(), arrow.getDeltaMovement(), ARROW_DRAG, ARROW_GRAVITY);
    }

    public static BlockHitResult landing(LocalPlayer player, Vec3 position, Vec3 motion, float drag, float gravity) {
        for (int tick = 0; tick < FLIGHT_TICKS; tick++) {
            Vec3 next = position.add(motion);
            BlockHitResult hit = Game.level().clip(new ClipContext(position, next, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK) return hit;
            position = next;
            motion = motion.scale(drag).subtract(0.0, gravity, 0.0);
        }
        return null;
    }

    public static Vec3 throwOrigin(LocalPlayer player) {
        return player.getEyePosition().subtract(0.0, EYE_DROP, 0.0);
    }

    public static Launch launch(Projectile thrown, Vec3 origin) {
        Vec3 position = thrown.position();
        Vec3 velocity = thrown.getDeltaMovement();
        for (int tick = 0; tick <= thrown.tickCount + 1; tick++) {
            if (position.distanceToSqr(origin) < SPAWN_MATCH_SQR) return new Launch(position, velocity, thrown.getGravity());
            position = position.subtract(velocity);
            velocity = velocity.scale(1.0 / THROWN_DRAG).add(0.0, thrown.getGravity(), 0.0);
        }
        return null;
    }

    public static Intercept windCharge(LocalPlayer player, Launch pearl, int ticksLater) {
        Vec3 origin = player.getEyePosition();
        Vec3 moved = player.position().subtract(player.oldPosition());
        Vec3 motion = player.onGround() ? moved.horizontal() : moved;
        Vec3 position = pearl.position();
        Vec3 velocity = pearl.velocity();
        Intercept best = null;
        for (int tick = 1; tick <= FLIGHT_TICKS; tick++) {
            velocity = velocity.add(0.0, -pearl.gravity(), 0.0).scale(THROWN_DRAG);
            Vec3 next = position.add(velocity);
            if (blocked(player, position, next)) break;
            int moves = tick - 1 - ticksLater;
            if (moves >= 1) {
                double margin = Math.clamp((tick - MARGIN_DELAY_TICKS) / MARGIN_RAMP_TICKS, 0.0, ProjectileUtil.DEFAULT_ENTITY_HIT_RESULT_MARGIN);
                double reach = EntityTypes.WIND_CHARGE.getWidth() / 2 + margin;
                Intercept hit = intercept(position, next, origin.add(motion.scale(moves)), WindChargeItem.PROJECTILE_SHOOT_POWER * moves, reach);
                if (hit != null && (best == null || hit.error() < best.error())) best = hit;
            }
            position = next;
        }
        return best == null || best.error() > 0 || blocked(player, origin, best.point()) ? null : best;
    }

    private static Intercept intercept(Vec3 from, Vec3 to, Vec3 center, double radius, double reach) {
        Vec3 step = to.subtract(from);
        double length = step.length();
        Vec3 along = step.scale(1.0 / length);
        Vec3 start = from.add(along.scale(reach / Math.max(Math.abs(along.x), Math.max(Math.abs(along.y), Math.abs(along.z)))));
        Vec3 offset = start.subtract(center);
        double b = offset.dot(along);
        double root = b * b - offset.lengthSqr() + radius * radius;
        if (root < 0) return null;
        double half = length / 2;
        double near = -b - Math.sqrt(root);
        double far = -b + Math.sqrt(root);
        double travel = Math.abs(near - half) < Math.abs(far - half) ? near : far;
        Vec3 point = start.add(along.scale(travel));
        return new Intercept(point.subtract(center).normalize(), point, Math.abs(travel - half) - half);
    }

    private static boolean blocked(LocalPlayer player, Vec3 from, Vec3 to) {
        return Game.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS;
    }
}
