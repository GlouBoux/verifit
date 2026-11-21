package com.example.verifit;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lecture et ecriture des donnees du moteur dans l'app (lot M3.1, 17/11/2026).
 *
 * Java est le seul gardien de ces fichiers (decision 1 du lot M3) : Python recoit leur
 * texte et rend du texte. Le stockage concret (dossier prive moteur/, ecriture atomique par
 * AtomicFile) est dans EngineFilesAndroid ; cette classe ne voit que l'interface Files, ce
 * qui la rend testable en JUnit.
 *
 * Chargement (meme logique que StorageCheck du lot S1) :
 * - rien du tout (ni etat.json ni aucun fichier) : EMPTY, aucun import fait ;
 * - fichier illisible (JSON invalide, ligne coupee, valeur non finie) : BLOCKED ;
 * - fichier absent ou vide alors que etat.json annonce du contenu : BLOCKED ;
 * - sinon OK, et etat.json est recale s'il manque ou si ses compteurs different (arret
 *   brutal entre l'ecriture d'un fichier et celle de l'etat).
 * BLOCKED n'efface jamais rien et n'ecrit rien : seuls un nouvel import ou la
 * restauration d'un backup qui contient le moteur remplacent les fichiers.
 */
public final class EngineStorage
{
    // Acces aux fichiers du dossier moteur/.
    public interface Files
    {
        // Texte du fichier (UTF-8), ou null s'il n'existe pas.
        String read(String name) throws IOException;

        // Remplace le fichier entier, de facon atomique (jamais de fichier a moitie ecrit).
        void write(String name, String text) throws IOException;
    }

    public enum Status
    {
        EMPTY,
        OK,
        BLOCKED
    }

    public static final class Loaded
    {
        public final Status status;
        public final EngineContent content;          // non null si OK
        public final EngineState state;              // non null si OK
        public final String problem;                 // texte lisible si BLOCKED
        public final Map<String, String> unreadable; // fichier -> texte brut illisible (copie de diagnostic)
        public final boolean stateRewritten;

        Loaded(Status status, EngineContent content, EngineState state, String problem,
               Map<String, String> unreadable, boolean stateRewritten)
        {
            this.status = status;
            this.content = content;
            this.state = state;
            this.problem = problem;
            this.unreadable = Collections.unmodifiableMap(unreadable);
            this.stateRewritten = stateRewritten;
        }
    }

    private final Files files;

    public EngineStorage(Files files)
    {
        this.files = files;
    }

    public Loaded load()
    {
        LinkedHashMap<String, String> unreadable = new LinkedHashMap<String, String>();
        StringBuilder problem = new StringBuilder();

        String stateText = readQuietly(EngineState.FILE, problem);
        EngineState state = null;
        boolean stateExists = stateText != null;
        if (stateText != null)
        {
            try
            {
                state = EngineState.parse(stateText);
            }
            catch (IllegalArgumentException e)
            {
                // Etat illisible : les compteurs sont perdus, mais les fichiers de contenu
                // font foi s'ils se lisent. L'etat sera reecrit.
                state = null;
            }
        }

        LinkedHashMap<String, String> texts = new LinkedHashMap<String, String>();
        boolean anyContent = false;
        for (String name : EngineContent.FILES)
        {
            String text = readQuietly(name, problem);
            if (text != null)
            {
                anyContent = true;
                texts.put(name, text);
            }
        }
        if (!stateExists && !anyContent && problem.length() == 0)
        {
            return new Loaded(Status.EMPTY, null, null, null, unreadable, false);
        }

        EngineContent content = null;
        if (problem.length() == 0)
        {
            try
            {
                content = EngineContent.of(texts);
            }
            catch (IllegalArgumentException e)
            {
                content = null;
            }
        }

        if (content == null)
        {
            // Recherche fichier par fichier pour dire exactement ce qui ne va pas.
            for (String name : EngineContent.FILES)
            {
                String text = texts.get(name);
                if (text == null && problem.indexOf("- " + name + " : lecture impossible") >= 0)
                {
                    continue;
                }
                if (text == null)
                {
                    if (!EngineContent.JOURNALS.contains(name) || (state != null && state.count(name) > 0))
                    {
                        problem.append("- ").append(name).append(" : absent\n");
                    }
                    continue;
                }
                try
                {
                    EngineContent.validate(name, text);
                }
                catch (IllegalArgumentException e)
                {
                    problem.append("- ").append(e.getMessage()).append('\n');
                    unreadable.put(name, text);
                }
            }
            if (problem.length() == 0)
            {
                problem.append("- données incohérentes\n");
            }
            return new Loaded(Status.BLOCKED, null, null, problem.toString(), unreadable, false);
        }

        if (state != null)
        {
            for (String name : EngineContent.FILES)
            {
                if (content.count(name) == 0 && state.count(name) > 0)
                {
                    problem.append("- ").append(name).append(" : vide, ").append(state.count(name))
                            .append(" élément(s) attendu(s)\n");
                }
            }
            if (problem.length() > 0)
            {
                return new Loaded(Status.BLOCKED, null, null, problem.toString(), unreadable, false);
            }
        }

        boolean rewrite = state == null || !state.counts().equals(content.counts());
        EngineState current = state == null
                ? new EngineState(null, "recalage", null, content.counts())
                : state.withCounts(content.counts());
        if (rewrite)
        {
            try
            {
                files.write(EngineState.FILE, current.toJson());
            }
            catch (IOException e)
            {
                rewrite = false; // donnees lisibles quand meme ; nouvel essai au prochain chargement
            }
        }
        return new Loaded(Status.OK, content, current, null, unreadable, rewrite);
    }

    // Remplace TOUS les fichiers de contenu, puis l'etat. Les fichiers sont ecrits un par un
    // (chacun atomiquement) : un arret au milieu laisse un melange que le prochain chargement
    // detecte s'il manque du contenu, et qu'un nouvel import repare.
    public EngineState replaceAll(EngineContent content, String importedAt, String source, String exportedAt) throws IOException
    {
        EngineState state = new EngineState(importedAt, source, exportedAt, content.counts());
        writeAll(content, state);
        return state;
    }

    // Meme chose en gardant l'origine du dernier import (renommage, M3.5).
    public EngineState rewrite(EngineContent content, EngineState previous) throws IOException
    {
        EngineState state = previous.withCounts(content.counts());
        writeAll(content, state);
        return state;
    }

    private void writeAll(EngineContent content, EngineState state) throws IOException
    {
        for (String name : EngineContent.WRITE_ORDER)
        {
            files.write(name, content.text(name));
        }
        files.write(EngineState.FILE, state.toJson());
    }

    private String readQuietly(String name, StringBuilder problem)
    {
        try
        {
            return files.read(name);
        }
        catch (IOException | RuntimeException e)
        {
            problem.append("- ").append(name).append(" : lecture impossible (").append(e.getMessage()).append(")\n");
            return null;
        }
    }
}
