package me.exerosis.physics;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.linearmath.btDefaultMotionState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.Collection;

public final class PhysicsBlock {
    private final ArmorStand stand;
    private final btRigidBody body;
    private final Matrix4 transform;
    private final PhysicsPlugin plugin;
    private final Location location;
    private Collection<ItemStack> drops;
    private ItemStack headItem;
    private float life;

    public PhysicsBlock(PhysicsPlugin plugin, Location location, BlockState blockState) {
        this(plugin, location);
        drops = blockState.getBlock().getDrops();
        headItem = blockState.getData().toItemStack();
        stand.setHelmet(headItem);
    }

    public PhysicsBlock(PhysicsPlugin plugin, Location location, ItemStack headItem) {
        this(plugin, location);
        this.headItem = headItem;
        stand.setHelmet(headItem);
    }

    public PhysicsBlock(PhysicsPlugin plugin, Location location, ItemStack headItem, Collection<ItemStack> drops) {
        this(plugin, location);
        this.headItem = headItem;
        this.drops = drops;
        stand.setHelmet(headItem);
    }

    public PhysicsBlock(PhysicsPlugin plugin, Location location) {
        headItem = new ItemStack(Material.STONE);
        life = 60.0F;

        location.setYaw(0.0F);
        location.setPitch(0.0F);
        location.add(0.0D, -1.8D, 0.0D);

        stand = location.getWorld().spawn(location, ArmorStand.class);
        stand.setGravity(false);
        stand.setVisible(false);
        stand.setSmall(false);
        stand.setHelmet(headItem);

        transform = new Matrix4();
        transform.idt();
        transform.setTranslation((float) location.getX(), (float) location.getY() + 1.8F, (float) location.getZ());

        btDefaultMotionState motionState = new btDefaultMotionState(transform);
        btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(30.0F, motionState, plugin.getBoxCollision(), plugin.getBoxInertia());
        info.setAdditionalDamping(true);
        body = new btRigidBody(info);
        info.dispose();
        plugin.getDynamicsWorld().addRigidBody(body);

        this.plugin = plugin;
        this.location = location;
    }

    public final void tick() {
        body.getMotionState().getWorldTransform(transform);
        Quaternion rot = new Quaternion();
        transform.getRotation(rot);
        EulerAngle eul = PhysicsPackage.quaternionToEuler(rot);
        stand.setHeadPose(eul);
        Vector3 origin = transform.getTranslation(new Vector3());
        location.setX(origin.x);
        location.setY(origin.y - 1.8F + 0.05F);
        location.setZ(origin.z);
        double halfSize = PhysicsPackage.ARMOR_BLOCK_SIZE_HALF;
        double x = -Math.sin(eul.getX()) * halfSize / 2;
        double y = -Math.cos(eul.getX()) * halfSize / 2 - Math.cos(eul.getZ()) * halfSize / 2;
        double z = -Math.sin(eul.getZ()) * halfSize / 2;
        location.add(x, y, z);
        if (location.getY() < -10 || location.getY() > 300 || stand.isDead())
            stand.remove();
        stand.teleport(location);
    }

    public final void kill() {
        if (body.isDisposed())
            return;
        stand.remove();
        plugin.getDynamicsWorld().removeRigidBody(body);

     /*for (ItemStack drop : drops)
           location.getWorld().dropItemNaturally(location, drop);
     */
        body.dispose();
    }


    public final Location getLocation() {
        return location.clone();
    }

    public final void setLocation(Location location) {
        if (location.getWorld().equals(location.getWorld()))
            return;
        location.setX(location.getX());
        location.setY(location.getY());
        location.setZ(location.getZ());

        transform.idt();
        transform.setTranslation((float) location.getX(), (float) location.getY() + 1.8F, (float) location.getZ());

        body.setWorldTransform(transform);
    }

    public final void applyForce(Vector vec) {
        body.setLinearVelocity(new Vector3((float) vec.getX(), (float) vec.getY(), (float) vec.getZ()));
    }

    public final PhysicsPlugin getPlugin() {
        return plugin;
    }

    public final ArmorStand getStand() {
        return stand;
    }

    public final btRigidBody getBody() {
        return body;
    }

    public final Matrix4 getTransform() {
        return transform;
    }

    public final Collection<ItemStack> getDrops() {
        return drops;
    }

    public final void setDrops(Collection<ItemStack> drops) {
        this.drops = drops;
    }

    public final ItemStack getHeadItem() {
        return headItem;
    }

    public final void setHeadItem(ItemStack headItem) {
        this.headItem = headItem;
    }

    public final float getLife() {
        return life;
    }

    public final void setLife(float life) {
        this.life = life;
    }
}