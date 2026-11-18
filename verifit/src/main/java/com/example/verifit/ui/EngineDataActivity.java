package com.example.verifit.ui;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.example.verifit.EngineDataView;
import com.example.verifit.EngineRepository;
import com.example.verifit.EngineStorage;
import com.example.verifit.model.Exercise;

import java.util.ArrayList;
import java.util.List;

/**
 * Ecran « Donnees du moteur » (lot M3.4, 17/11/2026) : lecture seule, pour verifier l'import
 * contre l'Excel. Liste des exercices par cycle ; un appui ouvre le tableau RM -> poids et les
 * reglages de l'exercice (textes : EngineDataView). Rien ne se modifie ici (lots M5 et M6).
 */
public class EngineDataActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setTitle("Données du moteur");
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
        {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        EngineStorage.Loaded loaded = EngineRepository.get(this);
        List<Exercise> known = MainActivity.dataStorage == null
                ? new ArrayList<Exercise>() : MainActivity.dataStorage.getKnownExercises();
        List<EngineDataView.Row> rows = loaded.status == EngineStorage.Status.OK
                ? EngineDataView.rows(loaded.content, known) : new ArrayList<EngineDataView.Row>();

        // Liste a plat : un titre de section avant le premier exercice de chaque section.
        final List<Object> items = new ArrayList<Object>();
        String section = null;
        for (EngineDataView.Row row : rows)
        {
            if (!row.section.equals(section))
            {
                section = row.section;
                items.add(section);
            }
            items.add(row);
        }

        ListView list = new ListView(this);
        TextView header = text(EngineDataView.header(loaded), false);
        int pad = dp(16);
        header.setPadding(pad, pad, pad, pad);
        list.addHeaderView(header, null, false);
        list.setAdapter(new BaseAdapter()
        {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int position) { return items.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public int getViewTypeCount() { return 2; }
            @Override public int getItemViewType(int position) { return items.get(position) instanceof String ? 0 : 1; }
            @Override public boolean isEnabled(int position) { return !(items.get(position) instanceof String); }

            @Override
            public View getView(int position, View convertView, ViewGroup parent)
            {
                Object item = items.get(position);
                if (item instanceof String)
                {
                    TextView t = text((String) item, false);
                    t.setTypeface(Typeface.DEFAULT_BOLD);
                    t.setPadding(dp(16), dp(16), dp(16), dp(4));
                    return t;
                }
                EngineDataView.Row row = (EngineDataView.Row) item;
                TextView t = text(row.name + "\n" + row.subtitle, false);
                t.setPadding(dp(24), dp(10), dp(16), dp(10));
                return t;
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                Object item = parent.getItemAtPosition(position);
                if (item instanceof EngineDataView.Row)
                {
                    showDetail((EngineDataView.Row) item);
                }
            }
        });
        setContentView(list);
    }

    private void showDetail(EngineDataView.Row row)
    {
        TextView detail = text(row.detail, true);
        int pad = dp(16);
        detail.setPadding(pad, pad, pad, pad);
        detail.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(detail);
        new AlertDialog.Builder(this)
                .setView(scroll)
                .setPositiveButton("OK", null)
                .show();
    }

    private TextView text(String value, boolean monospace)
    {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, monospace ? 13 : 15);
        if (monospace)
        {
            t.setTypeface(Typeface.MONOSPACE);
        }
        return t;
    }

    private int dp(int value)
    {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == android.R.id.home)
        {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
