# CHEMOPUNK RPG: SECTOR 7 ACID SUMP

Welcome to **Sector 7 - Chemical Wasteland**, an underground bio-weapons lab and coolant repository compromised during the Great Rupture. Hazardous mutagenic sludge, cybernetic sentry drones, and mutated apex predators roam the corroded catwalks.

---

## GAME_CONFIG
- initial_floor: 1
- max_toxicity: 100
- starting_hp: 100
- starting_credits: 50
- starting_weapon: plasma_scalpel
- font_style: CRT_GREEN

---

## ITEM_DATABASE

### Item: anti_toxin
- Name: Anti-Toxin Serum
- Type: CONSUMABLE
- Value: 25
- Effect: Reduce Toxicity by 40, Heal 15 HP
- Description: Purified chelating agent that scrubs mutagenic isotopes from blood cells.

### Item: hyper_stim
- Name: Hyper-Stim Injector
- Type: CONSUMABLE
- Value: 40
- Effect: Heal 50 HP, Increase Attack Power by +5 for 3 turns
- Description: Adrenaline cocktail with military-grade neural accelerants.

### Item: plasma_scalpel
- Name: Overcharged Plasma Scalpel
- Type: WEAPON
- Damage: 22
- Description: High-energy thermal cutter that slices through reinforced hazmat plating.

### Item: chem_respirator
- Name: Mk.IV Chem-Respirator
- Type: ARMOR
- Defense: 10
- Description: Pressurized silicone respirator lined with activated charcoal filters.

### Item: hazmat_vest
- Name: Chem-Resistant Vest
- Type: ARMOR
- Defense: 14
- Description: Multi-layered polymer armor lined with lead foil and acid-repellent sealant.

### Item: keycard_alpha
- Name: Sector 7 Security Keycard
- Type: KEY_ITEM
- Value: 100
- Description: Encrypted magnetic keycard granting clearance to the central coolant override reactor.

---

## ENEMY_DATABASE

### Enemy: acid_slug
- Name: Mutated Acid Slug
- HP: 35
- MaxHP: 35
- Attack: 8
- Armor: 2
- ToxicityDamage: 8
- ASCII_Glyph: S
- ExpReward: 15
- Loot: anti_toxin

### Enemy: chem_drone
- Name: Chem-Sentry Drone v2
- HP: 55
- MaxHP: 55
- Attack: 15
- Armor: 6
- ToxicityDamage: 0
- ASCII_Glyph: D
- ExpReward: 35
- Loot: hyper_stim

### Enemy: bio_mutant
- Name: Cyber-Mutant Behemoth
- HP: 120
- MaxHP: 120
- Attack: 24
- Armor: 9
- ToxicityDamage: 15
- ASCII_Glyph: M
- ExpReward: 100
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
#.....#........#..E#
####################
```

Legend:
- `P`: Player Start Position
- `#`: Solid Bio-Steel Wall
- `.`: Walkable Steel Grating
- `S`: Mutated Acid Slug
- `D`: Chem-Sentry Drone
- `M`: Cyber-Mutant Boss
- `E`: Extraction Lift to Level 2

---

## STORY_NODES

### Node: start
- Title: Waking Up in Sector 7
- Speaker: BIOS VITAL MONITOR
- Mood: WARNING
- Category: STORY
- Content:
> [BIO-SUIT OS]: `CRITICAL ALERT: Environmental seal integrity at 88%. Atmospheric toxicity: ELEVATED.`

You regain consciousness on a corroded metal catwalk suspended above bubbling green effluent. Corroded pipes shudder overhead, venting plumes of pressurized sulfur dioxide. Your Geiger counter clicks in a rhythmic, unsettling cadence.

To your left, a spark-spitting terminal console hums. Ahead, the dark corridor extends toward the **Extraction Lift**.
- Choice: [Inspect the flickering terminal](@node_terminal)
- Choice: [Check survival gear & cyberware](@node_inventory)
- Choice: [Step onto the catwalk into the dungeon](@action_gameview)
- Choice: [Read survivor audio logs](@node_audio_logs)

### Node: node_terminal
- Title: Corroded Terminal Display
- Speaker: MAINFRAME SECTOR-7
- Mood: GLITCH
- Category: TERMINAL
- Content:
```
==================================================
SECTOR 7 MAINFRAME // FIRMWARE REV 4.19.88
STATUS: REACTOR COOLANT SEALS COMPROMISED
FACILITY EVACUATION: FAILED (249 DAYS AGO)
==================================================
```

The CRT phosphor screen flickers between amber and blood red. A prompt blinks:

> "UNAUTHORIZED OPERATOR DETECTED. EMERGENCY PURGE PROTOCOL IS ARMED. WARNING: MANUAL OVERRIDE WILL DISCHARGE LOCAL SLUDGE TANKS."
- Choice: [Initiate emergency valve purge (-15 Toxicity)](@node_purge)
- Choice: [Download facility maintenance schematics](@node_schematics)
- Choice: [Return to catwalk](@node_start)

### Node: node_purge
- Title: Valve Purge Successful
- Speaker: FACILITY ANNOUNCER
- Mood: HAZARD
- Category: STORY
- Content:
> [SYSTEM]: `PNEUMATIC SLUDGE PURGE COMPLETED. TOXICITY CONCENTRATION DILUTED BY 15%.`

High-pressure steam roars through the vents, flushing corrosive chemicals away from your immediate area. Your bio-suit HUD confirms your blood toxicity dropped!

However, the deafening alarm has alerted a pack of **Mutated Acid Slugs** prowling nearby corridors.
- Choice: [Equip weapon and enter combat grid](@action_gameview)
- Choice: [Return to main terminal hub](@node_start)

### Node: node_inventory
- Title: Cybernetic Rig & Storage
- Speaker: SUIT DIAGNOSTICS
- Mood: NORMAL
- Category: STORY
- Content:
### FIELD READINESS OVERVIEW

| System | Status | Integrity |
| --- | --- | --- |
| **Neural Chip** | Synchronized | `100%` |
| **Plasma Rail** | Charged | `100%` |
| **Bio-Respirator** | Active | `94%` |

Your inventory holds standard issue anti-toxin injectors and heavy armor plates. Keep your toxicity under **100%** to prevent organ liquefaction.
- Choice: [Return to Sector 7 hub](@node_start)
- Choice: [Engage tactical isometric view](@action_gameview)

### Node: node_schematics
- Title: Classified Schematics Downloaded
- Speaker: SYSTEM REPO
- Mood: CRYPTIC
- Category: ARCHIVE
- Content:
> `DOWNLOADING: /vault/docs/chem_behemoth_spec.dat`

The terminal dumps autopsy notes on **Subject #091 (Cyber-Mutant Behemoth)**:

1. **Thick Chitin Plating**: Negates standard kinetic rounds. Use **Plasma Scalpels** or high-temperature weapons.
2. **Morale Threshold**: When reduced below **30% HP**, the beast's synthetic adrenaline gland malfunctions, triggering a panicked retreat.
3. **Acidic Blood**: Deals lethal contact toxicity upon striking.
- Choice: [Return to terminal menu](@node_terminal)
- Choice: [Prepare for tactical combat](@action_gameview)

### Node: node_audio_logs
- Title: Intercepted Transmissions
- Speaker: COMMS RELAY
- Mood: NORMAL
- Category: AUDIO_LOG
- Content:
Several unencrypted survivor logs were found stored in the local cache. Listen to audio logs to learn more about the downfall of Sector 7.
- Choice: [Access Audio Logs Archive](@node_archive_menu)
- Choice: [Return to Hub](@node_start)

### Node: node_archive_menu
- Title: Vault Voice Archive
- Speaker: ARCHIVE INDEX
- Mood: NORMAL
- Category: AUDIO_LOG
- Content:
Select a transmission log to decode:
- Choice: [Dr. Vance: Initial Contamination Log](@node_vance_log)
- Choice: [Chief Ramos: Defense Perimeter Rupture](@node_ramos_log)
- Choice: [Return to Start](@node_start)

### Node: node_vance_log
- Title: Audio Log #01 - Dr. Vance
- Speaker: DR. ARLO VANCE (CHIEF RESEARCHER)
- Mood: HAZARD
- Category: AUDIO_LOG
- Content:
> [DR. VANCE]: *"Day 14 after containment breach. The acid isn't just corroding the bulkheads—it's mutating the bacteria inside the coolant loops. Yesterday, an maintenance slug crawled into the mutagen vat. This morning it was the size of a warhound and spitting hydrochloric acid. If anyone receives this... do NOT send rescue teams without Level 5 hazmat rigs."*
- Choice: [Back to Archive](@node_archive_menu)
- Choice: [Deploy into dungeon](@action_gameview)

### Node: node_ramos_log
- Title: Audio Log #04 - Chief Ramos
- Speaker: CHIEF RAMOS (VAULT SECURITY)
- Mood: WARNING
- Category: AUDIO_LOG
- Content:
> [CHIEF RAMOS]: *"All sentry drones have lost IFF recognition. Their neural targets are locked on anything emitting biological heat signatures. We are barricading the Sub-Level 3 lift. If you need to get through, use emergency flares to blind their optical sensors before striking."*
- Choice: [Back to Archive](@node_archive_menu)
- Choice: [Deploy into dungeon](@action_gameview)
