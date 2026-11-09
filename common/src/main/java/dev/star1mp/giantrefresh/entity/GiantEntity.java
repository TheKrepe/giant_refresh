package dev.star1mp.giantrefresh.entity;

import dev.star1mp.giantrefresh.client.GiantClientHooks;
import dev.star1mp.giantrefresh.config.GRConfig;
import dev.star1mp.giantrefresh.entity.ai.GiantAttackGoal;
import dev.star1mp.giantrefresh.registry.GRContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.star1mp.giantrefresh.entity.anim.GiantPose;
import org.joml.Vector3f;

/**
 * The refreshed Giant: a slow, heavy, 12-block tall undead with telegraphed attacks,
 * an optional harness "hat" that carries a small crew of hostile riders.
 */
public class GiantEntity extends Monster {
    // ------------------------------------------------------------------ synced data
    private static final EntityDataAccessor<Byte> DATA_ATTACK = SynchedEntityData.defineId(GiantEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_SLEEPING = SynchedEntityData.defineId(GiantEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_HAT = SynchedEntityData.defineId(GiantEntity.class, EntityDataSerializers.BYTE);

    // ------------------------------------------------------------------ entity events (client effects)
    public static final byte EVENT_STEP_LEFT = 100;
    public static final byte EVENT_STEP_RIGHT = 101;
    public static final byte EVENT_STOMP = 102;
    public static final byte EVENT_PUNCH = 103;
    public static final byte EVENT_WAKE = 104;
    public static final byte EVENT_ROAR = 105;
    public static final byte EVENT_DEATH_THUD = 106;

    // ------------------------------------------------------------------ attacks
    public static final int ATTACK_NONE = 0;
    public static final int ATTACK_PUNCH = 1;
    public static final int ATTACK_STOMP = 2;
    public static final int ATTACK_SCOOP = 3;

    /** Tick at which the blow lands (wind-up length). */
    public static int impactTick(int attack) {
        return switch (attack) {
            case ATTACK_PUNCH -> 20;
            case ATTACK_STOMP -> 26;
            case ATTACK_SCOOP -> 15;
            default -> 0;
        };
    }

    /** Total length of the attack, including recovery. */
    public static int attackLength(int attack) {
        return switch (attack) {
            case ATTACK_PUNCH -> 34;
            case ATTACK_STOMP -> 40;
            case ATTACK_SCOOP -> 28;
            default -> 0;
        };
    }

    /** Divides the walk-cycle so that a huge body takes slow, long strides. Shared with the renderer. */
    public static final float STRIDE = 2.6F;
    public static final int MAX_RIDERS = 4;
    /** Rider slots on top of the hat, in head-space pixels (x, z). */
    private static final float[][] RIDER_SLOTS = {{-6.0F, -3.0F}, {6.0F, -3.0F}, {-6.0F, 6.5F}, {6.0F, 6.5F}};
    /** Death animation length in ticks (vanilla is 20). */
    public static final int DEATH_TICKS = 40;

    // ------------------------------------------------------------------ server state
    private int attackTicks;
    private int attackCooldown;
    private int lastStepIndex = Integer.MIN_VALUE;
    private boolean nextStepRight;
    private boolean hadTarget;

    // ------------------------------------------------------------------ shared animation state (both sides)
    private int roarTicks = -1;
    private int wakeTicks = -1;
    private float aggroAmount;
    private float aggroAmountO;
    private final float animSeed;
    private final GiantPose riderPose = new GiantPose();

    // ------------------------------------------------------------------ client state
    private int clientAttack;
    private int clientAttackTicks;
    private float sleepAmount;
    private float sleepAmountO;
    private int gesture;
    private int gestureTicks;
    private int gestureCooldown = 200;

    public GiantEntity(EntityType<? extends GiantEntity> type, Level level) {
        super(type, level);
        this.xpReward = 80;
        this.setPathfindingMalus(PathType.LEAVES, 0.0F);
        this.animSeed = (float) ((getUUID().getLeastSignificantBits() >>> 11) % 10000L) / 10000.0F * Mth.TWO_PI;
    }

    public static AttributeSupplier.Builder createAttributes() {
        GRConfig cfg = GRConfig.get();
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, cfg.giantMaxHealth)
                .add(Attributes.MOVEMENT_SPEED, cfg.giantMovementSpeed)
                .add(Attributes.ATTACK_DAMAGE, cfg.giantPunchDamage)
                .add(Attributes.FOLLOW_RANGE, cfg.giantFollowRange)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.STEP_HEIGHT, 1.6)
                .add(Attributes.SAFE_FALL_DISTANCE, 10.0)
                .add(Attributes.CAMERA_DISTANCE, 16.0);
    }

    // ================================================================== setup
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new GiantAttackGoal(this));
        this.goalSelector.addGoal(5, new MoveThroughVillageGoal(this, 0.8, false, 6, () -> false));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, GiantEntity.class, Zombie.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACK, (byte) 0);
        builder.define(DATA_SLEEPING, false);
        builder.define(DATA_HAT, (byte) -1);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("GiantSleeping", isGiantSleeping());
        output.putInt("HatColor", getHatColor());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setGiantSleeping(input.getBooleanOr("GiantSleeping", false));
        setHatColor(input.getIntOr("HatColor", -1));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason, SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, groupData);
        RandomSource random = level.getRandom();
        GRConfig cfg = GRConfig.get();
        if (reason != EntitySpawnReason.CONVERSION && random.nextDouble() < cfg.hatChance) {
            setHatColor(random.nextInt(16));
        }
        if (hasHat() && random.nextDouble() < cfg.ridersChance) {
            GiantRiders.spawnRiders(this, level, difficulty);
        }
        if ((reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.EVENT) && random.nextDouble() < cfg.hordeChance) {
            GiantRiders.spawnHorde(this, level, difficulty);
        }
        return data;
    }

    // ================================================================== state accessors
    public int getAttackType() {
        return entityData.get(DATA_ATTACK);
    }

    public boolean isAttacking() {
        return getAttackType() != ATTACK_NONE;
    }

    public int getAttackTicks() {
        return attackTicks;
    }

    public int getAttackCooldown() {
        return attackCooldown;
    }

    public boolean isGiantSleeping() {
        return entityData.get(DATA_SLEEPING);
    }

    public void setGiantSleeping(boolean sleeping) {
        entityData.set(DATA_SLEEPING, sleeping);
        setPose(sleeping ? Pose.SITTING : Pose.STANDING);
        if (sleeping) {
            sleepAmount = sleepAmountO = 1.0F;
        }
    }

    public int getHatColor() {
        return entityData.get(DATA_HAT);
    }

    public void setHatColor(int color) {
        entityData.set(DATA_HAT, (byte) Mth.clamp(color, -1, 15));
    }

    public boolean hasHat() {
        return getHatColor() >= 0;
    }

    /** Client: attack animation clock. */
    public int getClientAttack() {
        return clientAttack;
    }

    public float getClientAttackTime(float partialTick) {
        return clientAttack == ATTACK_NONE ? 0.0F : clientAttackTicks + partialTick;
    }

    public float getSleepAmount(float partialTick) {
        if (!level().isClientSide()) {
            return isGiantSleeping() ? 1.0F : 0.0F;
        }
        return Mth.lerp(partialTick, sleepAmountO, sleepAmount);
    }

    // ================================================================== dimensions & riders
    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions base = super.getDefaultDimensions(pose);
        if (pose == Pose.SITTING) {
            return EntityDimensions.scalable(3.6F, 8.6F).withEyeHeight(7.0F);
        }
        return base;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return hasHat() && passenger instanceof Mob && getPassengers().size() < MAX_RIDERS;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return null; // riders never steer the Giant
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        int index = getPassengers().indexOf(passenger);
        Vec3 pos = riderPosition(Math.max(0, index), 1.0F);
        // like vanilla: the rider's own seat offset, so a sitting rider rests on the hat instead of hovering
        Vec3 seat = passenger.getVehicleAttachmentPoint(this);
        moveFunction.accept(passenger, pos.x - seat.x, pos.y - seat.y, pos.z - seat.z);
    }

    /** World position of rider slot {@code index} standing on the harness hat (follows the animated head). */
    public Vec3 riderPosition(int index, float partialTick) {
        float[] slot = RIDER_SLOTS[index % RIDER_SLOTS.length];
        fillPose(riderPose, partialTick);
        riderPose.compute();
        Vector3f off = riderPose.headPointOffset(Mth.rotLerp(partialTick, yBodyRotO, yBodyRot), slot[0], GiantPose.HAT_TOP, slot[1]);
        return new Vec3(Mth.lerp(partialTick, xo, getX()) + off.x, Mth.lerp(partialTick, yo, getY()) + off.y,
                Mth.lerp(partialTick, zo, getZ()) + off.z);
    }

    /** Fills the animation inputs for this Giant (used by the renderer and by rider placement). */
    public void fillPose(GiantPose pose, float partialTick) {
        boolean client = level().isClientSide();
        pose.age = tickCount + partialTick;
        pose.walkPos = walkAnimation.position(partialTick) / STRIDE;
        pose.walkSpeed = walkAnimation.speed(partialTick);
        pose.headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, yHeadRotO, yHeadRot) - Mth.rotLerp(partialTick, yBodyRotO, yBodyRot));
        pose.headPitch = getXRot(partialTick);
        if (client) {
            pose.attack = clientAttack;
            pose.attackTime = getClientAttackTime(partialTick);
            pose.gesture = gesture;
            pose.gestureTime = gesture == GiantPose.GESTURE_NONE ? 0.0F : gestureTicks + partialTick;
        } else {
            pose.attack = getAttackType();
            pose.attackTime = attackTicks + partialTick;
            pose.gesture = GiantPose.GESTURE_NONE;
            pose.gestureTime = 0.0F;
        }
        pose.sleep = getSleepAmount(partialTick);
        pose.aggro = Mth.lerp(partialTick, aggroAmountO, aggroAmount);
        pose.seed = animSeed;
        pose.hurt = hurtTime > 0 ? Math.max(0.0F, (hurtTime - partialTick) / 10.0F) : 0.0F;
        pose.roar = roarTicks < 0 ? -1.0F : roarTicks + partialTick;
        pose.wake = wakeTicks < 0 ? -1.0F : wakeTicks + partialTick;
        pose.death = deathTime > 0 ? deathTime + partialTick : 0.0F;
    }

    public boolean isRoaring() {
        return roarTicks >= 0 && roarTicks < GiantPose.ROAR_LENGTH;
    }

    @Override
    public int getMaxHeadYRot() {
        return 35;
    }

    @Override
    public int getMaxHeadXRot() {
        return 15;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isGiantSleeping();
    }

    // ================================================================== sounds
    @Override
    protected SoundEvent getAmbientSound() {
        return isGiantSleeping() ? GRContent.GIANT_SNORE.get() : GRContent.GIANT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GRContent.GIANT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GRContent.GIANT_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // Footsteps are played on real foot-falls, see onFootfall().
    }

    @Override
    protected float getSoundVolume() {
        return 3.0F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return isGiantSleeping() ? 90 : 200;
    }

    // ================================================================== tick
    @Override
    public void tick() {
        super.tick();
        tickSharedAnimation();
        if (level().isClientSide()) {
            clientTick();
            return;
        }
        ServerLevel server = (ServerLevel) level();
        LivingEntity target = getTarget();
        boolean hasTarget = target != null && target.isAlive();
        if (hasTarget && !hadTarget && !isGiantSleeping() && !isAttacking() && isAlive()) {
            roar(server);
        }
        hadTarget = hasTarget;
        if (isGiantSleeping()) {
            if (tickCount % 10 == 0) {
                checkWakeUp(server);
            }
            return;
        }
        detectFootfalls(server);
        if (tickCount % 4 == 0) {
            breakObstacles(server, horizontalCollision || tickCount % 20 == 0);
        }
        if (tickCount % 10 == 0) {
            GiantRiders.tickRiders(this, server);
        }
    }

    private void tickSharedAnimation() {
        if (roarTicks >= 0 && ++roarTicks > GiantPose.ROAR_LENGTH) {
            roarTicks = -1;
        }
        if (wakeTicks >= 0 && ++wakeTicks > GiantPose.WAKE_LENGTH) {
            wakeTicks = -1;
        }
        aggroAmountO = aggroAmount;
        float target = isAggressive() && !isGiantSleeping() ? 1.0F : 0.0F;
        aggroAmount = aggroAmount < target ? Math.min(target, aggroAmount + 0.06F) : Math.max(target, aggroAmount - 0.04F);
    }

    private void roar(ServerLevel level) {
        roarTicks = 0;
        getNavigation().stop();
        level.playSound(null, getX(), getY() + 9, getZ(), GRContent.GIANT_ROAR.get(), getSoundSource(), 4.0F, 0.85F + random.nextFloat() * 0.1F);
        level.broadcastEntityEvent(this, EVENT_ROAR);
    }

    /** Client-only idle gestures: scratching the head, looking around, stretching, thumping the chest. */
    private void tickGestures() {
        if (gesture != GiantPose.GESTURE_NONE) {
            if (++gestureTicks > GiantPose.gestureLength(gesture) || isAttacking() || isGiantSleeping() || aggroAmount > 0.3F) {
                gesture = GiantPose.GESTURE_NONE;
                gestureTicks = 0;
                gestureCooldown = 160 + random.nextInt(400);
            }
            return;
        }
        boolean idle = !walkAnimation.isMoving() && !isAttacking() && !isGiantSleeping() && aggroAmount < 0.05F
                && roarTicks < 0 && wakeTicks < 0 && deathTime == 0;
        if (idle && --gestureCooldown <= 0) {
            gesture = 1 + random.nextInt(4);
            gestureTicks = 0;
        }
    }

    private void clientTick() {
        tickGestures();
        int attack = getAttackType();
        if (attack != clientAttack) {
            clientAttack = attack;
            clientAttackTicks = 0;
        } else if (attack != ATTACK_NONE) {
            clientAttackTicks++;
        }
        sleepAmountO = sleepAmount;
        float target = isGiantSleeping() ? 1.0F : 0.0F;
        sleepAmount = sleepAmount < target ? Math.min(target, sleepAmount + 0.05F) : Math.max(target, sleepAmount - 0.05F);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        tickAttack(level);
    }

    // ================================================================== attacks
    public void startAttack(int attack) {
        if (isAttacking() || attack == ATTACK_NONE) {
            return;
        }
        attackTicks = 0;
        entityData.set(DATA_ATTACK, (byte) attack);
        getNavigation().stop();
        SoundEvent cue = attack == ATTACK_STOMP ? GRContent.GIANT_ROAR.get() : GRContent.GIANT_SWING.get();
        playSound(cue, attack == ATTACK_STOMP ? 2.5F : 2.0F, 0.7F + random.nextFloat() * 0.2F);
    }

    private void tickAttack(ServerLevel level) {
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        int attack = getAttackType();
        if (attack == ATTACK_NONE) {
            return;
        }
        attackTicks++;
        LivingEntity target = getTarget();
        // Track the target during most of the wind-up, then commit so the player can dodge.
        if (target != null && attackTicks < impactTick(attack) - 7) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            float wanted = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            float yaw = Mth.approachDegrees(getYRot(), wanted, 8.0F);
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
        if (attackTicks == impactTick(attack)) {
            switch (attack) {
                case ATTACK_PUNCH -> impactPunch(level);
                case ATTACK_STOMP -> impactStomp(level);
                case ATTACK_SCOOP -> impactScoop(level);
                default -> {}
            }
        }
        if (attackTicks >= attackLength(attack)) {
            entityData.set(DATA_ATTACK, (byte) ATTACK_NONE);
            attackTicks = 0;
            attackCooldown = 18 + random.nextInt(22);
        }
    }

    private Vec3 forward() {
        double yaw = Math.toRadians(yBodyRot);
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    private Vec3 right() {
        double yaw = Math.toRadians(yBodyRot);
        return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
    }

    public Vec3 footPosition(boolean rightFoot) {
        Vec3 side = right().scale(rightFoot ? 0.72 : -0.72);
        return position().add(side).add(forward().scale(0.5));
    }

    private boolean canHit(LivingEntity e) {
        if (e == this || !e.isAlive() || e.getVehicle() == this || e instanceof GiantEntity) {
            return false;
        }
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return false;
        }
        // Undead friends and other monsters are spared unless they are the actual target.
        return !(e instanceof Enemy) || e == getTarget();
    }

    private void impactPunch(ServerLevel level) {
        Vec3 fwd = forward();
        Vec3 center = position().add(fwd.scale(3.4));
        AABB box = new AABB(center.x - 2.6, getY() - 0.5, center.z - 2.6, center.x + 2.6, getY() + 7.0, center.z + 2.6);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
            if (e.hurtServer(level, damageSources().mobAttack(this), damage)) {
                e.knockback(2.2, -fwd.x, -fwd.z, damageSources().mobAttack(this), damage);
                e.addDeltaMovement(new Vec3(0, 0.45, 0));
                e.needsSync = true;
            }
        }
        BlockState ground = level.getBlockState(BlockPos.containing(center.x, getY() - 0.5, center.z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), center.x, getY() + 0.2, center.z, 40, 1.6, 0.2, 1.6, 0.2);
        }
        level.playSound(null, center.x, getY(), center.z, GRContent.GIANT_PUNCH.get(), getSoundSource(), 3.0F, 0.8F + random.nextFloat() * 0.15F);
        level.broadcastEntityEvent(this, EVENT_PUNCH);
    }

    private void impactStomp(ServerLevel level) {
        Vec3 center = footPosition(true).add(forward().scale(0.8));
        double radius = 6.5;
        AABB box = new AABB(center.x - radius, getY() - 1.0, center.z - radius, center.x + radius, getY() + 3.0, center.z + radius);
        float base = (float) GRConfig.get().giantStompDamage;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
            double dx = e.getX() - center.x;
            double dz = e.getZ() - center.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > radius || !(e.onGround() || e.getY() - getY() < 1.5)) {
                continue;
            }
            double falloff = 1.0 - dist / (radius + 0.5);
            if (e.hurtServer(level, damageSources().mobAttack(this), Math.max(2.0F, base * (float) falloff))) {
                e.knockback(0.6 + 1.4 * falloff, -dx, -dz, damageSources().mobAttack(this), base);
                e.addDeltaMovement(new Vec3(0, 0.25 + 0.45 * falloff, 0));
                e.needsSync = true;
            }
        }
        // a ring of dust and earth
        for (int ring = 1; ring <= 5; ring++) {
            int points = ring * 8;
            for (int i = 0; i < points; i++) {
                double a = (Math.PI * 2 * i) / points;
                double px = center.x + Math.cos(a) * ring;
                double pz = center.z + Math.sin(a) * ring;
                BlockState st = level.getBlockState(BlockPos.containing(px, getY() - 0.5, pz));
                if (!st.isAir()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), px, getY() + 0.1, pz, 2, 0.2, 0.05, 0.2, 0.15);
                }
            }
        }
        level.sendParticles(ParticleTypes.POOF, center.x, getY() + 0.3, center.z, 20, 2.0, 0.2, 2.0, 0.05);
        level.playSound(null, center.x, getY(), center.z, GRContent.GIANT_STOMP.get(), getSoundSource(), 4.0F, 0.7F + random.nextFloat() * 0.1F);
        level.broadcastEntityEvent(this, EVENT_STOMP);
        crushPlants(level, center, 2);
        leaveFootprint(level, center, true);
    }

    private void impactScoop(ServerLevel level) {
        Vec3 fwd = forward();
        Vec3 center = position().add(fwd.scale(3.0));
        AABB box = new AABB(center.x - 2.0, getY() - 0.5, center.z - 2.0, center.x + 2.0, getY() + 3.0, center.z + 2.0);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.4F;
        boolean hitAny = false;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, this::canHit)) {
            if (e.getBbHeight() > 2.6F) {
                continue;
            }
            e.hurtServer(level, damageSources().mobAttack(this), damage);
            e.setDeltaMovement(fwd.x * 1.15, 1.35, fwd.z * 1.15);
            e.resetFallDistance();
            e.needsSync = true;
            hitAny = true;
        }
        level.playSound(null, center.x, getY(), center.z, GRContent.GIANT_SWING.get(), getSoundSource(), 2.5F, hitAny ? 0.6F : 0.9F);
    }

    // ================================================================== world interaction
    private boolean canGrief(ServerLevel level) {
        return level.getGameRules().get(GameRules.MOB_GRIEFING);
    }

    private void detectFootfalls(ServerLevel level) {
        float pos = walkAnimation.position() / STRIDE;
        int index = Mth.floor(pos * 0.6662F / (float) Math.PI);
        if (index != lastStepIndex) {
            boolean first = lastStepIndex == Integer.MIN_VALUE;
            lastStepIndex = index;
            if (!first && walkAnimation.speed() > 0.12F && onGround()) {
                nextStepRight = (index & 1) == 0;
                onFootfall(level, nextStepRight);
            }
        }
    }

    private void onFootfall(ServerLevel level, boolean rightFoot) {
        Vec3 foot = footPosition(rightFoot);
        BlockState ground = level.getBlockState(BlockPos.containing(foot.x, getY() - 0.3, foot.z));
        level.playSound(null, foot.x, getY(), foot.z, GRContent.GIANT_STEP.get(), getSoundSource(), 2.2F, 0.75F + random.nextFloat() * 0.2F);
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), foot.x, getY() + 0.1, foot.z, 18, 0.7, 0.05, 0.7, 0.12);
        }
        level.broadcastEntityEvent(this, rightFoot ? EVENT_STEP_RIGHT : EVENT_STEP_LEFT);
        crushPlants(level, foot, 1);
        squashSmallCreatures(level, foot);
        if (random.nextDouble() < GRConfig.get().footprintChance) {
            leaveFootprint(level, foot, false);
        }
    }

    private void crushPlants(ServerLevel level, Vec3 at, int r) {
        if (!GRConfig.get().crushPlants || !canGrief(level)) {
            return;
        }
        int y = Mth.floor(getY());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    p.set(Mth.floor(at.x) + dx, y + dy, Mth.floor(at.z) + dz);
                    BlockState st = level.getBlockState(p);
                    if (st.is(GRContent.GIANT_CRUSHABLE)) {
                        level.destroyBlock(p, false, this);
                    } else if (st.is(Blocks.FARMLAND)) {
                        level.setBlockAndUpdate(p, Blocks.DIRT.defaultBlockState());
                    }
                }
            }
        }
    }

    private void squashSmallCreatures(ServerLevel level, Vec3 foot) {
        AABB box = new AABB(foot.x - 0.9, getY() - 0.2, foot.z - 0.9, foot.x + 0.9, getY() + 1.0, foot.z + 0.9);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !(e instanceof Enemy) && !(e instanceof Player) && e.getVehicle() != this && e.getBbHeight() <= 1.0F);
        for (LivingEntity e : victims) {
            e.hurtServer(level, damageSources().mobAttack(this), 6.0F);
        }
    }

    private void leaveFootprint(ServerLevel level, Vec3 at, boolean large) {
        GRConfig cfg = GRConfig.get();
        if (!cfg.leaveFootprints || !canGrief(level)) {
            return;
        }
        int r = large ? 1 : 0;
        int y = Mth.floor(getY() - 0.3);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r - 1; dx <= r; dx++) {
            for (int dz = -r - 1; dz <= r; dz++) {
                if (!large && random.nextInt(4) == 0) {
                    continue;
                }
                p.set(Mth.floor(at.x) + dx, y, Mth.floor(at.z) + dz);
                if (level.getBlockState(p).is(GRContent.FOOTPRINT_REPLACEABLE) && level.getBlockState(p.above()).isAir()) {
                    level.setBlockAndUpdate(p, Blocks.COARSE_DIRT.defaultBlockState());
                }
            }
        }
    }

    private void breakObstacles(ServerLevel level, boolean active) {
        if (!active || !GRConfig.get().breakBlocks || !canGrief(level)) {
            return;
        }
        AABB box = getBoundingBox().inflate(0.45, 0.0, 0.45).expandTowards(0.0, 2.8, 0.0);
        int broken = 0;
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), Mth.floor(box.minY + 0.6), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            BlockState st = level.getBlockState(pos);
            if (st.is(GRContent.GIANT_BREAKABLE)) {
                level.destroyBlock(pos, true, this);
                if (++broken >= 16) {
                    break;
                }
            }
        }
    }

    private void checkWakeUp(ServerLevel level) {
        Player nearest = level.getNearestPlayer(this, 24.0);
        if (nearest == null || nearest.isCreative() || nearest.isSpectator()) {
            return;
        }
        double dist = Math.sqrt(distanceToSqr(nearest));
        boolean sneaking = nearest.isShiftKeyDown();
        boolean loud = nearest.isSprinting() && dist < 16.0;
        boolean tooClose = dist < (sneaking ? 4.5 : 8.0);
        if (loud || tooClose) {
            wakeUp(level, nearest);
        }
    }

    public void wakeUp(ServerLevel level, Entity cause) {
        if (!isGiantSleeping()) {
            return;
        }
        setGiantSleeping(false);
        wakeTicks = 0;
        hadTarget = true; // the wake-up stretch replaces the roar
        level.playSound(null, getX(), getY() + 8, getZ(), GRContent.GIANT_ROAR.get(), getSoundSource(), 4.0F, 0.75F);
        level.broadcastEntityEvent(this, EVENT_WAKE);
        if (cause instanceof LivingEntity living && living.isAlive() && !(living instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            setTarget(living);
        }
    }

    // ================================================================== damage & loot
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && (attacker.getVehicle() == this || attacker instanceof GiantEntity)) {
            return false;
        }
        if (isGiantSleeping()) {
            wakeUp(level, attacker);
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (source.getEntity() instanceof Creeper creeper && creeper.isPowered()) {
            spawnAtLocation(level, new ItemStack(GRContent.GIANT_HEAD.get()));
        }
    }

    /** A slower death: the Giant topples forward like a felled tree and hits the ground. */
    @Override
    protected void tickDeath() {
        deathTime++;
        if (level() instanceof ServerLevel server) {
            if (deathTime == 23) {
                double yaw = Math.toRadians(yBodyRot);
                double fx = -Math.sin(yaw);
                double fz = Math.cos(yaw);
                for (int i = 2; i <= 11; i++) {
                    double px = getX() + fx * i;
                    double pz = getZ() + fz * i;
                    BlockState st = server.getBlockState(BlockPos.containing(px, getY() - 0.5, pz));
                    if (!st.isAir()) {
                        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), px, getY() + 0.2, pz, 14, 1.2, 0.1, 1.2, 0.15);
                    }
                }
                server.sendParticles(ParticleTypes.POOF, getX() + fx * 7, getY() + 0.5, getZ() + fz * 7, 30, 3.0, 0.3, 3.0, 0.03);
                server.playSound(null, getX() + fx * 6, getY(), getZ() + fz * 6, GRContent.GIANT_STOMP.get(), getSoundSource(), 4.0F, 0.6F);
                server.broadcastEntityEvent(this, EVENT_DEATH_THUD);
            }
            if (deathTime >= DEATH_TICKS && !isRemoved()) {
                server.broadcastEntityEvent(this, (byte) 60); // vanilla death poof
                remove(Entity.RemovalReason.KILLED);
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return !isPersistenceRequired() && super.removeWhenFarAway(distance);
    }

    // ================================================================== client effects
    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case EVENT_STEP_LEFT, EVENT_STEP_RIGHT -> GiantClientHooks.onImpact(this, 0.35F);
            case EVENT_PUNCH -> GiantClientHooks.onImpact(this, 0.6F);
            case EVENT_STOMP -> GiantClientHooks.onImpact(this, 1.0F);
            case EVENT_WAKE -> {
                wakeTicks = 0;
                GiantClientHooks.onImpact(this, 0.45F);
            }
            case EVENT_ROAR -> {
                roarTicks = 0;
                GiantClientHooks.onImpact(this, 0.3F);
            }
            case EVENT_DEATH_THUD -> GiantClientHooks.onImpact(this, 1.0F);
            default -> super.handleEntityEvent(id);
        }
    }

    /** Ground height helper used by riders jumping off and by the horde spawner. */
    public static double groundY(Level level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    public static ItemStack tridentStack() {
        return new ItemStack(Items.TRIDENT);
    }
}
