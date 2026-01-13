package me.ryanhamshire.GriefPrevention;

import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Animals;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Display;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Explosive;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Mule;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class EntityDamageHandler implements Listener
{

    private static final Set<PotionEffectType> GRIEF_EFFECTS = Set.of(
            // Damaging effects
            PotionEffectType.INSTANT_DAMAGE,
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            // Effects that could remove entities from normally-secure pens
            PotionEffectType.JUMP_BOOST,
            PotionEffectType.LEVITATION
    );

    private final @NotNull DataStore dataStore;
    private final @NotNull GriefPrevention instance;

    EntityDamageHandler(@NotNull DataStore dataStore, @NotNull GriefPrevention plugin)
    {
        this.dataStore = dataStore;
        instance = plugin;
    }

    //when an entity is damaged
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityDamage(@NotNull EntityDamageEvent event)
    {
        this.handleEntityDamageEvent(new EntityDamageInstance(event), true);
    }

    //when a player attempts an attack
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPrePlayerAttack(@NotNull PrePlayerAttackEntityEvent event)
    {
        this.handleEntityDamageEvent(new EntityDamageInstance(event), true);
    }

    //when an entity is set on fire
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityCombustByEntity(@NotNull EntityCombustByEntityEvent event)
    {
        this.handleEntityDamageEvent(new EntityDamageInstance(event), false);
    }

    @EventHandler(ignoreCancelled = true)
	public void onEntityKnockback(EntityPushedByEntityAttackEvent event)
    {
		this.handleEntityDamageEvent(new EntityDamageInstance(event), false);
	}

    private void handleEntityDamageEvent(@NotNull EntityDamageInstance event, boolean sendMessages)
    {
        //horse protections can be disabled
        if (event.damaged() instanceof Horse && !instance.config_claims_protectHorses) return;
        if (event.damaged() instanceof Donkey && !instance.config_claims_protectDonkeys) return;
        if (event.damaged() instanceof Mule && !instance.config_claims_protectDonkeys) return;
        if (event.damaged() instanceof Llama && !instance.config_claims_protectLlamas) return;

        //protected death loot can't be destroyed, only picked up or despawned due to expiration
        if (event.damaged().getType() == EntityType.ITEM)
        {
            if (event.damaged().getPersistentDataContainer().has(GriefPrevention.instance.itemOwnerKey))
            {
                event.setCancelled(true);
            }
        }

        // Handle environmental damage to tamed animals that could easily be caused maliciously.
        if (handlePetDamageByEnvironment(event)) return;

        // Hostile mobs are not protected
        if (isHostile(event.damaged(), event.damager())) return;

        // Handle entity damage by block explosions.
        if (handleEntityDamageByBlockExplosion(event)) return;

        //the rest is only interested in entities damaging entities (ignoring environmental damage)
        if (event.damager() == null) return;

        //determine which player is attacking, if any
        Player attacker = event.getResponsiblePlayer();
        Entity damageSource = event.damager();

        //don't track in worlds where claims are not enabled
        if (!instance.claimsEnabledForWorld(event.damaged().getWorld())) return;

        //if the damaged entity is a claimed item frame or armor stand, the damager needs to be a player with build trust in the claim
        if (handleClaimedBuildTrustDamageByEntity(event, attacker, sendMessages)) return;

        //if the entity is a non-monster creature (remember monsters disqualified above), or a vehicle
        handleCreatureDamageByEntity(event, attacker, damageSource, sendMessages);
    }

    /**
     * Check if an {@link Entity} is considered hostile to the {@link Entity} attempting to damage it.
     *
     * @param entity the {@code Entity}
     * @return true if the {@code Entity} is hostile
     */
    private boolean isHostile(@NotNull Entity entity, @Nullable Entity damager)
    {
        switch (entity)
        {
            case Slime slime ->
            {
                // Size 0 "baby" slimes cannot deal damage and are often kept as pets.
                // This is really inconvenient for players who are trying to harvest slimeballs in areas with claims;
                // the full-sized slimes are considered dangerous, but the ones that actually drop the slimeballs are not.
                // To make this protection less obnoxious, only protect baby slimes that have lived for a minute or more.
                return slime.getSize() > 0 || slime.getTicksLived() < 1200;
            }
            case Enemy _ ->
            {
                return true;
            }
            case Rabbit rabbit ->
            {
                return rabbit.getRabbitType() == Rabbit.Type.THE_KILLER_BUNNY;
            }

            // Consider hostile if mob or its controlling passenger:
            // - Are targeting the damager or a player
            // - Are targeting the owner of the damager (if they are a pet)
            // - Are targeting a pet of the damager
            case Mob mob when damager != null ->
            {
                // Mob targeting damager or a player
                if (damager.equals(mob.getTarget()) || mob.getTarget() instanceof Player)
                {
                    return true;
                }

                // Mob targeting damager's pet
                if (mob.getTarget() instanceof Tameable tameableTarget && damager.equals(tameableTarget.getOwner()))
                {
                    return true;
                }

                // Damager is pet and mob is targeting owner
                if (damager instanceof Tameable tameable && tameable.getOwner() != null && tameable.getOwner().equals(mob.getTarget()))
                {
                    return true;
                }

                if (mob.getPassengers().isEmpty() || !(mob.getPassengers().getFirst() instanceof Mob driver))
                {
                    return false;
                }

                // Check controlling passenger as they control hostility
                return isHostile(driver, damager);
            }

            // Consider hostile if mob or its controlling passenger:
            // - Are targeting a player
            // - Are targeting a pet of a player
            case Mob mob ->
            {
                // Mob targeting player
                if (mob.getTarget() instanceof Player)
                {
                    return true;
                }

                // Mob targeting player's pet
                if (mob.getTarget() instanceof Tameable tameableTarget && tameableTarget.getOwner() instanceof Player)
                {
                    return true;
                }

                if (mob.getPassengers().isEmpty() || !(mob.getPassengers().getFirst() instanceof Mob driver))
                {
                    return false;
                }

                // Check controlling passenger as they control hostility
                return isHostile(driver, null);
            }
            default ->
            {
                return false;
            }
        }
    }

    /**
     * Handle damage to {@link Tameable} entities by environmental sources.
     *
     * @param event the {@link EntityDamageInstance}
     * @return true if the damage is handled
     */
    private boolean handlePetDamageByEnvironment(@NotNull EntityDamageInstance event)
    {
        // If the damaged entity is not a pet or the pet has no owner, allow.
        if (!(event.damaged() instanceof Tameable tameable && tameable.isTamed())
                && !(event.damaged() instanceof CopperGolem golem && golem.getSummoner() != null))
        {
            return false;
        }
        switch (event.cause())
        {
            // Block environmental and easy-to-cause damage sources.
            case BLOCK_EXPLOSION,
                    ENTITY_EXPLOSION,
                    FALLING_BLOCK,
                    FIRE,
                    FIRE_TICK,
                    LAVA,
                    SUFFOCATION,
                    CONTACT,
                    DROWNING ->
            {
                event.setCancelled(true);
                return true;
            }
            default ->
            {
                return false;
            }
        }
    }

    /**
     * Handle entity damage caused by block explosions.
     *
     * @param event the {@link EntityDamageInstance}
     * @return true if the damage is handled
     */
    private boolean handleEntityDamageByBlockExplosion(@NotNull EntityDamageInstance event)
    {
        if (event.cause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return false;

        Entity entity = event.damaged();

        // Skip players - does allow players to use block explosions to bypass PVP protections,
        // but also doesn't disable self-damage.
        if (entity instanceof Player) return false;

        Claim claim = dataStore.getClaimAt(entity.getLocation(), false, null);

        // Only block explosion damage inside claims.
        if (claim == null) return false;

        event.setCancelled(true);
        return true;
    }

    /**
     * Handle actions requiring build trust.
     *
     * @param event the {@link EntityDamageInstance}
     * @param attacker the attacking {@link Player}, if any
     * @param sendMessages whether to send denial messages to users involved
     * @return true if the damage is handled
     */
    private boolean handleClaimedBuildTrustDamageByEntity(
            @NotNull EntityDamageInstance event,
            @Nullable Player attacker,
            boolean sendMessages)
    {
        EntityType entityType = event.damaged().getType();
        if (!(event.damaged() instanceof Hanging)
                && !(event.damaged() instanceof Display)
                && !(event.damaged() instanceof ArmorStand)
                && !(event.damaged() instanceof Villager)
                && !(event.damaged() instanceof EnderCrystal))
        {
            return false;
        }

        if (entityType == EntityType.VILLAGER
                // Allow disabling villager protections in the config.
                && (!instance.config_claims_protectCreatures
                // Always allow zombies and raids to target villagers.
                //why exception?  so admins can set up a village which can't be CHANGED by players, but must be "protected" by players.
                || event.damager() instanceof Zombie
                || event.damager() instanceof Raider
                || event.damager() instanceof Vex
                || event.damager() instanceof Projectile projectile && projectile.getShooter() instanceof Raider
                || event.damager() instanceof EvokerFangs fangs && fangs.getOwner() instanceof Raider))
        {
            return true;
        }

        // Use attacker's cached claim to speed up lookup.
        Claim cachedClaim = null;
        if (attacker != null)
        {
            PlayerData playerData = this.dataStore.getPlayerData(attacker.getUniqueId());
            cachedClaim = playerData.lastClaim;
        }

        Claim claim = this.dataStore.getClaimAt(event.damaged().getLocation(), false, cachedClaim);

        // If the area is not claimed, do not handle.
        if (claim == null) return false;

        // If attacker isn't a player, cancel.
        if (attacker == null)
        {
            event.setCancelled(true);
            return true;
        }

        Supplier<String> failureReason = claim.checkPermission(attacker, ClaimPermission.Build, event.original());

        // If player has build trust, fall through to next checks.
        if (failureReason == null) return false;

        event.setCancelled(true);
        if (sendMessages) GriefPrevention.sendMessage(attacker, TextMode.Err, failureReason.get());
        return true;
    }

    /**
     * Handle damage to a {@link Creature} by an {@link Entity}. Because monsters are
     * already discounted, any qualifying entity is livestock or a pet.
     *
     * @param event the {@link EntityDamageInstance}
     * @param attacker the attacking {@link Player}, if any
     * @param damageSource the {@link Entity} dealing the damage
     * @param sendMessages whether to send denial messages to users involved
     * @return true if the damage is handled
     */
    private boolean handleCreatureDamageByEntity(
            @NotNull EntityDamageInstance event,
            @Nullable Player attacker,
            @Nullable Entity damageSource,
            boolean sendMessages)
    {
        if (!(event.damaged() instanceof Creature) || !instance.config_claims_protectCreatures)
            return false;

        //if entity is tameable and has an owner, apply special rules
        if (handlePetDamageByEntity(event, attacker, sendMessages)) return true;

        //if entity is a Copper Golem and has a summoner, apply special rules
        if (handleCopperGolemByEntity(event, attacker, sendMessages)) return true;

        // Can't be hit, but for simplicity
        if (damageSource == null) return false;

        EntityType damageSourceType = damageSource.getType();
        //if not a player, explosive, or ranged/area of effect attack, allow
        if (attacker == null
                && damageSourceType != EntityType.CREEPER
                && damageSourceType != EntityType.WITHER
                && damageSourceType != EntityType.END_CRYSTAL
                && damageSourceType != EntityType.AREA_EFFECT_CLOUD
                && damageSourceType != EntityType.WITCH
                && !(damageSource instanceof Projectile)
                && !(damageSource instanceof Explosive)
                && !(damageSource instanceof ExplosiveMinecart))
        {
            return true;
        }

        Claim cachedClaim = null;
        PlayerData playerData = null;
        if (attacker != null)
        {
            playerData = this.dataStore.getPlayerData(attacker.getUniqueId());
            cachedClaim = playerData.lastClaim;
        }

        Claim claim = this.dataStore.getClaimAt(event.damaged().getLocation(), false, cachedClaim);

        // Require a claim to handle.
        if (claim == null) return false;

        // If damaged by anything other than a player, cancel the event.
        if (attacker == null)
        {
            event.setCancelled(true);
            // Always remove projectiles shot by non-players.
            if (damageSource instanceof Projectile projectile) projectile.remove();
            return true;
        }

        //cache claim for later
        playerData.lastClaim = claim;

        // Do not message players about fireworks to prevent spam due to multi-hits.
        sendMessages &= damageSourceType != EntityType.FIREWORK_ROCKET;

        Supplier<String> override = null;
        if (sendMessages)
        {
            final Player finalAttacker = attacker;
            override = () ->
            {
                String message = dataStore.getMessage(Messages.NoDamageClaimedEntity, claim.getOwnerName());
                if (finalAttacker.hasPermission("griefprevention.ignoreclaims"))
                    message += "  " + dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
                return message;
            };
        }

        // Check for permission to access containers.
        Supplier<String> noContainersReason = claim.checkPermission(attacker, ClaimPermission.Inventory, event.original(), override);

        // If player has permission, action is allowed.
        if (noContainersReason == null) return true;

        event.setCancelled(true);

        if (damageSource instanceof Projectile projectile) {
            // Prevent projectiles from bouncing infinitely.
            preventInfiniteBounce(projectile, event.damaged());
        }

        if (sendMessages) GriefPrevention.sendMessage(attacker, TextMode.Err, noContainersReason.get());

        return true;
    }

    /**
     * Handle damage to a {@link Tameable} by a {@link Player}.
     *
     * @param event the {@link EntityDamageInstance}
     * @param attacker the attacking {@link Player}, if any
     * @param sendMessages whether to send denial messages to users involved
     * @return true if the damage is handled
     */
    private boolean handlePetDamageByEntity(
            @NotNull EntityDamageInstance event,
            @Nullable Player attacker,
            boolean sendMessages)
    {
        if (!(event.damaged() instanceof Tameable tameable) || !tameable.isTamed())
        {
            // If the animal is not owned, specifically allow attacks only if the animal is a wolf.
            return false;
        }

        AnimalTamer owner = tameable.getOwner();
        if (owner == null)
        {
            // Treat invalid state of tamed with no owner identically to untamed.
            return tameable.getType() == EntityType.WOLF;
        }

        //limit attacks by players to owners and admins in ignore claims mode
        if (attacker == null) return false;

        //if the player interacting is the owner, always allow
        if (attacker.equals(owner)) return true;

        //allow for admin override
        PlayerData attackerData = this.dataStore.getPlayerData(attacker.getUniqueId());
        if (attackerData.ignoreClaims) return true;

        event.setCancelled(true);
        if (sendMessages)
        {
            String ownerName = GriefPrevention.lookupPlayerName(owner);
            String message = dataStore.getMessage(Messages.NoDamageClaimedEntity, ownerName);
            if (attacker.hasPermission("griefprevention.ignoreclaims"))
                message += "  " + dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
            GriefPrevention.sendMessage(attacker, TextMode.Err, message);
        }
        return true;
    }

    /**
     * Handle damage to a {@link CopperGolem} by a {@link Player}.
     *
     * @param event the {@link EntityDamageInstance}
     * @param attacker the attacking {@link Player}, if any
     * @param sendMessages whether to send denial messages to users involved
     * @return true if the damage is handled
     */
    private boolean handleCopperGolemByEntity(
            @NotNull EntityDamageInstance event,
            @Nullable Player attacker,
            boolean sendMessages)
    {
        if (!(event.damaged() instanceof CopperGolem golem) || golem.getSummoner() == null)
        {
            // If the animal is not owned, specifically allow attacks only if the animal is a wolf.
            return false;
        }

        UUID owner = golem.getSummoner();
        if (owner == null)
        {
            // Treat invalid state of tamed with no owner identically to untamed.
            return false;
        }

        //limit attacks by players to owners and admins in ignore claims mode
        if (attacker == null) return false;

        //if the player interacting is the owner, always allow
        if (attacker.getUniqueId().equals(owner)) return true;

        //allow for admin override
        PlayerData attackerData = this.dataStore.getPlayerData(attacker.getUniqueId());
        if (attackerData.ignoreClaims) return true;

        event.setCancelled(true);
        if (sendMessages)
        {
            String ownerName = GriefPrevention.lookupPlayerName(owner);
            String message = dataStore.getMessage(Messages.NoDamageClaimedEntity, ownerName);
            if (attacker.hasPermission("griefprevention.ignoreclaims"))
                message += "  " + dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
            GriefPrevention.sendMessage(attacker, TextMode.Err, message);
        }
        return true;
    }

    /**
     * Prevent infinite bounces for cancelled projectile hits by removing or grounding {@link Projectile Projectiles}
     * as necessary.
     *
     * @param projectile the {@code Projectile} that has been prevented from hitting
     * @param entity the {@link Entity} being hit
     */
    private void preventInfiniteBounce(@Nullable Projectile projectile, @NotNull Entity entity)
    {
        if (projectile != null)
        {
            if (projectile.getType() == EntityType.TRIDENT)
            {
                // Instead of removing a trident, teleport it to the entity's foot location and remove velocity.
                projectile.teleport(entity);
                projectile.setVelocity(new Vector());
            }
            // Otherwise remove the projectile.
            else projectile.remove();
        }
    }

    //when a vehicle is damaged
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onVehicleDamage(@NotNull VehicleDamageEvent event)
    {
        //all of this is anti theft code
        if (!instance.config_claims_preventTheft) return;

        //don't track in worlds where claims are not enabled
        if (!instance.claimsEnabledForWorld(event.getVehicle().getWorld())) return;

        //determine which player is attacking, if any
        Player attacker = null;
        Projectile arrow = null;
        Entity damageSource = event.getAttacker();
        EntityType damageSourceType = null;

        //if damage source is null or a creeper, don't allow the damage when the vehicle is in a land claim
        if (damageSource != null)
        {
            damageSourceType = damageSource.getType();

            if (damageSource instanceof Player player)
            {
                attacker = player;
            }
            else if (damageSource instanceof Projectile projectile)
            {
                arrow = projectile;
                if (arrow.getShooter() instanceof Player shooter)
                {
                    attacker = shooter;
                }
            }
        }

        //if not a player and not an explosion, always allow
        if (attacker == null && damageSourceType != EntityType.CREEPER && damageSourceType != EntityType.WITHER && damageSourceType != EntityType.TNT)
        {
            return;
        }

        //NOTE: vehicles can be pushed around.
        //so unless precautions are taken by the owner, a resourceful thief might find ways to steal anyway
        Claim cachedClaim = null;
        PlayerData playerData = null;

        if (attacker != null)
        {
            playerData = this.dataStore.getPlayerData(attacker.getUniqueId());
            cachedClaim = playerData.lastClaim;
        }

        Claim claim = this.dataStore.getClaimAt(event.getVehicle().getLocation(), false, cachedClaim);

        // Require a claim.
        if (claim == null) return;

        //if damaged by anything other than a player, cancel the event
        if (attacker == null)
        {
            event.setCancelled(true);
            if (arrow != null) arrow.remove();
            return;
        }

        //otherwise the player damaging the entity must have permission
        final Player finalAttacker = attacker;
        Supplier<String> override = () ->
        {
            String message = dataStore.getMessage(Messages.NoDamageClaimedEntity, claim.getOwnerName());
            if (finalAttacker.hasPermission("griefprevention.ignoreclaims"))
                message += "  " + dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
            return message;
        };
        Supplier<String> noContainersReason = claim.checkPermission(attacker, ClaimPermission.Inventory, event, override);
        if (noContainersReason != null)
        {
            event.setCancelled(true);
            preventInfiniteBounce(arrow, event.getVehicle());
            GriefPrevention.sendMessage(attacker, TextMode.Err, noContainersReason.get());
        }

        //cache claim for later
        playerData.lastClaim = claim;
    }

    //when a splash potion affects one or more entities...
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPotionSplash(@NotNull PotionSplashEvent event)
    {
        ThrownPotion potion = event.getPotion();

        ProjectileSource projectileSource = potion.getShooter();
        // Ignore potions with no source.
        if (projectileSource == null) return;
        final Player thrower;
        if ((projectileSource instanceof Player))
            thrower = (Player) projectileSource;
        else thrower = null;
        AtomicBoolean messagedPlayer = new AtomicBoolean(false);

        Collection<PotionEffect> effects = potion.getEffects();
        for (PotionEffect effect : effects)
        {
            PotionEffectType effectType = effect.getType();

            // Restrict some potions on claimed villagers and animals.
            // Griefers could use potions to kill entities or steal them over fences.
            if (GRIEF_EFFECTS.contains(effectType))
            {
                Claim cachedClaim = null;
                for (LivingEntity affected : event.getAffectedEntities())
                {
                    // Always impact the thrower.
                    if (affected == thrower) continue;

                    if (affected.getType() == EntityType.VILLAGER || affected instanceof Animals)
                    {
                        Claim claim = this.dataStore.getClaimAt(affected.getLocation(), false, cachedClaim);
                        if (claim != null)
                        {
                            cachedClaim = claim;

                            if (thrower == null)
                            {
                                // Non-player source: Witches, dispensers, etc.
                                if (!EntityEventHandler.isBlockSourceInClaim(projectileSource, claim))
                                {
                                    // If the source is not a block in the same claim as the affected entity, disallow.
                                    event.setIntensity(affected, 0);
                                }
                            }
                            else
                            {
                                // Source is a player. Determine if they have permission to access entities in the claim.
                                Supplier<String> override = () -> instance.dataStore.getMessage(Messages.NoDamageClaimedEntity, claim.getOwnerName());
                                final Supplier<String> noContainersReason = claim.checkPermission(thrower, ClaimPermission.Inventory, event, override);
                                if (noContainersReason != null)
                                {
                                    event.setIntensity(affected, 0);
                                    if (messagedPlayer.compareAndSet(false, true))
                                    {
                                        GriefPrevention.sendMessage(thrower, TextMode.Err, noContainersReason.get());
                                    }
                                }
                            }
                        }
                    }
                }
            }

            //Otherwise, ignore potions not thrown by players
            if (thrower == null) return;

            //otherwise, no restrictions for positive effects
            if (effectType.getCategory() == PotionEffectTypeCategory.BENEFICIAL) continue;

            for (LivingEntity affected : event.getAffectedEntities())
            {
                //always impact the thrower
                if (affected == thrower) continue;

                //always impact non players
                if (!(affected instanceof Player affectedPlayer)) continue;
            }
        }
    }

    private record EntityDamageInstance(
            @NotNull Entity damaged,
            @Nullable Entity damager,
            @NotNull EntityDamageEvent.DamageCause cause,
            @NotNull Event original)
    {

        EntityDamageInstance(@NotNull EntityDamageEvent event)
        {
            this(
                    event.getEntity(),
                    event instanceof EntityDamageByEntityEvent damageBy ?
                            damageBy.getDamageSource().getCausingEntity() != null ? damageBy.getDamageSource().getCausingEntity() : damageBy.getDamager()
                    : null,
                    event.getCause(),
                    event
            );
        }

        EntityDamageInstance(@NotNull PrePlayerAttackEntityEvent event)
        {
            this(
                    event.getAttacked(),
                    event.getPlayer(),
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    event
            );
        }

        EntityDamageInstance(@NotNull EntityCombustEvent event)
        {
            this(
                    event.getEntity(),
                    event instanceof EntityCombustByEntityEvent combustBy ? combustBy.getCombuster() : null,
                    EntityDamageEvent.DamageCause.FIRE_TICK,
                    event
            );
        }

        EntityDamageInstance(@NotNull EntityPushedByEntityAttackEvent event)
        {
            this(
                    event.getEntity(),
                    event.getPushedBy(),
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    event
            );
        }

        public void setCancelled(boolean cancelled)
        {
            if (this.original instanceof Cancellable cancellable) cancellable.setCancelled(cancelled);
        }

        public Player getResponsiblePlayer() {
            return switch (damager)
            {
                case null -> null;
                case Player player -> player;
                case Projectile projectile -> projectile.getShooter() instanceof Player player ? player : null;
                case AreaEffectCloud cloud -> cloud.getSource() instanceof Player player ? player : null;
                case TNTPrimed tnt -> tnt.getSource() instanceof Player player ? player : null;
                case Tameable tameable -> tameable.getOwner() instanceof Player player ? player : null;
                case CopperGolem golem -> golem.getSummoner() != null ? Bukkit.getServer().getPlayer(golem.getSummoner()) : null;
                case LightningStrike lightning -> lightning.getCausingEntity() instanceof Player player ? player : null;
                default -> null;
            };
        }
    }
}
