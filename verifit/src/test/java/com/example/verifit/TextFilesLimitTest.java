package com.example.verifit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Lot S2 de la revue securite du 27/10/2026 (point M1) : taille maximale lue, sans
// changer la lecture d'un fichier normal (l'empreinte des plans importes en depend,
// voir ImportedPlans).
public class TextFilesLimitTest
{
    private static ByteArrayInputStream stream(String text)
    {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void lecture_normale_inchangee()
    throws IOException
    {
        assertEquals("a\nb\n", TextFiles.readAll(stream("a\r\nb"), 100));
        assertEquals("a\nb\n", TextFiles.readAll(stream("a\r\nb")));
        assertEquals("é\n", TextFiles.readAll(stream("é"), 1));
    }

    @Test
    public void fichier_trop_gros_refuse()
    {
        try
        {
            TextFiles.readAll(stream("0123456789"), 5);
            fail("IOException attendue");
        }
        catch (IOException e)
        {
            assertTrue(e.getMessage().startsWith("fichier trop gros"));
        }
    }

    @Test
    public void une_seule_ligne_geante_est_arretee_pendant_la_lecture()
    {
        // 3 millions de caracteres sans retour a la ligne, limite de 1 million.
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 3000000; i++)
        {
            big.append('x');
        }
        try
        {
            TextFiles.readAll(stream(big.toString()), TextFiles.MAX_SESSION_CHARS);
            fail("IOException attendue");
        }
        catch (IOException e)
        {
            assertTrue(e.getMessage().startsWith("fichier trop gros"));
        }
    }
}
