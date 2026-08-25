package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.room.CharacterProfileDao
import com.example.data.room.CharacterProfileEntity
import com.example.data.room.EquipSlot
import com.example.data.room.GameDatabase
import com.example.data.room.InventoryDao
import com.example.data.room.InventoryItemEntity
import com.example.data.room.InventoryRepository
import com.example.data.room.ItemRarity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomInventoryDatabaseTest {

    private lateinit var db: GameDatabase
    private lateinit var inventoryDao: InventoryDao
    private lateinit var profileDao: CharacterProfileDao
    private lateinit var repository: InventoryRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GameDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        inventoryDao = db.inventoryDao()
        profileDao = db.characterProfileDao()
        repository = InventoryRepository(inventoryDao, profileDao)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testInsertAndRetrieveInventoryItemWithRarityWeightCondition() = runBlocking {
        val item = InventoryItemEntity(
            itemId = "test_plasma_rifle",
            name = "Overcharged Plasma Rifle",
            type = "WEAPON",
            rarity = ItemRarity.EPIC.name,
            category = "Energy Weapon",
            damage = 45,
            defense = 0,
            durability = 85,
            maxDurability = 100,
            weightKg = 3.5f,
            creditValue = 180,
            quantity = 1,
            isEquipped = true,
            equipSlot = EquipSlot.WEAPON.name,
            description = "High-energy plasma projection rifle with thermal cooling coils."
        )

        inventoryDao.insertItem(item)

        val retrieved = inventoryDao.findItemDirect("test_plasma_rifle")
        assertNotNull(retrieved)
        assertEquals("Overcharged Plasma Rifle", retrieved?.name)
        assertEquals(ItemRarity.EPIC.name, retrieved?.rarity)
        assertEquals(3.5f, retrieved?.weightKg ?: 0f, 0.01f)
        assertEquals(85, retrieved?.durability)
        assertEquals(100, retrieved?.maxDurability)
        assertEquals(85, retrieved?.durabilityPercent)
        assertFalse(retrieved?.isBroken ?: true)
        assertTrue(retrieved?.isEquipped ?: false)
    }

    @Test
    fun testEquipAndUnequipWorkflow() = runBlocking {
        val weapon = InventoryItemEntity(
            itemId = "chain_blade",
            name = "Serrated Chain Blade",
            type = "WEAPON",
            rarity = ItemRarity.RARE.name,
            weightKg = 4.0f,
            damage = 28,
            durability = 100,
            maxDurability = 100,
            equipSlot = EquipSlot.WEAPON.name,
            isEquipped = false
        )
        inventoryDao.insertItem(weapon)

        val success = repository.equipItem("chain_blade")
        assertTrue(success)

        val equipped = inventoryDao.getEquippedBySlot(EquipSlot.WEAPON.name)
        assertNotNull(equipped)
        assertEquals("chain_blade", equipped?.itemId)
        assertTrue(equipped?.isEquipped == true)

        repository.unequipItem("chain_blade")
        val unequipped = inventoryDao.findItemDirect("chain_blade")
        assertFalse(unequipped?.isEquipped ?: true)
    }

    @Test
    fun testDurabilityDegradationAndRepair() = runBlocking {
        val armor = InventoryItemEntity(
            itemId = "hazmat_suit",
            name = "Reinforced Hazmat Suit",
            type = "ARMOR",
            rarity = ItemRarity.UNCOMMON.name,
            weightKg = 6.0f,
            defense = 20,
            durability = 100,
            maxDurability = 100,
            equipSlot = EquipSlot.ARMOR.name,
            isEquipped = true
        )
        inventoryDao.insertItem(armor)
        profileDao.insertOrUpdateProfile(
            CharacterProfileEntity(
                profileId = 1,
                credits = 100
            )
        )

        inventoryDao.degradeDurability("hazmat_suit", 30)
        val degraded = inventoryDao.findItemDirect("hazmat_suit")
        assertEquals(70, degraded?.durability)
        assertEquals(70, degraded?.durabilityPercent)

        val (repaired, cost) = repository.repairItem("hazmat_suit")
        assertTrue(repaired)
        assertEquals(15, cost) // (100 - 70) * 0.5 = 15 credits

        val fixed = inventoryDao.findItemDirect("hazmat_suit")
        assertEquals(100, fixed?.durability)

        val updatedProfile = profileDao.getProfileDirect(1)
        assertEquals(85, updatedProfile?.credits) // 100 - 15 = 85
    }

    @Test
    fun testTotalInventoryWeightCalculation() = runBlocking {
        val item1 = InventoryItemEntity(
            itemId = "medkit",
            name = "Stimpack",
            type = "CONSUMABLE",
            weightKg = 0.5f,
            quantity = 4,
            rarity = ItemRarity.COMMON.name
        )
        val item2 = InventoryItemEntity(
            itemId = "heavy_cannon",
            name = "Heavy Gauss Cannon",
            type = "WEAPON",
            weightKg = 12.0f,
            quantity = 1,
            rarity = ItemRarity.LEGENDARY.name
        )
        inventoryDao.insertAll(listOf(item1, item2))

        val totalWeight = inventoryDao.getTotalInventoryWeight().first()
        // 0.5 * 4 + 12.0 * 1 = 14.0 kg
        assertEquals(14.0f, totalWeight ?: 0f, 0.01f)
    }

    @Test
    fun testCraftRecipeCombiningChemicalAndScrapComponents() = runBlocking {
        // Seed materials: 10 Scrap Metal, 8 Chem Reagents, 5 Micro-Circuits
        repository.addMaterialQuantity(com.example.data.room.CraftingMaterials.SCRAP_METAL, 10)
        repository.addMaterialQuantity(com.example.data.room.CraftingMaterials.CHEM_REAGENT, 8)
        repository.addMaterialQuantity(com.example.data.room.CraftingMaterials.MICRO_CIRCUIT, 5)

        val plasmaRecipe = com.example.data.room.WastelandRecipes.ALL_RECIPES.first { it.recipeId == "craft_plasma_cutter" }
        // Costs: 4 Scrap, 3 Chem, 2 Circuits

        val craftResult = repository.craftRecipe(plasmaRecipe)
        assertTrue(craftResult.isSuccess)

        val craftedItem = craftResult.getOrNull()
        assertNotNull(craftedItem)
        assertEquals("Overcharged Plasma Cutter", craftedItem?.name)
        assertEquals("WEAPON", craftedItem?.type)
        assertEquals(38, craftedItem?.damage)

        // Verify remaining materials in Room Database
        val remainingScrap = repository.getMaterialQuantity(com.example.data.room.CraftingMaterials.SCRAP_METAL)
        val remainingChem = repository.getMaterialQuantity(com.example.data.room.CraftingMaterials.CHEM_REAGENT)
        val remainingCircuits = repository.getMaterialQuantity(com.example.data.room.CraftingMaterials.MICRO_CIRCUIT)

        assertEquals(6, remainingScrap)     // 10 - 4 = 6
        assertEquals(5, remainingChem)      // 8 - 3 = 5
        assertEquals(3, remainingCircuits)  // 5 - 2 = 3
    }

    @Test
    fun testUpgradeEquipmentIncreasesStatsAndConsumesMaterials() = runBlocking {
        // Seed weapon + profile + materials
        val katana = InventoryItemEntity(
            itemId = "cyber_blade_01",
            name = "Mono-Molecular Katana",
            type = "WEAPON",
            rarity = ItemRarity.RARE.name,
            damage = 25,
            defense = 0,
            durability = 100,
            maxDurability = 100,
            techLevel = 1,
            equipSlot = EquipSlot.WEAPON.name
        )
        inventoryDao.insertItem(katana)

        profileDao.insertOrUpdateProfile(
            CharacterProfileEntity(
                profileId = 1,
                credits = 100
            )
        )

        repository.addMaterialQuantity(com.example.data.room.CraftingMaterials.SCRAP_METAL, 10)
        repository.addMaterialQuantity(com.example.data.room.CraftingMaterials.CHEM_REAGENT, 10)

        // Upgrade Level 1 -> Level 2 (Cost: 4 Scrap, 2 Chem, 20 Credits)
        val upgradeResult = repository.upgradeEquipment("cyber_blade_01")
        assertTrue(upgradeResult.isSuccess)

        val upgraded = inventoryDao.findItemDirect("cyber_blade_01")
        assertNotNull(upgraded)
        assertEquals(2, upgraded?.techLevel)
        assertEquals("Mono-Molecular Katana +2", upgraded?.name)
        assertEquals(31, upgraded?.damage)          // 25 + 6 = 31
        assertEquals(115, upgraded?.maxDurability)  // 100 + 15 = 115

        // Check material & credit deduction
        val remainingScrap = repository.getMaterialQuantity(com.example.data.room.CraftingMaterials.SCRAP_METAL)
        val remainingChem = repository.getMaterialQuantity(com.example.data.room.CraftingMaterials.CHEM_REAGENT)
        val remainingCredits = profileDao.getProfileDirect(1)?.credits

        assertEquals(6, remainingScrap)     // 10 - 4 = 6
        assertEquals(8, remainingChem)      // 10 - 2 = 8
        assertEquals(80, remainingCredits)  // 100 - 20 = 80
    }

    @Test
    fun testScrapItemYieldsScrapMetalAndChemReagents() = runBlocking {
        val scrapWeapon = InventoryItemEntity(
            itemId = "junk_rifle",
            name = "Rusted Assault Carbine",
            type = "WEAPON",
            rarity = ItemRarity.COMMON.name,
            creditValue = 40,
            quantity = 1,
            techLevel = 2
        )
        inventoryDao.insertItem(scrapWeapon)
        profileDao.insertOrUpdateProfile(CharacterProfileEntity(profileId = 1, credits = 0))

        val result = repository.scrapItem("junk_rifle")
        assertEquals(24, result.creditsGained) // 40 * 0.6 = 24
        assertEquals(3, result.scrapMetalGained) // 1 + 2 = 3
        assertEquals(1, result.chemReagentsGained)

        val scrapMetalInDb = repository.getMaterialQuantity(com.example.data.room.CraftingMaterials.SCRAP_METAL)
        val chemReagentInDb = repository.getMaterialQuantity(com.example.data.room.CraftingMaterials.CHEM_REAGENT)
        val creditsInDb = profileDao.getProfileDirect(1)?.credits

        assertEquals(3, scrapMetalInDb)
        assertEquals(1, chemReagentInDb)
        assertEquals(24, creditsInDb)
    }
}
