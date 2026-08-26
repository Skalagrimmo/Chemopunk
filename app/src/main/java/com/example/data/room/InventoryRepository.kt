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
    private val profileDao: CharacterProfileDao,
    private val perkDao: PerkDao,
    private val shopDao: ShopDao,
    private val factionRepDao: FactionRepDao
) {
    val allInventoryItems: Flow<List<InventoryItemEntity>> = inventoryDao.getAllInventoryItems()

    val equippedItems: Flow<List<InventoryItemEntity>> = inventoryDao.getEquippedItems()

    val characterProfile: Flow<CharacterProfileEntity?> = profileDao.getProfile(1)

    val acquiredPerks: Flow<List<PerkEntity>> = perkDao.getAcquiredPerks()

    val shopItems: Flow<List<NpcShopEntity>> = shopDao.getShopItems()

    val factionReps: Flow<Map<String, Int>> =
        factionRepDao.observeFactionReps().map { list -> list.associate { it.faction to it.standing } }

    suspend fun allocateSkillPoint(skill: com.example.data.SkillType) {
        val p = profileDao.getProfileDirect(1) ?: return
        if (p.unspentSkillPoints <= 0) return
        val updated = when (skill) {
            com.example.data.SkillType.LOCKPICKING -> p.copy(skillLockpicking = p.skillLockpicking + 1, unspentSkillPoints = p.unspentSkillPoints - 1)
            com.example.data.SkillType.SCIENCE -> p.copy(skillScience = p.skillScience + 1, unspentSkillPoints = p.unspentSkillPoints - 1)
            com.example.data.SkillType.MELEE -> p.copy(skillMelee = p.skillMelee + 1, unspentSkillPoints = p.unspentSkillPoints - 1)
            com.example.data.SkillType.GUNS -> p.copy(skillGuns = p.skillGuns + 1, unspentSkillPoints = p.unspentSkillPoints - 1)
            com.example.data.SkillType.MEDICINE -> p.copy(skillMedicine = p.skillMedicine + 1, unspentSkillPoints = p.unspentSkillPoints - 1)
        }
        profileDao.insertOrUpdateProfile(updated)
    }

    suspend fun acquirePerk(perk: com.example.data.Perk) {
        perkDao.acquirePerk(
            PerkEntity(
                perkId = perk.perkId,
                name = perk.name,
                description = perk.description,
                tier = perk.tier
            )
        )
        val p = profileDao.getProfileDirect(1)
        if (p != null) {
            profileDao.insertOrUpdateProfile(p.copy(unspentPerkPoints = (p.unspentPerkPoints - 1).coerceAtLeast(0)))
        }
    }

    suspend fun grantLevelRewards(skillPoints: Int, perkPoints: Int) {
        val p = profileDao.getProfileDirect(1) ?: return
        profileDao.insertOrUpdateProfile(
            p.copy(
                unspentSkillPoints = p.unspentSkillPoints + skillPoints,
                unspentPerkPoints = p.unspentPerkPoints + perkPoints
            )
        )
    }

    suspend fun addExperience(amount: Int): CharacterProfileEntity? {
        val p = profileDao.getProfileDirect(1) ?: return null
        val updated = p.copy(exp = p.exp + amount, lastUpdated = System.currentTimeMillis())
        profileDao.insertOrUpdateProfile(updated)
        return updated
    }

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

            // Seed Initial Crafting Materials (Scrap Metal, Chem Reagents, Bio-Gel, Micro-Circuits)
            entities.addAll(CraftingMaterials.DEFAULT_MATERIALS)

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

data class ScrapResult(
    val creditsGained: Int,
    val scrapMetalGained: Int,
    val chemReagentsGained: Int,
    val itemName: String
)

    suspend fun getMaterialQuantity(materialId: String): Int {
        return inventoryDao.findItemDirect(materialId)?.quantity ?: 0
    }

    suspend fun addMaterialQuantity(materialId: String, count: Int) {
        if (count <= 0) return
        val existing = inventoryDao.findItemDirect(materialId)
        if (existing != null) {
            inventoryDao.updateQuantity(materialId, existing.quantity + count)
        } else {
            val template = CraftingMaterials.DEFAULT_MATERIALS.firstOrNull { it.itemId == materialId }
            if (template != null) {
                inventoryDao.insertItem(template.copy(quantity = count))
            }
        }
    }

    private suspend fun deductMaterial(materialId: String, amount: Int): Boolean {
        if (amount <= 0) return true
        val item = inventoryDao.findItemDirect(materialId) ?: return false
        if (item.quantity < amount) return false
        if (item.quantity == amount) {
            inventoryDao.deleteItemById(materialId)
        } else {
            inventoryDao.updateQuantity(materialId, item.quantity - amount)
        }
        return true
    }

    suspend fun craftRecipe(recipe: CraftingRecipe): Result<InventoryItemEntity> {
        val scrapHave = getMaterialQuantity(CraftingMaterials.SCRAP_METAL)
        val chemHave = getMaterialQuantity(CraftingMaterials.CHEM_REAGENT)
        val biogelHave = getMaterialQuantity(CraftingMaterials.BIOGEL_VIAL)
        val circuitHave = getMaterialQuantity(CraftingMaterials.MICRO_CIRCUIT)

        if (scrapHave < recipe.scrapMetalCost) {
            return Result.failure(Exception("Insufficient Scrap Metal ($scrapHave/${recipe.scrapMetalCost})"))
        }
        if (chemHave < recipe.chemReagentCost) {
            return Result.failure(Exception("Insufficient Chemical Reagents ($chemHave/${recipe.chemReagentCost})"))
        }
        if (biogelHave < recipe.biogelCost) {
            return Result.failure(Exception("Insufficient Bio-Gel ($biogelHave/${recipe.biogelCost})"))
        }
        if (circuitHave < recipe.microCircuitCost) {
            return Result.failure(Exception("Insufficient Micro-Circuits ($circuitHave/${recipe.microCircuitCost})"))
        }

        // Deduct materials (check return values to prevent crafting race conditions)
        if (!deductMaterial(CraftingMaterials.SCRAP_METAL, recipe.scrapMetalCost) ||
            !deductMaterial(CraftingMaterials.CHEM_REAGENT, recipe.chemReagentCost) ||
            !deductMaterial(CraftingMaterials.BIOGEL_VIAL, recipe.biogelCost) ||
            !deductMaterial(CraftingMaterials.MICRO_CIRCUIT, recipe.microCircuitCost)) {
            return Result.failure(Exception("Material deduction failed — crafting aborted"))
        }

        val cleanId = "crafted_${recipe.recipeId}_${System.currentTimeMillis()}"
        val slot = when (recipe.resultType) {
            "WEAPON" -> EquipSlot.WEAPON.name
            "ARMOR" -> EquipSlot.ARMOR.name
            "NEURAL_CHIP" -> EquipSlot.NEURAL_CHIP.name
            else -> EquipSlot.NONE.name
        }

        val newItem = InventoryItemEntity(
            itemId = cleanId,
            name = recipe.name,
            type = recipe.resultType,
            rarity = recipe.resultRarity.name,
            category = when (recipe.category) {
                CraftingCategory.WEAPONS -> "Fabricated Weapon"
                CraftingCategory.ARMOR -> "Fabricated Exosuit"
                CraftingCategory.CHEMS -> "Synthesized Chem"
                CraftingCategory.CYBERWARE -> "Synthesized Cyberware"
            },
            damage = recipe.damage,
            defense = recipe.defense,
            healHp = recipe.healHp,
            reduceToxicity = recipe.reduceToxicity,
            criticalBonus = recipe.criticalBonus,
            durability = recipe.maxDurability,
            maxDurability = recipe.maxDurability,
            weightKg = recipe.weightKg,
            creditValue = (recipe.scrapMetalCost * 8 + recipe.chemReagentCost * 12 + recipe.microCircuitCost * 25 + 30),
            quantity = 1,
            isEquipped = false,
            equipSlot = slot,
            description = recipe.resultDescription,
            techLevel = 1,
            modSlots = if (recipe.resultRarity == ItemRarity.EPIC || recipe.resultRarity == ItemRarity.LEGENDARY) 2 else 1
        )

        inventoryDao.insertItem(newItem)
        return Result.success(newItem)
    }

    suspend fun upgradeEquipment(itemId: String): Result<InventoryItemEntity> {
        val item = inventoryDao.findItemDirect(itemId)
            ?: return Result.failure(Exception("Item not found"))

        if (item.type != "WEAPON" && item.type != "ARMOR" && item.type != "NEURAL_CHIP") {
            return Result.failure(Exception("Only Weapons, Armor, and Cyberware can be upgraded"))
        }

        val upgradeCost = UpgradeEngine.calculateUpgrade(item)
        val scrapHave = getMaterialQuantity(CraftingMaterials.SCRAP_METAL)
        val chemHave = getMaterialQuantity(CraftingMaterials.CHEM_REAGENT)
        val profile = profileDao.getProfileDirect(1)
            ?: return Result.failure(Exception("Character profile not found"))

        if (scrapHave < upgradeCost.scrapCost) {
            return Result.failure(Exception("Need ${upgradeCost.scrapCost} Scrap Metal (Have: $scrapHave)"))
        }
        if (chemHave < upgradeCost.chemCost) {
            return Result.failure(Exception("Need ${upgradeCost.chemCost} Chemical Reagents (Have: $chemHave)"))
        }
        if (profile.credits < upgradeCost.creditCost) {
            return Result.failure(Exception("Need ${upgradeCost.creditCost} Credits (Have: ${profile.credits} CR)"))
        }

        // Deduct resources (check return values to prevent upgrade race conditions)
        if (!deductMaterial(CraftingMaterials.SCRAP_METAL, upgradeCost.scrapCost) ||
            !deductMaterial(CraftingMaterials.CHEM_REAGENT, upgradeCost.chemCost)) {
            return Result.failure(Exception("Material deduction failed — upgrade aborted"))
        }
        profileDao.updateCredits(1, profile.credits - upgradeCost.creditCost)

        // Upgrade item
        val upgradedName = if (item.name.contains(" +")) {
            item.name.replace(Regex(" \\+\\d+"), " +${upgradeCost.nextTechLevel}")
        } else {
            "${item.name} +${upgradeCost.nextTechLevel}"
        }

        val upgradedItem = item.copy(
            name = upgradedName,
            damage = upgradeCost.nextDamage,
            defense = upgradeCost.nextDefense,
            durability = upgradeCost.nextMaxDurability,
            maxDurability = upgradeCost.nextMaxDurability,
            techLevel = upgradeCost.nextTechLevel,
            creditValue = item.creditValue + upgradeCost.creditCost / 2
        )

        inventoryDao.insertItem(upgradedItem)
        return Result.success(upgradedItem)
    }

    suspend fun scrapItem(itemId: String): ScrapResult {
        val item = inventoryDao.findItemDirect(itemId) ?: return ScrapResult(0, 0, 0, "")
        if (item.isEquipped) {
            unequipItem(itemId)
        }

        val scrapCredits = (item.creditValue * 0.6f).toInt().coerceAtLeast(1) * item.quantity
        val scrapMetalYield = when (item.type) {
            "WEAPON" -> (1 + item.techLevel) * item.quantity
            "ARMOR" -> (2 + item.techLevel * 2) * item.quantity
            "NEURAL_CHIP" -> 1 * item.quantity
            else -> 1 * item.quantity
        }
        val chemYield = when (item.type) {
            "CONSUMABLE" -> 2 * item.quantity
            "WEAPON" -> if (item.techLevel > 1) 1 else 0
            "ARMOR" -> if (item.techLevel > 1) 1 else 0
            "NEURAL_CHIP" -> 2 * item.quantity
            else -> 0
        }

        inventoryDao.deleteItemById(itemId)

        // Add salvaged materials back to inventory
        addMaterialQuantity(CraftingMaterials.SCRAP_METAL, scrapMetalYield)
        if (chemYield > 0) {
            addMaterialQuantity(CraftingMaterials.CHEM_REAGENT, chemYield)
        }

        val profile = profileDao.getProfileDirect(1)
        if (profile != null) {
            profileDao.updateCredits(1, profile.credits + scrapCredits)
        }

        return ScrapResult(
            creditsGained = scrapCredits,
            scrapMetalGained = scrapMetalYield,
            chemReagentsGained = chemYield,
            itemName = item.name
        )
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

    // region Trade / Barter

    suspend fun seedShopIfEmpty() {
        if (shopDao.findShopItem("shop_stimpack") != null) return
        val stock = listOf(
            NpcShopEntity("shop_stimpack", "Combat Stimpack", "CONSUMABLE", "Field-grade healing stim.", healHp = 35, reduceToxicity = 15, rarity = ItemRarity.UNCOMMON.name, weightKg = 0.3f, buyPrice = 40, sellPrice = 18, stock = 8, faction = "scientists"),
            NpcShopEntity("shop_antitoxin", "Anti-Toxin Vial", "CONSUMABLE", "Neutralizes toxic buildup.", healHp = 10, reduceToxicity = 45, rarity = ItemRarity.COMMON.name, weightKg = 0.3f, buyPrice = 25, sellPrice = 10, stock = 8, faction = "scientists"),
            NpcShopEntity("shop_plasma_scalpel", "Plasma Scalpel", "WEAPON", "Ranged energy blade.", damage = 22, rarity = ItemRarity.UNCOMMON.name, weightKg = 2.0f, buyPrice = 180, sellPrice = 80, stock = 3, faction = "raiders"),
            NpcShopEntity("shop_hazard_kevlar", "Hazard Kevlar", "ARMOR", "Ballistic hazmat plating.", defense = 14, rarity = ItemRarity.RARE.name, weightKg = 5.0f, buyPrice = 220, sellPrice = 100, stock = 2, faction = "raiders"),
            NpcShopEntity("shop_reflex_chip", "Reflex Chip", "NEURAL_CHIP", "Boosts crit reflex.", damage = 6, defense = 4, criticalBonus = 0.15f, rarity = ItemRarity.RARE.name, weightKg = 0.2f, buyPrice = 260, sellPrice = 120, stock = 2, faction = "mutants")
        )
        shopDao.insertAllShopItems(stock)
    }

    /** Player purchases a shop item. Returns the acquired domain item id, or null on failure. */
    suspend fun buyShopItem(itemId: String): String? {
        val shopItem = shopDao.findShopItem(itemId) ?: return null
        if (shopItem.stock <= 0) return null
        val profile = profileDao.getProfileDirect(1) ?: return null
        val standing = getFactionStanding(shopItem.faction)
        // Higher standing => lower prices (down to 60%).
        val factor = (1f - standing / 200f).coerceIn(0.6f, 1.4f)
        val finalPrice = (shopItem.buyPrice * factor).toInt()
        if (profile.credits < finalPrice) return null
        profileDao.updateCredits(1, profile.credits - finalPrice)
        shopDao.updateStock(itemId, shopItem.stock - 1)
        inventoryDao.insertItem(
            InventoryItemEntity.fromDomainItem(
                item = shopItem.toDomainItem(),
                rarity = ItemRarity.valueOf(shopItem.rarity),
                weightKg = shopItem.weightKg
            )
        )
        adjustFactionRep(shopItem.faction, +1)
        return shopItem.itemId
    }

    /** Player sells an owned inventory item. Returns true on success. */
    suspend fun sellInventoryItem(itemId: String): Boolean {
        val item = inventoryDao.findItemDirect(itemId) ?: return false
        if (item.isEquipped) return false
        val standing = getFactionStanding("scientists")
        // Higher standing => better sell price (up to 150%).
        val factor = (0.5f + standing / 200f).coerceIn(0.5f, 1.5f)
        val sellValue = (item.creditValue * factor).toInt()
        val profile = profileDao.getProfileDirect(1) ?: return false
        profileDao.updateCredits(1, profile.credits + sellValue)
        inventoryDao.deleteItemById(itemId)
        adjustFactionRep("scientists", +1)
        return true
    }

    // region Faction Reputation

    suspend fun getFactionStanding(faction: String): Int {
        return factionRepDao.getFactionRep(faction)?.standing ?: 0
    }

    /** Adjust a faction's standing, clamped to [-100, 100]. Creates the row if missing. */
    suspend fun adjustFactionRep(faction: String, delta: Int) {
        val current = factionRepDao.getFactionRep(faction)?.standing ?: 0
        val next = (current + delta).coerceIn(-100, 100)
        factionRepDao.upsertFactionRep(FactionRepEntity(faction = faction, standing = next))
    }

    suspend fun seedFactionRepsIfEmpty() {
        val known = listOf("raiders", "scientists", "mutants")
        known.forEach { faction ->
            if (factionRepDao.getFactionRep(faction) == null) {
                factionRepDao.upsertFactionRep(FactionRepEntity(faction = faction, standing = 0))
            }
        }
    }
    // endregion
}
