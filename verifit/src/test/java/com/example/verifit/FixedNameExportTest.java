package com.example.verifit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class FixedNameExportTest
{
    private static final String NAME = "verifit_coaching_latest.json";

    // Faux stockage : liste de fichiers (nom + contenu). "invisibles" = fichiers d'une
    // autre installation : ils occupent le nom mais find() ne les voit pas et on ne peut
    // pas les ouvrir ; un create() du meme nom est renomme "(1)" comme MediaStore.
    private static class FakeStore implements FixedNameExport.Store<FakeStore.F>
    {
        static class F
        {
            String name;
            byte[] content = new byte[0];
            boolean foreign;
            boolean orphan;
        }

        final List<F> files = new ArrayList<F>();
        boolean createReturnsNull;

        F add(String name, boolean foreign, boolean orphan, String content)
        {
            F f = new F();
            f.name = name;
            f.foreign = foreign;
            f.orphan = orphan;
            f.content = content.getBytes();
            files.add(f);
            return f;
        }

        @Override
        public F find(String name)
        {
            for (F f : files)
            {
                if (!f.foreign && f.name.equals(name))
                {
                    return f;
                }
            }
            return null;
        }

        @Override
        public F create(String name) throws IOException
        {
            if (createReturnsNull)
            {
                return null;
            }
            String actual = name;
            int n = 1;
            boolean taken = true;
            while (taken)
            {
                taken = false;
                for (F f : files)
                {
                    if (f.name.equals(actual))
                    {
                        taken = true;
                    }
                }
                if (taken)
                {
                    actual = name.replace(".json", " (" + n++ + ").json");
                }
            }
            return add(actual, false, false, "");
        }

        @Override
        public String nameOf(F file)
        {
            return file.name;
        }

        @Override
        public OutputStream openTruncate(final F file) throws IOException
        {
            if (file.foreign)
            {
                throw new SecurityException("pas a nous");
            }
            if (file.orphan)
            {
                throw new FileNotFoundException("disparu");
            }
            file.content = new byte[0];
            return new ByteArrayOutputStream()
            {
                @Override
                public void close() throws IOException
                {
                    super.close();
                    file.content = toByteArray();
                }
            };
        }

        @Override
        public void delete(F file)
        {
            files.remove(file);
        }
    }

    @Test
    public void premiereEcriture_creeLeFichier() throws Exception
    {
        FakeStore store = new FakeStore();

        FixedNameExport.Result r = FixedNameExport.write(store, NAME, "{\"a\":1}".getBytes());

        assertEquals(FixedNameExport.Result.CREATED, r);
        assertEquals(1, store.files.size());
        assertEquals(NAME, store.files.get(0).name);
        assertEquals("{\"a\":1}", new String(store.files.get(0).content));
    }

    @Test
    public void secondeEcriture_metAJourSurPlaceSansDoublon() throws Exception
    {
        FakeStore store = new FakeStore();
        FixedNameExport.write(store, NAME, "{\"version\":\"ancienne et longue\"}".getBytes());

        FixedNameExport.Result r = FixedNameExport.write(store, NAME, "{\"v\":2}".getBytes());

        assertEquals(FixedNameExport.Result.UPDATED, r);
        assertEquals(1, store.files.size());
        // Contenu plus court : rien de l'ancien contenu ne doit rester.
        assertEquals("{\"v\":2}", new String(store.files.get(0).content));
    }

    @Test
    public void fichierDUneAutreInstallation_estSignaleSansDoublon() throws Exception
    {
        FakeStore store = new FakeStore();
        store.add(NAME, true, false, "ancien");

        FixedNameExport.Result r = FixedNameExport.write(store, NAME, "{}".getBytes());

        assertEquals(FixedNameExport.Result.BLOCKED_BY_FOREIGN_FILE, r);
        // Le doublon "(1)" cree par l'insert a ete supprime : il ne reste que l'ancien.
        assertEquals(1, store.files.size());
        assertEquals("ancien", new String(store.files.get(0).content));
    }

    @Test
    public void fichierVisibleMaisNonOuvrable_estSignale() throws Exception
    {
        // find() le voit mais l'ouverture leve SecurityException (droits perdus).
        FakeStore store = new FakeStore()
        {
            @Override
            public OutputStream openTruncate(F file) throws IOException
            {
                if (file.name.equals(NAME))
                {
                    throw new SecurityException("droits perdus");
                }
                return super.openTruncate(file);
            }
        };
        store.add(NAME, false, false, "ancien");

        FixedNameExport.Result r = FixedNameExport.write(store, NAME, "{}".getBytes());

        assertEquals(FixedNameExport.Result.BLOCKED_BY_FOREIGN_FILE, r);
        assertEquals(1, store.files.size());
    }

    @Test
    public void entreeOrpheline_estRemplaceeProprement() throws Exception
    {
        FakeStore store = new FakeStore();
        store.add(NAME, false, true, "fantome");

        FixedNameExport.Result r = FixedNameExport.write(store, NAME, "{\"ok\":true}".getBytes());

        assertEquals(FixedNameExport.Result.CREATED, r);
        assertEquals(1, store.files.size());
        assertEquals(NAME, store.files.get(0).name);
        assertEquals("{\"ok\":true}", new String(store.files.get(0).content));
    }

    @Test
    public void creationImpossible_leveUneException()
    {
        FakeStore store = new FakeStore();
        store.createReturnsNull = true;

        try
        {
            FixedNameExport.write(store, NAME, "{}".getBytes());
            fail("IOException attendue");
        }
        catch (IOException expected)
        {
            assertTrue(expected.getMessage().contains(NAME));
        }
        assertEquals(0, store.files.size());
    }

    @Test
    public void autresFichiersDuDossierSontIntacts() throws Exception
    {
        FakeStore store = new FakeStore();
        store.add("verifit2026-10-01.json", false, false, "export date");

        FixedNameExport.write(store, NAME, "{}".getBytes());
        FixedNameExport.write(store, NAME, "{\"v\":2}".getBytes());

        assertEquals(2, store.files.size());
        assertEquals("export date", new String(store.files.get(0).content));
        assertNull(store.find("inexistant.json"));
    }
}
