package dev.star1mp.giantrefresh.cinema;

import dev.star1mp.giantrefresh.entity.GiantEntity;
import dev.star1mp.giantrefresh.entity.anim.GiantPose;
import dev.star1mp.giantrefresh.registry.GRContent;
import java.util.function.DoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/** Storyboard marks in a live, seeded Minecraft world. Coordinates are feet unless offset. */
final class Shorts extends Trailer.T {
    private final int number;
    private Vec3 center;
    private static final double[] LENGTH = {18, 23, 21, 25, 22};
    private static final long[] SUN = {2750, 1800, 12450, 4100, 10500};

    Shorts(int number) {
        super(LENGTH[number - 1], SUN[number - 1]);
        this.number = number;
    }

    private Vec3 atMark(double x,double y,double z) { return center.add(x,y,z); }
    private BlockPos cell(Vec3 point) { return BlockPos.containing(point); }
    private void place(Vec3 p, BlockState block) { level.setBlock(cell(p), block, 3); }
    private void hand(Actor a, ItemStack item) { a.living().setItemSlot(EquipmentSlot.MAINHAND,item); }
    private void pair(double start, double end, Vec3 eye, DoubleFunction<Vec3> subject, float fov) {
        cam.at(start,eye,subject).fov(fov);
        cam.at(end-.002,eye,subject).fov(fov);
    }
    private void pair(double start, double end, Vec3 eye, Vec3 subject, float fov) {
        pair(start,end,eye,fixed(subject),fov);
    }
    private static double u(double t,double a,double b) {
        double x=Math.max(0,Math.min(1,(t-a)/(b-a)));return x*x*(3-2*x);
    }
    @Override void scene() {
        cmd("difficulty normal");
        center=at(7,-32);
        int x=cell(center).getX(),z=cell(center).getZ(),y=cell(center).getY();
        Sets.flatten(level,x-12,z-6,x+13,z+12,y,18);
        Sets.scatterPlants(level,x-13,z-8,x+13,z+13,y,.095,.035,71);
        switch(number) {
            case 1 -> pillar();
            case 2 -> camp();
            case 3 -> passengers();
            case 4 -> potionDoor();
            case 5 -> farm();
            default -> throw new IllegalArgumentException();
        }
        // Capture blocking from the same ticks as the video, for the editor's continuity check.
        during(0,duration,t->{
            if (Math.round(t*20)%10!=0) return;
            StringBuilder marks=new StringBuilder();
            for(Actor a:actors) marks.append(String.format(java.util.Locale.ROOT," %s(%.2f,%.2f,%.2f;%d;%b)",a.e.getClass().getSimpleName(),a.e.getX()-center.x,a.e.getY()-center.y,a.e.getZ()-center.z,a.e.getId(),a.e.isPassenger()));
            dev.star1mp.giantrefresh.GiantRefresh.LOGGER.info("[BLOCKING] {} {}",t,marks);
        });
    }

    private void pillar() {
        Actor giant=giant(atMark(-2,0,1),178,-1,false).face(178);
        GiantEntity g=giantOf(giant);
        Vec3 palm=g.cinemaHandAt(GiantEntity.ATTACK_GRAB,GiantPose.GRAB_CATCH);
        Vec3 foot=new Vec3(Math.floor(palm.x)+.5,center.y,Math.floor(palm.z)+.5);
        int top=Math.max(5,(int)Math.round(palm.y-center.y-.9));
        int initial=Math.max(0,top-6);
        for(int i=0;i<initial;i++)place(foot.add(0,i,0),Blocks.DIRT.defaultBlockState());
        Actor steve=steve(foot.add(0,initial,0),180);
        hand(steve,new ItemStack(Items.DIRT));
        steve.e.setInvisible(true);
        hand(steve,ItemStack.EMPTY);
        steve.lookAt(foot.add(.3,-1,.1));
        Actor zombie=summon("minecraft:zombie",foot.add(-2.5,0,-1.8),-30,"NoAI:1b,Invulnerable:1b");
        zombie.living().setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.LEATHER_HELMET));
        zombie.lookAt(steve.e,1);
        on(.1,()->zombie.walkTo(foot.add(-.85,0,-.6),.57));
        on(1.48,()->zombie.living().swing(InteractionHand.MAIN_HAND));
        final double[] beats={.08,.68,1.29,1.88,2.50,3.10};
        for(int i=0;i<Math.min(6,top);i++){
            double t=beats[i];int height=initial+i;
            on(t,()->{steve.e.setDeltaMovement(0,.48,0);steve.living().swing(InteractionHand.MAIN_HAND);});
            on(t+.22,()->{
                steve.e.setPos(foot.x,foot.y+height+1.08,foot.z);
                place(foot.add(0,height,0),Blocks.DIRT.defaultBlockState());
                dust(foot.add(.30,height+.95,-.3),3,.18);
            });
        }
        Vec3 perch=foot.add(0,top,0);
        on(3.44,()->{steve.e.setInvisible(false);hand(steve,new ItemStack(Items.DIRT));steve.lookAt(perch.add(1.2,1.6,-3.55));});
        on(3.65,()->steve.crouch(true).lookAt(zombie.e,1.1));
        on(4.7,()->steve.crouch(false).lookAt(perch.add(1,1.45,-3.6)));
        on(5.65,()->hand(steve,new ItemStack(Items.BREAD)));
        on(6.75,()->steve.living().startUsingItem(InteractionHand.MAIN_HAND));
        on(8.05,()->{steve.living().stopUsingItem();steve.lookAt(g,8);});
        on(8.68,()->steve.crouch(true).lookAt(g,10));
        on(9.43,()->{steve.crouch(false); hand(steve,ItemStack.EMPTY);});
        on(10.83,()->steve.living().swing(InteractionHand.MAIN_HAND));
        on(9.75,()->{steve.living().stopUsingItem(); g.startAttack(GiantEntity.ATTACK_GRAB);});
        during(10.58,10.85,t->{if(g.getHeld()==null)g.showcaseGrab(steve.living(),GiantEntity.ATTACK_GRAB,g.getAttackTicks());});
        jolt(11.58,.5);jolt(12.03,.77);
        on(12.48,()->{steve.crouch(true);steve.lookAt(g,9);});
        on(13.22,()->steve.crouch(false));
        on(13.65,()->{steve.lookAt(foot.add(-5,1,-9));steve.sprint(true).walkTo(foot.add(-5,0,-9),1.08);});
        DoubleFunction<Vec3> pov=t->foot.add(.12,initial+2.08+Math.min(6,Math.floor(t/.61))+Math.sin(Math.PI*Math.min(1,(t%.61)/.61))*.72+(t%.61)/.61*(t<3.65?1:0),-.22);
        cam.follow(0,pov,fixed(foot.add(-.35,0,-.6))).fov(77);
        cam.follow(3.45,pov,fixed(foot.add(-.35,0,-.6))).fov(77);
        pair(3.452,6.38,perch.add(1.20,1.74,-3.10),track(steve,1.37),41);
        cam.at(6.38,perch.add(.66,1.83,-4.45),track(steve,1.2)).fov(54);
        cam.at(9.50,perch.add(6,1.85,-11),perch.add(-.85,.95,1.5)).fov(57);
        cam.at(9.748,perch.add(6,1.85,-11),perch.add(-.85,.95,1.5)).fov(57);
        pair(9.75,12.54,perch.add(6.5,2.0,-8.2),track(steve,.8),53);
        pair(12.54,15.5,foot.add(4.2,1.45,-10.6),track(steve,1.05),58);
        pair(15.50,18.05,giant.e.position().add(-8,2,-15),track(giant,6.3),59);
    }

    private void camp() {
        center=at(World.CAMP_X,World.CAMP_Z);
        int gx=cell(center).getX(), gz=cell(center).getZ();
        Sets.flatten(level,gx-6,gz+4,gx+8,gz+16,cell(center).getY(),9);
        Actor giant=giant(center,3,-1,false).face(3);
        GiantEntity g=giantOf(giant);g.setGiantSleeping(true);
        Vec3 edge=center.add(0,0,7);
        Actor alex=person("alex",edge.add(-3,0,1),-88);
        Actor steve=steve(edge.add(-3.3,0,2.3),-90);
        Vec3 trace=center.add(-5.7,0,7.0);
        dev.star1mp.giantrefresh.worldgen.GiantTrailFeature.cinemaFoot(level,net.minecraft.util.RandomSource.create(77),trace.x,trace.z,0,-1,true,.98);
        alex.lookAt(trace);
        on(.24,()->{alex.crouch(true).lookAt(trace);steve.lookAt(alex.e,1.25);});
        on(1.18,()->alex.living().swing(InteractionHand.MAIN_HAND));
        on(2.1,()->alex.lookAt(g,5));
        on(3.1,()->steve.lookAt(g,5));
        on(4.3,()->alex.lookAt(edge.add(2,1,-.75)));
        on(5.0,()->alex.crouch(true).walkTo(edge.add(1.12,0,-.55),.25));
        on(6.75,()->steve.crouch(true).walkTo(edge.add(-1.3,0,.70),.28).lookAt(alex.e,1.4));
        on(9.45,()->steve.stop().crouch(false).lookAt(alex.e,1.36));
        on(10.2,()->{alex.stop().lookAt(steve.e,1.6);steve.lookAt(alex.e,1.4);});
        on(10.82,()->alex.living().swing(InteractionHand.MAIN_HAND));
        on(12.55,()->steve.crouch(true).lookAt(alex.e,1.1));
        on(12.9,()->steve.crouch(false).lookAt(edge.add(6,1,-.6)));
        on(13.65,()->steve.sprint(true).walkTo(edge.add(4.1,0,-.6),1.00));
        on(14.15,()->alex.lookAt(steve.e,1.48));
        on(15.65,()->steve.stop().sprint(false).lookAt(g,6));
        on(16.0,()->g.wakeUp(level,null));
        on(16.5,()->alex.lookAt(g,10).crouch(false));
        on(16.94,()->steve.backUp(.48f));
        on(17.32,()->alex.sprint(true).walkTo(edge.add(-5.8,0,3.6),.97));
        on(18.02,()->{steve.stop().lookAt(edge.add(8,1,4));steve.sprint(true).walkTo(edge.add(8,0,4),1.02);});
        on(18.25,()->giant.lookAt(steve.e,1.5).walkTo(center.add(0,0,4.75),1));
        thump(19.43,center,1.4,center.add(.75,0,1.8),16);
        pair(0,2.32,trace.add(1.25,4.0,2.1),trace.add(.3,.12,.20),50);
        Vec3 ruins=edge.add(-3.1,0,1.05);
        cam.at(2.32,ruins.add(2.45,1.48,4.1),ruins.add(.2,1.15,.1)).fov(58);
        cam.at(4.995,ruins.add(1.85,1.64,3.85),ruins.add(.2,1.24,.1)).fov(56);
        cam.at(5.0,center.add(-3.5,1.55,16),center.add(-1.25,3.38,3.8)).fov(58);
        cam.at(10.38,center.add(-1.6,1.80,15.4),center.add(-.4,3.40,3.8)).fov(58);
        pair(10.39,13.63,edge.add(-.20,1.50,7.8),edge.add(-.17,1.08,.12),58);
        cam.at(13.65,edge.add(-2.9,1.25,5.8),track(steve,1.02)).fov(64);
        cam.at(15.948,edge.add(2.35,1.40,6.6),track(steve,1.12)).fov(63);
        cam.at(15.95,edge.add(2.70,1.2,6.5),center.add(.5,3.20,1.25)).fov(61);
        cam.at(20.44,edge.add(3.75,1.70,7.8),center.add(.7,5.3,1.1)).fov(65);
        pair(20.45,23.05,center.add(-8,4.0,14),track(giant,6),60);
    }

    private void passengers() {
        Actor giant=giant(atMark(0,0,0),0,14,false).face(0);
        Actor bones=summon("minecraft:skeleton",center.add(0,12,0),0,"NoAI:1b");
        Actor zombie=summon("minecraft:zombie",center.add(0,12,0),0,"NoAI:1b");
        hand(bones,new ItemStack(Items.BOW));
        bones.living().setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.CHAINMAIL_HELMET));
        zombie.living().setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));
        bones.e.startRiding(giant.e,true,true);zombie.e.startRiding(giant.e,true,true);
        Actor spider=summon("minecraft:spider",atMark(.1,8.8,1.75),0,"NoAI:1b,NoGravity:1b");
        Actor steve=steve(atMark(.80,0,9.45),180);
        hand(steve,new ItemStack(Items.SHIELD)); steve.lookAt(giant.e,10);
        bones.lookAt(zombie.e,1.35);zombie.lookAt(bones.e,1.32);
        on(.65,()->zombie.living().swing(InteractionHand.MAIN_HAND));
        on(2.15,()->zombie.lookAt(spider.e,.30));
        on(3.55,()->bones.lookAt(spider.e,.3));
        on(7.25,()->{bones.lookAt(steve.e,1.4);zombie.lookAt(steve.e,1.5);});
        on(8.72,()->bones.living().startUsingItem(InteractionHand.MAIN_HAND));
        double zStart=giant.e.getZ();
        on(0.0,()->giant.walkTo(atMark(0,0,2.55),.34));
        during(0,6.85,t->{
            Vec3 p=giant.e.position();double progress=u(t,1.2,6.65);double k=progress+.018*Math.sin(progress*Math.PI*8);
            spider.e.setPos(p.x+.64,p.y+8.55+3.75*k,p.z+1.77-.93*k);
        });
        on(6.9,()->spider.e.startRiding(giant.e,true,true));
        on(9.74,()->steve.living().startUsingItem(InteractionHand.MAIN_HAND));
        on(10.7,()->{bones.living().stopUsingItem();bones.living().swing(InteractionHand.MAIN_HAND);spawnArrow(bones.e.getEyePosition(),steve.e.position().add(0,1.12,0),1.66);});
        on(11.4,()->{steve.crouch(true);steve.lookAt(bones.e,1.0);});
        on(11.82,()->steve.crouch(false).lookAt(zombie.e,1.25));
        Vec3 landing=atMark(.1,0,6.25);
        final Vec3[] start={null};
        on(12.14,()->{start[0]=zombie.e.position();zombie.e.stopRiding();zombie.e.setNoGravity(true);});
        during(12.15,13.46,t->{
            double k=Math.min(1,(t-12.15)/1.30);
            Vec3 p=start[0].lerp(landing,k).add(0,4.8*k*(1-k),0);
            zombie.e.setDeltaMovement(Vec3.ZERO);zombie.e.setPos(p.x,p.y,p.z);
        });
        on(13.5,()->{zombie.e.setNoGravity(false);dust(landing,12,.40);zombie.lookAt(steve.e,1.45);});
        on(13.8,()->zombie.walkTo(atMark(.55,0,8),.78));
        on(14.05,()->{steve.living().stopUsingItem();steve.backUp(.47f);});
        on(14.68,()->{steve.stop().lookAt(spider.e,.4);zombie.living().swing(InteractionHand.MAIN_HAND);});
        Vec3 lens=atMark(.6,1.65,10.1);
        final Vec3[] launch={null};
        on(16.86,()->{launch[0]=spider.e.position();spider.e.stopRiding();spider.e.setNoGravity(true);});
        during(16.9,18.34,t->{
            double k=Math.max(0,Math.min(1,(t-16.9)/1.39));
            Vec3 p=launch[0].lerp(lens.add(0,-.22,-.2),k).add(0,5.0*k*(1-k),0);
            spider.e.setPos(p.x,p.y,p.z);
        });
        DoubleFunction<Vec3> gp=track(giant,0);
        cam.follow(0,plus(gp,.55,13.44,5),plus(gp,0,12.63,0)).fov(53);
        cam.follow(3.45,plus(gp,.82,13.46,4.75),plus(gp,0,12.68,0)).fov(51);
        cam.follow(3.452,plus(gp,1.89,12.13,5.8),track(spider,.38)).fov(49);
        cam.follow(6.6,plus(gp,2.10,12.55,5.9),track(spider,.38)).fov(48);
        cam.follow(6.602,plus(gp,1.5,13.42,5.4),plus(gp,0,12.35,.25)).fov(53);
        cam.follow(9.50,plus(gp,6.15,6.3,16.6),plus(gp,0,6.4,.4)).fov(58);
        pair(9.502,12.04,steve.e.position().add(1.85,.98,-3.1),track(steve,1.05),49);
        pair(12.05,13.63,atMark(4.8,2.6,12.1),track(zombie,.79),68);
        pair(13.64,15.72,atMark(4.3,1.21,8.4),atMark(.60,1.1,8.0),70);
        pair(15.73,16.85,atMark(2.9,11.15,7.0),track(spider,.42),59);
        pair(16.86,18.5,lens,track(spider,.42),71);
        pair(18.5,21.05,atMark(-8,3.4,16),track(giant,6.2),61);
    }

    private void potionDoor() {
        // Use a real vanilla village house. Anchor the set to its doorway and normal.
        var box=Sets.template(level,"minecraft:village/plains/houses/plains_small_house_1",cell(center),Rotation.NONE);
        BlockPos lower=null;
        for(int x=box.minX();x<=box.maxX();x++)for(int z=box.minZ();z<=box.maxZ();z++)for(int y=box.minY();y<box.maxY();y++){
            var p=new BlockPos(x,y,z);var s=level.getBlockState(p);
            if(s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER)lower=p;
        }
        final BlockPos dp=java.util.Objects.requireNonNull(lower);
        Vec3 d=new Vec3(dp.getX()+.5,dp.getY(),dp.getZ()+.5);
        // A vertical offset on the template includes the foundation; get exterior feet from terrain.
        Vec3 bc=new Vec3((box.minX()+box.maxX()+1)*.5,d.y,(box.minZ()+box.maxZ()+1)*.5);
        Vec3 nn=d.subtract(bc);Vec3 out=Math.abs(nn.x)>Math.abs(nn.z)?new Vec3(Math.signum(nn.x),0,0):new Vec3(0,0,Math.signum(nn.z));
        Vec3 right=new Vec3(-out.z,0,out.x);
        Vec3 front=d.add(out.scale(2.8)),stand=front.add(right.scale(1.30));
        for(int along=2;along<=8;along++) for(int across=-4;across<=4;across++){
            Vec3 tile=d.add(out.scale(along)).add(right.scale(across));
            place(tile.add(0,-1,0), (Math.abs(across)<2 ? Blocks.DIRT_PATH : Blocks.GRASS_BLOCK).defaultBlockState());
            for(int h=0;h<5;h++)place(tile.add(0,h,0),Blocks.AIR.defaultBlockState());
        }
        Vec3 floor=new Vec3(stand.x,d.y,stand.z);BlockPos standPos=cell(floor.add(0,.10,0));
        level.setBlock(standPos.below(),Blocks.COBBLESTONE.defaultBlockState(),3);
        Actor steve=steve(front.add(right.scale(-.75)),0);
        steve.lookAt(front.add(out.scale(5)).add(0,1.6,0));
        Actor alex=person("alex",d.add(right.scale(-3.2)).add(out.scale(1.20)),0);
        var down=level.getBlockState(dp);var up=level.getBlockState(dp.above());
        on(.10,()->{alex.lookAt(d.add(0,1.05,0));alex.living().swing(InteractionHand.MAIN_HAND);door(dp,true);});
        on(.64,()->steve.walkTo(front,.52).lookAt(floor.add(0,.56,0)));
        on(1.62,()->alex.lookAt(steve.e,1.45));
        on(2.1,()->{alex.living().swing(InteractionHand.MAIN_HAND);door(dp,false);});
        on(2.78,()->{steve.crouch(true);steve.living().swing(InteractionHand.MAIN_HAND);cmd("setblock "+standPos.getX()+" "+standPos.getY()+" "+standPos.getZ()+" minecraft:brewing_stand[has_bottle_0=true,has_bottle_1=true]");steve.lookAt(floor.add(0,.5,0));hand(steve,new ItemStack(GRContent.GIANT_TOOTH.get()));});
        final net.minecraft.world.entity.item.ItemEntity[] tooth={null};
        on(3.6,()->{steve.living().swing(InteractionHand.MAIN_HAND);hand(steve,ItemStack.EMPTY);Vec3 fp=floor.add(0,1.03,0);tooth[0]=new net.minecraft.world.entity.item.ItemEntity(level,fp.x,fp.y,fp.z,new ItemStack(GRContent.GIANT_TOOTH.get()),0,0,0);tooth[0].setNoGravity(true);tooth[0].setDeltaMovement(Vec3.ZERO);level.addFreshEntity(tooth[0]);});
        on(5.25,()->{if(tooth[0]!=null)tooth[0].discard();});
        on(4.62,()->{steve.living().swing(InteractionHand.MAIN_HAND);hand(steve,ItemStack.EMPTY);});
        for(double t:new double[]{5.12,5.48,5.84})on(t,()->particles(ParticleTypes.BUBBLE,floor.add(0,.83,0),10,.13,.025));
        on(5.8,()->{steve.living().swing(InteractionHand.MAIN_HAND);alex.lookAt(floor.add(0,.78,0));});
        on(6.37,()->{steve.crouch(false);hand(steve,growthPotion());steve.lookAt(alex.e,1.5);});on(7.02,()->steve.living().startUsingItem(InteractionHand.MAIN_HAND));
        on(8.36,()->{
            steve.living().stopUsingItem();hand(steve,new ItemStack(Items.GLASS_BOTTLE));steve.living().addEffect(new MobEffectInstance(GRContent.growthHolder(),-1,0,false,false));
        });
        on(9.02,()->alex.lookAt(steve.e,3.8).backUp(.36f));
        on(9.62,()->alex.stop().lookAt(steve.e,4));
        on(10.24,()->steve.lookAt(alex.e,1.45));
        on(11.05,()->{steve.lookAt(d.add(0,1.5,0));steve.walkTo(d.add(out.scale(1.73)).add(right.scale(.6)),.36);});
        on(12.48,()->steve.stop().crouch(true).lookAt(d.add(0,1.1,0)));
        on(13.32,()->steve.lookAt(alex.e,1.4));
        on(14.05,()->{alex.walkTo(d.add(right.scale(-1.0)).add(out),.59);alex.lookAt(d.add(0,1.1,0));});
        on(15.62,()->alex.crouch(true).lookAt(d.add(0,.9,0)));
        on(16.14,()->alex.living().swing(InteractionHand.MAIN_HAND));
        on(16.76,()->{alex.living().swing(InteractionHand.MAIN_HAND);level.setBlock(dp.above(),Blocks.AIR.defaultBlockState(),2);level.setBlock(dp,Blocks.AIR.defaultBlockState(),2);hand(alex,new ItemStack(Items.OAK_DOOR));});
        on(17.14,()->alex.crouch(false).backUp(.45f));on(17.60,()->alex.stop().lookAt(steve.e,3));
        on(17.64,()->steve.walkTo(d.add(out.scale(1.35)),.28));on(19.05,()->{steve.stop().lookAt(alex.e,1.55);alex.lookAt(steve.e,4);});
        on(19.84,()->steve.crouch(false).backUp(.36f));on(20.38,()->steve.stop().lookAt(alex.e,1.5));
        on(20.97,()->{hand(alex,ItemStack.EMPTY);level.setBlock(dp,down,2);level.setBlock(dp.above(),up,3);alex.living().swing(InteractionHand.MAIN_HAND);});
        pair(0,3.22,d.add(out.scale(6.7)).add(right.scale(-1.65)).add(0,1.72,0),front.add(right.scale(-.35)).add(0,1.13,0),60);
        pair(3.22,6.35,floor.add(out.scale(2.45)).add(right.scale(1.70)).add(0,1.30,0),floor.add(right.scale(-.52)).add(0,.83,0),53);
        pair(6.35,8.46,front.add(out.scale(3.2)).add(right.scale(.9)).add(0,1.66,0),track(steve,1.28),46);
        Vec3 wide=d.add(out.scale(9.35)).add(right.scale(-3.5)).add(0,2.10,0);
        pair(8.46,14.18,wide,d.add(out.scale(.77)).add(0,2.22,0),54);
        pair(14.18,17.14,d.add(out.scale(5.7)).add(right.scale(-2.45)).add(0,1.71,0),d.add(right.scale(-.48)).add(0,1.27,0),60);
        pair(17.14,22.50,wide,d.add(out.scale(.9)).add(0,2.22,0),54);
        pair(22.50,25.05,wide.add(right.scale(.8)),d.add(0,2.6,0),58);
    }

    private void farm() {
        Sets.template(level,"minecraft:village/plains/houses/plains_small_house_3",cell(atMark(-8,0,-8)),Rotation.NONE);
        for(int x=-3;x<=2;x++)for(int z=-3;z<=1;z++){
            boolean bare=x==0&&z==1;
            place(atMark(x,-1,z),bare?Blocks.DIRT.defaultBlockState():Blocks.FARMLAND.defaultBlockState());
            place(atMark(x,0,z),bare?Blocks.AIR.defaultBlockState():Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7));
        }
        for(int x=-4;x<=3;x++){place(atMark(x,-1,2),Blocks.OAK_LOG.defaultBlockState());place(atMark(x,0,-4),Blocks.OAK_FENCE.defaultBlockState());}
        place(atMark(2,0,2),Blocks.COMPOSTER.defaultBlockState());
        Actor farmer=villager("farmer",atMark(-1.38,0,2.75),-25);
        Actor steve=steve(atMark(1.0,0,3.04),65);
        hand(steve,new ItemStack(Items.IRON_HOE));farmer.lookAt(atMark(0,.1,1));steve.lookAt(farmer.e,1.48);
        on(.26,()->{farmer.walkTo(atMark(-.72,0,2.24),.43);steve.lookAt(atMark(.0,.1,1));});
        Actor bird=summon("minecraft:chicken",atMark(2,.90,2),65,"NoAI:1b,NoGravity:1b");
        Actor giant=giant(atMark(-11,0,-2.4),-90,-1,false);
        on(2.24,()->farmer.lookAt(steve.e,1.56));on(2.79,()->steve.lookAt(farmer.e,1.47));
        on(3.4,()->steve.backUp(.28f));on(3.82,()->{steve.stop();steve.living().swing(InteractionHand.MAIN_HAND);});
        on(4.72,()->steve.lookAt(atMark(0,.25,1)));
        on(4.98,()->giant.walkTo(atMark(13,0,-2.1),1.18));
        during(5.01,15.55,t->{double k=Math.max(0,Math.min(1,(t-5.01)/10.54)); Vec3 p=atMark(-10+23*k,0,-2.26);giant.e.setPos(p.x,p.y,p.z);});
        on(6.61,()->farmer.lookAt(giant.e,8));
        on(7.16,()->steve.lookAt(farmer.e,1.5));
        on(7.67,()->steve.lookAt(giant.e,8).backUp(.47f));
        on(8.28,()->{steve.stop().crouch(true);farmer.walkTo(atMark(-2,0,3.45),.76);});
        on(10.10,()->steve.crouch(false));
        thump(7.52,atMark(-2,0,-2),.96,atMark(-2,.2,-1.5),17);
        on(9.12,()->{level.destroyBlock(cell(atMark(0,0,-4)),false);dust(atMark(0,.55,-3.85),13,.50);});
        on(14.84,()->bird.lookAt(atMark(0,0,1)));on(15.53,()->bird.lookAt(atMark(2.9,1.45,1.4)));on(16.13,()->bird.lookAt(atMark(0,0,1)));
        during(16.40,17.31,t->{double k=u(t,16.4,17.31);Vec3 p=atMark(2*(1-k),.94*(1-k)+.55*Math.sin(Math.PI*k),2-k);bird.e.setPos(p.x,p.y,p.z);});
        on(17.35,()->{bird.e.setNoGravity(false);farmer.lookAt(bird.e,.4);});
        on(17.71,()->steve.lookAt(bird.e,.38));on(18.63,()->farmer.lookAt(steve.e,1.54));on(19.05,()->steve.crouch(true));
        pair(0,2.25,atMark(.55,2.67,3.06),atMark(0,.15,1),47);
        pair(2.25,6.20,atMark(-.12,1.67,8.6),atMark(-.06,1.33,2.59),58);
        pair(6.2,10.2,atMark(-.85,1.10,7.30),atMark(-.60,2.55,-.9),65);
        cam.at(10.2,atMark(4.25,2.75,13.7),atMark(.3,4.5,-1.4)).fov(62);
        cam.at(14.70,atMark(5.60,2.68,12.2),atMark(4.2,4.2,-1.25)).fov(62);
        pair(14.71,17.85,atMark(-1.4,1.63,5.55),atMark(1.08,.78,1.65),50);
        pair(17.85,19.5,atMark(.9,1.70,8.55),atMark(-.3,.95,2.93),64);
        pair(19.5,22.05,atMark(4.70,2.95,10.4),atMark(-.9,1.95,-1.5),61);
    }
}
