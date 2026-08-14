# CHEMOPUNK RPG: SECTOR 7 ACID SUMP

Welcome to **Sector 7 - Chemical Wasteland**, a post-apocalyptic underground facility filled with bio-hazards, mutagenic sludge, and forgotten cybernetic automated defenses.

---

## GAME_CONFIG
- initial_floor: 1
- max_toxicity: 100
- starting_hp: 100
- starting_credits: 50
- starting_weapon: Plasma Scalpel
- font_style: CRT_GREEN

---

## ITEM_DATABASE

### Item: anti_toxin
- Name: Anti-Toxin Serum
- Type: CONSUMABLE
- Value: 25
- Effect: Reduce Toxicity by 40, Heal 15 HP
- Description: Filters active mutagens out of the bloodstream.

### Item: hyper_stim
- Name: Hyper-Stim Injector
- Type: CONSUMABLE
- Value: 40
- Effect: Heal 50 HP, Increase Attack Power by +5 for 3 turns
- Description: Adrenaline and combat chem cocktail.

### Item: plasma_scalpel
- Name: Plasma Scalpel
- Type: WEAPON
- Damage: 18
- Description: High-frequency plasma blade designed for cutting through bio-steel and mutated hide.

### Item: hazmat_vest
- Name: Chem-Resistant Vest
- Type: ARMOR
- Defense: 8
- Description: Multi-layered polymer armor lined with lead foil.

---

## ENEMY_DATABASE

### Enemy: acid_slug
- Name: Mutated Acid Slug
- HP: 35
- MaxHP: 35
- Attack: 8
- Armor: 2
- ToxicityDamage: 5
- ASCII_Glyph: S
- ExpReward: 15
- Loot: anti_toxin

### Enemy: chem_drone
- Name: Chem-Sentry Drone v2
- HP: 55
- MaxHP: 55
- Attack: 14
- Armor: 5
- ToxicityDamage: 0
- ASCII_Glyph: D
- ExpReward: 30
- Loot: hyper_stim

### Enemy: bio_mutant
- Name: Cyber-Mutant Behemoth
- HP: 110
- MaxHP: 110
- Attack: 22
- Armor: 8
- ToxicityDamage: 12
- ASCII_Glyph: M
- ExpReward: 80
- Loot: hazmat_vest

---

## MAP_LAYOUT: Floor 1

```
####################
#P..#......#.......#
#...#..S...#...D...#
#...#......#.......#
###.########.#######
#.....#........#...#
#.S...#...M....#...#
#.....#........#...#
####################
```

Legend:
- P: Player Start Position
- #: Solid Bio-Steel Wall
- .: Walkable Steel Grating / Chem Floor
- S: Mutated Acid Slug
- D: Chem-Sentry Drone
- M: Cyber-Mutant Boss
- E: Extraction Lift

---

## STORY_NODES

### Node: start
- Title: Waking Up in Sector 7
- Content: You regain consciousness on a corroded metal catwalk. Green chemical haze hangs thick in the air. Your Geiger counter clicks erratically.
- Choice: [Inspect the nearby terminal](@node_terminal)
- Choice: [Equip your Plasma Scalpel and advance into the gloom](@node_explore)
- Choice: [Check backpack inventory](@node_inventory)

### Node: node_terminal
- Title: Corroded Terminal Display
- Content: The screen flickers with amber text: "WARNING: MAIN COOLANT PIPE RUPTURE IN SECTOR 7. HIGH TOXICITY DETECTED. AUTO-PURGE PROTOCOL STANDBY."
- Choice: [Purge nearest chemical valve (-10 Toxicity)](@node_purge)
- Choice: [Return to catwalk](@node_start)

### Node: node_purge
- Title: Valve Purge Successful
- Content: Steam hissed violently as pneumatic seals closed. Local toxicity levels dropped slightly, but hostile bio-constructs have picked up your signal.
- Choice: [Prepare for Combat!](@node_explore)

### Node: node_explore
- Title: Navigating the Corridors
- Content: Use the 3D ASCII viewport controls to navigate through the complex. Watch your toxicity levels on chemical tiles!
- Choice: [Open 3D Tactical Grid](@action_gameview)

### Node: node_inventory
- Title: Equipment & Survival Supplies
- Content: Manage your chems, weapons, and armor to survive the bio-hazards of Sector 7.
- Choice: [Return to main hub](@node_start)
