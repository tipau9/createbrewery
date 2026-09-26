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

    private static final String HELP = "\n\nErste Hilfe: Bewusstlos, Herzinfarkt oder Erbrochenes in den Atemwegen - "
        + "hock dich direkt neben die Person (Schleichen): Herzdruckmassage und stabile Seitenlage.";

    public static final Map<String, String> TEXT = new LinkedHashMap<>();

    static {
        TEXT.put("drug_test_kit", "Drogentest-Kit: Droge in die andere Hand, dann benutzen. Zeigt, wie stark diese Charge ist "
            + "und ob Fentanyl drin ist. Straßenware ist nie, was sie verspricht: Koks meist gestreckt, Pillen von schwach bis "
            + "gefährlich stark, Pilze so verschieden wie die Natur. Jeder Stapel ist eine eigene Charge."
            + "\n\nSehr stark: nur die Hälfte nehmen. Fentanyl: gar nicht - schon einmal kann die Atmung stoppen.");
        TEXT.put("koks", "Koks (Line ziehen): wach, schnell, redselig, alles scharf und hell. Etwa 3 Minuten."
            + "\n\nRisiko: Herzrasen bis Herzinfarkt, Nasenbluten, Gier nach der nächsten Line; ab der dritten Käfer unter der Haut. "
            + "Jede Line wirkt weniger, belastet das Herz aber voll."
            + "\n\nGefährlich mit: Alkohol (Kokaethylen: länger, härter fürs Herz), Heroin (Speedball), Keta (CK-Mix), Meth." + HELP);
        TEXT.put("keta", "Keta (Line ziehen): Watte im Kopf, die Welt rückt weg, Körper taub. Etwa 2 Minuten."
            + "\n\nRisiko: ab der dritten Line das K-Loch - bewegungsunfähig, ausgeklinkt. Blase leidet auf Dauer."
            + "\n\nGefährlich mit: Alkohol (Filmriss), Weed (K-Loch schon ab der zweiten), Opioiden und Xanax (Atmung)." + HELP);
        TEXT.put("joint", "Joint (sechs Züge): entspannt, alles lustig, Hunger, grüne warme Welt. Etwa 4 Minuten."
            + "\n\nRisiko: Pappmaul, zu viel auf einmal = Greening out (Kreislauf, Kotzen), Paranoia."
            + "\n\nGefährlich mit: Alkohol vorher (Greening out), Trips (stärker, öfter Horror), Keta." + HELP);
        TEXT.put("lsd", "LSD (Pappe): langsames Anfluten, dann Muster, atmende Wände, Farben, Nachbilder. Etwa 12 Minuten."
            + "\n\nRisiko: Horrortrip in dunkler oder lauter Umgebung, Flashbacks, Toleranz für einen Tag."
            + "\n\nGefährlich mit: Weed (Horror), Lachgas (Gipfelsturm), MDMA (Candyflip: wärmer, stärker)." + HELP);
        TEXT.put("magic_mushroom", "Pilze (kauen): wie LSD, aber organisch und in Wellen; der Magen dreht. Etwa 6 Minuten."
            + "\n\nRisiko: Übelkeit (mehr auf vollen Magen), Horrortrip, bei heroischer Dosis stille Dunkelheit."
            + "\n\nGefährlich mit: Weed, Lachgas, MDMA - wie LSD." + HELP);
        TEXT.put("peyote", "Peyote (kauen, bitter): Wüstenfarben, Geisttier, langer ruhiger Trip. Etwa 12 Minuten."
            + "\n\nRisiko: heftiges Kotzen beim Anfluten, Horrortrip."
            + "\n\nGefährlich mit: Weed, Lachgas - wie LSD." + HELP);
        TEXT.put("dmt", "DMT (ein tiefer Zug): Durchbruch in Sekunden - Wartezimmer, Wesen, das Jenseits, Rückkehr. Unter einer Minute."
            + "\n\nRisiko: völlig weg, der Körper steht ungeschützt herum. Hinsetzen, jemand passt auf."
            + "\n\nGefährlich mit: allem, was das Herz belastet." + HELP);
        TEXT.put("mdma", "MDMA (Pille): Wärme, Nähe, Musik geht durch den Körper, Tanzen. Etwa 6 Minuten, dann der Absturz."
            + " Nach dem Essen kommt sie später, auf leeren Magen früher. Pillen schwanken stark - testen, bei starken halbieren."
            + "\n\nRisiko: Überhitzung beim Tanzen, Kiefer mahlt; zu viel Wasser = Wasservergiftung. "
            + "Pausen, Schatten, Elektrolyt-Getränk statt literweise Wasser."
            + "\n\nGefährlich mit: Alkohol (Ausgetrocknet: überhitzt viel schneller), Koks, Meth." + HELP);
        TEXT.put("meth", "Crystal (ziehen): wach, getrieben, stundenlang. Etwa 12 Minuten."
            + "\n\nRisiko: Herz, Überhitzung, ohne Schlaf die Psychose - Schatten, Stimmen, Käfer, Schritte hinter dir. "
            + "Nur Schlaf hilft."
            + "\n\nGefährlich mit: Heroin (Speedball), Koks, MDMA, Alkohol." + HELP);
        TEXT.put("heroin", "Heroin (Spritze): warme Flut, dann Wegnicken, Traum. Etwa 4 Minuten."
            + "\n\nRisiko: Atemlähmung, Gewöhnung (jeder Schuss wirkt schwächer, Entzug), Krampfanfall."
            + "\n\nGefährlich mit: Alkohol und Xanax (Atmung setzt aus), Koks oder Crystal (Speedball: das Aufputschen verdeckt die Überdosis)."
            + "\n\nStraßenheroin kann Fentanyl enthalten - vorher testen (Drogentest-Kit). Naloxon wirkt auch dagegen."
            + "\n\nNaloxon rettet: Rechtsklick auf die Person." + HELP);
        TEXT.put("xanax", "Xanax (Pille): ruhig, weich, die Angst ist weg, alles unscharf. Etwa 5 Minuten, danach unruhiger als vorher."
            + "\n\nGefälschte Bars vom Schwarzmarkt enthalten manchmal Fentanyl - testen."
            + "\n\nRisiko: Rebound-Angst, Krampfanfall beim Absetzen nach Gewöhnung."
            + "\n\nGefährlich mit: Heroin und Alkohol (Atmung)." + HELP);
        TEXT.put("lachgas_balloon", "Lachgas (Ballon): Wah-Wah im Kopf, Lachen, ein paar Sekunden weg. Etwa 15 Sekunden."
            + "\n\nRisiko: viele Ballons hintereinander - Sauerstoffmangel, Filmriss, Sturz."
            + "\n\nGefährlich mit: Trips (Gipfelsturm), im Stehen an Kanten." + HELP);
        TEXT.put("space_brownie", "Space Brownie: Weed zum Essen - kommt erst spät, dann stark und lange. Nicht nachlegen, weil nichts passiert."
            + "\n\nRisiko: Greening out, Paranoia." + HELP);
        TEXT.put("naloxon", "Naloxon (Nasenspray): Gegenmittel bei Opioid-Überdosis. Rechtsklick auf die bewusstlose Person - "
            + "man kann es nicht bei sich selbst benutzen, wenn man nicht mehr atmet. Wirkt etwa 1 Minute; "
            + "das Heroin ist danach noch da und kann zurückkommen." + HELP);
        TEXT.put("electrolyte_drink", "Elektrolyt-Getränk: Salz und Zucker. Gegen Wasservergiftung und Überhitzung auf MDMA, "
            + "löscht auch den Durst (mit Tough As Nails).");
    }

    public static String key(String path) {
        return "jei." + CreateBrewery.MOD_ID + ".info." + path;
    }

    public static void addLang() {
        TEXT.forEach((path, text) -> CreateBrewery.REGISTRATE.addRawLang(key(path), text));
    }
}
