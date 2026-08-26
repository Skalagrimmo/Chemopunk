package com.example.data.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    @Query("SELECT * FROM inventory_items ORDER BY isEquipped DESC, dateAcquired DESC")
    fun getAllInventoryItems(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE isEquipped = 1")
    fun getEquippedItems(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE type = :type ORDER BY dateAcquired DESC")
    fun getItemsByType(type: String): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE itemId = :itemId LIMIT 1")
    fun getItemById(itemId: String): Flow<InventoryItemEntity?>

    @Query("SELECT * FROM inventory_items WHERE itemId = :itemId LIMIT 1")
    suspend fun findItemDirect(itemId: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE equipSlot = :slot AND isEquipped = 1 LIMIT 1")
    suspend fun getEquippedBySlot(slot: String): InventoryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InventoryItemEntity>)

    @Update
    suspend fun updateItem(item: InventoryItemEntity)

    @Delete
    suspend fun deleteItem(item: InventoryItemEntity)

    @Query("DELETE FROM inventory_items WHERE itemId = :itemId")
    suspend fun deleteItemById(itemId: String)

    @Query("DELETE FROM inventory_items")
    suspend fun clearAllInventory()

    @Query("UPDATE inventory_items SET isEquipped = 0 WHERE equipSlot = :slot")
    suspend fun unequipAllInSlot(slot: String)

    @Query("UPDATE inventory_items SET isEquipped = 1, equipSlot = :slot WHERE itemId = :itemId")
    suspend fun setEquippedState(itemId: String, slot: String)

    @Query("UPDATE inventory_items SET isEquipped = 0 WHERE itemId = :itemId")
    suspend fun unequipItem(itemId: String)

    @Query("UPDATE inventory_items SET quantity = :newQuantity WHERE itemId = :itemId")
    suspend fun updateQuantity(itemId: String, newQuantity: Int)

    @Query("UPDATE inventory_items SET durability = :newDurability WHERE itemId = :itemId")
    suspend fun updateDurability(itemId: String, newDurability: Int)

    @Query("SELECT COUNT(*) FROM inventory_items")
    suspend fun getItemCount(): Int

    @Query("SELECT SUM(weightKg * quantity) FROM inventory_items")
    fun getTotalInventoryWeight(): Flow<Float?>

    @Query("SELECT * FROM inventory_items WHERE durability < maxDurability ORDER BY durability ASC")
    fun getDamagedItems(): Flow<List<InventoryItemEntity>>

    @Query("UPDATE inventory_items SET durability = CASE WHEN durability - :amount < 0 THEN 0 ELSE durability - :amount END WHERE itemId = :itemId")
    suspend fun degradeDurability(itemId: String, amount: Int)

    @Query("UPDATE inventory_items SET durability = maxDurability WHERE itemId = :itemId")
    suspend fun repairFully(itemId: String)
}

@Dao
interface CharacterProfileDao {

    @Query("SELECT * FROM character_profiles WHERE profileId = :profileId LIMIT 1")
    fun getProfile(profileId: Int = 1): Flow<CharacterProfileEntity?>

    @Query("SELECT * FROM character_profiles WHERE profileId = :profileId LIMIT 1")
    suspend fun getProfileDirect(profileId: Int = 1): CharacterProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: CharacterProfileEntity)

    @Query("UPDATE character_profiles SET credits = :newCredits, lastUpdated = :timestamp WHERE profileId = :profileId")
    suspend fun updateCredits(profileId: Int = 1, newCredits: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE character_profiles SET equippedWeaponId = :weaponId, lastUpdated = :timestamp WHERE profileId = :profileId")
    suspend fun updateEquippedWeapon(profileId: Int = 1, weaponId: String?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE character_profiles SET equippedArmorId = :armorId, lastUpdated = :timestamp WHERE profileId = :profileId")
    suspend fun updateEquippedArmor(profileId: Int = 1, armorId: String?, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface PerkDao {

    @Query("SELECT * FROM perks ORDER BY tier ASC, name ASC")
    fun getAcquiredPerks(): Flow<List<PerkEntity>>

    @Query("SELECT * FROM perks WHERE perkId = :perkId LIMIT 1")
    suspend fun findPerk(perkId: String): PerkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun acquirePerk(perk: PerkEntity)

    @Query("DELETE FROM perks")
    suspend fun clearAllPerks()
}

@Dao
interface ShopDao {

    @Query("SELECT * FROM shop_items ORDER BY type ASC, buyPrice ASC")
    fun getShopItems(): Flow<List<NpcShopEntity>>

    @Query("SELECT * FROM shop_items WHERE itemId = :itemId LIMIT 1")
    suspend fun findShopItem(itemId: String): NpcShopEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShopItem(item: NpcShopEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllShopItems(items: List<NpcShopEntity>)

    @Query("UPDATE shop_items SET stock = :newStock WHERE itemId = :itemId")
    suspend fun updateStock(itemId: String, newStock: Int)

    @Query("DELETE FROM shop_items")
    suspend fun clearAllShopItems()
}
