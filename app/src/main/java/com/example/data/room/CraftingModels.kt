package com.example.data.room

/**
 * Representation of a Crafting Recipe combining Chemical Components and Scrap Metal.
 */
data class CraftingRecipe(
    val recipeId: String,
    val name: String,
    val category: CraftingCategory,
    val resultType: String,
    val resultRarity: ItemRarity,
    val resultDescription: String,
    val damage: Int = 0,
    val defense: Int = 0,
    val healHp: Int = 0,
    val reduceToxicity: Int = 0,
    val criticalBonus: Float = 0f,
    val weightKg: Float = 1.0f,
    val maxDurability: Int = 100,
    // Material Requirements
    val scrapMetalCost: Int = 0,
    val chemReagentCost: Int = 0,
    val biogelCost: Int = 0,
    val microCircuitCost: Int = 0
)

enum class CraftingCategory(val label: String) {
    WEAPONS("WEAPONS"),
    ARMOR("ARMOR"),
    CHEMS("MEDICAL / CHEMS"),
    CYBERWARE("CYBERWARE")
}

/**
 * Material Component Item IDs in Room Database.
 */
object CraftingMaterials {
    const val SCRAP_METAL = "mat_scrap_metal"
    const val CHEM_REAGENT = "mat_chem_reagent"
    const val BIOGEL_VIAL = "mat_biogel_vial"
    const val MICRO_CIRCUIT = "mat_micro_circuit"

    val DEFAULT_MATERIALS = listOf(
        InventoryItemEntity(
            itemId = SCRAP_METAL,
            name = "Salvaged Scrap Metal",
            type = "MATERIAL",
            rarity = ItemRarity.COMMON.name,
            category = "Crafting Material",
            weightKg = 0.25f,
            creditValue = 8,
            quantity = 12,
            description = "High-tensile alloy plating and reinforced chassis brackets salvaged from wasteland ruins."
        ),
        InventoryItemEntity(
            itemId = CHEM_REAGENT,
            name = "Volatile Chem Reagent",
            type = "MATERIAL",
            rarity = ItemRarity.UNCOMMON.name,
            category = "Crafting Material",
            weightKg = 0.2f,
            creditValue = 12,
            quantity = 8,
            description = "Purified chemical reactant and thermal synthesizer catalyst for weapons and med-packs."
        ),
        InventoryItemEntity(
            itemId = BIOGEL_VIAL,
            name = "Bio-Gel Compound",
            type = "MATERIAL",
            rarity = ItemRarity.RARE.name,
            category = "Crafting Material",
            weightKg = 0.15f,
            creditValue = 18,
            quantity = 5,
            description = "Organic cellular stabilizer used in high-grade medical infusions and neuro-linkers."
        ),
        InventoryItemEntity(
            itemId = MICRO_CIRCUIT,
            name = "Military Micro-Circuits",
            type = "MATERIAL",
            rarity = ItemRarity.EPIC.name,
            category = "Crafting Material",
            weightKg = 0.1f,
            creditValue = 25,
            quantity = 4,
            description = "Radiation-hardened microprocessor logic relays for cyberware and advanced weapon capacitors."
        )
    )
}

/**
 * Wasteland Field Fabricator Recipe Registry.
 */
object WastelandRecipes {
    val ALL_RECIPES = listOf(
        // Weapons
        CraftingRecipe(
            recipeId = "craft_plasma_cutter",
            name = "Overcharged Plasma Cutter",
            category = CraftingCategory.WEAPONS,
            resultType = "WEAPON",
            resultRarity = ItemRarity.EPIC,
            resultDescription = "High-density thermal energy blade with magnetically contained plasma arc.",
            damage = 38,
            criticalBonus = 0.20f,
            weightKg = 2.4f,
            maxDurability = 110,
            scrapMetalCost = 4,
            chemReagentCost = 3,
            microCircuitCost = 2
        ),
        CraftingRecipe(
            recipeId = "craft_chem_flamer",
            name = "Pressurized Chem-Flamer",
            category = CraftingCategory.WEAPONS,
            resultType = "WEAPON",
            resultRarity = ItemRarity.RARE,
            resultDescription = "Fires aerosolized volatile chemical stream inflicting devastating burning trauma.",
            damage = 32,
            criticalBonus = 0.10f,
            weightKg = 3.5f,
            maxDurability = 95,
            scrapMetalCost = 3,
            chemReagentCost = 4
        ),
        CraftingRecipe(
            recipeId = "craft_shock_mace",
            name = "Heavy Kinetic Shock Mace",
            category = CraftingCategory.WEAPONS,
            resultType = "WEAPON",
            resultRarity = ItemRarity.RARE,
            resultDescription = "Weighted scrap steel war-mace with high-voltage capacitor coils.",
            damage = 29,
            criticalBonus = 0.15f,
            weightKg = 4.2f,
            maxDurability = 130,
            scrapMetalCost = 5,
            microCircuitCost = 2
        ),

        // Armor
        CraftingRecipe(
            recipeId = "craft_hazard_exosuit",
            name = "Titanium Hazard Exosuit",
            category = CraftingCategory.ARMOR,
            resultType = "ARMOR",
            resultRarity = ItemRarity.EPIC,
            resultDescription = "Reinforced motorized frame providing extreme kinetic deflection and toxic pool resistance.",
            defense = 28,
            weightKg = 6.0f,
            maxDurability = 140,
            scrapMetalCost = 6,
            chemReagentCost = 3
        ),
        CraftingRecipe(
            recipeId = "craft_rad_vest",
            name = "Lead-Lined Ballistic Vest",
            category = CraftingCategory.ARMOR,
            resultType = "ARMOR",
            resultRarity = ItemRarity.RARE,
            resultDescription = "Multi-layer scrap composite armor with inner lead mesh dampening wasteland radiation.",
            defense = 19,
            weightKg = 4.5f,
            maxDurability = 100,
            scrapMetalCost = 4,
            biogelCost = 2
        ),

        // Medical & Chems
        CraftingRecipe(
            recipeId = "craft_hyper_stim",
            name = "Hyper-Stimpack Injection",
            category = CraftingCategory.CHEMS,
            resultType = "CONSUMABLE",
            resultRarity = ItemRarity.RARE,
            resultDescription = "Rapid-acting cellular regeneration cocktail. Instantly restores vital signs.",
            healHp = 70,
            reduceToxicity = 15,
            weightKg = 0.3f,
            chemReagentCost = 2,
            biogelCost = 2
        ),
        CraftingRecipe(
            recipeId = "craft_rad_purge",
            name = "Concentrated Rad-Purge Serum",
            category = CraftingCategory.CHEMS,
            resultType = "CONSUMABLE",
            resultRarity = ItemRarity.RARE,
            resultDescription = "Flushes cellular toxic isotopes and purges bio-waste from the bloodstream.",
            healHp = 25,
            reduceToxicity = 45,
            weightKg = 0.25f,
            chemReagentCost = 3,
            scrapMetalCost = 1
        ),
        CraftingRecipe(
            recipeId = "craft_reflex_cocktail",
            name = "Adrenaline Chem-Cocktail",
            category = CraftingCategory.CHEMS,
            resultType = "CONSUMABLE",
            resultRarity = ItemRarity.EPIC,
            resultDescription = "Emergency neurological booster providing combat rush and partial health restoration.",
            healHp = 45,
            reduceToxicity = 20,
            weightKg = 0.2f,
            chemReagentCost = 2,
            biogelCost = 1,
            microCircuitCost = 1
        ),

        // Cyberware
        CraftingRecipe(
            recipeId = "craft_synaptic_chip",
            name = "Synaptic Accelerator Matrix",
            category = CraftingCategory.CYBERWARE,
            resultType = "NEURAL_CHIP",
            resultRarity = ItemRarity.LEGENDARY,
            resultDescription = "Direct neural link mod overclocking reflex latency, weapon aim, and subcutaneous armor shielding.",
            damage = 14,
            defense = 10,
            criticalBonus = 0.25f,
            weightKg = 0.2f,
            maxDurability = 100,
            microCircuitCost = 3,
            chemReagentCost = 3,
            scrapMetalCost = 2
        )
    )
}

/**
 * Equipment Upgrade calculation helper.
 */
object UpgradeEngine {
    data class UpgradeCost(
        val scrapCost: Int,
        val chemCost: Int,
        val creditCost: Int,
        val nextDamage: Int,
        val nextDefense: Int,
        val nextMaxDurability: Int,
        val nextTechLevel: Int
    )

    fun calculateUpgrade(item: InventoryItemEntity): UpgradeCost {
        val currentLevel = item.techLevel
        val scrapCost = 2 + currentLevel * 2
        val chemCost = 1 + currentLevel
        val creditCost = currentLevel * 20

        val nextDamage = if (item.damage > 0) item.damage + 6 else 0
        val nextDefense = if (item.defense > 0) item.defense + 5 else 0
        val nextMaxDurability = item.maxDurability + 15
        val nextTechLevel = currentLevel + 1

        return UpgradeCost(
            scrapCost = scrapCost,
            chemCost = chemCost,
            creditCost = creditCost,
            nextDamage = nextDamage,
            nextDefense = nextDefense,
            nextMaxDurability = nextMaxDurability,
            nextTechLevel = nextTechLevel
        )
    }
}
