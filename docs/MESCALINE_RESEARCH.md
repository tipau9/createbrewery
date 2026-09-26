# Meskalin (Peyote) — Recherche & Briefing für Claude Code

Dieses Dokument fasst die reale Pharmakologie, Phänomenologie, anthropologische Forschung (Huichol/Wixárika) und Literatur (Aldous Huxley, Heinrich Klüver) zu Meskalin/Peyote zusammen, inklusive einer kritischen Analyse bisheriger Ansätze und sauberer Design-Empfehlungen für die Minecraft-Mod *Create Brewery*.

---

## 1. Reale Pharmakologie & Trip-Verlauf

### Wirkstoff
* **Meskalin** (3,4,5-Trimethoxyphenethylamin), enthalten im Peyote-Kaktus (*Lophophora williamsii*) und San Pedro (*Echinopsis pachanoi*).
* Chemische Klasse: **Phenethylamin** (anders als LSD und Psilocybin, die Tryptamine sind). Trotzdem klassischer 5-HT2A-Agonist mit starker Kreuztoleranz zu LSD/Shrooms.

### Phasen & Dauer (10–14 Stunden)
1. **0:00 – 1:30 (Come-up & Body Load):**
   * Extrem bitterer, erdiger Geschmack.
   * Starker Magenreiz durch Begleitalkaloide. Übelkeit, Zittern, Schweregefühl im Körper.
   * **Höhepunkt: „La Purga“ (Die Reinigung):** Fast alle Konsumenten erbrechen sich einmalig. Im rituellen Kontext ist dies kein Vergiftungsunfall, sondern eine spirituelle Katharsis. Unmittelbar nach dem Erbrechen weicht alle Schwere, der Kopf wird glasklar, und eine Welle von Wärme, Entlastung und Euphorie durchströmt den Körper.
2. **1:30 – 4:00 (Plateau & visuelle Entfaltung):**
   * Die Welt wird farbintensiv, warm und golden.
   * Dinge beginnen von innen heraus zu leuchten.
   * Klüversche Formkonstanten werden sichtbar, besonders bei geschlossenen Augen oder im Schatten.
3. **4:00 – 8:00 (Peak & Kontemplation):**
   * **Aldous Huxleys „Istigkeit“:** Alltägliche Objekte (ein Holzblock, Stofffalten, Sand) werden unendlich faszinierend. Kein Drang zu handeln, zu bauen oder zu rennen.
   * Tiefes Einheitsgefühl mit der Natur und den Ahnen.
   * Zeitlosigkeit: Minuten fühlen sich an wie Ewigkeiten, aber ohne Panik (wie bei einem Bad Trip), sondern in gelassener Ehrfurcht.
4. **8:00 – 14:00+ (Comedown & Afterglow):**
   * Sanftes, langes Ausklingen. Warme Sonnenuntergänge, tiefer innerer Frieden, keine Katerstimmung.

---

## 2. Die Kernquellen & Phänomene

### A. Aldous Huxley — *The Doors of Perception* (1954)
* **Istigkeit (Is-ness):** Der Geist filtert die Realität normalerweise auf „Nutzen“ (Kann ich das essen? Droht Gefahr?). Meskalin schaltet diesen Reduktionsfilter ab. Dinge existieren einfach in ihrer vollkommenen, reinen Pracht.
* **Kein Verlangen nach Aktion:** Huxley beschreibt, dass er während des Trips unfähig war, Interesse an Raum, Zeit oder zielgerichteter Handlung aufzubringen. Jede Bewegung erschien lächerlich überflüssig, weil das Hier und Jetzt bereits absolut vollständig war.
* **Optik:** Keine wild morphenden Monster oder zuckenden Farbblitze wie bei LSD. Stattdessen atemberaubende Farbintensität, Tiefe, Glanz, lebendige Texturen und Faltenwürfe.

### B. Heinrich Klüver — Formkonstanten (1926/1966)
Klüver erforschte die visuellen Halluzinationen von Meskalin wissenschaftlich und definierte vier universelle Formkonstanten:
1. **Gitter, Waben, Hexagone und Mosaike** (Chachbrettmuster, Maschendraht).
2. **Spinnennetze und Fächer.**
3. **Tunnel, Trichter, Kegel und Spiralen.**
4. **Kirchenfenster / Bleiglas:** Leuchtende, facettierte Farbflächen (tiefes Rubinrot, Lapislazuliblau, Smaragdgrün, Gold), die in der Dunkelheit oder bei geschlossenen Augen wie gotische Kirchenfenster erstrahlen.

### C. Wixárika (Huichol) Zeremonie & Tatewari
* **Tatewari (Großvater Feuer):** Das rituelle Zentrum. Die Teilnehmer sitzen die ganze Nacht um ein heiliges Holzfeuer in der Halbwüste von Wirikuta. Das Feuer spendet Orientierung, Schutz vor bösen Geistern und vermittelt Visionen.
* **Geisttiere:** Der Blaue Hirsch (*Kauyumari*), Puma, Adler. Tiere verhalten sich nicht wie Beute oder Feinde, sondern wie weise Wächter, die den Suchenden stumm ansehen.
* **Akustik:** Eintönige, beruhigende Rhythmen – die *Tepu* (Wassertrommel) und Rasselklänge. Absolut keine hektische Musik, keine Synästhesie-Tonleitern.

---

## 3. Review: Warum bisherige Ansätze unsauber wirkten

1. **Sprint-Abbruch via `player.setSprinting(false)`:**
   * *Problem:* Das harte Zurücksetzen im Tick-Loop fühlt sich an wie ein Tastatur-/Input-Lag oder Glitch, nicht wie natürliche Trance/Entschleunigung.
   * *Saubere Alternative:* Sanfte Anpassung von `generic.movement_speed` (z.B. subtiler Slowness- oder Trägheitseffekt) oder FOV-Glättung, kombiniert mit beruhigenden visuellen Reizen beim Stehenbleiben.
2. **Hardcoded Shader-Muster (`drunk.fsh`):**
   * *Problem:* Ein einfaches Hex-Grid mit `smoothstep` im Fragment-Shader ohne Antialiasing wirkt bei wechselnden Bildschirmauflösungen oft pixelig oder wie ein Rendering-Bug.
   * *Saubere Alternative:* Organische Überlagerung mit weichem Noise, dezentes Mosaik-Verschieben von Dunkelwerten, warme Kantenüberstrahlung (Edge Glow / Istigkeit) statt harter Overlays.
3. **Flaches Farb-Rechteck (`g.fill` in `onGui`):**
   * *Problem:* Ein harter Vollbild-Farbrechteck-Flash (`purgaFlash`) wirkt billig und überdeckt das UI unschön.
   * *Saubere Alternative:* Shader-basierte Farbverschiebung oder dezente Vignette / Partikelwirkung, die das GUI nicht blockiert.
4. **Partikel-Spamming auf Blöcken:**
   * *Problem:* `ColorParticleOption` direkt auf anvisierten Blöcken kann wie Trankpartikel-Rauschen aussehen.
   * *Saubere Alternative:* Sehr langsame, feine, schwebende Schimmerpartikel (wie Wüstenstaub / goldene Sporen) oder Shader-basierte Konturen-Hervorhebung.

---

## 4. Empfohlene saubere Architektur für Claude Code

| Bereich | Empfohlene saubere Umsetzung |
|---|---|
| **La Purga** | Beim Erbrechen: Partikeleffekt am Kopf, Entfernung aller negativen Effekte, kurzer warmer Blur/Bloom (Shader-Fade statt GUI-Fill), Verleihung von `ABSORPTION` + warmer Text im Chat/Actionbar. |
| **Istigkeit (Shader)** | Kanten-Erkennung (Sobel oder Normal-Differenz), die anvisierte oder nahe Blöcke mit einer warmen, sanft pulsierenden Gold-Aura umgibt. Texturen erhalten mehr Farbtiefe (Saturation & Micro-Contrast) statt flachem Farbfilter. |
| **Klüver-Muster** | Nur in tiefen Schatten / bei sehr geringer Helligkeit (`lum < 0.15`): Organisches, sanft rotierendes/pulsierendes Mosaik mit weichen Kanten, das an Kirchenfenster erinnert. |
| **Tatewari (Feuer)** | Wenn der Spieler nahe an einem Campfire steht und ruhig blickt: Weiche, warme Holzkohle-Vignette, sanftes Glutfunken-Partikelsystem, Besänftigung von Mobs via saubere Server-Logik. |
| **Entschleunigung** | Keine harten Tasten-Abbrüche. Stattdessen: Spieler belohnen fürs Verweilen (z.B. Visionen intensivieren sich beim Stillstehen), FOV sanft verengen, Bewegungsträgheit erhöhen. |
| **Sound** | Tiefer, sanfter Perkussions-Sound (Wassertrommel, Wind, leises Holzknistern) in diskreten Intervallen über `mc.getSoundManager().play(...)`. |
