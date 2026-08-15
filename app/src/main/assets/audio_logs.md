# RECOVERED SURVIVOR AUDIO LOGS // SECTOR 7

Unencrypted voice recordings and encrypted distress beacons retrieved from the comms array.

---

## AUDIO_LOGS

### Log: log_vance_01
- Title: Dr. Vance - Day 1 Containment Breach
- Speaker: DR. ARLO VANCE (BIO-CHEMS)
- Mood: HAZARD
- Category: AUDIO_LOG
- Content:
> [DR. VANCE]: *"Recording on emergency frequency 88.4 MHz. The pressure relief valve on Vat #3 blew thirty minutes ago. Liquid green mutagen is currently flooding corridor B-4. All personnel are instructed to seal their hazmat helmets and proceed to the Extraction Lift."*

The recording is interrupted by a high-pitched siren and metallic screeching in the background.

```
[AUDIO WAVEFORM ANALYSIS]
FREQUENCY: 44.1 kHz // SAMPLE BIT DEPTH: 16-BIT
BACKGROUND NOISE: HYDRAULIC ALARM (120 dB) + DISTANT ROAR
```
- Choice: [Listen to Log #02: Drone Malfunction](@log_vance_02)
- Choice: [Return to Comms Menu](@log_index)

### Log: log_vance_02
- Title: Dr. Vance - Day 9 Synthetic Mutation
- Speaker: DR. ARLO VANCE (BIO-CHEMS)
- Mood: WARNING
- Category: AUDIO_LOG
- Content:
> [DR. VANCE]: *"The mutagen isn't just organic... it's assimilating the silicon circuitry in our sentry drones. The drones aren't patrolling anymore; they're nesting near the coolant exhausts. If you encounter a **Chem-Sentry Drone**, do not attempt wireless handshake. Shoot on sight."*
- Choice: [Listen to Chief Ramos Log](@log_ramos_01)
- Choice: [Listen to Engineer Miller Log](@log_miller_01)
- Choice: [Return to Comms Menu](@log_index)

### Log: log_ramos_01
- Title: Chief Ramos - Final Stand at Sub-Level 2
- Speaker: CHIEF RAMOS (VAULT SECURITY)
- Mood: HAZARD
- Category: AUDIO_LOG
- Content:
> [CHIEF RAMOS]: *"We made our stand at the blast doors. The Behemoth came through the ferro-concrete like wet cardboard. Standard 9mm kinetic rounds ricocheted right off its carapace. The only thing that slowed it down was high-energy **Plasma Scalpels** aimed at its dorsal power core."*

> *"To whoever finds this rig: grab my plasma blade from the locker and make for the extraction lift. Don't look back."*
- Choice: [Listen to Miller's Audio Log](@log_miller_01)
- Choice: [Return to Comms Menu](@log_index)

### Log: log_miller_01
- Title: Engineer Miller - Sludge Reactor Override
- Speaker: ENGINEER SAM MILLER
- Mood: CRYPTIC
- Category: AUDIO_LOG
- Content:
> [MILLER]: *"I managed to jury-rig the pneumatic purge valve in the junction room. Activating the purge drops toxicity in the immediate sector by **15-20%**, but vents boiling acidic steam into adjacent ducts."*

> *"Always carry at least two injectors of **Anti-Toxin Serum** before entering the lower sump. If toxicity exceeds 100%, systemic organ failure is instantaneous."*
- Choice: [Return to Comms Menu](@log_index)
- Choice: [Listen to Dr. Vance Logs](@log_vance_01)

### Log: log_index
- Title: Audio Logs Index
- Speaker: TERMINAL LOG DECODER
- Mood: NORMAL
- Category: AUDIO_LOG
- Content:
Select a decrypted recording from the index below:

- **Log #01**: Dr. Vance - Day 1 Containment Breach
- **Log #02**: Dr. Vance - Day 9 Synthetic Mutation
- **Log #03**: Chief Ramos - Final Stand at Sub-Level 2
- **Log #04**: Engineer Miller - Sludge Reactor Override
- Choice: [Dr. Vance Log #01](@log_vance_01)
- Choice: [Dr. Vance Log #02](@log_vance_02)
- Choice: [Chief Ramos Log](@log_ramos_01)
- Choice: [Engineer Miller Log](@log_miller_01)
