package me.exerosis.physics;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.TimeUnit;

public final class PhysicsPlugin extends JavaPlugin implements Listener {
    static {
        Bullet.init();
    }

    //Collections and Maps
    private ArrayList<btRigidBody> pool = new ArrayList<>();
    private ArrayList<PhysicsBlock> blocks = new ArrayList<>();
    private HashSet<Location> visited = new HashSet<>();
    private HashMap<Player, btRigidBody> players = new HashMap<>();
    //Boxes
    private btBoxShape boxCollision;
    private btBoxShape boxStaticCollision;
    private btBoxShape playerCollision;
    private btDefaultCollisionConfiguration collisionConfig;
    private btCollisionDispatcher dispatcher;
    private btDbvtBroadphase broadphase;
    private btSequentialImpulseConstraintSolver solver;
    private btDiscreteDynamicsWorld dynamicsWorld;
    private Vector3 boxInertia = new Vector3(0.0F, 0.0F, 0.0F);
    private long lastSim;

    public PhysicsPlugin() {
        init();
    }

    private void init() {
        collisionConfig = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfig);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        dynamicsWorld = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfig);


        boxCollision = new btBoxShape(new Vector3(PhysicsPackage.getArmorBlockSizeH(), PhysicsPackage.getArmorBlockSizeH(), PhysicsPackage.getArmorBlockSizeH()));
        boxStaticCollision = new btBoxShape(new Vector3(1.0F, 1.0F, 1.0F));
        playerCollision = new btBoxShape(new Vector3(0.15F, 0.9F, 0.15F));


        lastSim = System.nanoTime();
    }

    @Override
    public void onEnable() {
        boxCollision.calculateLocalInertia(30.0F, boxInertia);
        dynamicsWorld.setGravity(new Vector3(0.0F, -10.0F, 0.0F));

        getServer().getScheduler().runTaskTimerAsynchronously(this, this::stepSimulation, 0L, 10L);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        blocks.forEach(PhysicsBlock::kill);
    }

    public final PhysicsBlock spawnBlock(Location location, BlockState blockState) {
        PhysicsBlock block = new PhysicsBlock(this, location, blockState);
        blocks.add(block);
        return block;
    }

    public final PhysicsBlock spawnBlock(Location location, MaterialData data) {
        PhysicsBlock block = new PhysicsBlock(this, location, data.toItemStack());
        blocks.add(block);
        return block;
    }

    public final PhysicsBlock spawnBlock(Location location, MaterialData data, Collection<ItemStack> drops) {
        PhysicsBlock block = new PhysicsBlock(this, location, data.toItemStack(), drops);
        blocks.add(block);
        return block;
    }

    @EventHandler
    public final void onExplode(EntityExplodeEvent event) {
        event.setCancelled(true);
        Location explosionLocation = event.getLocation();

        for (Block explodedBlock : event.blockList()) {
            if (!explodedBlock.getType().equals(Material.TNT) && explodedBlock.getType().isOccluding()) {
                Location location = explodedBlock.getLocation().add(0.5D, 0.5D, 0.5D);
                PhysicsBlock block = new PhysicsBlock(this, location, explodedBlock.getState());
                blocks.add(block);

                float x = (float) (explosionLocation.getX() - location.getX());
                float y = (float) (explosionLocation.getY() - location.getY());
                float z = (float) (explosionLocation.getZ() - location.getZ());

                Vector3 vec = new Vector3(x, y, z);
                vec.nor();
                vec.scl(-event.getYield() * 30);
                block.getBody().setLinearVelocity(vec);
            }
            explodedBlock.setType(Material.AIR);
        }

        for (PhysicsBlock block : blocks) {
            Location location = block.getStand().getLocation();
            if (explosionLocation.distanceSquared(location) >= event.getYield() * event.getYield() * event.getYield())
                continue;
            Vector3 vector = new Vector3((float) (explosionLocation.getX() - location.getX()), (float) (explosionLocation.getY() - location.getY()), (float) (explosionLocation.getZ() - location.getZ()));
            vector.nor();
            vector.scl(-event.getYield() * 50);
            block.getBody().setLinearVelocity(vector);
        }
    }

    @EventHandler
    public final void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();
        Matrix4 transform = new Matrix4();
        transform.idt();
        transform.setTranslation((float) location.getX(), (float) location.getY() + 0.9F, (float) location.getZ());
        btDefaultMotionState motionState = new btDefaultMotionState(transform);
        btRigidBody body = new btRigidBody(new btRigidBody.btRigidBodyConstructionInfo(0.0F, motionState, playerCollision, new Vector3(0.0F, 0.0F, 0.0F)));
        dynamicsWorld.addRigidBody(body);
        players.put(player, body);
    }

    @EventHandler
    public final void onPlayerQuit(PlayerQuitEvent event) {
        dynamicsWorld.removeRigidBody(players.remove(event.getPlayer()));
    }

    public final void stepSimulation() {
        int offset = 0;

        for (PhysicsBlock block : blocks) {
            if (!block.getBody().isActive())
                return;
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        Location location = block.getLocation().clone().add(x, y, z);
                        Block minecraftBlock = location.getBlock();

                        if (!minecraftBlock.getType().isOccluding())
                            continue;
                        if (visited.contains(location))
                            continue;

                        visited.add(location);

                        Matrix4 transform = new Matrix4();
                        transform.idt();
                        transform.setTranslation(location.getBlockX() + 0.5F, location.getBlockY() + 0.5F, location.getBlockZ() + 0.5F);
                        if (offset < getPool().size()) {
                            btRigidBody body = getPool().get(offset);
                            body.setWorldTransform(transform);
                            getDynamicsWorld().removeRigidBody(body);
                            getDynamicsWorld().addRigidBody(body);
                            body.setActivationState(0);
                            offset++;
                        }
                        btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(0.0F, null, getBoxStaticCollision(), new Vector3(0.0F, 0.0F, 0.0F));
                        btRigidBody body = new btRigidBody(info);
                        body.setActivationState(0);
                        body.setCollisionFlags(body.getCollisionFlags() | btCollisionObject.CollisionFlags.CF_STATIC_OBJECT);
                        getPool().add(body);
                    }
                }
            }
        }

        for (int i = offset; i < pool.size() - 1; i++)
            pool.get(i).setActivationState(CollisionConstants.DISABLE_SIMULATION);

        for (Map.Entry<Player, btRigidBody> entry : players.entrySet()) {
            btRigidBody body = entry.getValue();
            Player player = entry.getKey();
            Location location = player.getLocation();
            Matrix4 transform = body.getCenterOfMassTransform();
            transform.setTranslation((float) location.getX(), (float) location.getY() + 0.9F, (float) location.getZ());
            body.setCenterOfMassTransform(transform);
            body.setActivationState(CollisionConstants.ACTIVE_TAG);
        }

        long now = System.nanoTime();
        float delta = now - lastSim / TimeUnit.SECONDS.toNanos(1L);
        dynamicsWorld.stepSimulation(delta, 100);
        lastSim = now;

        int x = 0;
        while (x < blocks.size()) {
            PhysicsBlock block = blocks.get(x);
            block.tick();
            if (!block.getBody().isActive() || block.getStand().isDead())
                block.kill();
            if (block.getBody().isDisposed())
                blocks.remove(x);
        }
        visited.clear();
    }

    public final ArrayList<btRigidBody> getPool() {
        return pool;
    }

    public final HashSet<Location> getVisited() {
        return visited;
    }

    public btDefaultCollisionConfiguration getCollisionConfig() {
        return collisionConfig;
    }

    public void setCollisionConfig(btDefaultCollisionConfiguration collisionConfig) {
        this.collisionConfig = collisionConfig;
    }

    public btCollisionDispatcher getDispatcher() {
        return dispatcher;
    }

    public void setDispatcher(btCollisionDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public final btDbvtBroadphase getBroadphase() {
        return broadphase;
    }

    public void setBroadphase(btDbvtBroadphase broadphase) {
        this.broadphase = broadphase;
    }

    public final btSequentialImpulseConstraintSolver getSolver() {
        return solver;
    }

    public final void setSolver(btSequentialImpulseConstraintSolver solver) {
        this.solver = solver;
    }

    public final btDiscreteDynamicsWorld getDynamicsWorld() {
        return dynamicsWorld;
    }

    public void setDynamicsWorld(btDiscreteDynamicsWorld dynamicsWorld) {
        this.dynamicsWorld = dynamicsWorld;
    }

    public btBoxShape getBoxCollision() {
        return boxCollision;
    }

    public Vector3 getBoxInertia() {
        return boxInertia;
    }

    public btBoxShape getBoxStaticCollision() {
        return boxStaticCollision;
    }

    public btBoxShape getPlayerCollision() {
        return playerCollision;
    }

    public ArrayList<PhysicsBlock> getBlocks() {
        return blocks;
    }

    public HashMap<Player, btRigidBody> getPlayers() {
        return players;
    }

    public long getLastSim() {
        return lastSim;
    }

    public void setLastSim(long lastSim) {
        this.lastSim = lastSim;
    }
}