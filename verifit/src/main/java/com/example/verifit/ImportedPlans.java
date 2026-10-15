package com.example.verifit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registre des plans de seance deja importes (session du 15/10/2026, import idempotent).
 *
 * Un plan est reconnu par l'empreinte SHA-256 de son CONTENU seul (decision Romain du
 * 15/10/2026) : le meme plan renomme, ou regenere a l'identique par generate_workout.py
 * (nouvel horodatage dans le nom, meme contenu), est reconnu. Le nom du fichier est
 * garde pour l'affichage et pour l'accuse lu par le PC, jamais pour la comparaison.
 *
 * L'empreinte porte sur le texte "canonique" (voir canonical()) pour ne pas dependre de
 * la facon dont le fichier a ete lu : TextFiles.readAll() normalise les fins de ligne,
 * un JSON partage comme TEXTE (ACTION_SEND) ne passe pas par la. Meme calcul en Python
 * cote PC (voir docs/session-import-format.md) :
 *   t = open(p, encoding="utf-8-sig").read().replace("\r\n", "\n").replace("\r", "\n").rstrip("\n")
 *   hashlib.sha256(t.encode("utf-8")).hexdigest()
 *
 * Classe sans dependance Android (testee en JUnit). La persistance (SharedPreferences)
 * et l'ecriture de l'accuse sont dans ImportedPlansStore.
 */
public final class ImportedPlans
{
    // Au-dela, les plus anciennes entrees sont oubliees (un plan par jour environ : plus
    // d'un an d'historique). Un plan aussi vieux n'a de toute facon plus rien a faire dans
    // le dossier des plans.
    public static final int MAX_ENTRIES = 500;

    // Contenu de l'accuse Documents/FitEngine/fitengine_plan_imports.json. Pas de cle
    // "days" : sync_verifit_to_excel.py (looks_like_verifit_export) ignore donc ce
    // fichier, il ne peut pas etre pris pour l'export des seances.
    public static final String ACK_KIND = "fitengine-plan-imports";
    public static final int ACK_SCHEMA_VERSION = 1;

    // Une importation reussie. Champs publics serialises tels quels par Gson (noms = cles
    // JSON de l'accuse, contrat lu par le PC : ne pas les renommer).
    public static final class Entry
    {
        public String sha256 = "";
        public String fileName = "";      // vide si inconnu (JSON partage comme texte)
        public String sessionDate = "";   // jour qui a recu les series (AAAA-MM-JJ)
        public String comment = "";       // libelle du plan, ex. "Cycle A - Force"
        public String importedAt = "";    // AAAA-MM-JJTHH:MM:SS, heure du telephone
        public int setsImported;
        public boolean forced;            // reimport confirme malgre l'avertissement

        public Entry() {}

        public Entry(String sha256, String fileName, String sessionDate, String comment,
                     String importedAt, int setsImported, boolean forced)
        {
            this.sha256 = nonNull(sha256);
            this.fileName = nonNull(fileName);
            this.sessionDate = nonNull(sessionDate);
            this.comment = nonNull(comment);
            this.importedAt = nonNull(importedAt);
            this.setsImported = setsImported;
            this.forced = forced;
        }
    }

    // Forme du fichier d'accuse (et de la sauvegarde dans les preferences).
    static final class Ack
    {
        String kind = ACK_KIND;
        int schemaVersion = ACK_SCHEMA_VERSION;
        String updatedAt = "";
        List<Entry> imports = new ArrayList<Entry>();
    }

    // Plus recente en premier.
    private final ArrayList<Entry> entries;

    private ImportedPlans(List<Entry> entries)
    {
        this.entries = new ArrayList<Entry>();
        for (Entry entry : entries)
        {
            if (entry != null && entry.sha256 != null && !entry.sha256.isEmpty())
            {
                this.entries.add(entry);
            }
        }
    }

    public static ImportedPlans empty()
    {
        return new ImportedPlans(new ArrayList<Entry>());
    }

    // ---------------------------------------------------------------- empreinte

    // Texte sur lequel porte l'empreinte : BOM UTF-8 retire, fins de ligne en "\n",
    // sauts de ligne finaux retires. Rien d'autre (espaces et contenu inchanges).
    static String canonical(String text)
    {
        if (text == null)
        {
            return "";
        }
        String t = text;
        if (t.startsWith("﻿"))
        {
            t = t.substring(1);
        }
        t = t.replace("\r\n", "\n").replace('\r', '\n');
        int end = t.length();
        while (end > 0 && t.charAt(end - 1) == '\n')
        {
            end--;
        }
        return t.substring(0, end);
    }

    // SHA-256 du texte canonique, en hexadecimal minuscule (64 caracteres).
    public static String hash(String text)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical(text).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes)
            {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            // SHA-256 fait partie des algorithmes obligatoires de toute JVM / d'Android.
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------- registre

    // Derniere importation de ce plan (meme empreinte), ou null s'il n'a jamais ete importe.
    public Entry find(String sha256)
    {
        if (sha256 == null)
        {
            return null;
        }
        for (Entry entry : entries)
        {
            if (sha256.equals(entry.sha256))
            {
                return entry;
            }
        }
        return null;
    }

    // Ajoute une importation en tete (un reimport force ajoute une nouvelle entree : le
    // PC voit les deux). Garde au plus MAX_ENTRIES entrees.
    public void add(Entry entry)
    {
        if (entry == null || entry.sha256 == null || entry.sha256.isEmpty())
        {
            return;
        }
        entries.add(0, entry);
        while (entries.size() > MAX_ENTRIES)
        {
            entries.remove(entries.size() - 1);
        }
    }

    public List<Entry> getEntries()
    {
        return Collections.unmodifiableList(entries);
    }

    public int size()
    {
        return entries.size();
    }

    // ---------------------------------------------------------------- JSON

    // Meme JSON pour la sauvegarde locale et pour l'accuse. updatedAt : horodatage
    // passe par l'appelant (fixe dans les tests).
    public String toJson(String updatedAt)
    {
        Ack ack = new Ack();
        ack.updatedAt = nonNull(updatedAt);
        ack.imports = new ArrayList<Entry>(entries);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(ack);
    }

    // Relit toJson(). Texte vide, illisible ou d'un autre type : registre vide (jamais
    // d'exception, un registre perdu ne doit pas bloquer un import).
    public static ImportedPlans fromJson(String json)
    {
        if (json == null || json.trim().isEmpty())
        {
            return empty();
        }
        try
        {
            Ack ack = new Gson().fromJson(json, Ack.class);
            if (ack == null || !ACK_KIND.equals(ack.kind) || ack.imports == null)
            {
                return empty();
            }
            return new ImportedPlans(ack.imports);
        }
        catch (JsonSyntaxException | IllegalStateException | NumberFormatException e)
        {
            return empty();
        }
    }

    // ---------------------------------------------------------------- affichage

    // "15/10/2026 a 19:45" a partir de "2026-10-15T19:45:12" ; texte brut sinon.
    public static String formatImportedAt(String importedAt)
    {
        if (importedAt != null && importedAt.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*"))
        {
            return importedAt.substring(8, 10) + "/" + importedAt.substring(5, 7) + "/" + importedAt.substring(0, 4)
                    + " à " + importedAt.substring(11, 16);
        }
        return importedAt == null ? "" : importedAt;
    }

    // Phrase affichee avant un second import du meme plan.
    public static String describePreviousImport(Entry previous)
    {
        StringBuilder text = new StringBuilder();
        text.append("Ce plan a déjà été importé le ").append(formatImportedAt(previous.importedAt));
        if (!previous.sessionDate.isEmpty())
        {
            text.append(" (séance du ").append(previous.sessionDate).append(")");
        }
        text.append('.');
        if (!previous.fileName.isEmpty())
        {
            text.append("\nFichier : ").append(previous.fileName);
        }
        return text.toString();
    }

    private static String nonNull(String value)
    {
        return value == null ? "" : value;
    }
}
