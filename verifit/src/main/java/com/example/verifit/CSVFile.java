package com.example.verifit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


public class CSVFile {
    InputStream inputStream;

    public CSVFile(InputStream inputStream){
        this.inputStream = inputStream;
    }

    public List read(){
        List resultList = new ArrayList();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        try {
            String csvLine;
            while ((csvLine = reader.readLine()) != null) {
                String[] row = parseLine(csvLine);
                resultList.add(row);
            }
        }
        catch (IOException ex) {
            throw new RuntimeException("Error in reading CSV file: "+ex);
        }
        finally {
            try {
                inputStream.close();
            }
            catch (IOException e) {
                throw new RuntimeException("Error while closing input stream: "+e);
            }
        }
        return resultList;
    }

    // Decoupe une ligne CSV sur les virgules, en respectant les champs entre guillemets
    // (lot C.3, 30/09/2026 : un nom d'exercice avec virgule, cas reel "Pendulum Squat,
    // Secu Low, 50°, Pieds Centraux", decalait toutes les colonnes et le backup ecrit
    // par l'app ne pouvait plus etre reimporte). Un champ n'est "entre guillemets" que
    // s'il COMMENCE par un guillemet ; "" a l'interieur vaut un guillemet. Comme
    // l'ancien String.split(","), les champs vides en fin de ligne sont ignores : un
    // CSV ecrit avant ce changement (sans guillemets) est lu exactement comme avant.
    static String[] parseLine(String line)
    {
        List<String> fields = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStart = true;

        for (int i = 0; i < line.length(); i++)
        {
            char c = line.charAt(i);
            if (inQuotes)
            {
                if (c == '"')
                {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"')
                    {
                        field.append('"');
                        i++;
                    }
                    else
                    {
                        inQuotes = false;
                    }
                }
                else
                {
                    field.append(c);
                }
            }
            else if (c == ',')
            {
                fields.add(field.toString());
                field.setLength(0);
                fieldStart = true;
                continue;
            }
            else if (c == '"' && fieldStart)
            {
                inQuotes = true;
            }
            else
            {
                field.append(c);
            }
            fieldStart = false;
        }
        fields.add(field.toString());

        int size = fields.size();
        while (size > 1 && fields.get(size - 1).isEmpty())
        {
            size--;
        }
        return fields.subList(0, size).toArray(new String[0]);
    }

    // Ecrit une valeur dans une cellule : entre guillemets si elle contient une virgule
    // ou un guillemet (guillemets internes doubles), telle quelle sinon.
    public static String field(String value)
    {
        if (value == null)
        {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0)
        {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}