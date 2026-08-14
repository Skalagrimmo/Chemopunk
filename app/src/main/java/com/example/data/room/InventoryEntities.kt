package com.example.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.Item
import com.example.data.ItemType

enum class ItemRarity(val label: String, val colorHex: Long) {
    COMMON("COMMON", 0xFF94A3B8),
    UNCOMMON("UNCOMMON", 0xFF22C55E),
    RARE("RARE", 0xFF38BDF8),
    EPIC("EPIC", 0xFFA855F7),
    LEGENDARY("LEGENDARY", 0xFFFFD700)
}

enum class EquipSlot(val label: String) {
    WEAPON("WEAPON"),
    ARMOR("ARMOR"),
    NEURAL_CHIP("NEURAL CHIP"),
    ACCESSORY("ACCESSORY"),
    NONE("NONE")
}

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey val itemId: String,
    val name: String,
    val type: String, // CONSUMABLE, WEAPON, ARMOR, KEY_ITEM, NEURAL_CHIP
    val rarity: String = ItemRarity.COMMON.name,
    val category: String = "Gear",
    val damage: Int = 0,
    val defense: Int = 0,
    val healHp: Int = 0,
    val reduceToxicity: Int = 0,
    val criticalBonus: Float = 0f,
    val durability: Int = 100,
    val maxDurability: Int = 100,
    val weightKg: Float = 0.5f,
    val creditValue: Int = 10,
    val quantity: Int = 1,
    val isEquipped: Boolean = false,
    val equipSlot: String = EquipSlot.NONE.name,
    val description: String = "",
    val techLevel: Int = 1,
    val modSlots: Int = 1,
    val dateAcquired: Long = System.currentTimeMillis()
) {
    fun toDomainItem(): Item {
        val itemType = try {
            ItemType.valueOf(type)
        } catch (_: Exception) {
            ItemType.CONSUMABLE
        }
        return Item(
            id = itemId,
            name = name,
            type = itemType,
            value = creditValue,
            healHp = healHp,
            reduceToxicity = reduceToxicity,
            damage = damage,
            defense = defense,
            description = description
        )
    }

    companion object {
        fun fromDomainItem(
            item: Item,
            rarity: ItemRarity = ItemRarity.COMMON,
            weightKg: Float = 1.0f,
            equipSlot: EquipSlot = when (item.type) {
                ItemType.WEAPON -> EquipSlot.WEAPON
                ItemType.ARMOR -> EquipSlot.ARMOR
                else -> EquipSlot.NONE
            },
            isEquipped: Boolean = false
        ): InventoryItemEntity {
            return InventoryItemEntity(
                itemId = item.id,
                name = item.name,
                type = item.type.name,
                rarity = rarity.name,
                category = when (item.type) {
                    ItemType.WEAPON -> "Combat Weapon"
                    ItemType.ARMOR -> "Hazmat Exosuit"
                    ItemType.CONSUMABLE -> "Medical Chem"
                    ItemType.KEY_ITEM -> "Vault Cipher"
                },
                damage = item.damage,
                defense = item.defense,
                healHp = item.healHp,
                reduceToxicity = item.reduceToxicity,
                creditValue = item.value,
                description = item.description,
                weightKg = weightKg,
                equipSlot = equipSlot.name,
                isEquipped = isEquipped
            )
        }
    }
}

@Entity(tableName = "character_profiles")
data class CharacterProfileEntity(
    @PrimaryKey val profileId: Int = 1,
    val characterName: String = "Scythe-01",
    val level: Int = 1,
    val exp: Int = 0,
    val credits: Int = 50,
    val maxCarryWeightKg: Float = 45.0f,
    val equippedWeaponId: String? = null,
    val equippedArmorId: String? = null,
    val equippedChipId: String? = null,
    val totalItemsDiscovered: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
