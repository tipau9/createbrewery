package com.createbrewery.drugs;

import com.createbrewery.CreateBrewery;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What JEI shows on each drug's info page (compat/jei): effect, how long, risks, dangerous mixes
 * and first aid, in the game's numbers. Item path to text; the lang keys are
 * {@code jei.createbrewery.info.<path>}.
 */
public final class DrugInfo {
    private DrugInfo() {}

    private static final String HELP = "\n\nFirst Aid: Unconscious, heart attack, or choking on vomit - "
        + "crouch directly beside the victim (Sneak): CPR and recovery position keep airways clear.";

    public static final Map<String, String> TEXT = new LinkedHashMap<>();

    static {
        TEXT.put("drug_test_kit", "Drug Test Kit: Hold drug in off-hand, then use. Reveals batch potency "
            + "and tests for fentanyl. Street supply is never what it promises: coke is usually cut, pills vary from "
            + "weak to dangerously strong, mushrooms vary with nature. Every stack is its own batch."
            + "\n\nVery strong: take half. Fentanyl: do not take - a single dose can stop breathing.");
        TEXT.put("koks", "Cocaine (snort line): alert, fast, talkative, sharp and bright. About 3 minutes."
            + "\n\nRisks: tachycardia, heart attack, nosebleed, compulsive redosing; skin picking after 3 lines. "
            + "Each line feels weaker but strains the heart just as hard."
            + "\n\nDangerous with: alcohol (cocaethylene: longer, harder on the heart), heroin (speedball), ketamine (CK-mix), meth." + HELP);
        TEXT.put("keta", "Ketamine (snort line): cotton head, world drifts away, numb body. About 2 minutes."
            + "\n\nRisks: K-hole after 3 lines - paralyzed, dissociated. Long-term bladder damage."
            + "\n\nDangerous with: alcohol (blackout), cannabis (K-hole after 2 lines), opioids, and Xanax (breathing)." + HELP);
        TEXT.put("joint", "Joint (six drags): relaxed, giggles, munchies, warm green glow. About 4 minutes."
            + "\n\nRisks: cottonmouth, greening out from too much (collapse, vomiting), paranoia."
            + "\n\nDangerous with: alcohol before (greening out), psychedelics (amplifies intensity, bad trips), ketamine." + HELP);
        TEXT.put("lsd", "LSD (blotter): slow onset, then geometric patterns, breathing walls, color enhancement, trails. About 12 minutes."
            + "\n\nRisks: bad trip in dark or loud environments, flashbacks, 24h psychedelic tolerance."
            + "\n\nDangerous with: cannabis (bad trip), nitrous oxide (peak storming), MDMA (candyflip: warmer, stronger)." + HELP);
        TEXT.put("magic_mushroom", "Magic Mushrooms (chew): like LSD, but organic, earthy and in waves; stomach nausea. About 6 minutes."
            + "\n\nRisks: nausea (worse on full stomach), bad trip, silent darkness during heroic doses."
            + "\n\nDangerous with: cannabis, nitrous, MDMA - similar to LSD." + HELP);
        TEXT.put("peyote", "Peyote (chew button, bitter): desert colors, spirit guide, long calm journey. About 12 minutes."
            + "\n\nRisks: heavy nausea/purging during onset, bad trip."
            + "\n\nDangerous with: cannabis, nitrous - similar to LSD." + HELP);
        TEXT.put("dmt", "DMT (one deep hit): breakthrough in seconds - waiting room, entities, the beyond, return. Under one minute."
            + "\n\nRisks: completely out of body, physical form left defenseless. Sit down, have a trip sitter."
            + "\n\nDangerous with: anything that strains cardiovascular system." + HELP);
        TEXT.put("mdma", "MDMA (pill): warmth, empathy, music moves through body, euphoria, dancing. About 6 minutes, followed by comedown."
            + " Takes longer after food, faster on empty stomach. Pill strength varies wildly - test, split strong pills."
            + "\n\nRisks: overheating from dancing, jaw clenching; too much plain water = hyponatremia. "
            + "Take breaks, find shade, drink electrolytes instead of plain water."
            + "\n\nDangerous with: alcohol (dehydration: overheats much faster), cocaine, meth." + HELP);
        TEXT.put("meth", "Crystal Meth (snort/smoke): hyper-focused, driven, lasts for hours. About 12 minutes."
            + "\n\nRisks: cardiac strain, hyperthermia, sleep-deprivation psychosis - shadows, voices, delusions. "
            + "Only sleep helps."
            + "\n\nDangerous with: heroin (speedball), cocaine, MDMA, alcohol." + HELP);
        TEXT.put("heroin", "Heroin (syringe): warm rush, nodding off, dreamscape. About 4 minutes."
            + "\n\nRisks: respiratory depression, rapid tolerance and dependence (withdrawal), seizures."
            + "\n\nDangerous with: alcohol and Xanax (fatal respiratory arrest), cocaine or meth (speedball: upper masks overdose)."
            + "\n\nStreet heroin may contain fentanyl - test first with Drug Test Kit. Naloxone reverses overdoses."
            + "\n\nNaloxone saves lives: right-click on the victim." + HELP);
        TEXT.put("xanax", "Xanax (pill): calm, anxiety erased, sensory dampening. About 5 minutes, followed by rebound anxiety."
            + "\n\nFake black market bars often contain fentanyl - test first."
            + "\n\nRisks: rebound panic, seizures upon abrupt cessation after tolerance develops."
            + "\n\nDangerous with: heroin and alcohol (breathing)." + HELP);
        TEXT.put("lachgas_balloon", "Nitrous Oxide (balloon): auditory flange (wah-wah), laughing, dissociation for seconds. About 15 seconds."
            + "\n\nRisks: chained balloons cause hypoxia, blackouts, falling injuries."
            + "\n\nDangerous with: psychedelics (peak storming), near cliffs or edges." + HELP);
        TEXT.put("space_brownie", "Space Brownie: Edible cannabis - delayed onset, hits hard and lasts long. Do not redose early thinking it failed."
            + "\n\nRisks: intense greening out, panic, nausea." + HELP);
        TEXT.put("naloxon", "Naloxone (nasal spray): Antidote for opioid overdoses. Right-click on unconscious victim - "
            + "cannot self-administer if not breathing. Lasts about 1 minute; "
            + "heroin remains in system and can re-depress breathing." + HELP);
        TEXT.put("electrolyte_drink", "Electrolyte Drink: Salts and sugars. Counteracts hyponatremia and overheating on MDMA, "
            + "quenches thirst (with Tough As Nails).");
    }

    public static String key(String path) {
        return "jei." + CreateBrewery.MOD_ID + ".info." + path;
    }

    public static void addLang() {
        TEXT.forEach((path, text) -> CreateBrewery.REGISTRATE.addRawLang(key(path), text));
    }
}
