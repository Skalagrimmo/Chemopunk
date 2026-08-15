package com.example.data.room

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class GearCombatStats(
    val totalWeaponDamage: Int = 0,
    val totalArmorDefense: Int = 0,
    val totalWeightKg: Float = 0f,
    val totalItemsCount: Int = 0,
    val activeWeapon: InventoryItemEntity? = null,
    val activeArmor: InventoryItemEntity? = null,
    val activeChip: InventoryItemEntity? = null
)

class InventoryRepository(
    private val inventoryDao: InventoryDao,
    private val profileDao: CharacterProfileDao
) {
    val allInventoryItems: Flow<List<InventoryItemEntity>> = inventoryDao.getAllInventoryItems()

    val equippedItems: Flow<List<InventoryItemEntity>> = inventoryDao.getEquippedItems()

    val characterProfile: Flow<CharacterProfileEntity?> = profileDao.getProfile(1)

    val gearCombatStats: Flow<GearCombatStats> = inventoryDao.getAllInventoryItems().map { items ->
        val equipped = items.filter { it.isEquipped }
        val weapon = equipped.firstOrNull { it.equipSlot == EquipSlot.WEAPON.name }
        val armor = equipped.firstOrNull { it.equipSlot == EquipSlot.ARMOR.name }
        val chip = equipped.firstOrNull { it.equipSlot == EquipSlot.NEURAL_CHIP.name }

        val totalWeight = items.sumOf { (it.weightKg * it.quantity).toDouble() }.toFloat()
        val totalDmg = weapon?.damage ?: 0
        val totalDef = armor?.defense ?: 0

        GearCombatStats(
            totalWeaponDamage = totalDmg,
            totalArmorDefense = totalDef,
            totalWeightKg = totalWeight,
            totalItemsCount = items.sumOf { it.quantity },
            activeWeapon = weapon,
            activeArmor = armor,
            activeChip = chip
        )
    }

    suspend fun seedInitialInventoryIfEmpty(
        items: List<com.example.data.Item>,
        startingWeaponId: String? = null,
        startingCredits: Int = 50
    ) {
        val count = inventoryDao.getItemCount()
        if (count == 0) {
            val entities = mutableListOf<InventoryItemEntity>()
            items.forEach { domainItem ->
                val isStartingWeapon = domainItem.id == startingWeaponId || (startingWeaponId == null && domainItem.type == com.example.data.ItemType.WEAPON)
                val rarity = when (domainItem.id) {
                    "cyber_katana" -> ItemRarity.EPIC
                    "rad_purge_serum" -> ItemRarity.RARE
                    "plasma_scalpel" -> ItemRarity.UNCOMMON
                    "hazard_kevlar" -> ItemRarity.RARE
                    else -> ItemRarity.COMMON
                }
                val weight = when (domainItem.type) {
                    com.example.data.ItemType.WEAPON -> 2.5f
                    com.example.data.ItemType.ARMOR -> 5.0f
                    com.example.data.ItemType.CONSUMABLE -> 0.3f
                    com.example.data.ItemType.KEY_ITEM -> 0.1f
                }
                val slot = when (domainItem.type) {
                    com.example.data.ItemType.WEAPON -> EquipSlot.WEAPON
                    com.example.data.ItemType.ARMOR -> EquipSlot.ARMOR
                    else -> EquipSlot.NONE
                }
                entities.add(
                    InventoryItemEntity.fromDomainItem(
                        item = domainItem,
                        rarity = rarity,
                        weightKg = weight,
                        equipSlot = slot,
                        isEquipped = isStartingWeapon
                    )
                )
            }

            // Also add a cool Neural Implant Cyberware item
            entities.add(
                InventoryItemEntity(
                    itemId = "neural_reflex_chip",
                    name = "Sandevistan Neural Chip",
                    type = "NEURAL_CHIP",
                    rarity = ItemRarity.LEGENDARY.name,
                    category = "Cyberware",
                    damage = 8,
                    defense = 5,
                    criticalBonus = 0.25f,
                    durability = 100,
                    maxDurability = 100,
                    weightKg = 0.2f,
                    creditValue = 250,
                    quantity = 1,
                    isEquipped = false,
                    equipSlot = EquipSlot.NEURAL_CHIP.name,
                    description = "Military-grade synaptic booster. Increases critical reflex response and cyber-kinetic damage.",
                    techLevel = 3,
                    modSlots = 2
                )
            )

            inventoryDao.insertAll(entities)

            profileDao.insertOrUpdateProfile(
                CharacterProfileEntity(
                    profileId = 1,
                    characterName = "Scythe-01",
                    level = 1,
                    exp = 0,
                    credits = startingCredits,
                    maxCarryWeightKg = 45.0f,
                    equippedWeaponId = startingWeaponId,
                    equippedArmorId = null,
                    equippedChipId = null,
                    totalItemsDiscovered = entities.size
                )
            )
        }
    }

    suspend fun insertOrUpdateItem(item: InventoryItemEntity) {
        inventoryDao.insertItem(item)
    }

    suspend fun addItemFromDomain(
        domainItem: com.example.data.Item,
        rarity: ItemRarity = ItemRarity.COMMON,
        weightKg: Float = 1.0f
    ) {
        val existing = inventoryDao.findItemDirect(domainItem.id)
        if (existing != null && (domainItem.type == com.example.data.ItemType.CONSUMABLE || domainItem.type == com.example.data.ItemType.KEY_ITEM)) {
            // Stack consumables
            inventoryDao.updateQuantity(existing.itemId, existing.quantity + 1)
        } else {
            val entity = InventoryItemEntity.fromDomainItem(
                item = domainItem,
                rarity = rarity,
                weightKg = weightKg
            )
            inventoryDao.insertItem(entity)
        }
    }

    suspend fun equipItem(itemId: String): Boolean {
        val item = inventoryDao.findItemDirect(itemId) ?: return false
        val slot = when {
            item.type == com.example.data.ItemType.WEAPON.name || item.equipSlot == EquipSlot.WEAPON.name -> EquipSlot.WEAPON.name
            item.type == com.example.data.ItemType.ARMOR.name || item.equipSlot == EquipSlot.ARMOR.name -> EquipSlot.ARMOR.name
            item.type == "NEURAL_CHIP" || item.equipSlot == EquipSlot.NEURAL_CHIP.name -> EquipSlot.NEURAL_CHIP.name
            else -> return false
        }

        // Unequip currently equipped item in that slot
        inventoryDao.unequipAllInSlot(slot)
        // Equip target item
        inventoryDao.setEquippedState(itemId, slot)

        // Update profile
        when (slot) {
            EquipSlot.WEAPON.name -> profileDao.updateEquippedWeapon(1, itemId)
            EquipSlot.ARMOR.name -> profileDao.updateEquippedArmor(1, itemId)
            EquipSlot.NEURAL_CHIP.name -> {
                val p = profileDao.getProfileDirect(1)
                if (p != null) {
                    profileDao.insertOrUpdateProfile(p.copy(equippedChipId = itemId))
                }
            }
        }
        return true
    }

    suspend fun unequipItem(itemId: String) {
        val item = inventoryDao.findItemDirect(itemId) ?: return
        inventoryDao.unequipItem(itemId)

        when (item.equipSlot) {
            EquipSlot.WEAPON.name -> profileDao.updateEquippedWeapon(1, null)
            EquipSlot.ARMOR.name -> profileDao.updateEquippedArmor(1, null)
            EquipSlot.NEURAL_CHIP.name -> {
                val p = profileDao.getProfileDirect(1)
                if (p != null) {
                    profileDao.insertOrUpdateProfile(p.copy(equippedChipId = null))
                }
            }
        }
    }

    suspend fun consumeItem(itemId: String): InventoryItemEntity? {
        val item = inventoryDao.findItemDirect(itemId) ?: return null
        if (item.quantity > 1) {
            inventoryDao.updateQuantity(itemId, item.quantity - 1)
        } else {
            inventoryDao.deleteItemById(itemId)
        }
        return item
    }

    suspend fun scrapItem(itemId: String): Int {
        val item = inventoryDao.findItemDirect(itemId) ?: return 0
        if (item.isEquipped) {
            unequipItem(itemId)
        }
        val scrapValue = (item.creditValue * 0.75f).toInt().coerceAtLeast(1) * item.quantity
        inventoryDao.deleteItemById(itemId)

        val profile = profileDao.getProfileDirect(1)
        if (profile != null) {
            profileDao.updateCredits(1, profile.credits + scrapValue)
        }
        return scrapValue
    }

    suspend fun repairItem(itemId: String): Pair<Boolean, Int> {
        val item = inventoryDao.findItemDirect(itemId) ?: return Pair(false, 0)
        val missingDurability = item.maxDurability - item.durability
        if (missingDurability <= 0) return Pair(false, 0)

        val repairCost = (missingDurability * 0.5f).toInt().coerceAtLeast(1)
        val profile = profileDao.getProfileDirect(1) ?: return Pair(false, 0)

        if (profile.credits >= repairCost) {
            profileDao.updateCredits(1, profile.credits - repairCost)
            inventoryDao.updateDurability(itemId, item.maxDurability)
            return Pair(true, repairCost)
        }
        return Pair(false, repairCost)
    }

    suspend fun degradeEquippedWeapon(amount: Int = 1) {
        val equippedWeapon = inventoryDao.getEquippedBySlot(EquipSlot.WEAPON.name) ?: return
        inventoryDao.degradeDurability(equippedWeapon.itemId, amount)
    }

    suspend fun degradeEquippedArmor(amount: Int = 1) {
        val equippedArmor = inventoryDao.getEquippedBySlot(EquipSlot.ARMOR.name) ?: return
        inventoryDao.degradeDurability(equippedArmor.itemId, amount)
    }

    suspend fun deleteItem(itemId: String) {
        inventoryDao.deleteItemById(itemId)
    }
}
